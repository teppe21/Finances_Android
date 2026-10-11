package com.teppe21.finances.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.core.utils.TextNormalizer
import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.model.Category
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.domain.model.TransactionSource
import com.teppe21.finances.data.local.preferences.UserPreferences
import com.teppe21.finances.domain.fx.RateTable
import com.teppe21.finances.domain.usecase.DetectRecurringTransactionsUseCase
import com.teppe21.finances.domain.usecase.IngestionInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class TransactionSortOrder {
    NEWEST,
    OLDEST,
    AMOUNT_DESC,
    AMOUNT_ASC,
    MERCHANT_ASC,
    MERCHANT_DESC
}

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    /** Amount in the display currency; null when no exchange rate is available. */
    val convertedAmounts: Map<String, Long?> = emptyMap(),
    val unconvertedCount: Int = 0,
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val selectedPeriod: String = "this_month",
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val onlyRecurring: Boolean = false,
    val sortOrder: TransactionSortOrder = TransactionSortOrder.NEWEST,
    val selectedTransaction: Transaction? = null,
    val currency: String = "HUF"
)

class TransactionsViewModel(
    private val transactionsFlow: Flow<List<Transaction>>,
    private val categoriesFlow: Flow<List<Category>>,
    private val accountsFlow: Flow<List<Account>>,
    private val preferencesFlow: Flow<UserPreferences>,
    private val ratesFlow: Flow<RateTable>,
    private val recurringUseCase: DetectRecurringTransactionsUseCase,
    private val ingest: suspend (IngestionInput) -> Unit,
    private val delete: suspend (Transaction) -> Unit,
    private val recategorize: suspend (List<Transaction>, String) -> Unit = { _, _ -> },
    private val createRuleAndApply: suspend (categoryId: String, keyword: String) -> Set<String>? = { _, _ -> null }
) : ViewModel() {

    /** Used by the default `viewModel()` factory: wires the app-level repositories. */
    constructor() : this(FinancesApp.instance)

    private constructor(app: FinancesApp) : this(
        transactionsFlow = app.transactionRepository.getAllTransactionsFlow(),
        categoriesFlow = app.categoryRepository.getAllCategoriesFlow(),
        accountsFlow = app.accountRepository.getAllAccountsFlow(),
        preferencesFlow = app.preferencesRepository.preferencesFlow,
        ratesFlow = app.exchangeRateRepository.tableFlow,
        recurringUseCase = app.detectRecurringUseCase,
        ingest = { app.ingestTransactionUseCase.execute(it) },
        delete = { app.transactionRepository.deleteTransaction(it) },
        recategorize = { txs, c -> app.recategorizer.setCategory(txs, c) },
        createRuleAndApply = { c, k -> app.recategorizer.createRuleAndApply(c, k) }
    )

    private val _selectedPeriod = MutableStateFlow("this_month")
    private val _selectedCategory = MutableStateFlow("All")
    private val _searchQuery = MutableStateFlow("")
    private val _onlyRecurring = MutableStateFlow(false)
    private val _sortOrder = MutableStateFlow(TransactionSortOrder.NEWEST)
    private val _selectedTransaction = MutableStateFlow<Transaction?>(null)

    private data class FilterParams(
        val period: String,
        val category: String,
        val query: String,
        val onlyRecurring: Boolean,
        val sortOrder: TransactionSortOrder
    )

    private val _filterParams = combine(
        _selectedPeriod,
        _selectedCategory,
        _searchQuery,
        _onlyRecurring,
        _sortOrder
    ) { period, category, query, recurring, sort ->
        FilterParams(period, category, query, recurring, sort)
    }

    private data class TransactionRepoData(
        val transactions: List<Transaction>,
        val categories: List<Category>,
        val accounts: List<Account>,
        val preferences: UserPreferences,
        val rates: RateTable
    )

    private val _repoDataFlow = combine(
        transactionsFlow,
        categoriesFlow,
        accountsFlow,
        preferencesFlow,
        ratesFlow
    ) { txs, cats, accs, prefs, rates ->
        TransactionRepoData(txs, cats, accs, prefs, rates)
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        _repoDataFlow,
        _filterParams,
        _selectedTransaction
    ) { data, filters, selectedTx ->
        val allTx = data.transactions
        val cats = data.categories
        val accs = data.accounts
        val targetCurrency = data.preferences.currency
        val recurringIds = if (filters.onlyRecurring) recurringUseCase.execute(allTx).recurringTransactionIds else emptySet()

        // Compute converted amounts for all transactions in target display currency
        val convertedMap: Map<String, Long?> = allTx.associate { tx ->
            tx.id to data.rates.convert(tx.amountMinor, tx.currency, targetCurrency)
        }

        val now = LocalDate.now()
        val filtered = allTx.filter { tx ->
            // 1. Period filter
            val dateMatches = when (filters.period) {
                "this_month" -> tx.date.year == now.year && tx.date.month == now.month
                "last_month" -> {
                    val lastM = now.minusMonths(1)
                    tx.date.year == lastM.year && tx.date.month == lastM.month
                }
                "3_months" -> tx.date.isAfter(now.minusMonths(3))
                "6_months" -> tx.date.isAfter(now.minusMonths(6))
                else -> true
            }

            // 2. Category filter
            val catMatches = filters.category == "All" || tx.categoryId == filters.category

            // 3. Search query
            val q = TextNormalizer.normalizeSearch(filters.query)
            val queryMatches = if (q.isBlank()) true else {
                val merchantNorm = TextNormalizer.normalizeSearch(tx.merchant ?: "")
                val descNorm = TextNormalizer.normalizeSearch(tx.description)
                val noteNorm = TextNormalizer.normalizeSearch(tx.notes ?: "")
                merchantNorm.contains(q) || descNorm.contains(q) || noteNorm.contains(q)
            }

            // 4. Recurring filter
            val recurringMatches = !filters.onlyRecurring || recurringIds.contains(tx.id)

            dateMatches && catMatches && queryMatches && recurringMatches
        }

        val sorted = when (filters.sortOrder) {
            TransactionSortOrder.NEWEST -> filtered.sortedWith(
                compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt }
            )
            TransactionSortOrder.OLDEST -> filtered.sortedWith(
                compareBy<Transaction> { it.date }.thenBy { it.createdAt }
            )
            // Unconvertible amounts cannot be compared with the others; they sort last.
            TransactionSortOrder.AMOUNT_DESC -> filtered.sortedByDescending {
                convertedMap[it.id]?.let { v -> kotlin.math.abs(v) } ?: Long.MIN_VALUE
            }
            TransactionSortOrder.AMOUNT_ASC -> filtered.sortedBy {
                convertedMap[it.id]?.let { v -> kotlin.math.abs(v) } ?: Long.MAX_VALUE
            }
            TransactionSortOrder.MERCHANT_ASC -> filtered.sortedBy {
                (it.merchant?.ifBlank { null } ?: it.description).lowercase()
            }
            TransactionSortOrder.MERCHANT_DESC -> filtered.sortedByDescending {
                (it.merchant?.ifBlank { null } ?: it.description).lowercase()
            }
        }

        TransactionsUiState(
            transactions = allTx,
            filteredTransactions = sorted,
            convertedAmounts = convertedMap,
            unconvertedCount = sorted.count { convertedMap[it.id] == null },
            categories = cats,
            accounts = accs,
            selectedPeriod = filters.period,
            selectedCategory = filters.category,
            searchQuery = filters.query,
            onlyRecurring = filters.onlyRecurring,
            sortOrder = filters.sortOrder,
            // The sheet shows the stored row, so a category change appears at once.
            selectedTransaction = selectedTx?.let { s -> allTx.firstOrNull { it.id == s.id } ?: s },
            currency = targetCurrency
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState()
    )

    fun setPeriod(period: String) { _selectedPeriod.value = period }
    fun setSortOrder(order: TransactionSortOrder) { _sortOrder.value = order }
    fun setCategory(category: String) { _selectedCategory.value = category }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun toggleRecurring() { _onlyRecurring.value = !_onlyRecurring.value }
    fun selectTransaction(tx: Transaction?) { _selectedTransaction.value = tx }

    fun addManualTransaction(
        accountId: String,
        amountMinor: Long,
        currency: String = "HUF",
        direction: TransactionDirection,
        description: String,
        merchant: String?,
        categoryId: String?,
        date: LocalDate
    ) {
        viewModelScope.launch {
            ingest(
                IngestionInput(
                    accountId = accountId,
                    date = date,
                    amountMinor = amountMinor,
                    currency = currency,
                    direction = direction,
                    description = description,
                    merchant = merchant,
                    categoryId = categoryId,
                    source = TransactionSource.MANUAL,
                    categorySetByUser = categoryId != null
                )
            )
        }
    }


    /**
     * The user puts [tx] in [categoryId]. With a [ruleKeyword] the choice also becomes a rule
     * applied to every matching transaction whose category the user has not set.
     */
    fun changeCategory(tx: Transaction, categoryId: String, ruleKeyword: String?) {
        viewModelScope.launch {
            ruleKeyword?.let { createRuleAndApply(categoryId, it) }
            recategorize(listOf(tx), categoryId)
        }
    }

    fun deleteTransaction(tx: Transaction) {
        viewModelScope.launch {
            delete(tx)
            _selectedTransaction.value = null
        }
    }
}
