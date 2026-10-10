package com.teppe21.finances.stocks.data.provider

import com.teppe21.finances.core.net.HttpClient
import com.teppe21.finances.core.net.HttpFailure
import com.teppe21.finances.core.net.HttpResponse
import com.teppe21.finances.core.net.UrlConnectionHttpClient
import com.teppe21.finances.stocks.domain.HistoryRange
import com.teppe21.finances.stocks.domain.MarketDataError
import com.teppe21.finances.stocks.domain.MarketDataProvider
import com.teppe21.finances.stocks.domain.MarketResult
import com.teppe21.finances.stocks.domain.MarketStatus
import com.teppe21.finances.stocks.domain.PriceHistory
import com.teppe21.finances.stocks.domain.PricePoint
import com.teppe21.finances.stocks.domain.Quote
import com.teppe21.finances.stocks.domain.StockSymbol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant

/**
 * Finnhub REST API (https://finnhub.io/docs/api) with a key supplied by the user.
 *
 * The key travels in the X-Finnhub-Token header, never in the URL. Requests contain only
 * the symbol or search text; no personal finance data is sent.
 */
class FinnhubProvider(
    private val http: HttpClient = UrlConnectionHttpClient(),
    private val baseUrl: String = "https://finnhub.io/api/v1"
) : MarketDataProvider {

    override val name: String = "Finnhub"

    private suspend fun call(path: String, apiKey: String): MarketResult<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext MarketResult.Err(MarketDataError.MissingKey)
        val response: HttpResponse = try {
            http.get("$baseUrl$path", mapOf("X-Finnhub-Token" to apiKey))
        } catch (e: HttpFailure.Timeout) {
            return@withContext MarketResult.Err(MarketDataError.Timeout)
        } catch (e: HttpFailure) {
            return@withContext MarketResult.Err(MarketDataError.Offline)
        }
        mapStatus(response)
    }

    override suspend fun search(query: String, apiKey: String): MarketResult<List<StockSymbol>> {
        val q = query.trim()
        if (q.isEmpty()) return MarketResult.Ok(emptyList())
        return call("/search?q=${enc(q)}", apiKey).map { parseSearch(it) }
    }

    override suspend fun quote(symbol: String, apiKey: String): MarketResult<Quote> =
        call("/quote?symbol=${enc(symbol)}", apiKey).map { parseQuote(symbol, it, name) }

    override suspend fun profile(symbol: String, apiKey: String): MarketResult<StockSymbol> =
        call("/stock/profile2?symbol=${enc(symbol)}", apiKey).map { parseProfile(symbol, it) }

    override suspend fun history(symbol: String, range: HistoryRange, now: Instant, apiKey: String): MarketResult<PriceHistory> {
        val from = now.minus(range.span).epochSecond
        val to = now.epochSecond
        return call("/stock/candle?symbol=${enc(symbol)}&resolution=${resolution(range)}&from=$from&to=$to", apiKey)
            .map { parseCandles(symbol, range, it) }
    }

    override suspend fun marketStatus(exchange: String, apiKey: String): MarketResult<MarketStatus> =
        call("/stock/market-status?exchange=${enc(exchange)}", apiKey).map { parseMarketStatus(it) }

    companion object {
        private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

        fun resolution(range: HistoryRange): String = when (range) {
            HistoryRange.DAY_1 -> "5"
            HistoryRange.WEEK_1 -> "30"
            HistoryRange.MONTH_1, HistoryRange.MONTH_3, HistoryRange.YEAR_1 -> "D"
        }

        fun mapStatus(response: HttpResponse): MarketResult<String> = when (response.code) {
            in 200..299 -> MarketResult.Ok(response.body)
            401 -> MarketResult.Err(MarketDataError.InvalidKey)
            403 -> MarketResult.Err(MarketDataError.NotOnPlan)
            429 -> MarketResult.Err(
                MarketDataError.RateLimited(response.headers["retry-after"]?.trim()?.toLongOrNull()?.let { Duration.ofSeconds(it) })
            )
            else -> MarketResult.Err(MarketDataError.ProviderUnavailable(response.code))
        }

        /** Decimal from a JSON number or string without going through Double formatting rules. */
        private fun JSONObject.decimal(key: String): BigDecimal? {
            if (!has(key) || isNull(key)) return null
            return opt(key)?.toString()?.toBigDecimalOrNull()
        }

        private inline fun <T> parsing(block: () -> MarketResult<T>): MarketResult<T> = try {
            block()
        } catch (e: JSONException) {
            MarketResult.Err(MarketDataError.MalformedResponse)
        } catch (e: NumberFormatException) {
            MarketResult.Err(MarketDataError.MalformedResponse)
        } catch (e: ArithmeticException) {
            MarketResult.Err(MarketDataError.MalformedResponse)
        }

        fun parseSearch(body: String): MarketResult<List<StockSymbol>> = parsing {
            val json = JSONObject(body)
            val arr = json.optJSONArray("result") ?: JSONArray()
            val list = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val symbol = o.optString("symbol").trim()
                if (symbol.isEmpty()) return@mapNotNull null
                StockSymbol(
                    symbol = symbol,
                    displaySymbol = o.optString("displaySymbol").ifBlank { symbol },
                    name = o.optString("description").ifBlank { null },
                    type = o.optString("type").ifBlank { null }
                )
            }.distinctBy { it.symbol }
            MarketResult.Ok(list)
        }

        fun parseQuote(symbol: String, body: String, provider: String): MarketResult<Quote> = parsing {
            val o = JSONObject(body)
            val price = o.decimal("c")
            val t = o.optLong("t", 0L)
            // Finnhub answers unknown symbols with zeros instead of an error.
            if (price == null || price.signum() <= 0 || t <= 0L) {
                return@parsing MarketResult.Err(MarketDataError.UnknownSymbol)
            }
            MarketResult.Ok(
                Quote(
                    symbol = symbol,
                    price = price,
                    change = o.decimal("d"),
                    changePercent = o.decimal("dp"),
                    open = o.decimal("o")?.takeIf { it.signum() > 0 },
                    high = o.decimal("h")?.takeIf { it.signum() > 0 },
                    low = o.decimal("l")?.takeIf { it.signum() > 0 },
                    previousClose = o.decimal("pc")?.takeIf { it.signum() > 0 },
                    quoteTime = Instant.ofEpochSecond(t),
                    provider = provider
                )
            )
        }

        fun parseProfile(symbol: String, body: String): MarketResult<StockSymbol> = parsing {
            val o = JSONObject(body)
            if (o.length() == 0) return@parsing MarketResult.Err(MarketDataError.UnknownSymbol)
            MarketResult.Ok(
                StockSymbol(
                    symbol = symbol,
                    displaySymbol = o.optString("ticker").ifBlank { symbol },
                    name = o.optString("name").ifBlank { null },
                    exchange = o.optString("exchange").ifBlank { null },
                    currency = o.optString("currency").ifBlank { null }?.uppercase()
                )
            )
        }

        fun parseCandles(symbol: String, range: HistoryRange, body: String): MarketResult<PriceHistory> = parsing {
            val o = JSONObject(body)
            if (o.has("error")) return@parsing MarketResult.Err(MarketDataError.NotOnPlan)
            when (o.optString("s")) {
                "no_data" -> return@parsing MarketResult.Err(MarketDataError.NoData)
                "ok" -> Unit
                else -> return@parsing MarketResult.Err(MarketDataError.MalformedResponse)
            }
            val closes = o.getJSONArray("c")
            val times = o.getJSONArray("t")
            if (closes.length() != times.length()) return@parsing MarketResult.Err(MarketDataError.MalformedResponse)
            val points = (0 until times.length()).mapNotNull { i ->
                val time = times.optLong(i, 0L)
                val close = closes.opt(i)?.toString()?.toBigDecimalOrNull()
                if (time <= 0L || close == null || close.signum() <= 0) null else PricePoint(Instant.ofEpochSecond(time), close)
            }.sortedBy { it.time }
            if (points.isEmpty()) MarketResult.Err(MarketDataError.NoData)
            else MarketResult.Ok(PriceHistory(symbol, range, points))
        }

        fun parseMarketStatus(body: String, now: Instant = Instant.now()): MarketResult<MarketStatus> = parsing {
            val o = JSONObject(body)
            val exchange = o.optString("exchange").ifBlank { return@parsing MarketResult.Err(MarketDataError.MalformedResponse) }
            MarketResult.Ok(
                MarketStatus(
                    exchange = exchange,
                    isOpen = if (o.has("isOpen") && !o.isNull("isOpen")) o.getBoolean("isOpen") else null,
                    session = if (o.isNull("session")) null else o.optString("session").ifBlank { null },
                    timezone = o.optString("timezone").ifBlank { null },
                    holiday = if (o.isNull("holiday")) null else o.optString("holiday").ifBlank { null },
                    checkedAt = now
                )
            )
        }
    }
}

inline fun <T, R> MarketResult<T>.map(transform: (T) -> MarketResult<R>): MarketResult<R> = when (this) {
    is MarketResult.Ok -> transform(value)
    is MarketResult.Err -> this
}
