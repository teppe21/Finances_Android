package com.teppe21.finances

import com.teppe21.finances.core.net.HttpClient
import com.teppe21.finances.core.net.HttpFailure
import com.teppe21.finances.core.net.HttpResponse
import com.teppe21.finances.stocks.data.provider.FinnhubProvider
import com.teppe21.finances.stocks.domain.AddResult
import com.teppe21.finances.stocks.domain.ApiKeySource
import com.teppe21.finances.stocks.domain.DataStatus
import com.teppe21.finances.stocks.domain.Freshness
import com.teppe21.finances.stocks.domain.HistoryRange
import com.teppe21.finances.stocks.domain.MarketDataError
import com.teppe21.finances.stocks.domain.MarketResult
import com.teppe21.finances.stocks.domain.Quote
import com.teppe21.finances.stocks.domain.QuoteCache
import com.teppe21.finances.stocks.domain.RefreshPolicy
import com.teppe21.finances.stocks.domain.StockRepository
import com.teppe21.finances.stocks.domain.StockSymbol
import com.teppe21.finances.stocks.domain.WatchedStock
import com.teppe21.finances.stocks.domain.WatchlistStore
import com.teppe21.finances.support.InMemoryTransactionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class StocksTest {

    private val now = Instant.parse("2026-09-17T15:00:00Z")

    /** Canned HTTP responses keyed by path prefix; records every request. */
    private class FakeHttp(var routes: Map<String, HttpResponse>, var failure: HttpFailure? = null) : HttpClient {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        override fun get(url: String, headers: Map<String, String>): HttpResponse {
            requests += url to headers
            failure?.let { throw it }
            val path = url.removePrefix("https://finnhub.io/api/v1")
            return routes.entries.firstOrNull { path.startsWith(it.key) }?.value ?: HttpResponse(404, "{}")
        }
    }

    private fun ok(body: String) = HttpResponse(200, body)

    private val quoteJson = """{"c":261.74,"d":-1.52,"dp":-0.5774,"h":263.31,"l":260.68,"o":262.5,"pc":263.26,"t":1789570800}"""
    private val openJson = """{"exchange":"US","holiday":null,"isOpen":true,"session":"regular","timezone":"America/New_York","t":1789570800}"""
    private val closedJson = """{"exchange":"US","holiday":null,"isOpen":false,"session":null,"timezone":"America/New_York","t":1789570800}"""

    // ---- Provider parsing -------------------------------------------------------------

    @Test
    fun `quote json is parsed exactly`() {
        val q = (FinnhubProvider.parseQuote("AAPL", quoteJson, "Finnhub") as MarketResult.Ok).value
        assertEquals(BigDecimal("261.74"), q.price)
        assertEquals(BigDecimal("-1.52"), q.change)
        assertEquals(BigDecimal("-0.5774"), q.changePercent)
        assertEquals(BigDecimal("263.26"), q.previousClose)
        assertEquals(Instant.ofEpochSecond(1789570800), q.quoteTime)
    }

    @Test
    fun `unknown symbol zeros and null fields are not turned into prices`() {
        val zeros = """{"c":0,"d":null,"dp":null,"h":0,"l":0,"o":0,"pc":0,"t":0}"""
        assertEquals(MarketResult.Err(MarketDataError.UnknownSymbol), FinnhubProvider.parseQuote("ZZZZ", zeros, "Finnhub"))
        val partial = (FinnhubProvider.parseQuote("X", """{"c":10.5,"d":null,"dp":null,"t":1789570800}""", "Finnhub") as MarketResult.Ok).value
        assertNull(partial.change)
        assertNull(partial.open)
        assertEquals(MarketResult.Err(MarketDataError.MalformedResponse), FinnhubProvider.parseQuote("X", "<html>", "Finnhub"))
        assertEquals(MarketResult.Err(MarketDataError.UnknownSymbol), FinnhubProvider.parseQuote("X", """{"c":"abc","t":1}""", "Finnhub"))
        assertEquals(MarketResult.Err(MarketDataError.UnknownSymbol), FinnhubProvider.parseQuote("X", """{"c":10,"t":-5}""", "Finnhub"))
    }

    @Test
    fun `search results and empty responses`() {
        val body = """{"count":2,"result":[{"description":"APPLE INC","displaySymbol":"AAPL","symbol":"AAPL","type":"Common Stock"},{"description":"","displaySymbol":"","symbol":""}]}"""
        val list = (FinnhubProvider.parseSearch(body) as MarketResult.Ok).value
        assertEquals(1, list.size)
        assertEquals("APPLE INC", list[0].name)
        assertEquals(emptyList<StockSymbol>(), (FinnhubProvider.parseSearch("""{"count":0,"result":[]}""") as MarketResult.Ok).value)
    }

    @Test
    fun `profile provides exchange and trading currency`() {
        val p = (FinnhubProvider.parseProfile("SAP", """{"currency":"eur","exchange":"XETRA","name":"SAP SE","ticker":"SAP.DE"}""") as MarketResult.Ok).value
        assertEquals("EUR", p.currency)
        assertEquals("XETRA", p.exchange)
        assertEquals(MarketResult.Err(MarketDataError.UnknownSymbol), FinnhubProvider.parseProfile("X", "{}"))
    }

    @Test
    fun `history candles are sorted and validated`() {
        val body = """{"c":[101.5,100.25,0],"t":[1789570800,1789484400,1789398000],"s":"ok"}"""
        val h = (FinnhubProvider.parseCandles("AAPL", HistoryRange.MONTH_1, body) as MarketResult.Ok).value
        assertEquals(2, h.points.size)
        assertTrue(h.points[0].time.isBefore(h.points[1].time))
        assertEquals(BigDecimal("100.25"), h.points[0].close)
        assertEquals(MarketResult.Err(MarketDataError.NoData), FinnhubProvider.parseCandles("AAPL", HistoryRange.DAY_1, """{"s":"no_data"}"""))
        assertEquals(MarketResult.Err(MarketDataError.MalformedResponse), FinnhubProvider.parseCandles("AAPL", HistoryRange.DAY_1, """{"c":[1],"t":[1,2],"s":"ok"}"""))
        assertEquals(MarketResult.Err(MarketDataError.NotOnPlan), FinnhubProvider.parseCandles("AAPL", HistoryRange.DAY_1, """{"error":"You don't have access to this resource."}"""))
    }

    @Test
    fun `market status keeps provider timezone`() {
        val s = (FinnhubProvider.parseMarketStatus(openJson, now) as MarketResult.Ok).value
        assertEquals(true, s.isOpen)
        assertEquals("America/New_York", s.timezone)
        assertEquals("regular", s.session)
        val closed = (FinnhubProvider.parseMarketStatus(closedJson, now) as MarketResult.Ok).value
        assertNull(closed.session)
    }

    @Test
    fun `http status codes map to explicit errors`() = runTest {
        val http = FakeHttp(emptyMap())
        val provider = FinnhubProvider(http)
        suspend fun codeFor(code: Int, headers: Map<String, String> = emptyMap()): MarketDataError? {
            http.routes = mapOf("/quote" to HttpResponse(code, """{"error":"x"}""", headers))
            return (provider.quote("AAPL", "k") as? MarketResult.Err)?.error
        }
        assertEquals(MarketDataError.InvalidKey, codeFor(401))
        assertEquals(MarketDataError.NotOnPlan, codeFor(403))
        assertEquals(MarketDataError.RateLimited(Duration.ofSeconds(30)), codeFor(429, mapOf("retry-after" to "30")))
        assertEquals(MarketDataError.RateLimited(null), codeFor(429))
        assertEquals(MarketDataError.ProviderUnavailable(503), codeFor(503))

        http.failure = HttpFailure.Timeout()
        assertEquals(MarketResult.Err(MarketDataError.Timeout), provider.quote("AAPL", "k"))
        http.failure = HttpFailure.Offline(java.io.IOException("x"))
        assertEquals(MarketResult.Err(MarketDataError.Offline), provider.quote("AAPL", "k"))
    }

    @Test
    fun `api key goes in a header, never in the url, and blank keys make no request`() = runTest {
        val http = FakeHttp(mapOf("/quote" to ok(quoteJson)))
        val provider = FinnhubProvider(http)
        provider.quote("AAPL", "secret-key-123")
        val (url, headers) = http.requests.single()
        assertFalse(url.contains("secret-key-123"))
        assertEquals("secret-key-123", headers["X-Finnhub-Token"])

        http.requests.clear()
        assertEquals(MarketResult.Err(MarketDataError.MissingKey), provider.quote("AAPL", ""))
        assertTrue(http.requests.isEmpty())
    }

    // ---- Freshness ------------------------------------------------------------------

    @Test
    fun `freshness labels`() {
        assertEquals(DataStatus.LIVE, Freshness.classify(now.minusSeconds(30), now, marketOpen = true))
        assertEquals(DataStatus.DELAYED, Freshness.classify(now.minus(Duration.ofMinutes(15)), now, marketOpen = true))
        assertEquals(DataStatus.STALE, Freshness.classify(now.minus(Duration.ofHours(2)), now, marketOpen = true))
        assertEquals(DataStatus.LAST_CLOSE, Freshness.classify(now.minus(Duration.ofHours(16)), now, marketOpen = false))
        assertEquals(DataStatus.STALE, Freshness.classify(now.minus(Duration.ofDays(9)), now, marketOpen = false))
        // Unknown session (e.g. non-US exchange): a fresh price is still not called real-time.
        assertEquals(DataStatus.DELAYED, Freshness.classify(now.minusSeconds(10), now, marketOpen = null))
        assertEquals(DataStatus.UNAVAILABLE, Freshness.classify(null, now, true))
        // A just-closed market never shows LIVE even for a very recent print.
        assertEquals(DataStatus.LAST_CLOSE, Freshness.classify(now.minusSeconds(5), now, marketOpen = false))
    }

    @Test
    fun `refresh policy respects market state and rate limits`() {
        assertEquals(Duration.ofSeconds(60), RefreshPolicy.nextDelay(true, 0, null))
        assertNull(RefreshPolicy.nextDelay(false, 0, null))
        assertEquals(Duration.ofMinutes(5), RefreshPolicy.nextDelay(null, 0, null))
        assertEquals(Duration.ofSeconds(120), RefreshPolicy.nextDelay(true, 2, null))
        assertEquals(Duration.ofMinutes(15), RefreshPolicy.nextDelay(true, 10, null))
        assertEquals(Duration.ofMinutes(3), RefreshPolicy.nextDelay(true, 0, Duration.ofMinutes(3)))
        assertTrue(RefreshPolicy.usesUsMarketStatus("AAPL"))
        assertFalse(RefreshPolicy.usesUsMarketStatus("SAP.DE"))
    }

    // ---- Watchlist repository -------------------------------------------------------

    private class MemoryWatchlist : WatchlistStore {
        val state = MutableStateFlow<List<WatchedStock>>(emptyList())
        override fun observe(): Flow<List<WatchedStock>> = state
        override suspend fun all() = state.value.sortedBy { it.position }
        override suspend fun nextPosition() = (state.value.maxOfOrNull { it.position } ?: -1) + 1
        override suspend fun insertIfAbsent(stock: WatchedStock): Boolean {
            if (state.value.any { it.symbol == stock.symbol }) return false
            state.value = state.value + stock
            return true
        }
        override suspend fun remove(symbol: String) { state.value = state.value.filterNot { it.symbol == symbol } }
        override suspend fun setPosition(symbol: String, position: Int) {
            state.value = state.value.map { if (it.symbol == symbol) it.copy(position = position) else it }
        }
    }

    private class MemoryQuotes : QuoteCache {
        val state = MutableStateFlow<Map<String, Quote>>(emptyMap())
        override fun observe(): Flow<Map<String, Quote>> = state
        override suspend fun put(quote: Quote) { state.value = state.value + (quote.symbol to quote) }
        override suspend fun remove(symbol: String) { state.value = state.value - symbol }
    }

    private fun repo(http: FakeHttp, watchlist: WatchlistStore = MemoryWatchlist(), quotes: QuoteCache = MemoryQuotes(), key: String? = "k") =
        StockRepository(FinnhubProvider(http), watchlist, quotes, ApiKeySource { key }, clock = { now })

    private val apple = StockSymbol("AAPL", "AAPL", "APPLE INC")

    @Test
    fun `add, prevent duplicates, reorder and remove`() = runTest {
        val http = FakeHttp(mapOf("/stock/profile2" to ok("""{"currency":"USD","exchange":"NASDAQ","name":"Apple Inc","ticker":"AAPL"}""")))
        val watchlist = MemoryWatchlist()
        val r = repo(http, watchlist)
        assertEquals(AddResult.Added, r.add(apple))
        assertEquals(AddResult.AlreadyOnWatchlist, r.add(apple.copy(symbol = "aapl")))
        assertEquals(AddResult.Added, r.add(StockSymbol("MSFT", "MSFT", "MICROSOFT")))
        assertEquals("USD", watchlist.all().first().currency)
        r.move("MSFT", up = true)
        assertEquals(listOf("MSFT", "AAPL"), watchlist.all().map { it.symbol })
        r.remove("AAPL")
        assertEquals(listOf("MSFT"), watchlist.all().map { it.symbol })
    }

    @Test
    fun `watchlist survives a new repository instance`() = runTest {
        val watchlist = MemoryWatchlist()
        repo(FakeHttp(emptyMap()), watchlist).add(apple)
        val reloaded = repo(FakeHttp(emptyMap()), watchlist)
        assertEquals(listOf("AAPL"), reloaded.rows().first().map { it.stock.symbol })
    }

    @Test
    fun `watchlist size is capped`() = runTest {
        val r = repo(FakeHttp(emptyMap()))
        repeat(RefreshPolicy.MAX_WATCHLIST_SIZE) { r.add(StockSymbol("S$it", "S$it", null)) }
        assertEquals(AddResult.LimitReached, r.add(apple))
    }

    @Test
    fun `missing key is reported without network calls`() = runTest {
        val http = FakeHttp(emptyMap())
        val watchlist = MemoryWatchlist()
        repo(http, watchlist).add(apple)
        http.requests.clear()
        val r = repo(http, watchlist, key = null)
        val report = r.refreshAll()
        assertEquals(MarketDataError.MissingKey, report.error)
        assertTrue(http.requests.isEmpty())
        assertEquals(DataStatus.API_KEY_REQUIRED, r.rows().first().single().status)
    }

    @Test
    fun `refresh stores quotes and labels them from provider metadata`() = runTest {
        val http = FakeHttp(mapOf("/stock/market-status" to ok(openJson), "/quote" to ok(quoteJson)))
        val quotes = MemoryQuotes()
        val r = repo(http, quotes = quotes)
        r.add(apple)
        val report = r.refreshAll()
        assertEquals(1, report.updated)
        // Quote timestamp 1789570800 = 2026-09-16T15:00Z, a day before "now": stale even though the market is open.
        assertEquals(DataStatus.STALE, r.rows().first().single().status)

        val liveRepo = StockRepository(FinnhubProvider(http), MemoryWatchlist(), quotes, ApiKeySource { "k" }, clock = { Instant.ofEpochSecond(1789570800 + 30) })
        liveRepo.add(apple)
        liveRepo.refreshAll()
        assertEquals(DataStatus.LIVE, liveRepo.rows().first().single().status)
    }

    @Test
    fun `closed market shows last close, not a live price`() = runTest {
        val http = FakeHttp(mapOf("/stock/market-status" to ok(closedJson), "/quote" to ok(quoteJson)))
        val r = StockRepository(FinnhubProvider(http), MemoryWatchlist(), MemoryQuotes(), ApiKeySource { "k" }, clock = { Instant.ofEpochSecond(1789570800 + 5) })
        r.add(apple)
        r.refreshAll()
        assertEquals(DataStatus.LAST_CLOSE, r.rows().first().single().status)
    }

    @Test
    fun `rate limit stops the refresh loop immediately`() = runTest {
        val http = FakeHttp(mapOf("/stock/market-status" to ok(openJson), "/quote" to HttpResponse(429, "{}")))
        val r = repo(http)
        repeat(5) { r.add(StockSymbol("S$it", "S$it", null)) }
        http.requests.clear()
        val report = r.refreshAll()
        assertTrue(report.error is MarketDataError.RateLimited)
        // market-status + the first quote only
        assertEquals(2, http.requests.size)
        assertEquals(Duration.ofMinutes(1), StockRepository.retryAfter(report.error))
    }

    @Test
    fun `cached quote is kept but relabelled when the provider fails`() = runTest {
        val http = FakeHttp(mapOf("/stock/market-status" to ok(openJson), "/quote" to ok(quoteJson)))
        val quotes = MemoryQuotes()
        val r = StockRepository(FinnhubProvider(http), MemoryWatchlist(), quotes, ApiKeySource { "k" }, clock = { Instant.ofEpochSecond(1789570800 + 30) })
        r.add(apple)
        r.refreshAll()
        http.failure = HttpFailure.Offline(java.io.IOException("down"))
        val later = StockRepository(FinnhubProvider(http), MemoryWatchlist().also { it.insertIfAbsent(WatchedStock("AAPL", "AAPL", null, null, null, 0, now)) }, quotes, ApiKeySource { "k" }, clock = { Instant.ofEpochSecond(1789570800 + 3600) })
        later.refreshAll()
        val row = later.rows().first().single()
        assertEquals(BigDecimal("261.74"), row.quote?.price)
        assertTrue(row.status != DataStatus.LIVE)
    }

    @Test
    fun `stock operations never touch personal finance data`() = runTest {
        val finance = InMemoryTransactionStore()
        val http = FakeHttp(mapOf("/stock/market-status" to ok(openJson), "/quote" to ok(quoteJson)))
        val r = repo(http)
        r.add(apple)
        r.refreshAll()
        r.remove("AAPL")
        // The repository has no access to the transaction store at all; nothing was written there.
        assertTrue(finance.rows.isEmpty())
        assertTrue(http.requests.none { it.first.contains("transaction") || it.first.contains("balance") })
    }
}
