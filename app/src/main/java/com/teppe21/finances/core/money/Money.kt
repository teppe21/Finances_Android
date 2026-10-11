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

    /** Throws ArithmeticException if the amount does not fit in a Long (never wraps silently). */
    fun toMinor(major: BigDecimal): Long {
        val unscaled = major.setScale(STORAGE_SCALE, RoundingMode.HALF_UP).unscaledValue()
        // BigInteger.longValueExact() needs API 31; minSdk is 26.
        if (unscaled.bitLength() > 63) throw ArithmeticException("BigInteger out of long range")
        return unscaled.toLong()
    }

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


/** Which characters an amount string uses as decimal and grouping separators. */
enum class SeparatorStyle(internal val decimal: Char?) {
    /** Decide from the text itself, using only rules that cannot misread a valid number. */
    AUTO(null),

    /** Hungarian / continental style: `1.234,56`; `.`, space and `'` only group digits. */
    COMMA_DECIMAL(','),

    /** English style: `1,234.56`; `,`, space and `'` only group digits. */
    DOT_DECIMAL('.')
}

/**
 * Parses localized amount strings from bank notifications, receipts and CSV files
 * into exact BigDecimal values. No floating point arithmetic is used.
 *
 * Accepted shapes (anything else returns null instead of being guessed):
 * - An optional sign (leading `-`/`+`/U+2212, or a trailing `-`), then digits.
 * - Grouping must be regular: 1-3 leading digits (not starting with 0), then groups of exactly
 *   three, always separated by the same character (`.`, `,`, space, NBSP, thin space or `'`).
 * - At most one decimal separator, followed by one or two digits.
 *
 * With [SeparatorStyle.AUTO]:
 * - `.` and `,` both present: the right-most one is the decimal separator, the other groups.
 * - One kind present, once, followed by 1 or 2 digits: decimal separator (`12,99`, `804.00`).
 * - One kind present in three-digit groups (`22,500`, `4.500`): grouping. A currency with at most
 *   two decimals cannot have a three-digit fraction, so this reading is the only valid one.
 * An explicit style removes that choice: `1.500` is 1500 as [SeparatorStyle.COMMA_DECIMAL] but
 * invalid as [SeparatorStyle.DOT_DECIMAL], where it would be a three-digit fraction.
 */
object AmountParser {

    private const val GROUP_CHARS = ".,' "
    private val NO_BREAK_SPACE = Char(0x00A0)
    private val NARROW_NO_BREAK_SPACE = Char(0x202F)
    private val MINUS_SIGN = Char(0x2212)
    private val GROUPED = Regex("^[1-9]\\d{0,2}([.,' ])\\d{3}(\\1\\d{3})*$")
    private val PLAIN = Regex("^\\d+$")

    fun parse(raw: String?, style: SeparatorStyle = SeparatorStyle.AUTO): BigDecimal? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
            .replace(NO_BREAK_SPACE, ' ')
            .replace(NARROW_NO_BREAK_SPACE, ' ')
            .replace(MINUS_SIGN, '-')
        var negative = false
        val leadingSign = s.firstOrNull()?.takeIf { it == '-' || it == '+' }
        if (leadingSign != null) {
            negative = leadingSign == '-'
            s = s.substring(1).trim()
        }
        if (s.endsWith("-")) {
            // A sign on both ends ("-5-") is malformed, not a double negative.
            if (leadingSign != null) return null
            negative = true
            s = s.dropLast(1).trim()
        }
        if (s.isEmpty() || !s.first().isDigit() || !s.last().isDigit()) return null
        if (!s.all { it.isDigit() || it in GROUP_CHARS }) return null

        val decimal = if (style == SeparatorStyle.AUTO) autoDecimal(s) else style.decimal
        val value = parseWith(s, decimal) ?: return null
        return if (negative) value.negate() else value
    }

    fun parseMinor(raw: String?, style: SeparatorStyle = SeparatorStyle.AUTO): Long? =
        parse(raw, style)?.let { Money.toMinor(it) }

    /**
     * The separator style a single, unambiguous amount string proves, or null when it proves
     * nothing (plain integers, or a lone `1.500`-style value that fits either convention).
     */
    fun styleProvedBy(raw: String?): SeparatorStyle? {
        val s = raw?.trim()?.removePrefix("-")?.removePrefix("+")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        return when {
            lastDot >= 0 && lastComma >= 0 -> if (lastDot > lastComma) SeparatorStyle.DOT_DECIMAL else SeparatorStyle.COMMA_DECIMAL
            lastDot >= 0 || lastComma >= 0 -> {
                val sep = if (lastDot >= 0) '.' else ','
                val fraction = s.substring(s.lastIndexOf(sep) + 1)
                if (s.count { it == sep } == 1 && fraction.length in 1..2 && fraction.all { it.isDigit() }) {
                    if (sep == '.') SeparatorStyle.DOT_DECIMAL else SeparatorStyle.COMMA_DECIMAL
                } else {
                    null
                }
            }
            else -> null
        }
    }

    /** The decimal separator AUTO would use, or null when the text has none. */
    private fun autoDecimal(s: String): Char? {
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        return when {
            lastDot >= 0 && lastComma >= 0 -> if (lastDot > lastComma) '.' else ','
            lastDot >= 0 || lastComma >= 0 -> {
                val sep = if (lastDot >= 0) '.' else ','
                val fraction = s.substring(s.lastIndexOf(sep) + 1)
                sep.takeIf { s.count { c -> c == sep } == 1 && fraction.length in 1..2 }
            }
            else -> null
        }
    }

    private fun parseWith(s: String, decimal: Char?): BigDecimal? {
        val integerPart: String
        val fraction: String?
        if (decimal != null) {
            if (s.count { it == decimal } > 1) return null
            val at = s.indexOf(decimal)
            if (at < 0) {
                integerPart = s
                fraction = null
            } else {
                integerPart = s.substring(0, at)
                fraction = s.substring(at + 1)
            }
        } else {
            integerPart = s
            fraction = null
        }
        if (fraction != null && (fraction.length !in 1..2 || !fraction.all { it.isDigit() })) return null

        val digits = when {
            PLAIN.matches(integerPart) -> integerPart
            else -> {
                val match = GROUPED.matchEntire(integerPart) ?: return null
                // The decimal separator can never also be the grouping separator.
                if (match.groupValues[1].single() == decimal) return null
                integerPart.filter { it.isDigit() }
            }
        }
        return (if (fraction == null) digits else "$digits.$fraction").toBigDecimalOrNull()
    }
}
