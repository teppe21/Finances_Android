package com.teppe21.finances.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.fx.RateTable
import com.teppe21.finances.domain.model.AccountType
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.usecase.CalculateFinancialStatsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class AccountRow(
    val account: Account,
    /** Opening balance plus confirmed transactions, in the account currency. */
    val balanceMinor: Long,
    /** Transactions in another currency that could not be converted (balance is incomplete). */
    val unconvertedCount: Int,
    val pendingCount: Int
)

class AccountsViewModel(
    accountsFlow: Flow<List<Account>>,
    transactionsFlow: Flow<List<Transaction>>,
    ratesFlow: Flow<RateTable>,
    private val stats: CalculateFinancialStatsUseCase,
    private val insertAccount: suspend (Account) -> Unit
) : ViewModel() {

    /** Used by the default `viewModel()` factory: wires the app-level repositories. */
    constructor() : this(FinancesApp.instance)

    private constructor(app: FinancesApp) : this(
        accountsFlow = app.accountRepository.getAllAccountsFlow(),
        transactionsFlow = app.transactionRepository.getAllTransactionsFlow(),
        ratesFlow = app.exchangeRateRepository.tableFlow,
        stats = app.calculateStatsUseCase,
        insertAccount = { app.accountRepository.insertAccount(it) }
    )

    val accounts: StateFlow<List<AccountRow>> = combine(
        accountsFlow,
        transactionsFlow,
        ratesFlow
    ) { accounts, transactions, table ->
        val byAccount = transactions.groupBy { it.accountId }
        accounts.map { acc ->
            val txs = byAccount[acc.id].orEmpty()
            val balance = stats.accountBalance(acc.openingBalanceMinor, acc.currency, txs) { a, from, to -> table.convert(a, from, to) }
            AccountRow(acc, balance.balanceMinor, balance.unconvertedCount, txs.count { it.pending })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addAccount(name: String, institution: String, type: AccountType, currency: String, openingBalanceMinor: Long) {
        viewModelScope.launch {
            insertAccount(
                Account(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    institution = institution.trim(),
                    type = type,
                    currency = currency,
                    openingBalanceMinor = openingBalanceMinor,
                    currentBalanceMinor = openingBalanceMinor
                )
            )
        }
    }
}
