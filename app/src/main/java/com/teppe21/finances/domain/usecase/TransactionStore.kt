package com.teppe21.finances.domain.usecase

import com.teppe21.finances.domain.model.CategoryRule
import com.teppe21.finances.domain.model.Transaction
import java.time.LocalDate

/** Storage operations the ingestion pipeline needs. Implemented by the Room repository. */
interface TransactionStore {
    suspend fun findByFingerprint(fingerprint: String): Transaction?
    suspend fun findByExternalId(externalId: String): Transaction?
    suspend fun findSameAmount(amountMinor: Long, currency: String, from: LocalDate, to: LocalDate): List<Transaction>

    /** Inserts without replacing; returns false if a row with the same id or fingerprint exists. */
    suspend fun insertIfAbsent(transaction: Transaction): Boolean
    suspend fun update(transaction: Transaction)
}

fun interface CategoryRuleSource {
    suspend fun activeRules(): List<CategoryRule>
}
