package com.teppe21.finances.native.notification.parsing

import com.teppe21.finances.domain.model.NotificationStatusCode
import com.teppe21.finances.domain.model.ParsedNotificationTransaction
import com.teppe21.finances.domain.model.RawNotification
import com.teppe21.finances.domain.model.TransactionDirection
import java.time.ZoneId

sealed interface Recognition {
    data class Rejected(val source: NotificationSource?, val code: NotificationStatusCode) : Recognition
    data class Recognized(val source: NotificationSource, val transaction: ParsedNotificationTransaction) : Recognition
}

/**
 * Pure recognition stage of the notification pipeline:
 *
 *   package routing -> security filter -> promotional filter -> source parser -> validation
 *
 * It has no side effects; persistence happens in [NotificationIngestor].
 */
class NotificationRecognizer(
    private val sources: (String) -> NotificationSource? = SupportedSources::forPackage,
    private val zone: ZoneId = ZoneId.systemDefault()
) {

    fun recognize(raw: RawNotification): Recognition {
        val source = sources(raw.packageName)
            ?: return Recognition.Rejected(null, NotificationStatusCode.REJECTED_UNSUPPORTED_PACKAGE)

        val text = NotificationText(raw.title, raw.text, raw.bigText)
        if (text.full.isBlank()) {
            return Recognition.Rejected(source, NotificationStatusCode.REJECTED_NON_TRANSACTION)
        }
        if (SecurityFilter.isSecurityMessage(text.folded)) {
            return Recognition.Rejected(source, NotificationStatusCode.REJECTED_SECURITY_MESSAGE)
        }
        if (PromotionalFilter.hasStrongCue(text.folded)) {
            return Recognition.Rejected(source, NotificationStatusCode.REJECTED_PROMOTIONAL)
        }

        val fields = when (val outcome = source.parser.parse(text)) {
            is ParseOutcome.Parsed -> outcome.fields
            ParseOutcome.NotATransaction -> {
                val code = if (PromotionalFilter.hasWeakCue(text.folded)) {
                    NotificationStatusCode.REJECTED_PROMOTIONAL
                } else {
                    NotificationStatusCode.REJECTED_NON_TRANSACTION
                }
                return Recognition.Rejected(source, code)
            }
            ParseOutcome.Malformed -> return Recognition.Rejected(source, NotificationStatusCode.REJECTED_MALFORMED_TRANSACTION)
        }

        // Reward / cashback style credits are treated as promotional: their wording overlaps
        // with real incoming payments, and a missed credit is safer than an invented one.
        if (PromotionalFilter.hasWeakCue(text.folded) && fields.direction != TransactionDirection.EXPENSE) {
            return Recognition.Rejected(source, NotificationStatusCode.REJECTED_PROMOTIONAL)
        }

        if (!isValid(fields)) {
            return Recognition.Rejected(source, NotificationStatusCode.REJECTED_MALFORMED_TRANSACTION)
        }

        val confidence = if (source.verifiedFixtures) fields.confidence else minOf(fields.confidence, 0.8f)
        return Recognition.Recognized(
            source,
            ParsedNotificationTransaction(
                amountMinor = fields.amountMinor,
                currency = fields.currency,
                direction = fields.direction,
                merchant = fields.merchant,
                description = fields.description,
                date = raw.postedAt.atZone(zone).toLocalDate(),
                occurredAt = raw.postedAt,
                suggestedInstitution = fields.suggestedInstitution,
                cardLast4 = fields.cardLast4,
                confidence = confidence
            )
        )
    }

    private fun isValid(fields: ParsedFields): Boolean {
        if (fields.amountMinor == 0L) return false
        // 100 million in major units is far outside personal card / transfer notifications
        // and almost certainly a misread reference number.
        if (kotlin.math.abs(fields.amountMinor) > 100_000_000_00L) return false
        val outflow = fields.direction == TransactionDirection.EXPENSE || fields.direction == TransactionDirection.TRANSFER
        return if (outflow) fields.amountMinor < 0 else fields.amountMinor > 0
    }
}
