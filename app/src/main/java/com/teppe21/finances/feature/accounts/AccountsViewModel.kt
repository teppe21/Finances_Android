package com.teppe21.finances.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.model.AccountType
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

class AccountsViewModel : ViewModel() {

    private val app = FinancesApp.instance
    private val accountRepo = app.accountRepository
    private val stats = app.calculateStatsUseCase

    val accounts: StateFlow<List<AccountRow>> = combine(
        accountRepo.getAllAccountsFlow(),
        app.transactionRepository.getAllTransactionsFlow(),
        app.exchangeRateRepository.tableFlow
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
            accountRepo.insertAccount(
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
