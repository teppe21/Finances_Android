package com.teppe21.finances.domain.usecase

import com.teppe21.finances.domain.model.Category
import com.teppe21.finances.domain.model.CategoryBreakdown
import com.teppe21.finances.domain.model.FinancialStats
import com.teppe21.finances.domain.model.MaxExpenseItem
import com.teppe21.finances.domain.model.MonthlyTrend
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.model.TransactionDirection
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Converts an amount (scale 2) from its currency into the display currency.
 * Returns null when no exchange rate is available; callers must never substitute
 * the unconverted number.
 */
typealias CurrencyAmountConverter = (amountMinor: Long, currency: String, date: LocalDate) -> Long?

/** Identity converter for single-currency data sets (all values already in one currency). */
val NoConversion: CurrencyAmountConverter = { amount, _, _ -> amount }

/**
 * Aggregations for the dashboard and analytics. Pending (unreviewed) transactions are
 * always excluded, and transactions that cannot be converted into the display currency
 * are excluded and counted instead of being added with the wrong unit.
 */
class CalculateFinancialStatsUseCase {

    private fun Transaction.isIncomeLike(): Boolean =
        direction == TransactionDirection.INCOME ||
            (direction == TransactionDirection.REFUND && amountMinor > 0)

    private fun confirmed(transactions: List<Transaction>) = transactions.filter { !it.pending }

    fun execute(
        transactions: List<Transaction>,
        converter: CurrencyAmountConverter = NoConversion
    ): FinancialStats {
        var income = 0L
        var expense = 0L
        var transfers = 0L
        var unconverted = 0
        var expenseCount = 0
        var maxExpense = MaxExpenseItem()
        val confirmed = confirmed(transactions)

        for (t in confirmed) {
            val converted = converter(t.amountMinor, t.currency, t.date)
            if (converted == null) {
                unconverted++
                continue
            }
            val amt = abs(converted)
            when {
                t.isIncomeLike() -> income += amt
                t.direction == TransactionDirection.EXPENSE -> {
                    expense += amt
                    expenseCount++
                    if (amt > maxExpense.amountMinor) {
                        maxExpense = MaxExpenseItem(t.merchant ?: t.description, amt)
                    }
                }
                t.direction == TransactionDirection.TRANSFER -> transfers += amt
            }
        }

        return FinancialStats(
            incomeMinor = income,
            expenseMinor = expense,
            balanceMinor = income - expense,
            transfersMinor = transfers,
            savingsRate = calculateSavingsRate(income, expense),
            transactionCount = confirmed.size,
            maxExpenseItem = maxExpense,
            avgExpenseMinor = if (expenseCount > 0) expense / expenseCount else 0L,
            unconvertedCount = unconverted
        )
    }

    fun calculateIncome(transactions: List<Transaction>, converter: CurrencyAmountConverter = NoConversion): Long =
        execute(transactions, converter).incomeMinor

    fun calculateExpenses(transactions: List<Transaction>, converter: CurrencyAmountConverter = NoConversion): Long =
        execute(transactions, converter).expenseMinor

    fun calculateBalance(transactions: List<Transaction>, converter: CurrencyAmountConverter = NoConversion): Long =
        execute(transactions, converter).balanceMinor

    fun calculateSavingsRate(incomeMinor: Long, expenseMinor: Long): Float {
        if (incomeMinor <= 0L) return 0.0f
        val rate = (incomeMinor - expenseMinor).toFloat() / incomeMinor.toFloat()
        return max(-1.0f, min(1.0f, rate))
    }

    fun calculateMonthlyTrends(
        transactions: List<Transaction>,
        monthCount: Int = 6,
        referenceDate: LocalDate = LocalDate.now(),
        converter: CurrencyAmountConverter = NoConversion
    ): List<MonthlyTrend> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM")
        val monthKeys = ((monthCount - 1) downTo 0).map { referenceDate.minusMonths(it.toLong()).format(formatter) }
        val incomeMap = monthKeys.associateWith { 0L }.toMutableMap()
        val expenseMap = monthKeys.associateWith { 0L }.toMutableMap()

        for (t in confirmed(transactions)) {
            val key = t.date.format(formatter)
            if (!incomeMap.containsKey(key)) continue
            val converted = converter(t.amountMinor, t.currency, t.date) ?: continue
            when {
                t.isIncomeLike() -> incomeMap[key] = incomeMap.getValue(key) + abs(converted)
                t.direction == TransactionDirection.EXPENSE -> expenseMap[key] = expenseMap.getValue(key) + abs(converted)
            }
        }

        return monthKeys.map { key ->
            val inc = incomeMap.getValue(key)
            val exp = expenseMap.getValue(key)
            MonthlyTrend(month = key, incomeMinor = inc, expenseMinor = exp, balanceMinor = inc - exp)
        }
    }

    fun calculateCategoryBreakdown(
        transactions: List<Transaction>,
        categories: List<Category>,
        converter: CurrencyAmountConverter = NoConversion
    ): List<CategoryBreakdown> {
        val catMap = categories.associateBy { it.id }
        val totals = mutableMapOf<String, Long>()
        val counts = mutableMapOf<String, Int>()
        var totalExpense = 0L

        for (t in confirmed(transactions)) {
            if (t.direction != TransactionDirection.EXPENSE) continue
            val amt = abs(converter(t.amountMinor, t.currency, t.date) ?: continue)
            val catId = t.categoryId ?: "other"
            totalExpense += amt
            totals[catId] = (totals[catId] ?: 0L) + amt
            counts[catId] = (counts[catId] ?: 0) + 1
        }

        return totals.map { (catId, sum) ->
            val cat = catMap[catId]
            CategoryBreakdown(
                categoryId = catId,
                categoryName = cat?.displayName() ?: catId,
                color = cat?.color,
                icon = cat?.icon,
                totalMinor = sum,
                percentage = if (totalExpense > 0L) (sum.toFloat() / totalExpense.toFloat()) * 100f else 0f,
                transactionCount = counts[catId] ?: 0
            )
        }.sortedByDescending { it.totalMinor }
    }

    /**
     * Current balance of an account in its own currency: opening balance plus every
     * confirmed transaction. Transactions in another currency are converted; those
     * without a rate are reported in [AccountBalance.unconvertedCount].
     */
    fun accountBalance(
        openingBalanceMinor: Long,
        accountCurrency: String,
        accountTransactions: List<Transaction>,
        converter: (amountMinor: Long, from: String, to: String) -> Long?
    ): AccountBalance {
        var balance = openingBalanceMinor
        var unconverted = 0
        for (t in confirmed(accountTransactions)) {
            val amount = if (t.currency.equals(accountCurrency, ignoreCase = true)) {
                t.amountMinor
            } else {
                converter(t.amountMinor, t.currency, accountCurrency)
            }
            if (amount == null) unconverted++ else balance += amount
        }
        return AccountBalance(balance, unconverted)
    }
}

data class AccountBalance(val balanceMinor: Long, val unconvertedCount: Int)
