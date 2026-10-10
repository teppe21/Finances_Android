package com.teppe21.finances.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Amounts are stored as Long values with a fixed scale of 2 for every currency
 * (1 HUF = 100, 1 EUR = 100). The display precision of a currency (HUF shows no
 * decimals) is a formatting concern only, so arithmetic never mixes scales.
 */
object Money {
    const val STORAGE_SCALE = 2

    fun toMinor(major: BigDecimal): Long =
        major.setScale(STORAGE_SCALE, RoundingMode.HALF_UP).unscaledValue().longValueExact()

    fun toMajor(minor: Long): BigDecimal = BigDecimal.valueOf(minor, STORAGE_SCALE)

    /**
     * Parses a number typed by the user, e.g. "1250", "12.5", "12,50", "1 234,56".
     * Returns null for blank, malformed, or more than two fractional digits.
     */
    fun parseUserInput(raw: String): Long? {
        val compact = raw.trim().replace(" ", "").replace(" ", "")
        if (compact.isEmpty()) return null
        if (!compact.matches(Regex("[0-9]+([.,][0-9]{1,2})?"))) return null
        val major = compact.replace(',', '.').toBigDecimalOrNull() ?: return null
        return toMinor(major)
    }
}

/**
 * Parses localized amount strings from bank notifications, receipts and CSV files
 * into exact BigDecimal values. No floating point arithmetic is used.
 *
 * Separator rules:
 * - Both '.' and ',' present: the right-most one is the decimal separator.
 * - Only one kind present, followed by exactly 1 or 2 digits at the end: decimal separator.
 * - Only one kind present in 3-digit groups ("22,500", "4.500", "1 234 567"): thousands separators.
 */
object AmountParser {

    private val GROUPED = Regex("^\\d{1,3}([.,' ]\\d{3})+$")

    fun parse(raw: String?): BigDecimal? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
            .replace(" ", " ")
            .replace(" ", " ")
            .replace("−", "-")
        var negative = false
        if (s.startsWith("-")) {
            negative = true
            s = s.substring(1).trim()
        } else if (s.startsWith("+")) {
            s = s.substring(1).trim()
        }
        if (s.endsWith("-")) {
            negative = true
            s = s.dropLast(1).trim()
        }
        if (s.isEmpty() || !s.first().isDigit()) return null
        if (!s.all { it.isDigit() || it == '.' || it == ',' || it == ' ' || it == '\'' }) return null

        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val normalized: String = when {
            lastDot >= 0 && lastComma >= 0 -> {
                val decimalSep = if (lastDot > lastComma) '.' else ','
                val thousandsSep = if (decimalSep == '.') ',' else '.'
                val intPart = s.substring(0, s.lastIndexOf(decimalSep))
                val fracPart = s.substring(s.lastIndexOf(decimalSep) + 1)
                if (fracPart.length !in 1..2 || !fracPart.all { it.isDigit() }) return null
                val cleanInt = intPart.replace(thousandsSep.toString(), "").replace(" ", "").replace("'", "")
                if (!cleanInt.all { it.isDigit() } || cleanInt.isEmpty()) return null
                "$cleanInt.$fracPart"
            }
            lastDot >= 0 || lastComma >= 0 -> {
                val sep = if (lastDot >= 0) '.' else ','
                val fracPart = s.substring(s.lastIndexOf(sep) + 1)
                val occurrences = s.count { it == sep }
                if (occurrences == 1 && fracPart.length in 1..2 && fracPart.all { it.isDigit() }) {
                    val intPart = s.substring(0, s.lastIndexOf(sep)).replace(" ", "").replace("'", "")
                    if (intPart.isEmpty() || !intPart.all { it.isDigit() }) return null
                    "$intPart.$fracPart"
                } else if (GROUPED.matches(s)) {
                    s.filter { it.isDigit() }
                } else {
                    return null
                }
            }
            else -> {
                if (s.contains(' ') || s.contains('\'')) {
                    if (!GROUPED.matches(s)) return null
                }
                s.filter { it.isDigit() }
            }
        }
        val value = normalized.toBigDecimalOrNull() ?: return null
        return if (negative) value.negate() else value
    }

    fun parseMinor(raw: String?): Long? = parse(raw)?.let { Money.toMinor(it) }
}
