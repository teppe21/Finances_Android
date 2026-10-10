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
import com.teppe21.finances.domain.usecase.IngestionInput
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

class TransactionsViewModel : ViewModel() {

    private val app = FinancesApp.instance
    private val txRepo = app.transactionRepository
    private val catRepo = app.categoryRepository
    private val accountRepo = app.accountRepository
    private val prefsRepo = app.preferencesRepository
    private val fxRepo = app.exchangeRateRepository
    private val ingestUseCase = app.ingestTransactionUseCase
    private val recurringUseCase = app.detectRecurringUseCase

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
        val preferences: com.teppe21.finances.data.local.preferences.UserPreferences,
        val rates: com.teppe21.finances.domain.fx.RateTable
    )

    private val _repoDataFlow = combine(
        txRepo.getAllTransactionsFlow(),
        catRepo.getAllCategoriesFlow(),
        accountRepo.getAllAccountsFlow(),
        prefsRepo.preferencesFlow,
        fxRepo.tableFlow
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
            selectedTransaction = selectedTx,
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
            ingestUseCase.execute(
                IngestionInput(
                    accountId = accountId,
                    date = date,
                    amountMinor = amountMinor,
                    currency = currency,
                    direction = direction,
                    description = description,
                    merchant = merchant,
                    categoryId = categoryId,
                    source = TransactionSource.MANUAL
                )
            )
        }
    }


    fun deleteTransaction(tx: Transaction) {
        viewModelScope.launch {
            txRepo.deleteTransaction(tx)
            _selectedTransaction.value = null
        }
    }
}
