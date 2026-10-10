package com.teppe21.finances.native.notification.parsing

import com.teppe21.finances.core.money.AmountParser
import com.teppe21.finances.core.money.Money

/** An amount with an explicit currency found in notification text. */
data class MoneyMention(
    /** Absolute amount, scale 2. */
    val amountMinor: Long,
    val currency: String,
    /** -1 when the number carried an explicit minus sign, +1 for plus, 0 otherwise. */
    val explicitSign: Int,
    val start: Int,
    val end: Int
)

object MoneyMentions {

    private const val NUMBER =
        "[+\\-−]?\\s?(?:\\d{1,3}(?:[ .,'\\u00A0\\u202F]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)"
    private const val CODE = "HUF|EUR|USD|GBP|CHF|Ft"
    private const val SYMBOL = "€|\\$|£"

    // "HUF804", "€12.50", "EUR 1 200,00"
    private val PREFIX = Regex(
        "(?<![\\p{L}\\p{N}])($CODE|$SYMBOL)\\s?($NUMBER)(?![\\d])",
        RegexOption.IGNORE_CASE
    )

    // "14 500 Ft", "12,99 EUR", "-4.500 Ft", "25.50€"
    private val SUFFIX = Regex(
        "(?<![\\p{L}\\p{N}.,])($NUMBER)\\s?($CODE|$SYMBOL)(?![\\p{L}])",
        RegexOption.IGNORE_CASE
    )

    private val BALANCE_CUES = listOf(
        "balance", "egyenleg", "elerheto", "available", "szamlaegyenleg", "keret", "limit"
    )

    fun normalizeCurrency(token: String): String? = when (token.trim().uppercase()) {
        "FT", "HUF" -> "HUF"
        "EUR", "€" -> "EUR"
        "USD", "$" -> "USD"
        "GBP", "£" -> "GBP"
        "CHF" -> "CHF"
        else -> null
    }

    /**
     * Finds every currency amount in [text], in order of appearance. Lines that describe an
     * account balance or available limit are skipped so that a balance can never be
     * mistaken for the transaction amount.
     */
    fun find(text: String): List<MoneyMention> {
        val results = mutableListOf<MoneyMention>()
        var offset = 0
        for (line in text.split('\n')) {
            val stripped = Cues.fold(line)
            val isBalanceLine = BALANCE_CUES.any { stripped.contains(it) }
            if (!isBalanceLine) {
                results += findInLine(line, offset)
            }
            offset += line.length + 1
        }
        return results.sortedBy { it.start }
    }

    private fun findInLine(line: String, offset: Int): List<MoneyMention> {
        val found = mutableListOf<MoneyMention>()
        val taken = mutableListOf<IntRange>()
        for (m in PREFIX.findAll(line)) {
            toMention(m.groupValues[2], m.groupValues[1], m.range, offset)?.let {
                found += it
                taken += m.range
            }
        }
        for (m in SUFFIX.findAll(line)) {
            if (taken.any { it.first <= m.range.last && m.range.first <= it.last }) continue
            toMention(m.groupValues[1], m.groupValues[2], m.range, offset)?.let { found += it }
        }
        return found
    }

    private fun toMention(number: String, currencyToken: String, range: IntRange, offset: Int): MoneyMention? {
        val currency = normalizeCurrency(currencyToken) ?: return null
        val trimmed = number.trim().replace("−", "-")
        val sign = when {
            trimmed.startsWith("-") -> -1
            trimmed.startsWith("+") -> 1
            else -> 0
        }
        val value = AmountParser.parse(trimmed.removePrefix("-").removePrefix("+").trim()) ?: return null
        if (value.signum() <= 0) return null
        val minor = runCatching { Money.toMinor(value) }.getOrNull() ?: return null
        return MoneyMention(minor, currency, sign, offset + range.first, offset + range.last + 1)
    }
}
