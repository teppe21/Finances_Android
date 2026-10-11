package com.teppe21.finances.domain.usecase

import com.teppe21.finances.core.utils.TextNormalizer
import com.teppe21.finances.domain.model.BuiltIns
import com.teppe21.finances.domain.model.CategoryRule
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.model.TransactionDirection
import java.time.Instant
import kotlin.math.abs

/**
 * Runs the current rules over stored transactions.
 *
 * - A category the user chose ([Transaction.categorySetByUser]) is never touched.
 * - A row the rules can no longer place keeps the category it has: re-applying never moves a
 *   categorized row back to "Other". (The bank's type column is not stored, so a row that
 *   was placed by it at import could otherwise lose its category.)
 * - The kind follows the category, as at import: transfer categories make a transfer, and a
 *   former transfer that now matches a spending rule becomes income or expense by sign.
 */
class ReapplyRulesUseCase(private val categorize: CategorizeTransactionUseCase = CategorizeTransactionUseCase()) {

    /** The transactions whose category, kind or merchant changed, already updated. */
    fun execute(
        transactions: List<Transaction>,
        rules: List<CategoryRule>,
        onlyMatching: CategoryRule? = null,
        now: Instant = Instant.now()
    ): List<Transaction> = transactions.mapNotNull { tx ->
        if (tx.categorySetByUser) return@mapNotNull null
        val input = ClassificationInput(tx.description, tx.merchant, signedAmountMinor = tx.amountMinor)
        if (onlyMatching != null && !categorize.matches(onlyMatching, input)) return@mapNotNull null

        val c = categorize.classify(input, rules)
        val keepExisting = c.isFallback && tx.categoryId != null && tx.categoryId != BuiltIns.UNCATEGORIZED
        if (keepExisting) return@mapNotNull null

        val direction = when {
            c.direction.isTransfer -> c.direction
            tx.direction == TransactionDirection.REFUND -> TransactionDirection.REFUND
            else -> c.direction
        }
        val merchant = if (direction.isTransfer) null else tx.merchant
        if (c.categoryId == tx.categoryId && direction == tx.direction && merchant == tx.merchant) return@mapNotNull null
        tx.copy(categoryId = c.categoryId, direction = direction, merchant = merchant, updatedAt = now)
    }
}

/** The user puts [this] in [categoryId] by hand: the kind follows the category, and rules leave it alone afterwards. */
fun Transaction.withUserCategory(categoryId: String, now: Instant = Instant.now()): Transaction {
    val kind = BuiltIns.kindFor(categoryId, amountMinor)
    val direction = when {
        kind.isTransfer -> kind
        direction == TransactionDirection.REFUND -> TransactionDirection.REFUND
        else -> kind
    }
    return copy(categoryId = categoryId, direction = direction, categorySetByUser = true, updatedAt = now)
}

/** Uncategorized transactions with the same (folded) merchant, for resolving them together. */
data class UncategorizedGroup(
    /** The folded merchant text; also the suggested keyword for a rule. */
    val key: String,
    /** The most common original spelling, for display. */
    val displayName: String,
    val transactions: List<Transaction>
) {
    val count: Int get() = transactions.size
    /** Sum of absolute amounts per currency. */
    val totals: Map<String, Long> get() = transactions.groupBy { it.currency }.mapValues { (_, l) -> l.sumOf { abs(it.amountMinor) } }
    /** Suggested keyword: the merchant name the bank shows, which the rule will match from now on. */
    val suggestedKeyword: String get() = displayName.trim().lowercase()
}

object UncategorizedGroups {
    /** Expense or income rows still in "Other", grouped by merchant, the largest groups first. */
    fun from(transactions: List<Transaction>): List<UncategorizedGroup> =
        transactions.asSequence()
            .filter { (it.categoryId == null || it.categoryId == BuiltIns.UNCATEGORIZED) && !it.direction.isTransfer }
            .groupBy { TextNormalizer.normalizeSearch(it.merchant ?: it.description) }
            .filterKeys { it.isNotBlank() }
            .map { (key, list) ->
                val name = list.groupingBy { (it.merchant ?: it.description).trim() }.eachCount().maxBy { it.value }.key
                UncategorizedGroup(key, name, list.sortedByDescending { it.date })
            }
            .sortedWith(compareByDescending<UncategorizedGroup> { it.count }.thenBy { it.displayName.lowercase() })
}

/**
 * The user-facing recategorization actions: re-apply all rules, create a rule from a
 * transaction or merchant and apply it to matching existing transactions, and set a
 * category by hand. Storage is passed in, so this runs against Room or an in-memory list.
 */
class Recategorizer(
    private val transactions: suspend () -> List<Transaction>,
    private val updateAll: suspend (List<Transaction>) -> Unit,
    private val rules: CategoryRuleSource,
    /** Stores a user rule (or returns the identical one that exists) and returns it. */
    private val addUserRule: suspend (categoryId: String, keyword: String) -> CategoryRule?,
    private val reapply: ReapplyRulesUseCase = ReapplyRulesUseCase(),
    private val clock: () -> Instant = Instant::now
) {
    /** Runs every rule over every transaction; returns how many changed. */
    suspend fun reapplyAll(): Int {
        val changed = reapply.execute(transactions(), rules.activeRules(), now = clock())
        if (changed.isNotEmpty()) updateAll(changed)
        return changed.size
    }

    /**
     * Creates a user rule ([keyword] -> [categoryId]) and applies it to every existing
     * transaction it matches, except those whose category the user set. Returns the ids of the
     * transactions it changed, or null when the keyword is blank.
     */
    suspend fun createRuleAndApply(categoryId: String, keyword: String): Set<String>? {
        val rule = addUserRule(categoryId, keyword) ?: return null
        val changed = reapply.execute(transactions(), rules.activeRules(), onlyMatching = rule, now = clock())
        if (changed.isNotEmpty()) updateAll(changed)
        return changed.mapTo(mutableSetOf()) { it.id }
    }

    /** Puts [selected] in [categoryId] by hand; rules will leave them alone afterwards. */
    suspend fun setCategory(selected: List<Transaction>, categoryId: String) {
        val now = clock()
        updateAll(selected.map { it.withUserCategory(categoryId, now) })
    }
}
