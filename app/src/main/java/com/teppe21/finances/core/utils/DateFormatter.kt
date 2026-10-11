package com.teppe21.finances.core.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/**
 * The language dates and amounts are written in. English unless the app runs in Hungarian;
 * MainActivity sets it from the resources' locale, so it always matches the translated text.
 * Plain Kotlin (tests use the English default).
 */
object DisplayLocale {
    @Volatile
    var isHungarian: Boolean = false
        private set

    val locale: Locale get() = if (isHungarian) HUNGARIAN else Locale.US

    fun set(locale: Locale) {
        isHungarian = locale.language == "hu"
    }

    private val HUNGARIAN: Locale = Locale.forLanguageTag("hu-HU")
}

object DateFormatter {
    private fun pattern(en: String, hu: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (DisplayLocale.isHungarian) hu else en, DisplayLocale.locale)

    /** 15 Sep 2026 / 2026. szept. 15. */
    fun format(date: LocalDate): String = date.format(pattern("d MMM yyyy", "yyyy. MMM d."))

    /** 15 Sep 2026, 14:05 / 2026. szept. 15. 14:05 */
    fun dateTime(value: TemporalAccessor): String = pattern("d MMM yyyy, HH:mm", "yyyy. MMM d. HH:mm").format(value)

    /** 15 Sep, 14:05 / szept. 15. 14:05 */
    fun dayTime(value: TemporalAccessor): String = pattern("d MMM, HH:mm", "MMM d. HH:mm").format(value)

    /** September 2026 / 2026. szeptember */
    fun monthYear(value: TemporalAccessor): String = pattern("MMMM yyyy", "yyyy. LLLL").format(value)

    /** Sep / szept. */
    fun monthShort(value: TemporalAccessor): String = pattern("MMM", "LLL").format(value)
}
