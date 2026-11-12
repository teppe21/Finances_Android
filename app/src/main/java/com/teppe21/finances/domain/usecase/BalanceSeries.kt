package com.teppe21.finances.domain.usecase

import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.model.Transaction
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class BalancePoint(val date: LocalDate, val balanceMinor: Long)

/** Chart ranges; [days] null means the whole history. */
enum class BalanceRange(val days: Long?) { WEEK(7), MONTH(30), QUARTER(90), YEAR(365), ALL(null) }

/** Longest history charted, so a stray 1970 date cannot produce tens of thousands of points. */
private const val MAX_HISTORY_DAYS = 3660L

/**
 * Total balance per day, from the first confirmed transaction (at most [MAX_HISTORY_DAYS] back) to [today],
 * in [target]. Same rules as the dashboard total: pending transactions are ignored, and the series is empty
 * when a transaction cannot be converted into its account's currency or an account balance cannot be
 * converted into [target] (the total is then unavailable too). Later-dated transactions count on [today], so the last
 * point equals the current total. Transfers need no special case: their legs cancel.
 */
fun balanceSeries(
    accounts: List<Account>,
    transactions: List<Transaction>,
    today: LocalDate,
    target: String,
    convert: (amountMinor: Long, from: String, to: String) -> Long?
): List<BalancePoint> {
    val earliest = transactions.filter { !it.pending }.minOfOrNull { it.date } ?: today
    val from = maxOf(minOf(earliest, today), today.minusDays(MAX_HISTORY_DAYS))
    val days = ChronoUnit.DAYS.between(from, today).toInt() + 1
    val totals = LongArray(days)
    for (account in accounts) {
        val delta = LongArray(days)
        var running = account.openingBalanceMinor
        for (t in transactions) {
            if (t.pending || t.accountId != account.id) continue
            val amount = if (t.currency.equals(account.currency, ignoreCase = true)) t.amountMinor
            else convert(t.amountMinor, t.currency, account.currency) ?: return emptyList()
            if (t.date.isBefore(from)) running += amount
            else delta[ChronoUnit.DAYS.between(from, minOf(t.date, today)).toInt()] += amount
        }
        for (i in 0 until days) {
            running += delta[i]
            totals[i] += convert(running, account.currency, target) ?: return emptyList()
        }
    }
    return List(days) { BalancePoint(from.plusDays(it.toLong()), totals[it]) }
}

/** The part of the series within [range] of its last day. */
fun List<BalancePoint>.inRange(range: BalanceRange): List<BalancePoint> {
    val days = range.days ?: return this
    val start = lastOrNull()?.date?.minusDays(days) ?: return this
    return filter { !it.date.isBefore(start) }
}
