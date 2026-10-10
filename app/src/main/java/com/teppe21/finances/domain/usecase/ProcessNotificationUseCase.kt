package com.teppe21.finances.domain.usecase

import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.model.NotificationEvent
import com.teppe21.finances.domain.model.NotificationStatusCode
import com.teppe21.finances.domain.model.RawNotification
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.domain.model.TransactionSource
import com.teppe21.finances.native.notification.parsing.NotificationRecognizer
import com.teppe21.finances.native.notification.parsing.Recognition
import com.teppe21.finances.native.notification.parsing.SourceType
import com.teppe21.finances.native.notification.parsing.SupportedSources
import java.util.UUID

enum class AccountMatchConfidence { HIGH, MEDIUM, LOW }

data class AccountMatch(val account: Account?, val confidence: AccountMatchConfidence)

object AccountMatcher {

    /**
     * HIGH: the user explicitly mapped this institution to an account.
     * MEDIUM: exactly one active account belongs to the institution.
     * LOW: no account, or several candidates; the transaction goes to the review queue.
     */
    fun match(accounts: List<Account>, mappedAccountId: String?, institution: String?): AccountMatch {
        val active = accounts.filter { it.isActive }
        if (mappedAccountId != null) {
            active.firstOrNull { it.id == mappedAccountId }?.let { return AccountMatch(it, AccountMatchConfidence.HIGH) }
        }
        if (institution.isNullOrBlank()) return AccountMatch(null, AccountMatchConfidence.LOW)
        val candidates = active.filter { it.institution.equals(institution, ignoreCase = true) }
        return if (candidates.size == 1) {
            AccountMatch(candidates.single(), AccountMatchConfidence.MEDIUM)
        } else {
            AccountMatch(null, AccountMatchConfidence.LOW)
        }
    }
}

/** Wallet <-> bank pairs that may report the same card payment. */
val WalletBankPairPolicy = SourcePairPolicy { candidatePackage, existingPackage, candidateIssuer ->
    val a = SupportedSources.forPackage(candidatePackage)
    val b = SupportedSources.forPackage(existingPackage)
    when {
        a == null || b == null || a == b -> false
        a.type == SourceType.WALLET && b.type == SourceType.BANK ->
            candidateIssuer == null || candidateIssuer.equals(b.institution, ignoreCase = true)
        a.type == SourceType.BANK && b.type == SourceType.WALLET -> true
        else -> false
    }
}

fun interface NotificationEventSink {
    suspend fun record(event: NotificationEvent)
}

/**
 * Full notification pipeline after text extraction: recognition, account matching,
 * duplicate detection and persistence. Only sanitized status codes are recorded.
 * Notifications from unsupported packages are dropped without writing anything.
 */
class ProcessNotificationUseCase(
    private val recognizer: NotificationRecognizer,
    private val accounts: suspend () -> List<Account>,
    private val mappedAccountId: suspend (institution: String) -> String?,
    private val ingest: IngestTransactionUseCase,
    private val events: NotificationEventSink,
    private val autoAcceptThreshold: Float = 0.9f
) {

    suspend fun process(raw: RawNotification): NotificationStatusCode {
        val eventId = UUID.randomUUID().toString()
        val recognition = recognizer.recognize(raw)

        if (recognition is Recognition.Rejected) {
            if (recognition.source != null) {
                events.record(event(eventId, raw, recognition.source.displayName, recognition.code, null))
            }
            return recognition.code
        }

        val recognized = recognition as Recognition.Recognized
        val parsed = recognized.transaction
        val institution = if (recognized.source.type == SourceType.WALLET) parsed.suggestedInstitution else recognized.source.institution
        val match = AccountMatcher.match(
            accounts(),
            institution?.let { mappedAccountId(it) },
            institution
        )
        val lowConfidence = parsed.confidence < autoAcceptThreshold

        val output = ingest.execute(
            IngestionInput(
                accountId = match.account?.id ?: Transaction.UNASSIGNED_ACCOUNT,
                date = parsed.date,
                amountMinor = parsed.amountMinor,
                currency = parsed.currency,
                direction = parsed.direction,
                description = parsed.description,
                merchant = parsed.merchant,
                source = TransactionSource.NOTIFICATION,
                sourceAppPackage = raw.packageName,
                notificationEventId = eventId,
                pending = match.account == null || lowConfidence,
                confidence = parsed.confidence,
                occurredAt = parsed.occurredAt,
                cardLast4 = parsed.cardLast4,
                fingerprint = Fingerprints.notification(
                    raw.packageName, raw.notificationKey, raw.postedAt,
                    parsed.amountMinor, parsed.currency, parsed.direction
                ),
                issuerHint = parsed.suggestedInstitution
            )
        )

        val code = when {
            output.status == DeduplicationStatus.EXACT_DUPLICATE -> NotificationStatusCode.DUPLICATE_EXISTING_TRANSACTION
            output.status == DeduplicationStatus.POSSIBLE_DUPLICATE -> NotificationStatusCode.PENDING_POSSIBLE_DUPLICATE
            match.account == null -> NotificationStatusCode.PENDING_ACCOUNT_REVIEW
            lowConfidence -> NotificationStatusCode.PENDING_LOW_CONFIDENCE
            parsed.direction == TransactionDirection.INCOME -> NotificationStatusCode.ACCEPTED_INCOME
            parsed.direction == TransactionDirection.TRANSFER -> NotificationStatusCode.ACCEPTED_TRANSFER
            parsed.direction == TransactionDirection.REFUND -> NotificationStatusCode.ACCEPTED_REFUND
            else -> NotificationStatusCode.ACCEPTED_EXPENSE
        }
        events.record(
            event(
                eventId, raw, recognized.source.displayName, code,
                output.transaction?.id ?: output.duplicateOfId,
                fingerprint = output.transaction?.fingerprint
            )
        )
        return code
    }

    private fun event(
        id: String,
        raw: RawNotification,
        sourceName: String,
        code: NotificationStatusCode,
        transactionId: String?,
        fingerprint: String? = null
    ) = NotificationEvent(
        id = id,
        packageName = raw.packageName,
        postedAt = raw.postedAt,
        sourceBank = sourceName,
        processed = code.parseStatus != com.teppe21.finances.domain.model.NotificationParseStatus.NEEDS_REVIEW,
        parseStatus = code.parseStatus,
        transactionId = transactionId,
        fingerprint = fingerprint,
        reasonCode = code.name
    )
}
