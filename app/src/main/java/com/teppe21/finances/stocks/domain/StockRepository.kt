package com.teppe21.finances.stocks.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.time.Duration
import java.time.Instant

/** Persisted watchlist entry. */
data class WatchedStock(
    val symbol: String,
    val displaySymbol: String,
    val name: String?,
    val exchange: String?,
    val currency: String?,
    val position: Int,
    val addedAt: Instant
)

interface WatchlistStore {
    fun observe(): Flow<List<WatchedStock>>
    suspend fun all(): List<WatchedStock>
    suspend fun nextPosition(): Int
    /** Returns false if the symbol is already present. */
    suspend fun insertIfAbsent(stock: WatchedStock): Boolean
    suspend fun remove(symbol: String)
    suspend fun setPosition(symbol: String, position: Int)
}

interface QuoteCache {
    fun observe(): Flow<Map<String, Quote>>
    suspend fun put(quote: Quote)
    suspend fun remove(symbol: String)
}

fun interface ApiKeySource {
    suspend fun apiKey(): String?
}

/** One watchlist row as shown in the UI. */
data class WatchlistRow(
    val stock: WatchedStock,
    val quote: Quote?,
    val status: DataStatus
)

sealed interface AddResult {
    data object Added : AddResult
    data object AlreadyOnWatchlist : AddResult
    data object LimitReached : AddResult
}

data class RefreshReport(
    val updated: Int,
    val error: MarketDataError?,
    val marketOpen: Boolean?
)

/**
 * Watchlist and quotes. Read-only market information: nothing here touches accounts,
 * transactions or balances.
 */
class StockRepository(
    private val provider: MarketDataProvider,
    private val watchlist: WatchlistStore,
    private val quotes: QuoteCache,
    private val keys: ApiKeySource,
    private val clock: () -> Instant = Instant::now
) {
    private val _usMarket = MutableStateFlow<MarketStatus?>(null)
    val usMarketStatus: StateFlow<MarketStatus?> = _usMarket.asStateFlow()

    private val _lastError = MutableStateFlow<MarketDataError?>(null)
    val lastError: StateFlow<MarketDataError?> = _lastError.asStateFlow()

    val providerName: String get() = provider.name

    fun rows(now: () -> Instant = clock): Flow<List<WatchlistRow>> =
        combine(watchlist.observe(), quotes.observe(), _usMarket, _lastError) { stocks, cached, market, error ->
            stocks.map { stock ->
                val quote = cached[stock.symbol]
                val open = if (RefreshPolicy.usesUsMarketStatus(stock.symbol)) market?.isOpen else null
                val status = when {
                    quote != null -> Freshness.classify(quote.quoteTime, now(), open)
                    error != null -> error.toStatus()
                    else -> DataStatus.UNAVAILABLE
                }
                WatchlistRow(stock, quote, status)
            }
        }

    suspend fun search(query: String): MarketResult<List<StockSymbol>> {
        val key = keys.apiKey() ?: return MarketResult.Err(MarketDataError.MissingKey)
        return provider.search(query, key)
    }

    suspend fun add(symbol: StockSymbol): AddResult {
        val current = watchlist.all()
        if (current.any { it.symbol.equals(symbol.symbol, ignoreCase = true) }) return AddResult.AlreadyOnWatchlist
        if (current.size >= RefreshPolicy.MAX_WATCHLIST_SIZE) return AddResult.LimitReached

        // Best effort: the profile adds exchange and trading currency when the plan allows it.
        val key = keys.apiKey()
        val profile = if (key != null) (provider.profile(symbol.symbol, key) as? MarketResult.Ok)?.value else null
        val stock = WatchedStock(
            symbol = symbol.symbol.uppercase(),
            displaySymbol = symbol.displaySymbol,
            name = profile?.name ?: symbol.name,
            exchange = profile?.exchange ?: symbol.exchange,
            currency = profile?.currency ?: symbol.currency,
            position = watchlist.nextPosition(),
            addedAt = clock()
        )
        return if (watchlist.insertIfAbsent(stock)) AddResult.Added else AddResult.AlreadyOnWatchlist
    }

    suspend fun remove(symbol: String) {
        watchlist.remove(symbol)
        quotes.remove(symbol)
    }

    suspend fun move(symbol: String, up: Boolean) {
        val list = watchlist.all().toMutableList()
        val index = list.indexOfFirst { it.symbol == symbol }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in list.indices) return
        val moved = list.removeAt(index)
        list.add(target, moved)
        list.forEachIndexed { i, s -> if (s.position != i) watchlist.setPosition(s.symbol, i) }
    }

    /**
     * Refreshes quotes for the whole watchlist. Stops at the first key or rate-limit error
     * so the provider's limits are not hammered.
     */
    suspend fun refreshAll(): RefreshReport {
        val key = keys.apiKey()
        if (key.isNullOrBlank()) {
            _lastError.value = MarketDataError.MissingKey
            return RefreshReport(0, MarketDataError.MissingKey, null)
        }
        val stocks = watchlist.all()
        if (stocks.isEmpty()) {
            _lastError.value = null
            return RefreshReport(0, null, _usMarket.value?.isOpen)
        }

        if (stocks.any { RefreshPolicy.usesUsMarketStatus(it.symbol) }) {
            when (val status = provider.marketStatus("US", key)) {
                is MarketResult.Ok -> _usMarket.value = status.value
                is MarketResult.Err -> if (status.error.isFatal()) return fail(status.error)
            }
        }

        var updated = 0
        var softError: MarketDataError? = null
        for (stock in stocks) {
            when (val result = provider.quote(stock.symbol, key)) {
                is MarketResult.Ok -> {
                    quotes.put(result.value)
                    updated++
                }
                is MarketResult.Err -> {
                    if (result.error.isFatal()) return fail(result.error, updated)
                    softError = result.error
                }
            }
        }
        _lastError.value = if (updated == 0) softError else null
        return RefreshReport(updated, softError, _usMarket.value?.isOpen)
    }

    suspend fun refreshOne(symbol: String): MarketResult<Quote> {
        val key = keys.apiKey() ?: return MarketResult.Err(MarketDataError.MissingKey)
        val result = provider.quote(symbol, key)
        if (result is MarketResult.Ok) quotes.put(result.value)
        return result
    }

    suspend fun history(symbol: String, range: HistoryRange): MarketResult<PriceHistory> {
        val key = keys.apiKey() ?: return MarketResult.Err(MarketDataError.MissingKey)
        return provider.history(symbol, range, clock(), key)
    }

    /** Checks a key without storing it. */
    suspend fun validateKey(candidate: String): MarketResult<Unit> {
        if (candidate.isBlank()) return MarketResult.Err(MarketDataError.MissingKey)
        return when (val r = provider.marketStatus("US", candidate.trim())) {
            is MarketResult.Ok -> MarketResult.Ok(Unit)
            is MarketResult.Err -> r
        }
    }

    fun clearError() {
        _lastError.value = null
    }

    private fun fail(error: MarketDataError, updated: Int = 0): RefreshReport {
        _lastError.value = error
        return RefreshReport(updated, error, _usMarket.value?.isOpen)
    }

    private fun MarketDataError.isFatal(): Boolean =
        this == MarketDataError.MissingKey || this == MarketDataError.InvalidKey ||
            this is MarketDataError.RateLimited || this == MarketDataError.Offline

    companion object {
        fun retryAfter(error: MarketDataError?): Duration? =
            (error as? MarketDataError.RateLimited)?.let { it.retryAfter ?: Duration.ofMinutes(1) }
    }
}
