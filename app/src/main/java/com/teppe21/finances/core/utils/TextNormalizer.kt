package com.teppe21.finances.core.utils

import java.text.Normalizer
import java.util.regex.Pattern

object TextNormalizer {

    private val DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")

    fun removeDiacritics(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        return DIACRITICS_PATTERN.matcher(normalized).replaceAll("")
    }

    /**
     * Lowercase, accent-free words separated by single spaces. Apostrophes are dropped rather
     * than turned into a space, so "McDonald's" and "MCDONALDS" give the same word.
     */
    fun normalizeSearch(text: String?): String {
        return removeDiacritics(text)
            .lowercase()
            .replace(Regex("['’`´]"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Leading card-payment wording that banks put in front of the merchant name. */
    private val CARD_PREFIXES = Regex(
        "^(?:" + listOf(
            "card transaction of [\\d.,]+ [A-Za-z]{3} issued by",
            "card payment to",
            "card payment at",
            "card payment",
            "payment to",
            "purchase at",
            "erste kartyas vasarlas",
            "kartyas vasarlas",
            "kartyas fizetes",
            "vasarlas kartyaval",
            "vasarlas pos",
            "pos vasarlas",
            "vasarlas",
            "pos"
        ).joinToString("|") + ")[\\s:*-]+",
        RegexOption.IGNORE_CASE
    )

    /** Card masks (`*1234`, `XXXX1234`), dates and store or terminal numbers. */
    private val NOISE_TOKENS = listOf(
        Regex("\\*+\\d{2,}"),
        Regex("(?i)\\bx{2,}\\d{2,}\\b"),
        Regex("\\b\\d{4}[./-]\\d{2}[./-]\\d{2}\\.?"),
        Regex("\\b\\d{2}[./-]\\d{2}[./-]\\d{4}\\b"),
        Regex("(?<=\\s)#?\\d{3,}\\b")
    )

    /** Trailing location words added by card terminals; only removed after a real name. */
    private val TRAILING_LOCATION = setOf(
        "hu", "hun", "hungary", "budapest", "bp", "debrecen", "szeged", "miskolc", "pecs", "gyor",
        "nyiregyhaza", "kecskemet", "szekesfehervar", "szombathely", "eger", "veszprem", "sopron",
        "budaors", "soroksar", "zalaegerszeg", "tatabanya", "kaposvar", "erd", "dunaujvaros"
    )

    fun cleanMerchantName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var cleaned = Normalizer.normalize(raw, Normalizer.Form.NFC).trim().trimStart(':', '-', '–', ' ', '\t')
        // Card wording is matched without accents ("KÁRTYÁS VÁSÁRLÁS") but removed from the original text.
        CARD_PREFIXES.find(removeDiacritics(cleaned))?.let { m ->
            if (m.range.last + 1 < cleaned.length) cleaned = cleaned.substring(m.range.last + 1)
        }
        for (noise in NOISE_TOKENS) cleaned = cleaned.replace(noise, " ")
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        val words = cleaned.split(' ').toMutableList()
        while (words.size > 1 && removeDiacritics(words.last()).lowercase().trim(',', '.') in TRAILING_LOCATION) {
            words.removeAt(words.lastIndex)
        }
        cleaned = words.joinToString(" ")

        cleaned = cleaned
            .replace(Regex("^(kft\\.?|zrt\\.?|bt\\.?|nyrt\\.?)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(kft\\.?|zrt\\.?|bt\\.?|nyrt\\.?)$", RegexOption.IGNORE_CASE), "")
            // "Bolt" is left alone: it is also a ride-hailing and food delivery brand.
            .replace(Regex("^(üzlet|uzlet|aruhaz|áruház)\\s+", RegexOption.IGNORE_CASE), "")
            .trim().trimStart(':', '-', '–', ' ', '\t').trimEnd(':', '-', '–', ' ', '\t', ',')

        // Never clean a name away entirely: fall back to the trimmed original.
        return cleaned.ifBlank { raw.trim() }
    }
}
