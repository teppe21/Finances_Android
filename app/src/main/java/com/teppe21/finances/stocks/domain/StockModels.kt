package com.teppe21.finances.stocks.domain

import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

data class StockSymbol(
    /** Provider symbol used for requests (e.g. "AAPL"). */
    val symbol: String,
    val displaySymbol: String,
    val name: String?,
    val type: String? = null,
    val exchange: String? = null,
    val currency: String? = null
)

/** A price snapshot exactly as delivered by the provider. */
data class Quote(
    val symbol: String,
    val price: BigDecimal,
    val change: BigDecimal?,
    val changePercent: BigDecimal?,
    val open: BigDecimal?,
    val high: BigDecimal?,
    val low: BigDecimal?,
    val previousClose: BigDecimal?,
    /** Provider timestamp of the last price. */
    val quoteTime: Instant,
    val provider: String
)

data class MarketStatus(
    val exchange: String,
    /** null when the provider did not say. */
    val isOpen: Boolean?,
    val session: String?,
    val timezone: String?,
    val holiday: String?,
    val checkedAt: Instant
)

data class PricePoint(val time: Instant, val close: BigDecimal)

enum class HistoryRange(val label: String, val span: Duration) {
    DAY_1("1D", Duration.ofDays(1)),
    WEEK_1("1W", Duration.ofDays(7)),
    MONTH_1("1M", Duration.ofDays(31)),
    MONTH_3("3M", Duration.ofDays(92)),
    YEAR_1("1Y", Duration.ofDays(366))
}

data class PriceHistory(val symbol: String, val range: HistoryRange, val points: List<PricePoint>)

/**
 * What the UI may claim about a quote. LIVE is only used when the provider's own
 * timestamp shows a price from the last minutes while the market is open.
 */
enum class DataStatus(val label: String) {
    LIVE("Real-time"),
    DELAYED("Delayed"),
    LAST_CLOSE("Last close"),
    STALE("Stale"),
    UNAVAILABLE("Unavailable"),
    API_KEY_REQUIRED("API key required"),
    API_KEY_INVALID("API key rejected"),
    RATE_LIMITED("Rate limited"),
    NOT_ON_PLAN("Not included in plan")
}

sealed interface MarketDataError {
    data object MissingKey : MarketDataError
    data object InvalidKey : MarketDataError
    data class RateLimited(val retryAfter: Duration?) : MarketDataError
    /** HTTP 403: the provider plan does not include this data. */
    data object NotOnPlan : MarketDataError
    data object UnknownSymbol : MarketDataError
    data object NoData : MarketDataError
    data object Offline : MarketDataError
    data object Timeout : MarketDataError
    data class ProviderUnavailable(val httpCode: Int) : MarketDataError
    data object MalformedResponse : MarketDataError
}

sealed interface MarketResult<out T> {
    data class Ok<T>(val value: T) : MarketResult<T>
    data class Err(val error: MarketDataError) : MarketResult<Nothing>
}

fun MarketDataError.toStatus(): DataStatus = when (this) {
    MarketDataError.MissingKey -> DataStatus.API_KEY_REQUIRED
    MarketDataError.InvalidKey -> DataStatus.API_KEY_INVALID
    is MarketDataError.RateLimited -> DataStatus.RATE_LIMITED
    MarketDataError.NotOnPlan -> DataStatus.NOT_ON_PLAN
    else -> DataStatus.UNAVAILABLE
}

/** Operations the watchlist needs from a market data provider. */
interface MarketDataProvider {
    val name: String
    suspend fun search(query: String, apiKey: String): MarketResult<List<StockSymbol>>
    suspend fun quote(symbol: String, apiKey: String): MarketResult<Quote>
    suspend fun profile(symbol: String, apiKey: String): MarketResult<StockSymbol>
    suspend fun history(symbol: String, range: HistoryRange, now: Instant, apiKey: String): MarketResult<PriceHistory>
    suspend fun marketStatus(exchange: String, apiKey: String): MarketResult<MarketStatus>
}
