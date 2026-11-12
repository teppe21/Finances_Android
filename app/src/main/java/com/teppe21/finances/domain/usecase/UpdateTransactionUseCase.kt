package com.teppe21.finances.domain.usecase

import com.teppe21.finances.core.utils.TextNormalizer
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.model.TransactionDirection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** What the edit dialog can change. [amountMinor] may carry either sign; the direction decides it. */
data class TransactionEdit(
    val accountId: String,
    val date: LocalDate,
    val amountMinor: Long,
    val currency: String,
    val direction: TransactionDirection,
    val description: String,
    val merchant: String?,
    /** Null keeps an uncategorised transaction uncategorised. */
    val categoryId: String?
)

/**
 * Edits a stored transaction in place, applying the same sign and merchant normalisation as ingestion.
 * Identity, source, fingerprint, external id, pending flag and transfer link are kept: a fingerprint of a
 * manual row is random and one from an import identifies the source row, so keeping it neither collides
 * with itself nor lets the same import row be added again. Rules are not re-run; the category only
 * becomes "set by user" when it actually changed. A date change moves [Transaction.valueDate] to the new
 * day and keeps its time of day, so cross-source matching still sees the same moment on that day.
 */
class UpdateTransactionUseCase(
    private val store: TransactionStore,
    private val clock: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    /** @throws IllegalArgumentException for a zero amount or a date after today. */
    suspend fun execute(existing: Transaction, edit: TransactionEdit): Transaction {
        require(edit.amountMinor != 0L) { "amount must not be zero" }
        require(!edit.date.isAfter(clock().atZone(zone).toLocalDate())) { "date must not be in the future" }
        val merchant = TextNormalizer.cleanMerchantName(edit.merchant).ifBlank { null }
        val categoryChanged = edit.categoryId != existing.categoryId
        val updated = existing.copy(
            accountId = edit.accountId,
            date = edit.date,
            valueDate = if (edit.date == existing.date) existing.valueDate else existing.valueDate?.let { moveToDay(it, edit.date, zone) },
            amountMinor = IngestTransactionUseCase.normalizedAmount(edit.direction, edit.amountMinor),
            currency = edit.currency.uppercase().trim(),
            direction = edit.direction,
            description = edit.description.trim().ifBlank { merchant ?: "Transaction" },
            merchant = merchant,
            categoryId = edit.categoryId,
            categorySetByUser = existing.categorySetByUser || categoryChanged,
            updatedAt = clock()
        )
        store.update(updated)
        return updated
    }
}

/** Material's date picker speaks UTC-midnight millis for a calendar day; the app stores a [LocalDate]. */
fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
fun pickerMillisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/** [instant] on [day] in [zone], with the same local time of day. */
fun moveToDay(instant: Instant, day: LocalDate, zone: ZoneId): Instant =
    instant.atZone(zone).toLocalTime().atDate(day).atZone(zone).toInstant()
