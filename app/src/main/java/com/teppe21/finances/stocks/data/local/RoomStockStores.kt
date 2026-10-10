package com.teppe21.finances.stocks.data.local

import com.teppe21.finances.data.local.dao.StockQuoteDao
import com.teppe21.finances.data.local.dao.WatchlistDao
import com.teppe21.finances.data.local.entity.StockQuoteEntity
import com.teppe21.finances.data.local.entity.WatchlistEntity
import com.teppe21.finances.stocks.domain.Quote
import com.teppe21.finances.stocks.domain.QuoteCache
import com.teppe21.finances.stocks.domain.WatchedStock
import com.teppe21.finances.stocks.domain.WatchlistStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class RoomWatchlistStore(private val dao: WatchlistDao) : WatchlistStore {
    override fun observe(): Flow<List<WatchedStock>> = dao.getAllFlow().map { list -> list.map { it.toDomain() } }
    override suspend fun all(): List<WatchedStock> = dao.getAll().map { it.toDomain() }
    override suspend fun nextPosition(): Int = dao.maxPosition() + 1
    override suspend fun insertIfAbsent(stock: WatchedStock): Boolean = dao.insertIgnore(stock.toEntity()) != -1L
    override suspend fun remove(symbol: String) = dao.delete(symbol)
    override suspend fun setPosition(symbol: String, position: Int) = dao.updatePosition(symbol, position)
}

class RoomQuoteCache(private val dao: StockQuoteDao) : QuoteCache {
    override fun observe(): Flow<Map<String, Quote>> =
        dao.getAllFlow().map { list -> list.mapNotNull { it.toDomain() }.associateBy { it.symbol } }

    override suspend fun put(quote: Quote) = dao.upsert(quote.toEntity(Instant.now()))
    override suspend fun remove(symbol: String) = dao.delete(symbol)
}

private fun WatchlistEntity.toDomain() = WatchedStock(symbol, displaySymbol, name, exchange, currency, position, addedAt)

private fun WatchedStock.toEntity() = WatchlistEntity(symbol, displaySymbol, name, exchange, currency, position, addedAt)

private fun StockQuoteEntity.toDomain(): Quote? {
    val p = price.toBigDecimalOrNull() ?: return null
    return Quote(
        symbol = symbol,
        price = p,
        change = change?.toBigDecimalOrNull(),
        changePercent = changePercent?.toBigDecimalOrNull(),
        open = open?.toBigDecimalOrNull(),
        high = high?.toBigDecimalOrNull(),
        low = low?.toBigDecimalOrNull(),
        previousClose = previousClose?.toBigDecimalOrNull(),
        quoteTime = quoteTime,
        provider = provider
    )
}

private fun Quote.toEntity(fetchedAt: Instant) = StockQuoteEntity(
    symbol = symbol,
    price = price.toPlainString(),
    change = change?.toPlainString(),
    changePercent = changePercent?.toPlainString(),
    open = open?.toPlainString(),
    high = high?.toPlainString(),
    low = low?.toPlainString(),
    previousClose = previousClose?.toPlainString(),
    quoteTime = quoteTime,
    fetchedAt = fetchedAt,
    provider = provider,
    currency = null
)
