package com.teppe21.finances.domain.fx

import com.teppe21.finances.core.utils.CurrencyConverter
import java.math.BigDecimal
import java.time.Instant

enum class RateOrigin {
    /** Fetched from the provider during this app session. */
    FETCHED,
    /** Loaded from the local cache of an earlier fetch. */
    CACHED,
    /** Entered by the user in Settings; overrides fetched rates. */
    MANUAL
}

/** 1 [RateTable.BASE] = [rate] units of the currency. */
data class RateQuote(
    val rate: BigDecimal,
    val asOf: Instant,
    val provider: String,
    val origin: RateOrigin
)

/**
 * Exchange rates relative to EUR. There are no built-in default rates: an empty table
 * means conversions are unavailable and the UI must say so instead of guessing.
 *
 * Every conversion uses the latest rate in the table, not the historical rate of the
 * transaction date.
 */
data class RateTable(val quotes: Map<String, RateQuote> = emptyMap()) {

    val isEmpty: Boolean get() = quotes.isEmpty()

    fun rate(from: String, to: String): BigDecimal? {
        val f = from.uppercase().trim()
        val t = to.uppercase().trim()
        if (f == t) return BigDecimal.ONE
        return CurrencyConverter.calculateCrossRate(f, t, BASE, quotes.mapValues { it.value.rate })
    }

    fun convert(amountMinor: Long, from: String, to: String): Long? {
        if (from.equals(to, ignoreCase = true)) return amountMinor
        return CurrencyConverter.convert(amountMinor, from, to, rate(from, to))
    }

    /** Quotes involved in converting [from] into [to]; empty for same-currency pairs. */
    fun quotesFor(from: String, to: String): List<RateQuote> {
        if (from.equals(to, ignoreCase = true)) return emptyList()
        return listOf(from, to).map { it.uppercase() }.filter { it != BASE }.mapNotNull { quotes[it] }
    }

    fun oldestAsOf(): Instant? = quotes.values.minOfOrNull { it.asOf }

    fun withOverrides(manual: Map<String, RateQuote>): RateTable = RateTable(quotes + manual)

    companion object {
        const val BASE = "EUR"
    }
}
