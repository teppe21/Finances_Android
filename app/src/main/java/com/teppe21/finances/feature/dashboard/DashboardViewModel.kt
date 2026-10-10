package com.teppe21.finances.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.domain.model.CategoryBreakdown
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
    val reviewCount: Int = 0,
    val currency: String = "HUF",
    val ratesAsOf: Instant? = null,
    val hasAnyTransactions: Boolean = false,
    val isLoading: Boolean = true
)

class DashboardViewModel : ViewModel() {

    private val app = FinancesApp.instance
    private val stats = app.calculateStatsUseCase

    val uiState: StateFlow<DashboardUiState> = combine(
        app.transactionRepository.getAllTransactionsFlow(),
        app.categoryRepository.getAllCategoriesFlow(),
        app.accountRepository.getAllAccountsFlow(),
        app.preferencesRepository.preferencesFlow,
        app.exchangeRateRepository.tableFlow
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
            reviewCount = transactions.count { it.pending },
            currency = target,
            ratesAsOf = table.oldestAsOf().takeIf { !table.isEmpty },
            hasAnyTransactions = transactions.isNotEmpty(),
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

}
