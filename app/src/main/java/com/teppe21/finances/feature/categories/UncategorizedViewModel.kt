package com.teppe21.finances.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.domain.model.Category
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.usecase.UncategorizedGroup
import com.teppe21.finances.domain.usecase.UncategorizedGroups
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-shot result shown in a snackbar. */
sealed interface RecategorizeMessage {
    data class Updated(val count: Int) : RecategorizeMessage
    data class RuleSaved(val count: Int) : RecategorizeMessage
}

data class UncategorizedUiState(
    val loading: Boolean = true,
    val groups: List<UncategorizedGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val busy: Boolean = false,
    val message: RecategorizeMessage? = null
)

class UncategorizedViewModel(
    transactionsFlow: Flow<List<Transaction>>,
    categoriesFlow: Flow<List<Category>>,
    private val reapplyAll: suspend () -> Int,
    private val createRuleAndApply: suspend (categoryId: String, keyword: String) -> Set<String>?,
    private val setCategory: suspend (List<Transaction>, String) -> Unit
) : ViewModel() {

    constructor() : this(FinancesApp.instance)

    private constructor(app: FinancesApp) : this(
        transactionsFlow = app.transactionRepository.getAllTransactionsFlow(),
        categoriesFlow = app.categoryRepository.getAllCategoriesFlow(),
        reapplyAll = { app.recategorizer.reapplyAll() },
        createRuleAndApply = { c, k -> app.recategorizer.createRuleAndApply(c, k) },
        setCategory = { txs, c -> app.recategorizer.setCategory(txs, c) }
    )

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<RecategorizeMessage?>(null)

    val uiState: StateFlow<UncategorizedUiState> = combine(transactionsFlow, categoriesFlow, busy, message) { txs, cats, b, m ->
        UncategorizedUiState(loading = false, groups = UncategorizedGroups.from(txs), categories = cats, busy = b, message = m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UncategorizedUiState())

    fun reapplyRules() = run { message.value = RecategorizeMessage.Updated(reapplyAll()) }

    /**
     * Puts [group] in [categoryId]. With a [ruleKeyword] the choice is also kept as a rule and
     * applied to every other matching transaction. The group's own rows are always moved, even
     * if the user edited the keyword so that it no longer matches them.
     */
    fun categorize(group: UncategorizedGroup, categoryId: String, ruleKeyword: String?) = run {
        val byRule = ruleKeyword?.let { createRuleAndApply(categoryId, it) }
        setCategory(group.transactions, categoryId)
        val total = (byRule.orEmpty() + group.transactions.map { it.id }).size
        message.value = if (byRule != null) RecategorizeMessage.RuleSaved(total) else RecategorizeMessage.Updated(total)
    }

    fun messageShown() {
        message.value = null
    }

    private fun run(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy.value = false
            }
        }
    }
}
