package com.teppe21.finances.core.utils

import com.teppe21.finances.domain.model.SupportedCurrencies
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * Money as text. A non-breaking space (normal width) separates the number from the unit, so
 * "12,345 Ft" never wraps between the two. English: `12,345 Ft`, `€12.50`, `CHF 12.50`.
 * Hungarian ([DisplayLocale]): `12 345 Ft`, `12,50 €`, `12,50 CHF`, grouped with the same
 * non-breaking space and a decimal comma, units after the number.
 */
object CurrencyFormatter {

    const val NBSP = ' '

    private val englishSymbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }
    private val hungarianSymbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = NBSP
        decimalSeparator = ','
    }

    fun format(amountMinor: Long, currency: String = "HUF"): String {
        val curr = SupportedCurrencies.fromCode(currency)
        // Exact decimal: amounts are scale 2; HUF is rounded half-up to whole forints for display.
        val major = BigDecimal.valueOf(abs(amountMinor), 2).setScale(curr.decimalPlaces, RoundingMode.HALF_UP)

        val pattern = if (curr.decimalPlaces == 0) {
            "#,##0"
        } else {
            "#,##0." + "0".repeat(curr.decimalPlaces)
        }

        val hungarian = DisplayLocale.isHungarian
        val df = DecimalFormat(pattern, if (hungarian) hungarianSymbols else englishSymbols).apply { roundingMode = RoundingMode.HALF_UP }
        val number = df.format(major)

        if (hungarian) {
            val unit = when (curr.code) {
                "HUF" -> "Ft"
                "EUR" -> "€"
                "USD" -> "$"
                "GBP" -> "£"
                else -> curr.code
            }
            return "$number$NBSP$unit"
        }
        return when (curr.code) {
            "HUF" -> "$number${NBSP}Ft"
            "EUR" -> "€$number"
            "USD" -> "$$number"
            "GBP" -> "£$number"
            "CHF" -> "CHF$NBSP$number"
            else -> "$number$NBSP${curr.symbol}"
        }
    }

    /** One decimal and a percent sign: `12.3%`, Hungarian `12,3%`. */
    fun formatPercent(value: Float): String = if (value > 0f && value < 0.05f) "<" + String.format(DisplayLocale.locale, "%.1f%%", 0.1f) else
        String.format(DisplayLocale.locale, "%.1f%%", value)

    fun formatSigned(amountMinor: Long, currency: String = "HUF"): String {
        val base = format(amountMinor, currency)
        return when {
            amountMinor > 0 -> "+$base"
            amountMinor < 0 -> "-$base"
            else -> base
        }
    }

    /**
     * Formats an amount with its original value in parentheses if currencies differ.
     * E.g.: "€27.24 (10,000 Ft)"
     */
    fun formatWithOriginal(
        convertedMinor: Long,
        displayCurrency: String,
        originalMinor: Long,
        originalCurrency: String
    ): String {
        val mainStr = formatSigned(convertedMinor, displayCurrency)
        if (displayCurrency.equals(originalCurrency, ignoreCase = true)) {
            return mainStr
        }
        val origStr = formatSigned(originalMinor, originalCurrency)
        return "$mainStr ($origStr)"
    }
}
