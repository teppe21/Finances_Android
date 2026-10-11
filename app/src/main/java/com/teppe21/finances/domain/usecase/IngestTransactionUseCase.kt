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
    val issuerHint: String? = null,
    /** The bank's own transaction type from a CSV file, used by the rules. */
    val bankType: String? = null,
    /** A built-in category id named by the file's own category column. */
    val bankCategoryId: String? = null,
    /** [categoryId] was chosen by the user; rules will never change it. */
    val categorySetByUser: Boolean = false
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

        // A category given by the caller is the user's choice and keeps the caller's direction.
        // Otherwise the rules pick the category, and the category decides whether this is a transfer.
        val categoryId: String
        val direction: TransactionDirection
        if (input.categoryId != null) {
            categoryId = input.categoryId
            direction = input.direction
        } else {
            val c = categorizeUseCase.classify(
                ClassificationInput(input.description, cleanMerchant, input.bankType, input.bankCategoryId, amount),
                rules.activeRules()
            )
            categoryId = c.categoryId
            direction = when {
                c.direction.isTransfer -> c.direction
                input.direction == TransactionDirection.REFUND -> TransactionDirection.REFUND
                else -> c.direction
            }
        }
        // A person's name or "Exchanged to EUR" is not a merchant.
        val merchant = if (direction.isTransfer && input.categoryId == null) null else cleanMerchant
        val transferId = if (direction == TransactionDirection.TRANSFER) findTransferLeg(input, amount, currency) else null

        val possibleDuplicate = check.status == DeduplicationStatus.POSSIBLE_DUPLICATE
        val now = clock()
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            accountId = input.accountId,
            date = input.date,
            valueDate = input.occurredAt ?: now,
            amountMinor = amount,
            currency = currency,
            direction = direction,
            description = input.description.trim().ifBlank { cleanMerchant ?: "Transaction" },
            merchant = merchant,
            categoryId = categoryId,
            categorySetByUser = input.categorySetByUser && input.categoryId != null,
            transferId = transferId?.second,
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
        transferId?.let { (leg, id) -> store.update(leg.copy(transferId = id, updatedAt = now)) }
        return IngestionOutput(check.status, transaction, check.matchedTransactionId, check.reason)
    }

    /**
     * The other leg of a move between two of the user's accounts: an unlinked own-account
     * transfer in another account with the opposite amount in the same currency, at most
     * [TRANSFER_LEG_DAYS] days apart (the closest wins). Returns it with a new shared id.
     * Exchanges have two currencies and are not linked; they are excluded from totals anyway.
     */
    private suspend fun findTransferLeg(input: IngestionInput, amount: Long, currency: String): Pair<Transaction, String>? {
        if (input.accountId.isBlank() || amount == 0L) return null
        val from = input.date.minusDays(TRANSFER_LEG_DAYS)
        val to = input.date.plusDays(TRANSFER_LEG_DAYS)
        val leg = store.findSameAmount(-amount, currency, from, to)
            .filter { it.direction == TransactionDirection.TRANSFER && it.transferId == null && it.accountId.isNotBlank() && it.accountId != input.accountId }
            .minByOrNull { abs(it.date.toEpochDay() - input.date.toEpochDay()) }
            ?: return null
        return leg to UUID.randomUUID().toString()
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
        const val TRANSFER_LEG_DAYS = 3L

        /** Signed amount convention: money leaving the account is negative. */
        fun normalizedAmount(direction: TransactionDirection, amountMinor: Long): Long = when (direction) {
            TransactionDirection.EXPENSE -> -abs(amountMinor)
            TransactionDirection.INCOME, TransactionDirection.REFUND -> abs(amountMinor)
            else -> amountMinor
        }
    }
}
