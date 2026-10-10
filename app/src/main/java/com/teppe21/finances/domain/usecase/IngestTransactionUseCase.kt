package com.teppe21.finances.domain.usecase

import com.teppe21.finances.core.utils.TextNormalizer
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.domain.model.TransactionSource
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

data class IngestionInput(
    /** Target account; [Transaction.UNASSIGNED_ACCOUNT] keeps the transaction in the review queue. */
    val accountId: String,
    val date: LocalDate,
    val amountMinor: Long,
    val currency: String = "HUF",
    val direction: TransactionDirection,
    val description: String,
    val merchant: String? = null,
    val categoryId: String? = null,
    val source: TransactionSource,
    val sourceAppPackage: String? = null,
    val externalId: String? = null,
    val receiptId: String? = null,
    val notificationEventId: String? = null,
    val notes: String? = null,
    val pending: Boolean = false,
    val confidence: Float = 1.0f,
    /** Exact time of the event when known (notifications); used for cross-source matching. */
    val occurredAt: Instant? = null,
    val cardLast4: String? = null,
    /** Caller-supplied deterministic fingerprint; a unique one is generated when null. */
    val fingerprint: String? = null,
    /** Card issuer reported by a wallet notification, used by the source pair policy. */
    val issuerHint: String? = null
)

data class IngestionOutput(
    val status: DeduplicationStatus,
    val transaction: Transaction?,
    val duplicateOfId: String? = null,
    val reason: DuplicateReason = DuplicateReason.NONE
)

/**
 * Single entry point for every transaction source (manual, CSV, receipt, notification).
 * Normalizes sign and merchant, runs duplicate detection, categorizes, and stores the row
 * without ever replacing an existing one.
 */
class IngestTransactionUseCase(
    private val store: TransactionStore,
    private val rules: CategoryRuleSource,
    private val detector: DuplicateDetector = DuplicateDetector(),
    private val categorizeUseCase: CategorizeTransactionUseCase = CategorizeTransactionUseCase(),
    private val clock: () -> Instant = Instant::now
) {

    suspend fun execute(input: IngestionInput): IngestionOutput {
        val amount = normalizedAmount(input.direction, input.amountMinor)
        val cleanMerchant = TextNormalizer.cleanMerchantName(input.merchant).ifBlank { null }
        val fingerprint = input.fingerprint ?: Fingerprints.manual(UUID.randomUUID().toString())
        val currency = input.currency.uppercase().trim()

        val byExternal = input.externalId?.takeIf { it.isNotBlank() }?.let { store.findByExternalId(it) }
        val byFingerprint = store.findByFingerprint(fingerprint)
        val nearby = if (input.source == TransactionSource.NOTIFICATION && input.occurredAt != null) {
            val (from, to) = detector.searchDates(input.date)
            store.findSameAmount(amount, currency, from, to)
        } else {
            emptyList()
        }

        val check = detector.check(
            candidate = DedupCandidate(
                fingerprint = fingerprint,
                externalId = input.externalId,
                sourcePackage = input.sourceAppPackage,
                occurredAt = input.occurredAt,
                amountMinor = amount,
                currency = currency,
                direction = input.direction,
                merchant = cleanMerchant,
                cardLast4 = input.cardLast4
            ),
            exactByExternalId = byExternal,
            exactByFingerprint = byFingerprint,
            nearby = nearby,
            candidateIssuer = input.issuerHint
        )

        if (check.status == DeduplicationStatus.EXACT_DUPLICATE) {
            val existing = nearby.firstOrNull { it.id == check.matchedTransactionId }
            if (existing != null && check.reason == DuplicateReason.CROSS_SOURCE_MATCH) {
                enrich(existing, cleanMerchant, input)
            }
            return IngestionOutput(DeduplicationStatus.EXACT_DUPLICATE, null, check.matchedTransactionId, check.reason)
        }

        val categoryId = input.categoryId ?: when (input.direction) {
            TransactionDirection.TRANSFER -> "transfers"
            TransactionDirection.INCOME -> categorizeUseCase.execute(input.description, cleanMerchant, rules.activeRules(), fallback = "income")
            else -> categorizeUseCase.execute(input.description, cleanMerchant, rules.activeRules())
        }

        val possibleDuplicate = check.status == DeduplicationStatus.POSSIBLE_DUPLICATE
        val now = clock()
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            accountId = input.accountId,
            date = input.date,
            valueDate = input.occurredAt ?: now,
            amountMinor = amount,
            currency = currency,
            direction = input.direction,
            description = input.description.trim().ifBlank { cleanMerchant ?: "Transaction" },
            merchant = cleanMerchant,
            categoryId = categoryId,
            source = input.source,
            sourceAppPackage = input.sourceAppPackage,
            externalId = input.externalId?.takeIf { it.isNotBlank() },
            fingerprint = fingerprint,
            receiptId = input.receiptId,
            notificationEventId = input.notificationEventId,
            notes = input.notes ?: if (possibleDuplicate) "Possible duplicate: review before confirming" else null,
            pending = input.pending || possibleDuplicate || input.accountId.isBlank(),
            confidence = input.confidence,
            importedAt = now,
            createdAt = now,
            updatedAt = now,
            cardLast4 = input.cardLast4
        )

        if (!store.insertIfAbsent(transaction)) {
            // Lost a race with an identical insert (same fingerprint): treat as duplicate.
            val winner = store.findByFingerprint(fingerprint)
            return IngestionOutput(DeduplicationStatus.EXACT_DUPLICATE, null, winner?.id, DuplicateReason.FINGERPRINT)
        }
        return IngestionOutput(check.status, transaction, check.matchedTransactionId, check.reason)
    }

    /** The first notification may lack a merchant or card number that the second one has. */
    private suspend fun enrich(existing: Transaction, merchant: String?, input: IngestionInput) {
        val newMerchant = existing.merchant ?: merchant
        val newCard = existing.cardLast4 ?: input.cardLast4
        if (newMerchant != existing.merchant || newCard != existing.cardLast4) {
            store.update(
                existing.copy(
                    merchant = newMerchant,
                    description = if (existing.merchant == null && merchant != null) input.description else existing.description,
                    cardLast4 = newCard,
                    updatedAt = clock()
                )
            )
        }
    }

    companion object {
        /** Signed amount convention: money leaving the account is negative. */
        fun normalizedAmount(direction: TransactionDirection, amountMinor: Long): Long = when (direction) {
            TransactionDirection.EXPENSE -> -abs(amountMinor)
            TransactionDirection.INCOME, TransactionDirection.REFUND -> abs(amountMinor)
            else -> amountMinor
        }
    }
}
