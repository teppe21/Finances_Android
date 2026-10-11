package com.teppe21.finances.domain.usecase

import com.teppe21.finances.core.utils.TextNormalizer
import com.teppe21.finances.domain.model.BuiltIns
import com.teppe21.finances.domain.model.CategoryRule
import com.teppe21.finances.domain.model.TransactionDirection

/** Everything the rules may look at for one transaction. */
data class ClassificationInput(
    val description: String,
    val merchant: String? = null,
    /** The bank's own transaction type, e.g. Revolut `EXCHANGE` or OTP `Vásárlás kártyával`. */
    val bankType: String? = null,
    /** A category id suggested by the file itself (CSV category column), if any. */
    val bankCategoryId: String? = null,
    /** Signed amount; credits and debits are allowed different categories (see [execute]). */
    val signedAmountMinor: Long
)

data class Classification(
    val categoryId: String,
    val direction: TransactionDirection,
    /** The rule that decided, or null for the bank hint or the fallback. */
    val ruleId: String? = null
) {
    val isFallback: Boolean get() = ruleId == null && categoryId in setOf(BuiltIns.UNCATEGORIZED, BuiltIns.INCOME_FALLBACK)
}

/**
 * Keyword categorization and the transfer/income/expense decision.
 *
 * Text is folded (case, accents, apostrophes, punctuation) before matching. The highest
 * priority matching rule wins; on equal priority the longer keyword wins, so "uber eats"
 * beats "uber". The category then decides the kind: own-account and cash categories make a
 * [TransactionDirection.TRANSFER], the people category a [TransactionDirection.PERSONAL_TRANSFER],
 * anything else is income or expense by sign.
 */
class CategorizeTransactionUseCase {

    /** Categories that only make sense for money coming in (salary, pension). */
    private val incomeOnly = setOf("income")

    fun classify(
        input: ClassificationInput,
        rules: List<CategoryRule>,
        incomeCategoryIds: Set<String> = incomeOnly
    ): Classification {
        // 0 means the sign is unknown (legacy callers): nothing is excluded by sign then.
        val debit = input.signedAmountMinor < 0
        val texts = MatchTexts.of(input)
        val cardLike = isCardLike(input.bankType)

        fun allowed(rule: CategoryRule): Boolean {
            if (!rule.isActive) return false
            // Salary keywords never turn a payment into income ("fizetés" also means "payment").
            if (debit && rule.categoryId in incomeCategoryIds) return false
            // A card purchase is never a transfer, unless the user said so.
            if (cardLike && !rule.isUserRule && BuiltIns.kindFor(rule.categoryId, 0).isTransfer) return false
            return true
        }

        val best = rules.asSequence()
            .filter(::allowed)
            .filter { matches(it, texts) }
            .maxWithOrNull(
                compareBy<CategoryRule> { it.priority }
                    .thenBy { TextNormalizer.normalizeSearch(it.pattern).length }
                    .thenBy { it.createdAt } // the newest of two equal user rules wins
            )
        val bankCategory = input.bankCategoryId?.takeIf { !debit || it !in incomeCategoryIds }

        val (categoryId, ruleId) = when {
            // The file's own category sits between user rules and the specific built-in rules.
            bankCategory != null && (best == null || best.priority <= CategoryRule.PRIORITY_BANK_CATEGORY) -> bankCategory to null
            best != null -> best.categoryId to best.id
            input.signedAmountMinor > 0 -> BuiltIns.INCOME_FALLBACK to null
            else -> BuiltIns.UNCATEGORIZED to null
        }
        return Classification(categoryId, BuiltIns.kindFor(categoryId, input.signedAmountMinor), ruleId)
    }

    /** Category only; kept for callers that do not need the kind. */
    fun execute(
        description: String,
        merchant: String? = null,
        rules: List<CategoryRule>,
        fallback: String = BuiltIns.UNCATEGORIZED
    ): String {
        val c = classify(ClassificationInput(description, merchant, signedAmountMinor = 0), rules)
        return if (c.isFallback) fallback else c.categoryId
    }

    /** True when [rule] matches the transaction's text. */
    fun matches(rule: CategoryRule, input: ClassificationInput): Boolean = matches(rule, MatchTexts.of(input))

    private class MatchTexts(val all: String, val fields: List<String>, val raw: String) {
        companion object {
            fun of(input: ClassificationInput): MatchTexts {
                val parts = listOfNotNull(input.merchant, input.description, input.bankType).filter { it.isNotBlank() }
                val fields = listOfNotNull(input.merchant, input.description).map(TextNormalizer::normalizeSearch).filter { it.isNotBlank() }
                return MatchTexts(TextNormalizer.normalizeSearch(parts.joinToString(" ")), fields, parts.joinToString(" "))
            }
        }
    }

    private fun matches(rule: CategoryRule, texts: MatchTexts): Boolean {
        val pattern = TextNormalizer.normalizeSearch(rule.pattern)
        if (pattern.isBlank()) return false
        return when (rule.matchType) {
            "exact" -> texts.fields.any { it == pattern }
            "regex" -> runCatching {
                val regex = Regex(rule.pattern, RegexOption.IGNORE_CASE)
                regex.containsMatchIn(texts.raw) || regex.containsMatchIn(texts.all)
            }.getOrDefault(false)
            "word" -> containsWord(texts.all, pattern, wholeWord = true)
            else -> containsWord(texts.all, pattern, wholeWord = pattern.length <= SHORT_KEYWORD && ' ' !in pattern)
        }
    }

    companion object {
        /** Keywords this short must be a whole word: "mol" is not "Molnár", "bér" is not "bérlet". */
        const val SHORT_KEYWORD = 3

        /**
         * True when [pattern] starts a word of [text] (both normalized). A multi-word keyword also
         * matches its words written together ("burger king" matches "BURGERKING").
         */
        fun containsWord(text: String, pattern: String, wholeWord: Boolean): Boolean {
            val haystack = " $text "
            if (wholeWord) return haystack.contains(" $pattern ")
            if (haystack.contains(" $pattern")) return true
            return ' ' in pattern && haystack.contains(" ${pattern.replace(" ", "")}")
        }

        /** Bank types for card purchases and direct debits: never a transfer by keyword alone. */
        private val CARD_TYPES = listOf("card", "kartya", "vasarlas", "pos", "csoportos beszedes", "direct debit")

        private val CASH_WORDS = listOf("atm", "keszpenz", "cash")

        fun isCardLike(bankType: String?): Boolean {
            val t = TextNormalizer.normalizeSearch(bankType)
            if (t.isBlank() || CASH_WORDS.any { containsWord(t, it, wholeWord = false) }) return false
            return CARD_TYPES.any { containsWord(t, it, wholeWord = false) }
        }
    }
}
