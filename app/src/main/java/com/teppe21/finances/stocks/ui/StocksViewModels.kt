package com.teppe21.finances.stocks.ui

import com.teppe21.finances.R
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.stocks.domain.AddResult
import com.teppe21.finances.stocks.domain.HistoryRange
import com.teppe21.finances.stocks.domain.MarketDataError
import com.teppe21.finances.stocks.domain.MarketResult
import com.teppe21.finances.stocks.domain.MarketStatus
import com.teppe21.finances.stocks.domain.PriceHistory
import com.teppe21.finances.stocks.domain.RefreshPolicy
import com.teppe21.finances.stocks.domain.StockRepository
import com.teppe21.finances.stocks.domain.StockSymbol
import com.teppe21.finances.stocks.domain.WatchlistRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/** Re-evaluates freshness labels as time passes, even without new data. */
private val ticker = flow {
    while (true) {
        emit(Instant.now())
        delay(15_000)
    }
}

data class StocksUiState(
    val hasKey: Boolean = false,
    val rows: List<WatchlistRow> = emptyList(),
    val market: MarketStatus? = null,
    val error: MarketDataError? = null,
    val refreshing: Boolean = false,
    val lastRefresh: Instant? = null,
    val loaded: Boolean = false
)

data class SearchState(
    val query: String = "",
    val results: List<StockSymbol> = emptyList(),
    val searching: Boolean = false,
    val error: MarketDataError? = null,
    /** A string resource with the symbol as its argument. */
    val message: Pair<Int, String>? = null
)

class StocksViewModel : ViewModel() {

    private val app = FinancesApp.instance
    private val repo: StockRepository = app.stockRepository

    private val refreshing = MutableStateFlow(false)
    private val lastRefresh = MutableStateFlow<Instant?>(null)

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()
    private var searchJob: Job? = null

    val state: StateFlow<StocksUiState> = combine(
        repo.rows(),
        app.preferencesRepository.preferencesFlow.map { it.hasStocksApiKey },
        combine(repo.usMarketStatus, repo.lastError, ::Pair),
        combine(refreshing, lastRefresh, ::Pair),
        ticker
    ) { rows, hasKey, marketAndError, refreshState, _ ->
        StocksUiState(
            hasKey = hasKey,
            rows = rows,
            market = marketAndError.first,
            error = marketAndError.second,
            refreshing = refreshState.first,
            lastRefresh = refreshState.second,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StocksUiState())

    /**
     * Polls while the caller's coroutine is active (the screen collects it only while
     * STARTED). Stops automatic polling while the market is closed and backs off on errors
     * and rate limits.
     */
    suspend fun autoRefreshWhileVisible() {
        var failures = 0
        while (true) {
            val report = refreshNow()
            failures = if (report == null || report.error == null) 0 else failures + 1
            val wait = RefreshPolicy.nextDelay(
                marketOpen = report?.marketOpen,
                consecutiveFailures = if (report?.error is MarketDataError.RateLimited) 0 else failures,
                rateLimitRetryAfter = StockRepository.retryAfter(report?.error)
            ) ?: return
            if (report?.error == MarketDataError.MissingKey || report?.error == MarketDataError.InvalidKey) return
            delay(wait.toMillis())
        }
    }

    private suspend fun refreshNow(): com.teppe21.finances.stocks.domain.RefreshReport? {
        if (refreshing.value) return null
        refreshing.value = true
        return try {
            repo.refreshAll().also { lastRefresh.value = Instant.now() }
        } finally {
            refreshing.value = false
        }
    }

    fun manualRefresh() {
        viewModelScope.launch { refreshNow() }
    }

    fun setQuery(query: String) {
        _search.value = _search.value.copy(query = query, message = null)
        searchJob?.cancel()
        if (query.trim().length < 1) {
            _search.value = _search.value.copy(results = emptyList(), searching = false, error = null)
            return
        }
        searchJob = viewModelScope.launch {
            delay(450) // debounce: one request per pause in typing
            _search.value = _search.value.copy(searching = true, error = null)
            when (val r = repo.search(query)) {
                is MarketResult.Ok -> _search.value = _search.value.copy(results = r.value.take(20), searching = false)
                is MarketResult.Err -> _search.value = _search.value.copy(results = emptyList(), searching = false, error = r.error)
            }
        }
    }

    fun add(symbol: StockSymbol) {
        viewModelScope.launch {
            val message = when (repo.add(symbol)) {
                AddResult.Added -> R.string.stock_added to symbol.displaySymbol
                AddResult.AlreadyOnWatchlist -> R.string.stock_already_added to symbol.displaySymbol
                AddResult.LimitReached -> R.string.stock_limit_reached to RefreshPolicy.MAX_WATCHLIST_SIZE.toString()
            }
            _search.value = SearchState(message = message)
            repo.refreshOne(symbol.symbol)
        }
    }

    fun move(symbol: String, up: Boolean) {
        viewModelScope.launch { repo.move(symbol, up) }
    }
}

data class DetailState(
    val symbol: String = "",
    val row: WatchlistRow? = null,
    val market: MarketStatus? = null,
    val range: HistoryRange = HistoryRange.MONTH_1,
    val history: PriceHistory? = null,
    val historyLoading: Boolean = false,
    val historyError: MarketDataError? = null
)

class StockDetailViewModel : ViewModel() {
    private val app = FinancesApp.instance
    private val repo = app.stockRepository

