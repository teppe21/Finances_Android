package com.teppe21.finances.data.local.database

import androidx.room.withTransaction
import com.teppe21.finances.data.local.entity.AccountEntity
import com.teppe21.finances.data.local.entity.TransactionEntity
import com.teppe21.finances.domain.model.AccountType
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.domain.model.TransactionSource
import java.time.Instant
import java.time.LocalDate

/**
 * "Delete all financial data": removes transactions, accounts, categories, keyword rules,
 * budgets, recurring rules, receipts and notification diagnostics, then restores the
 * default (empty) accounts and categories so the app keeps working.
 *
 * It deliberately leaves the app lock, preferences, exchange-rate cache and the stock
 * watchlist untouched; those are handled by the separate full reset.
 */
suspend fun deleteAllFinancialData(db: AppDatabase) {
    db.withTransaction {
        db.transactionDao().deleteAll()
        db.notificationDao().deleteAll()
        db.receiptDao().deleteAll()
        db.budgetDao().deleteAll()
        db.recurringDao().deleteAll()
        db.categoryRuleDao().deleteAll()
        db.categoryDao().deleteAll()
        db.accountDao().deleteAll()
        prepopulateDefaults(db)
    }
}

/** Full application reset of the database: every table, including watchlist and rates. */
suspend fun resetDatabase(db: AppDatabase) {
    db.withTransaction {
        deleteAllFinancialData(db)
        db.exchangeRateDao().deleteAll()
        db.watchlistDao().deleteAll()
        db.stockQuoteDao().deleteAll()
    }
}

/** Notification diagnostics are kept for 30 days. */
suspend fun pruneNotificationEvents(db: AppDatabase, now: Instant = Instant.now()) {
    db.notificationDao().deleteOlderThan(now.minusSeconds(30L * 24 * 3600))
}

private const val DEMO_PREFIX = "demo:"

/**
 * Loads clearly labelled synthetic data for demos and Play review. Every demo row has a
 * "demo:" fingerprint and the note "Demo data" so it can be removed again precisely.
 */
suspend fun loadSampleData(db: AppDatabase, today: LocalDate = LocalDate.now()) {
    val now = Instant.now()
    db.withTransaction {
        val accounts = db.accountDao().getAll().associateBy { it.id }
        suspend fun ensure(id: String, name: String, institution: String, type: AccountType, opening: Long) {
            val existing = accounts[id]
            val entity = existing?.copy(openingBalanceMinor = opening, currentBalanceMinor = opening, updatedAt = now)
                ?: AccountEntity(id, name, institution, type, "HUF", opening, opening, true, now, now)
            db.accountDao().insert(entity)
        }
        ensure("acc_cash", "Cash Wallet", "Wallet", AccountType.CASH, 25_000_00L)
        ensure("acc_otp", "OTP Current Account", "OTP Bank", AccountType.BANK, 450_000_00L)
        ensure("acc_revolut", "Revolut", "Revolut", AccountType.BANK, 120_000_00L)

        data class Demo(val days: Long, val acc: String, val amount: Long, val cur: String, val dir: TransactionDirection, val merchant: String, val cat: String)
        val rows = listOf(
            Demo(1, "acc_revolut", -4_590_00, "HUF", TransactionDirection.EXPENSE, "Demo Grocery", "food"),
            Demo(2, "acc_revolut", -1_290_00, "HUF", TransactionDirection.EXPENSE, "Demo Coffee Bar", "dining"),
            Demo(3, "acc_otp", -12_000_00, "HUF", TransactionDirection.EXPENSE, "Demo Fuel Station", "transport"),
            Demo(5, "acc_otp", 520_000_00, "HUF", TransactionDirection.INCOME, "Demo Employer", "income"),
            Demo(6, "acc_revolut", -1_499, "EUR", TransactionDirection.EXPENSE, "Demo Streaming", "subscriptions"),
            Demo(9, "acc_otp", -185_000_00, "HUF", TransactionDirection.EXPENSE, "Demo Rent", "housing"),
            Demo(12, "acc_cash", -3_200_00, "HUF", TransactionDirection.EXPENSE, "Demo Bakery", "food"),
            Demo(20, "acc_revolut", -2_350, "USD", TransactionDirection.EXPENSE, "Demo Online Shop", "shopping"),
            Demo(35, "acc_otp", 520_000_00, "HUF", TransactionDirection.INCOME, "Demo Employer", "income"),
            Demo(38, "acc_revolut", -6_780_00, "HUF", TransactionDirection.EXPENSE, "Demo Grocery", "food")
        )
        rows.forEachIndexed { index, d ->
            db.transactionDao().insertIgnore(
                TransactionEntity(
                    id = "demo_tx_$index",
                    accountId = d.acc,
                    date = today.minusDays(d.days),
                    valueDate = null,
                    amountMinor = d.amount,
                    currency = d.cur,
                    direction = d.dir,
                    description = d.merchant,
                    merchant = d.merchant,
                    categoryId = d.cat,
                    source = TransactionSource.MANUAL,
                    sourceAppPackage = null,
                    externalId = null,
                    fingerprint = "$DEMO_PREFIX$index",
                    receiptId = null,
                    notificationEventId = null,
                    recurringRuleId = null,
                    transferId = null,
                    notes = "Demo data",
                    pending = false,
                    confidence = 1f,
                    importedAt = now,
                    createdAt = now,
                    updatedAt = now,
                    cardLast4 = null
                )
            )
        }
    }
}

/** Removes only the rows created by [loadSampleData] and resets demo opening balances. */
suspend fun clearSampleData(db: AppDatabase) {
    val now = Instant.now()
    db.withTransaction {
        db.transactionDao().deleteByFingerprintPrefix("$DEMO_PREFIX%")
        for (id in listOf("acc_cash", "acc_otp", "acc_revolut")) {
            db.accountDao().getById(id)?.let {
                db.accountDao().insert(it.copy(openingBalanceMinor = 0L, currentBalanceMinor = 0L, updatedAt = now))
            }
        }
    }
}
