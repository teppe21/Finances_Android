package com.teppe21.finances.support

import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.model.AccountType
import com.teppe21.finances.domain.model.CategoryRule
import com.teppe21.finances.domain.model.NotificationEvent
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.domain.model.TransactionSource
import com.teppe21.finances.domain.usecase.CategoryRuleSource
import com.teppe21.finances.domain.usecase.NotificationEventSink
import com.teppe21.finances.domain.usecase.TransactionStore
import java.time.Instant
import java.time.LocalDate

/** In-memory TransactionStore with the same no-replace semantics as the Room DAO. */
class InMemoryTransactionStore : TransactionStore {
    val rows = mutableListOf<Transaction>()

    override suspend fun findByFingerprint(fingerprint: String) = rows.firstOrNull { it.fingerprint == fingerprint }
    override suspend fun findByExternalId(externalId: String) = rows.firstOrNull { it.externalId == externalId }
    override suspend fun findSameAmount(amountMinor: Long, currency: String, from: LocalDate, to: LocalDate) =
        rows.filter { it.amountMinor == amountMinor && it.currency == currency && !it.date.isBefore(from) && !it.date.isAfter(to) }

    override suspend fun insertIfAbsent(transaction: Transaction): Boolean {
        if (rows.any { it.id == transaction.id || it.fingerprint == transaction.fingerprint }) return false
        rows += transaction
        return true
    }

    override suspend fun update(transaction: Transaction) {
        val i = rows.indexOfFirst { it.id == transaction.id }
        if (i >= 0) rows[i] = transaction
    }
}

class RecordingEventSink : NotificationEventSink {
    val events = mutableListOf<NotificationEvent>()
    override suspend fun record(event: NotificationEvent) {
        events += event
    }
}

val NoRules = CategoryRuleSource { emptyList<CategoryRule>() }

fun account(id: String, institution: String, name: String = institution) =
    Account(id = id, name = name, institution = institution, type = AccountType.BANK)

fun tx(
    id: String,
    amountMinor: Long,
    date: LocalDate = LocalDate.of(2026, 9, 15),
    currency: String = "HUF",
    direction: TransactionDirection = if (amountMinor < 0) TransactionDirection.EXPENSE else TransactionDirection.INCOME,
    pending: Boolean = false,
    categoryId: String? = null,
    accountId: String = "acc"
) = Transaction(
    id = id,
    accountId = accountId,
    date = date,
    amountMinor = amountMinor,
    currency = currency,
    direction = direction,
    description = id,
    merchant = id,
    categoryId = categoryId,
    source = TransactionSource.MANUAL,
    fingerprint = "fp_$id",
    pending = pending,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH
)
