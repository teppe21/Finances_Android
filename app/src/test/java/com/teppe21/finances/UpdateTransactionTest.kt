package com.teppe21.finances

import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.domain.model.TransactionSource
import com.teppe21.finances.domain.usecase.IngestTransactionUseCase
import com.teppe21.finances.domain.usecase.IngestionInput
import com.teppe21.finances.domain.usecase.TransactionEdit
import com.teppe21.finances.domain.usecase.UpdateTransactionUseCase
import com.teppe21.finances.domain.usecase.moveToDay
import com.teppe21.finances.domain.usecase.pickerMillisToDate
import com.teppe21.finances.domain.usecase.toPickerMillis
import java.time.Instant
import java.time.ZoneId
import com.teppe21.finances.support.InMemoryTransactionStore
import com.teppe21.finances.support.NoRules
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UpdateTransactionTest {
    private val store = InMemoryTransactionStore()
    private val update = UpdateTransactionUseCase(store)
    private val day = LocalDate.of(2026, 3, 5)

    private suspend fun seed(pending: Boolean = false, categoryId: String? = null) =
        IngestTransactionUseCase(store, NoRules).execute(
            IngestionInput(
                accountId = "a1", date = day, amountMinor = 1000, direction = TransactionDirection.EXPENSE,
                description = "Lunch", merchant = "Cafe", categoryId = categoryId,
                source = TransactionSource.MANUAL, pending = pending
            )
        ).transaction!!

    private fun edit(tx: com.teppe21.finances.domain.model.Transaction, amount: Long = 2500, direction: TransactionDirection = TransactionDirection.EXPENSE, category: String? = tx.categoryId) =
        TransactionEdit("a1", day, amount, "huf", direction, "Dinner", "  Bistro  ", category ?: "food")

    @Test fun editKeepsIdentityAndNormalisesSignAndMerchant() = runTest {
        val tx = seed(categoryId = "food")
        val out = update.execute(tx, edit(tx, amount = 2500))
        assertEquals(tx.id, out.id)
        assertEquals(tx.fingerprint, out.fingerprint)
        assertEquals(tx.source, out.source)
        assertEquals(-2500L, out.amountMinor)
        assertEquals("HUF", out.currency)
        assertEquals("Bistro", out.merchant)
        assertEquals(1, store.rows.size)
        assertEquals(out, store.rows.single())

        val income = update.execute(out, edit(out, amount = -700, direction = TransactionDirection.INCOME))
        assertEquals(700L, income.amountMinor)
    }

    @Test fun categoryFlagSetOnlyWhenCategoryChanges() = runTest {
        val tx = seed(categoryId = "food").copy(categorySetByUser = false).also { store.update(it) }
        val same = update.execute(tx, edit(tx))
        assertFalse(same.categorySetByUser)
        assertEquals("food", same.categoryId)

        val changed = update.execute(tx, edit(tx, category = "transport"))
        assertTrue(changed.categorySetByUser)
        assertEquals("transport", changed.categoryId)

        val stays = update.execute(changed, edit(changed))
        assertTrue(stays.categorySetByUser)
    }

    @Test fun pendingStaysPending() = runTest {
        val tx = seed(pending = true, categoryId = "food")
        assertTrue(update.execute(tx, edit(tx)).pending)
    }

    @Test fun dateAndAccountChangeKeepTheRestAndPending() = runTest {
        val tx = seed(pending = true, categoryId = "food")
        val out = update.execute(tx, edit(tx).copy(accountId = "a2", date = day.plusDays(3)))
        assertEquals("a2", out.accountId)
        assertEquals(day.plusDays(3), out.date)
        assertTrue(out.pending)
        assertEquals(tx.copy(accountId = "a2", date = day.plusDays(3), valueDate = out.valueDate, amountMinor = -2500, description = "Dinner", merchant = "Bistro", updatedAt = out.updatedAt), out)
    }

    @Test fun uncategorisedStaysUncategorised() = runTest {
        val tx = seed(categoryId = "food").copy(categoryId = null).also { store.update(it) }
        val out = update.execute(tx, edit(tx).copy(categoryId = null))
        assertEquals(null, out.categoryId)
        assertFalse(out.categorySetByUser)
    }

    @Test fun pickerConversionAndTimeOfDay() {
        assertEquals(day, pickerMillisToDate(day.toPickerMillis()))
        val zone = ZoneId.of("Europe/Budapest")
        val late = day.atTime(23, 30).atZone(zone).toInstant()
        assertEquals(late, moveToDay(late, day, zone))
        val moved = moveToDay(late, day.plusDays(2), zone)
        assertEquals(day.plusDays(2).atTime(23, 30).atZone(zone).toInstant(), moved)
        val auckland = ZoneId.of("Pacific/Auckland")
        val evening = Instant.parse("2026-03-05T10:00:00Z") // 23:00 local, 5 March UTC+13
        assertEquals(day, evening.atZone(auckland).toLocalDate())
        assertEquals(evening.atZone(auckland).toLocalTime(), moveToDay(evening, day.plusDays(1), auckland).atZone(auckland).toLocalTime())
    }
}