    private val symbol = MutableStateFlow("")
    private val range = MutableStateFlow(HistoryRange.MONTH_1)
    private val history = MutableStateFlow<Pair<PriceHistory?, MarketDataError?>>(null to null)
    private val loading = MutableStateFlow(false)
    private val historyCache = mutableMapOf<Pair<String, HistoryRange>, PriceHistory>()

    val state: StateFlow<DetailState> = combine(
        combine(symbol, repo.rows(), ::Pair),
        repo.usMarketStatus,
        range,
        history,
        combine(loading, ticker) { l, _ -> l }
    ) { symbolAndRows, market, r, h, isLoading ->
        val (sym, rows) = symbolAndRows
        DetailState(
            symbol = sym,
            row = rows.firstOrNull { it.stock.symbol == sym },
            market = market,
            range = r,
            history = h.first,
            historyLoading = isLoading,
            historyError = h.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailState())

    fun load(newSymbol: String) {
        if (symbol.value == newSymbol) return
        symbol.value = newSymbol
        viewModelScope.launch { repo.refreshOne(newSymbol) }
        loadHistory(range.value)
    }

    fun selectRange(newRange: HistoryRange) {
        range.value = newRange
        loadHistory(newRange)
    }

    private fun loadHistory(r: HistoryRange) {
        val sym = symbol.value
        historyCache[sym to r]?.let {
            history.value = it to null
            return
        }
        viewModelScope.launch {
            loading.value = true
            history.value = null to null
            when (val result = repo.history(sym, r)) {
                is MarketResult.Ok -> {
                    historyCache[sym to r] = result.value
                    history.value = result.value to null
                }
                is MarketResult.Err -> history.value = null to result.error
            }
            loading.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch { repo.refreshOne(symbol.value) }
    }

    fun remove(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.remove(symbol.value)
            onDone()
        }
    }
}

sealed interface KeyCheck {
    data object Idle : KeyCheck
    data object Checking : KeyCheck
    data object Saved : KeyCheck
    data class Failed(@StringRes val message: Int, val canSaveAnyway: Boolean) : KeyCheck
}

class StocksSetupViewModel : ViewModel() {
    private val app = FinancesApp.instance

    val hasKey: StateFlow<Boolean> = app.preferencesRepository.preferencesFlow.map { it.hasStocksApiKey }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _check = MutableStateFlow<KeyCheck>(KeyCheck.Idle)
    val check: StateFlow<KeyCheck> = _check.asStateFlow()

    fun validateAndSave(key: String) {
        viewModelScope.launch {
            _check.value = KeyCheck.Checking
            _check.value = when (val r = app.stockRepository.validateKey(key)) {
                is MarketResult.Ok -> {
                    app.preferencesRepository.setStocksApiKey(key)
                    app.stockRepository.clearError()
                    KeyCheck.Saved
                }
                is MarketResult.Err -> when (r.error) {
                    MarketDataError.InvalidKey -> KeyCheck.Failed(R.string.stock_key_rejected, canSaveAnyway = false)
                    MarketDataError.MissingKey -> KeyCheck.Failed(R.string.stock_key_missing, canSaveAnyway = false)
                    MarketDataError.Offline, MarketDataError.Timeout ->
                        KeyCheck.Failed(R.string.stock_key_unreachable, canSaveAnyway = true)
                    else -> KeyCheck.Failed(R.string.stock_key_unconfirmed, canSaveAnyway = true)
                }
            }
        }
    }

    fun saveWithoutCheck(key: String) {
        viewModelScope.launch {
            app.preferencesRepository.setStocksApiKey(key)
            app.stockRepository.clearError()
            _check.value = KeyCheck.Saved
        }
    }

    fun removeKey() {
        viewModelScope.launch {
            app.preferencesRepository.clearStocksApiKey()
            _check.value = KeyCheck.Idle
        }
    }
}
