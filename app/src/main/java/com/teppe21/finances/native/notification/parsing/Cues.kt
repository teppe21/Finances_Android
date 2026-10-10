package com.teppe21.finances.native.notification.parsing

import java.text.Normalizer

/**
 * Keyword matching on accent-folded, lower-cased text with word boundaries, so that
 * "gift" does not match "ft" and "Code Cafe" style merchants are handled predictably.
 *
 * A cue ending in '*' is a stem: only its left edge must be a word boundary
 * (e.g. "ajanl*" matches "ajanlj" and "ajanlas").
 */
object Cues {

    private val COMBINING = Regex("\\p{InCombiningDiacriticalMarks}+")

    fun fold(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        return COMBINING.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
            .lowercase()
            .replace(' ', ' ')
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit()

    fun containsCue(folded: String, cue: String): Boolean {
        val stem = cue.endsWith("*")
        val needle = if (stem) cue.dropLast(1) else cue
        if (needle.isEmpty()) return false
        var from = 0
        while (true) {
            val idx = folded.indexOf(needle, from)
            if (idx < 0) return false
            val leftOk = idx == 0 || !isWordChar(folded[idx - 1]) || !isWordChar(needle.first())
            val endIdx = idx + needle.length
            val rightOk = stem || endIdx >= folded.length || !isWordChar(folded[endIdx]) || !isWordChar(needle.last())
            if (leftOk && rightOk) return true
            from = idx + 1
        }
    }

    fun anyCue(folded: String, cues: Collection<String>): Boolean = cues.any { containsCue(folded, it) }

    fun matchingCues(folded: String, cues: Collection<String>): List<String> = cues.filter { containsCue(folded, it) }
}
