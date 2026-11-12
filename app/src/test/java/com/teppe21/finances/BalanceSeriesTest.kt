package com.teppe21.finances

import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.model.AccountType
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.domain.model.TransactionSource
import com.teppe21.finances.domain.usecase.BalanceRange
import com.teppe21.finances.domain.usecase.balanceSeries
import com.teppe21.finances.domain.usecase.inRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BalanceSeriesTest {
    private val today = LocalDate.of(2026, 3, 10)
    private fun acc(id: String, opening: Long, currency: String = "HUF") =
        Account(id, id, "", AccountType.BANK, currency, openingBalanceMinor = opening)

    private fun tx(account: String, daysAgo: Long, amount: Long, currency: String = "HUF", pending: Boolean = false) = Transaction(
        id = "$account-$daysAgo-$amount", accountId = account, date = today.minusDays(daysAgo), amountMinor = amount,
        currency = currency, direction = if (amount >= 0) TransactionDirection.INCOME else TransactionDirection.EXPENSE,
        description = "", source = TransactionSource.MANUAL, fingerprint = "$account-$daysAgo-$amount", pending = pending
    )

    private val same: (Long, String, String) -> Long? = { a, f, t -> if (f == t) a else null }

    private fun series(accounts: List<Account>, txs: List<Transaction>, convert: (Long, String, String) -> Long? = same) =
        balanceSeries(accounts, txs, today, "HUF", convert)

    @Test fun openingBalanceOnly() {
        val s = series(listOf(acc("a", 5000)), emptyList())
        assertEquals(1, s.size)
        assertEquals(5000L, s.single().balanceMinor)
        assertEquals(today, s.single().date)
    }

    @Test fun incomeAndExpenseOnDifferentDays() {
        val s = series(listOf(acc("a", 1000)), listOf(tx("a", 3, 500), tx("a", 1, -200)))
        assertEquals(listOf(1500L, 1500L, 1300L, 1300L), s.map { it.balanceMinor })
        assertEquals(today.minusDays(3), s.first().date)
    }

    @Test fun pendingIsExcluded() {
        val s = series(listOf(acc("a", 0)), listOf(tx("a", 2, 100), tx("a", 1, 900, pending = true)))
        assertEquals(listOf(100L, 100L, 100L), s.map { it.balanceMinor })
    }

    @Test fun gapsCarryForwardAcrossAccounts() {
        val s = series(listOf(acc("a", 100), acc("b", 10)), listOf(tx("a", 4, 50), tx("b", 1, 5)))
        assertEquals(listOf(160L, 160L, 160L, 165L, 165L), s.map { it.balanceMinor })
    }

    @Test fun transferBetweenOwnAccountsKeepsTotal() {
        val s = series(listOf(acc("a", 1000), acc("b", 0)), listOf(tx("a", 2, -300), tx("b", 2, 300)))
        assertTrue(s.all { it.balanceMinor == 1000L })
    }

    @Test fun foreignAccountIsConverted() {
        val rate: (Long, String, String) -> Long? = { a, f, t -> if (f == t) a else if (f == "EUR") a * 400 else null }
        val s = series(listOf(acc("a", 100), acc("e", 10, "EUR")), emptyList(), rate)
        assertEquals(4100L, s.single().balanceMinor)
    }

    @Test fun missingRateGivesEmptySeries() {
        assertTrue(series(listOf(acc("e", 10, "EUR")), emptyList()).isEmpty())
    }

    @Test fun transactionWithoutRateGivesEmptySeries() {
        val s = series(listOf(acc("a", 100)), listOf(tx("a", 2, 50, currency = "CHF")))
        assertTrue(s.isEmpty())
    }

    @Test fun futureDatedCountsToday() {
        val s = series(listOf(acc("a", 0)), listOf(tx("a", -5, 70)))
        assertEquals(70L, s.last().balanceMinor)
    }

    @Test fun rangeCutsFromLastDay() {
        val s = series(listOf(acc("a", 0)), listOf(tx("a", 100, 1)))
        assertEquals(101, s.size)
        assertEquals(8, s.inRange(BalanceRange.WEEK).size)
        assertEquals(today.minusDays(7), s.inRange(BalanceRange.WEEK).first().date)
        assertEquals(101, s.inRange(BalanceRange.ALL).size)
        assertEquals(101, s.inRange(BalanceRange.YEAR).size)
    }
}
