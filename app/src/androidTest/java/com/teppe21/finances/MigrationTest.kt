package com.teppe21.finances

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.teppe21.finances.data.local.database.AppDatabase
import com.teppe21.finances.data.local.database.deleteAllFinancialData
import com.teppe21.finances.data.local.entity.WatchlistEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Runs on a device or emulator: ./gradlew connectedDebugAndroidTest
 * Schemas 3, 4 and 5 are exported under app/schemas (5.json is written by the first build).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate3To5_keepsUserDataAndScrubsNotificationText() {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                """INSERT INTO accounts (id, name, institution, type, currency, openingBalanceMinor, currentBalanceMinor, isActive, createdAt, updatedAt)
                   VALUES ('acc_1', 'Main', 'OTP Bank', 'BANK', 'HUF', 100000, 100000, 1, 0, 0)"""
            )
            execSQL(
                """INSERT INTO transactions (id, accountId, date, valueDate, amountMinor, currency, direction, description, merchant, categoryId,
                   source, sourceAppPackage, externalId, fingerprint, receiptId, notificationEventId, recurringRuleId, transferId, notes,
                   pending, confidence, importedAt, createdAt, updatedAt)
                   VALUES ('tx_1', 'acc_1', '2026-03-28', NULL, -80400, 'HUF', 'EXPENSE', 'You spent HUF804 HUF balance: HUF42,285.97', 'Coop', 'food',
                   'NOTIFICATION', 'com.revolut.revolut', NULL, 'fp1', NULL, NULL, NULL, NULL, NULL, 0, 0.95, 0, 0, 0)"""
            )
            execSQL(
                """INSERT INTO transactions (id, accountId, date, valueDate, amountMinor, currency, direction, description, merchant, categoryId,
                   source, sourceAppPackage, externalId, fingerprint, receiptId, notificationEventId, recurringRuleId, transferId, notes,
                   pending, confidence, importedAt, createdAt, updatedAt)
                   VALUES ('tx_2', 'acc_1', '2026-03-29', NULL, -125000, 'HUF', 'EXPENSE', 'Groceries', 'Spar', 'food',
                   'MANUAL', NULL, NULL, 'fp2', NULL, NULL, NULL, NULL, 'my note', 0, 1.0, 0, 0, 0)"""
            )
            execSQL(
                """INSERT INTO receipts (id, imageUri, scannedAt, merchant, date, totalMinor, subtotalMinor, taxMinor, currency, paymentMethod, rawOcrText, confidence, transactionId)
                   VALUES ('r1', 'content://media/1', 0, 'Spar', '2026-03-29', 125000, NULL, NULL, 'HUF', 'CARD', 'SPAR ... FIZETENDO', 0.9, 'tx_2')"""
            )
            execSQL(
                """INSERT INTO notification_events (id, packageName, title, text, postedAt, sourceBank, processed, parseStatus, transactionId, fingerprint)
                   VALUES ('n1', 'com.revolut.revolut', 'Coop', 'You spent HUF804', 0, 'Revolut', 1, 'PARSED', 'tx_1', 'fp1')"""
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 5, true, *AppDatabase.ALL_MIGRATIONS)

        db.query("SELECT description, merchant, amountMinor, cardLast4 FROM transactions WHERE id = 'tx_1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Coop", c.getString(0))
            assertEquals(-80400L, c.getLong(2))
            assertTrue(c.isNull(3))
        }
        db.query("SELECT description, notes FROM transactions WHERE id = 'tx_2'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Groceries", c.getString(0))
            assertEquals("my note", c.getString(1))
        }
        db.query("SELECT rawOcrText, imageUri, totalMinor FROM receipts WHERE id = 'r1'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
            assertTrue(c.isNull(1))
            assertEquals(125000L, c.getLong(2))
        }
        db.query("PRAGMA table_info(notification_events)").use { c ->
            val columns = mutableListOf<String>()
            while (c.moveToNext()) columns += c.getString(c.getColumnIndexOrThrow("name"))
            assertTrue("raw notification columns must be gone", "title" !in columns && "text" !in columns)
        }
        db.query("SELECT COUNT(*) FROM watchlist").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun deletingFinancialDataKeepsWatchlistAndRestoresDefaults() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        db.watchlistDao().insertIgnore(WatchlistEntity("AAPL", "AAPL", "Apple", "NASDAQ", "USD", 0, Instant.EPOCH))
        com.teppe21.finances.data.local.database.loadSampleData(db)
        assertTrue(db.transactionDao().getAll().isNotEmpty())

        deleteAllFinancialData(db)

        assertTrue(db.transactionDao().getAll().isEmpty())
        assertEquals(4, db.accountDao().getAll().size)
        assertTrue(db.accountDao().getAll().all { it.openingBalanceMinor == 0L })
        assertEquals("AAPL", db.watchlistDao().getAll().single().symbol)
        assertNull(db.receiptDao().getById("anything"))
        db.close()
    }
}
