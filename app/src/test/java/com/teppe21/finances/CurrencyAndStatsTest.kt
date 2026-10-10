package com.teppe21.finances

import com.teppe21.finances.core.utils.CurrencyConverter
import com.teppe21.finances.domain.fx.RateOrigin
import com.teppe21.finances.domain.fx.RateQuote
import com.teppe21.finances.domain.fx.RateTable
import com.teppe21.finances.domain.model.Category
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.domain.usecase.CalculateFinancialStatsUseCase
import com.teppe21.finances.domain.usecase.CategorizeTransactionUseCase
import com.teppe21.finances.domain.usecase.CurrencyAmountConverter
import com.teppe21.finances.data.fx.FrankfurterRateProvider
import com.teppe21.finances.data.fx.OpenErApiRateProvider
import com.teppe21.finances.domain.model.CategoryRule
import com.teppe21.finances.support.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class CurrencyAndStatsTest {

    private val asOf = Instant.parse("2026-09-14T00:00:00Z")
    private fun quote(rate: String) = RateQuote(BigDecimal(rate), asOf, "test", RateOrigin.FETCHED)

    // 1 EUR = 400 HUF = 1.10 USD
    private val table = RateTable(mapOf("HUF" to quote("400"), "USD" to quote("1.10")))
    private val stats = CalculateFinancialStatsUseCase()

    private fun converterTo(target: String, rates: RateTable = table): CurrencyAmountConverter =
        { amount, currency, _ -> rates.convert(amount, currency, target) }

    // The same underlying data: 40,000 HUF income, 20,000 HUF + 10 EUR + 11 USD expenses.
    private val data = listOf(
        tx("salary", 4_000_000L),
        tx("groceries", -2_000_000L),
        tx("book", -1_000L, currency = "EUR"),
        tx("app", -1_100L, currency = "USD")
    )

    @Test
    fun `display currency changes the numbers, not just the symbol`() {
        val huf = stats.execute(data, converterTo("HUF"))
        val eur = stats.execute(data, converterTo("EUR"))
        val usd = stats.execute(data, converterTo("USD"))

        // HUF: 20,000 + 10*400 + 11*(400/1.1) = 20,000 + 4,000 + 4,000 = 28,000 HUF
        assertEquals(2_800_000L, huf.expenseMinor)
        assertEquals(4_000_000L, huf.incomeMinor)
        // EUR: 50 + 10 + 10 = 70 EUR expenses, 100 EUR income
        assertEquals(7_000L, eur.expenseMinor)
        assertEquals(10_000L, eur.incomeMinor)
        // USD: 55 + 11 + 11 = 77 USD
        assertEquals(7_700L, usd.expenseMinor)
        assertEquals(0, huf.unconvertedCount + eur.unconvertedCount + usd.unconvertedCount)
    }

    @Test
    fun `missing rates exclude transactions instead of mixing units`() {
        val onlyHuf = RateTable(mapOf("HUF" to quote("400")))
        val eur = stats.execute(data, converterTo("EUR", onlyHuf))
        // USD row has no rate: excluded and counted, never added as if it were EUR.
        assertEquals(1, eur.unconvertedCount)
        assertEquals(6_000L, eur.expenseMinor)

        val empty = stats.execute(data, converterTo("EUR", RateTable()))
        assertEquals(3, empty.unconvertedCount) // HUF x2 and USD; the EUR row converts to itself
        assertEquals(1_000L, empty.expenseMinor)
    }

    @Test
    fun `pending transactions are excluded from every aggregate`() {
        val withPending = data + tx("unreviewed", -9_999_900L, pending = true)
        assertEquals(stats.execute(data).expenseMinor, stats.execute(withPending).expenseMinor)
        assertEquals(4, stats.execute(withPending).transactionCount)
    }

    @Test
    fun `cross rates and rounding`() {
        assertEquals(BigDecimal.ONE, table.rate("HUF", "HUF"))
        assertEquals(0, BigDecimal("0.0025").compareTo(table.rate("HUF", "EUR")!!.stripTrailingZeros()))
        // 804 HUF -> 2.01 EUR exactly
        assertEquals(201L, table.convert(80_400L, "HUF", "EUR"))
        // Half-up rounding at the minor unit: 1 HUF = 0.0025 EUR -> 0.00 EUR (0.25 minor) ; 2 HUF -> 0.01 (0.5 minor)
        assertEquals(0L, table.convert(100L, "HUF", "EUR"))
        assertEquals(1L, table.convert(200L, "HUF", "EUR"))
        assertEquals(-201L, table.convert(-80_400L, "HUF", "EUR"))
        assertEquals(0L, table.convert(0L, "HUF", "EUR"))
        assertNull(table.convert(100L, "HUF", "GBP"))
        assertNull(CurrencyConverter.convert(100L, "HUF", "EUR", BigDecimal.ZERO))
    }

    @Test
    fun `manual rates override fetched ones`() {
        val manual = mapOf("HUF" to RateQuote(BigDecimal("380"), asOf, "Manual", RateOrigin.MANUAL))
        val merged = table.withOverrides(manual)
        assertEquals(100L, merged.convert(380_00L, "HUF", "EUR"))
        assertEquals(RateOrigin.MANUAL, merged.quotesFor("HUF", "EUR").single().origin)
    }

    @Test
    fun `chart trends use the same conversion`() {
        val ref = LocalDate.of(2026, 9, 30)
        val trends = stats.calculateMonthlyTrends(data, monthCount = 2, referenceDate = ref, converter = converterTo("EUR"))
        assertEquals(listOf("2026-08", "2026-09"), trends.map { it.month })
        assertEquals(7_000L, trends.last().expenseMinor)
        assertEquals(stats.execute(data, converterTo("EUR")).expenseMinor, trends.sumOf { it.expenseMinor })
    }

    @Test
    fun `category breakdown percentages and names`() {
        val cats = listOf(Category("food", "Élelmiszer"), Category("custom1", "Hobby"))
        val rows = listOf(
            tx("a", -3_000L, categoryId = "food"),
            tx("b", -1_000L, categoryId = "custom1")
        )
        val breakdown = stats.calculateCategoryBreakdown(rows, cats)
        assertEquals("Food & Groceries", breakdown.first().categoryName)
        assertEquals(75f, breakdown.first().percentage, 0.001f)
        assertEquals("Hobby", breakdown.last().categoryName)
    }

    @Test
    fun `account balance is opening balance plus confirmed transactions`() {
        val rows = listOf(
            tx("in", 10_000_00L, accountId = "acc"),
            tx("out", -2_500_00L, accountId = "acc"),
            tx("eur", -10_00L, currency = "EUR", accountId = "acc"),
            tx("pend", -999_00L, pending = true, accountId = "acc")
        )
        val balance = stats.accountBalance(1_000_00L, "HUF", rows) { a, from, to -> table.convert(a, from, to) }
        assertEquals(1_000_00L + 10_000_00L - 2_500_00L - 4_000_00L, balance.balanceMinor)
        assertEquals(0, balance.unconvertedCount)

        val noRates = stats.accountBalance(0L, "HUF", rows) { a, from, to -> RateTable().convert(a, from, to) }
        assertEquals(1, noRates.unconvertedCount)
    }

    @Test
    fun `categorization keywords match at word starts only`() {
        val rules = listOf(
            CategoryRule("r1", "income", "bér"),
            CategoryRule("r2", "transport", "uber"),
            CategoryRule("r3", "food", "spar")
        )
        val categorize = CategorizeTransactionUseCase()
        assertEquals("transport", categorize.execute("Uber trip", "UBER", rules))
        assertEquals("income", categorize.execute("Havi bér", null, rules))
        assertEquals("food", categorize.execute("SPAR Budapest", null, rules))
        assertEquals("other", categorize.execute("Kasparov chess club", null, rules))
    }

    @Test
    fun `exchange rate responses are parsed with their own dates`() {
        val f = FrankfurterRateProvider.parseFrankfurter(
            """{"amount":1.0,"base":"EUR","date":"2026-10-09","rates":{"CHF":0.9312,"GBP":0.8655,"HUF":389.65,"USD":1.0921}}""",
            "ECB"
        )
        assertEquals(BigDecimal("389.65"), f.rates["HUF"])
        assertEquals(Instant.parse("2026-10-09T00:00:00Z"), f.asOf)

        val o = OpenErApiRateProvider.parseOpenEr(
            """{"result":"success","time_last_update_unix":1791504001,"base_code":"EUR","rates":{"EUR":1,"HUF":390.1,"USD":1.09}}""",
            "ER"
        )
        assertEquals(Instant.ofEpochSecond(1791504001), o.asOf)
        assertTrue(runCatching { OpenErApiRateProvider.parseOpenEr("""{"result":"error"}""", "ER") }.isFailure)
        assertTrue(runCatching { FrankfurterRateProvider.parseFrankfurter("not json", "ECB") }.isFailure)
    }

    @Test
    fun `direction drives income and expense classification`() {
        val refund = tx("refund", 500L, direction = TransactionDirection.REFUND)
        val transfer = tx("move", -1_000L, direction = TransactionDirection.TRANSFER)
        val s = stats.execute(listOf(refund, transfer))
        assertEquals(500L, s.incomeMinor)
        assertEquals(0L, s.expenseMinor)
        assertEquals(1_000L, s.transfersMinor)
    }
}
