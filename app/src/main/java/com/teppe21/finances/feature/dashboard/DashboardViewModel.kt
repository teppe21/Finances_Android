package com.teppe21.finances.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.data.local.preferences.UserPreferences
import com.teppe21.finances.domain.fx.RateTable
import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.model.Category
import com.teppe21.finances.domain.model.CategoryBreakdown
import com.teppe21.finances.domain.usecase.CalculateFinancialStatsUseCase
import kotlinx.coroutines.flow.Flow
import com.teppe21.finances.domain.model.FinancialStats
import com.teppe21.finances.domain.model.MonthlyTrend
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.usecase.CurrencyAmountConverter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

data class DashboardUiState(
    /** Sum of all account balances converted to the display currency; null if any part is unconvertible. */
    val totalBalanceMinor: Long? = null,
    val balanceUnconvertedAccounts: Int = 0,
    val monthStats: FinancialStats = FinancialStats(),
    val month: YearMonth = YearMonth.now(),
    val recentTransactions: List<Transaction> = emptyList(),
    val recentConverted: Map<String, Long?> = emptyMap(),
    val monthlyTrends: List<MonthlyTrend> = emptyList(),
    val categoryBreakdown: List<CategoryBreakdown> = emptyList(),
    /** For translated category names; the breakdown carries ids. */
    val categories: List<Category> = emptyList(),
    val reviewCount: Int = 0,
    val currency: String = "HUF",
    val ratesAsOf: Instant? = null,
    val hasAnyTransactions: Boolean = false,
    val isLoading: Boolean = true
)

class DashboardViewModel(
    transactions: Flow<List<Transaction>>,
    categories: Flow<List<Category>>,
    accounts: Flow<List<Account>>,
    preferences: Flow<UserPreferences>,
    rates: Flow<RateTable>,
    private val stats: CalculateFinancialStatsUseCase = CalculateFinancialStatsUseCase()
) : ViewModel() {

    /** Used by the default `viewModel()` factory: wires the app-level repositories. */
    constructor() : this(FinancesApp.instance)

    private constructor(app: FinancesApp) : this(
        transactions = app.transactionRepository.getAllTransactionsFlow(),
        categories = app.categoryRepository.getAllCategoriesFlow(),
        accounts = app.accountRepository.getAllAccountsFlow(),
        preferences = app.preferencesRepository.preferencesFlow,
        rates = app.exchangeRateRepository.tableFlow,
        stats = app.calculateStatsUseCase
    )

    val uiState: StateFlow<DashboardUiState> = combine(
        transactions,
        categories,
        accounts,
        preferences,
        rates
    ) { transactions, categories, accounts, prefs, table ->
        val target = prefs.currency
        val converter: CurrencyAmountConverter = { amount, currency, _ -> table.convert(amount, currency, target) }
        val month = YearMonth.now()
        val thisMonth = transactions.filter { YearMonth.from(it.date) == month }

        var total = 0L
        var unconvertedAccounts = 0
        for (account in accounts) {
            val balance = stats.accountBalance(
                account.openingBalanceMinor,
                account.currency,
                transactions.filter { it.accountId == account.id }
            ) { a, from, to -> table.convert(a, from, to) }
            val converted = table.convert(balance.balanceMinor, account.currency, target)
            if (converted == null || balance.unconvertedCount > 0) unconvertedAccounts++
            if (converted != null) total += converted
        }

        val recent = transactions.take(5)
        DashboardUiState(
            totalBalanceMinor = if (unconvertedAccounts == 0) total else null,
            balanceUnconvertedAccounts = unconvertedAccounts,
            monthStats = stats.execute(thisMonth, converter),
            month = month,
            recentTransactions = recent,
            recentConverted = recent.associate { it.id to table.convert(it.amountMinor, it.currency, target) },
            monthlyTrends = stats.calculateMonthlyTrends(transactions, monthCount = 6, referenceDate = LocalDate.now(), converter = converter),
            categoryBreakdown = stats.calculateCategoryBreakdown(thisMonth, categories, converter),
            categories = categories,
            reviewCount = transactions.count { it.pending },
            currency = target,
            ratesAsOf = table.oldestAsOf().takeIf { !table.isEmpty },
            hasAnyTransactions = transactions.isNotEmpty(),
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

}
