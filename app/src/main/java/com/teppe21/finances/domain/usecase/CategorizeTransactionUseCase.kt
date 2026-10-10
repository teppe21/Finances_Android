package com.teppe21.finances.domain.usecase

import com.teppe21.finances.core.utils.TextNormalizer
import com.teppe21.finances.domain.model.CategoryRule

class CategorizeTransactionUseCase {

    fun execute(
        description: String,
        merchant: String? = null,
        rules: List<CategoryRule>,
        fallback: String = "other"
    ): String {
        val targetText = "${merchant ?: ""} $description"
        val normalized = TextNormalizer.normalizeSearch(targetText)

        // Sort rules by priority descending (higher evaluated first)
        val activeRules = rules.filter { it.isActive }.sortedByDescending { it.priority }

        for (rule in activeRules) {
            val normPattern = TextNormalizer.normalizeSearch(rule.pattern)
            if (normPattern.isBlank()) continue

            when (rule.matchType) {
                "exact" -> {
                    if (normalized == normPattern) {
                        return rule.categoryId
                    }
                }
                "regex" -> {
                    try {
                        if (Regex(rule.pattern, RegexOption.IGNORE_CASE).containsMatchIn(targetText)) {
                            return rule.categoryId
                        }
                    } catch (_: Exception) {}
                }
                else -> { // "contains": the keyword must start at a word boundary ("ber" must not match "uber")
                    if (" $normalized".contains(" $normPattern")) {
                        return rule.categoryId
                    }
                }
            }
        }

        return fallback
    }
}
