package com.teppe21.finances.domain.model

data class FinancialStats(
    val incomeMinor: Long = 0L,
    val expenseMinor: Long = 0L,
    val balanceMinor: Long = 0L,
    /**
     * Money moved out of an account to another own account, an exchange or cash. Only outgoing
     * legs count, so a move between two tracked accounts is not counted twice.
     */
    val transfersMinor: Long = 0L,
    val transferCount: Int = 0,
    /** Money sent to and received from other people. Never part of income or spending. */
    val personalSentMinor: Long = 0L,
    val personalReceivedMinor: Long = 0L,
    val personalCount: Int = 0,
    val savingsRate: Float = 0.0f,
    val transactionCount: Int = 0,
    val maxExpenseItem: MaxExpenseItem = MaxExpenseItem(),
    val avgExpenseMinor: Long = 0L,
    /** Confirmed transactions left out because no exchange rate was available. */
    val unconvertedCount: Int = 0
)

data class MaxExpenseItem(
    val description: String = "-",
    val amountMinor: Long = 0L
)

data class MonthlyTrend(
    val month: String, // YYYY-MM
    val incomeMinor: Long,
    val expenseMinor: Long,
    val balanceMinor: Long
)

data class CategoryBreakdown(
    val categoryId: String,
    val categoryName: String,
    val color: String? = null,
    val icon: String? = null,
    val totalMinor: Long = 0L,
    val percentage: Float = 0.0f,
    val transactionCount: Int = 0
)
