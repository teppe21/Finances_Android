package com.teppe21.finances

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.teppe21.finances.domain.model.BuiltIns
import com.teppe21.finances.data.local.database.AppDatabase
import com.teppe21.finances.data.local.database.deleteAllFinancialData
import com.teppe21.finances.data.local.database.prepopulateDefaults
import com.teppe21.finances.data.local.entity.AccountEntity
import com.teppe21.finances.data.local.entity.CategoryEntity
import com.teppe21.finances.data.local.entity.CategoryRuleEntity
import com.teppe21.finances.data.local.entity.NotificationEventEntity
import com.teppe21.finances.data.local.entity.ReceiptEntity
import com.teppe21.finances.data.local.entity.TransactionEntity
import com.teppe21.finances.data.repository.ReceiptRepository
import com.teppe21.finances.domain.model.AccountType
import com.teppe21.finances.domain.model.NotificationParseStatus
import com.teppe21.finances.domain.model.PaymentMethod
import com.teppe21.finances.domain.model.ReceiptScan
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.domain.model.TransactionSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Real SQLite through Room, in isolated databases with synthetic rows only. Never touches
 * the app's own finances.db.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun close() {
        db.close()
    }

    private fun tx(
        id: String,
        fingerprint: String = "fp_$id",
        accountId: String = "acc_1",
        date: LocalDate = LocalDate.of(2026, 3, 15),
        amountMinor: Long = -10_000L,
        currency: String = "HUF",
        categoryId: String? = "food",
        pending: Boolean = false,
        externalId: String? = null
    ) = TransactionEntity(
        id = id, accountId = accountId, date = date, valueDate = null, amountMinor = amountMinor,
        currency = currency, direction = TransactionDirection.EXPENSE, description = id, merchant = id,
        categoryId = categoryId, source = TransactionSource.MANUAL, sourceAppPackage = null,
        externalId = externalId, fingerprint = fingerprint, receiptId = null, notificationEventId = null,
        recurringRuleId = null, transferId = null, notes = null, pending = pending, confidence = 1f,
        importedAt = Instant.EPOCH, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
    )

    private fun account(id: String, name: String = id, active: Boolean = true) = AccountEntity(
        id, name, "Example Bank", AccountType.BANK, "HUF", 0L, 0L, active, Instant.EPOCH, Instant.EPOCH
    )

    // ---- default data ---------------------------------------------------------------------

    @Test
    fun prepopulateCreatesDefaultsOnce() = runBlocking {
        prepopulateDefaults(db)
        val accounts = db.accountDao().getAll().size
        val categories = db.categoryDao().getAll().size
        val rules = db.categoryRuleDao().getAll().size
        assertEquals(4, accounts)
        assertEquals(BuiltIns.categories.size, categories)
        assertTrue(rules > 40)

        prepopulateDefaults(db)
        assertEquals(accounts, db.accountDao().getAll().size)
        assertEquals(categories, db.categoryDao().getAll().size)
        assertEquals(rules, db.categoryRuleDao().getAll().size)
    }

    @Test
    fun prepopulateNeverOverwritesUserChangesToDefaults() = runBlocking {
        prepopulateDefaults(db)
        val cash = db.accountDao().getById("acc_cash")!!
        db.accountDao().update(cash.copy(name = "My wallet", openingBalanceMinor = 55_000L))
        val food = db.categoryDao().getById("food")!!
        db.categoryDao().update(food.copy(name = "Groceries", color = "#000000"))
        val rule = db.categoryRuleDao().getRulesForCategory("food").first { it.pattern == "lidl" }
        db.categoryRuleDao().update(rule.copy(isActive = false))

        prepopulateDefaults(db)

        assertEquals("My wallet", db.accountDao().getById("acc_cash")!!.name)
        assertEquals(55_000L, db.accountDao().getById("acc_cash")!!.openingBalanceMinor)
        assertEquals("Groceries", db.categoryDao().getById("food")!!.name)
        assertEquals("#000000", db.categoryDao().getById("food")!!.color)
        assertFalse(
            "a disabled default rule stays disabled",
            db.categoryRuleDao().getRulesForCategory("food").any { it.id == rule.id }
        )
    }

    @Test
    fun theCreateCallbackSeedsTheInstanceItBelongsTo() = runBlocking {
        val ready = CompletableDeferred<AppDatabase>()
        val seeded = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.prepopulationCallback(ready))
            .build()
        try {
            ready.complete(seeded)
            seeded.openHelper.writableDatabase // opens the file, which triggers onCreate
            withTimeout(10_000) {
                while (seeded.accountDao().getAll().size < 4) delay(50)
            }
            assertEquals(4, seeded.accountDao().getAll().size)
            assertEquals(BuiltIns.categories.size, seeded.categoryDao().getAll().size)
        } finally {
            seeded.close()
        }
    }

    @Test
    fun aDefaultTheUserDeletedIsNotRecreatedWhenTheDatabaseIsReopened() = runBlocking {
        val name = "reopen-test.db"
        context.deleteDatabase(name)
        suspend fun open(): AppDatabase {
            val ready = CompletableDeferred<AppDatabase>()
            val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addCallback(AppDatabase.prepopulationCallback(ready))
                .build()
            ready.complete(database)
            database.openHelper.writableDatabase
            return database
        }

        val first = open()
        try {
            withTimeout(10_000) { while (first.categoryDao().getAll().size < BuiltIns.categories.size) delay(50) }
            first.categoryDao().deleteById("entertainment")
            assertNull(first.categoryDao().getById("entertainment"))
        } finally {
            first.close()
        }

        val second = open()
        try {
            delay(500) // onCreate must not run again, so there is nothing to wait for
            assertNull("removed default is not resurrected", second.categoryDao().getById("entertainment"))
            assertEquals(BuiltIns.categories.size - 1, second.categoryDao().getAll().size)
        } finally {
            second.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun deleteAllFinancialDataRestoresCleanDefaultsWithoutDuplicating() = runBlocking {
        prepopulateDefaults(db)
        db.transactionDao().insertIgnore(tx("t1"))
        db.categoryRuleDao().insert(CategoryRuleEntity("user_rule", "food", "my shop", "contains", 50, true, Instant.EPOCH))

        deleteAllFinancialData(db)

        assertTrue(db.transactionDao().getAll().isEmpty())
        assertEquals(4, db.accountDao().getAll().size)
        assertEquals(BuiltIns.categories.size, db.categoryDao().getAll().size)
        assertTrue(db.categoryRuleDao().getAll().none { it.id == "user_rule" })
    }

    // ---- transactions ---------------------------------------------------------------------

    @Test
    fun insertIgnoreKeepsTheOriginalRowOnFingerprintOrIdCollision() = runBlocking {
        assertTrue(db.transactionDao().insertIgnore(tx("t1", amountMinor = -111L)) != -1L)
        assertEquals(-1L, db.transactionDao().insertIgnore(tx("t2", fingerprint = "fp_t1", amountMinor = -222L)))
        assertEquals(-1L, db.transactionDao().insertIgnore(tx("t1", fingerprint = "other", amountMinor = -333L)))
        val all = db.transactionDao().getAll()
        assertEquals(1, all.size)
        assertEquals(-111L, all.single().amountMinor)
        assertEquals("t1", db.transactionDao().findByFingerprint("fp_t1")!!.id)
    }

    @Test
    fun periodAndPendingQueriesFilterAndOrder() = runBlocking {
        db.transactionDao().insertIgnore(tx("march1", date = LocalDate.of(2026, 3, 1)))
        db.transactionDao().insertIgnore(tx("march31", date = LocalDate.of(2026, 3, 31)))
        db.transactionDao().insertIgnore(tx("april1", date = LocalDate.of(2026, 4, 1)))
        db.transactionDao().insertIgnore(tx("waiting", date = LocalDate.of(2026, 3, 20), pending = true))

        val march = db.transactionDao().getByPeriodFlow(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).first()
        assertEquals(setOf("march1", "march31", "waiting"), march.map { it.id }.toSet())
        assertEquals("newest first", "march31", march.first().id)

        assertEquals(listOf("waiting"), db.transactionDao().getPendingFlow().first().map { it.id })
        assertEquals(listOf("april1", "march31"), db.transactionDao().getRecentFlow(2).first().map { it.id })
    }

    @Test
    fun possibleDuplicatesMatchAmountCurrencyAndDateWindow() = runBlocking {
        db.transactionDao().insertIgnore(tx("a", amountMinor = -80_400L, date = LocalDate.of(2026, 3, 10)))
        db.transactionDao().insertIgnore(tx("b", amountMinor = -80_400L, date = LocalDate.of(2026, 3, 12)))
        db.transactionDao().insertIgnore(tx("other-currency", amountMinor = -80_400L, currency = "EUR", date = LocalDate.of(2026, 3, 10)))
        db.transactionDao().insertIgnore(tx("other-amount", amountMinor = -80_500L, date = LocalDate.of(2026, 3, 10)))
        db.transactionDao().insertIgnore(tx("too-early", amountMinor = -80_400L, date = LocalDate.of(2026, 3, 1)))

        val found = db.transactionDao().findPossibleDuplicates(-80_400L, "HUF", LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 11))
        assertEquals(listOf("a"), found.map { it.id })
    }

    @Test
    fun externalIdLookupAndDeletes() = runBlocking {
        db.transactionDao().insertIgnore(tx("t1", externalId = "bank-ref-1"))
        db.transactionDao().insertIgnore(tx("t2"))
        assertEquals("t1", db.transactionDao().findByExternalId("bank-ref-1")!!.id)
        assertNull(db.transactionDao().findByExternalId("missing"))

        db.transactionDao().deleteById("t1")
        assertNull(db.transactionDao().getById("t1"))
        db.transactionDao().deleteByFingerprintPrefix("fp_t%")
        assertTrue(db.transactionDao().getAll().isEmpty())
    }

    @Test
    fun reassigningAccountsAndCategoriesMovesOnlyTheMatchingRows() = runBlocking {
        db.transactionDao().insertIgnore(tx("t1", accountId = "old", categoryId = "food"))
        db.transactionDao().insertIgnore(tx("t2", accountId = "old", categoryId = "other"))
        db.transactionDao().insertIgnore(tx("t3", accountId = "keep", categoryId = "food"))

        db.transactionDao().reassignAccount("old", "new", Instant.EPOCH)
        assertEquals(setOf("new"), db.transactionDao().getAll().filter { it.id != "t3" }.map { it.accountId }.toSet())
        assertEquals("keep", db.transactionDao().getById("t3")!!.accountId)

        assertEquals(2, db.transactionDao().countByCategoryId("food"))
        db.transactionDao().reassignCategory("food", "shopping")
        assertEquals(0, db.transactionDao().countByCategoryId("food"))
        assertEquals(2, db.transactionDao().countByCategoryId("shopping"))
    }

    // ---- accounts, categories, rules ------------------------------------------------------

    @Test
    fun inactiveAccountsAreHiddenFromTheListsButStillLoadableById() = runBlocking {
        db.accountDao().insert(account("a1", "Zeta"))
        db.accountDao().insert(account("a2", "Alpha"))
        db.accountDao().insert(account("a3", "Hidden", active = false))
        assertEquals(listOf("Alpha", "Zeta"), db.accountDao().getAll().map { it.name })
        assertEquals(listOf("Alpha", "Zeta"), db.accountDao().getAllFlow().first().map { it.name })
        assertNotNull(db.accountDao().getById("a3"))
    }

    @Test
    fun insertAllIfAbsentIgnoresExistingIds() = runBlocking {
        db.accountDao().insert(account("a1", "Mine"))
        db.accountDao().insertAllIfAbsent(listOf(account("a1", "Default"), account("a2", "New")))
        assertEquals("Mine", db.accountDao().getById("a1")!!.name)
        assertEquals("New", db.accountDao().getById("a2")!!.name)

        db.categoryDao().insert(CategoryEntity("c1", "Mine", null, null, false, false, Instant.EPOCH, Instant.EPOCH))
        db.categoryDao().insertAllIfAbsent(listOf(CategoryEntity("c1", "Default", null, null, false, true, Instant.EPOCH, Instant.EPOCH)))
        assertEquals("Mine", db.categoryDao().getById("c1")!!.name)
    }

    @Test
    fun activeRulesAreOrderedByPriorityAndPerCategory() = runBlocking {
        db.categoryRuleDao().insert(CategoryRuleEntity("r1", "food", "lidl", "contains", 10, true, Instant.EPOCH))
        db.categoryRuleDao().insert(CategoryRuleEntity("r2", "food", "my shop", "contains", 50, true, Instant.EPOCH))
        db.categoryRuleDao().insert(CategoryRuleEntity("r3", "food", "off", "contains", 90, false, Instant.EPOCH))
        db.categoryRuleDao().insert(CategoryRuleEntity("r4", "dining", "pizza", "contains", 10, true, Instant.EPOCH))

        assertEquals(listOf("r2", "r1", "r4"), db.categoryRuleDao().getAll().map { it.id })
        assertEquals(listOf("r2", "r1"), db.categoryRuleDao().getRulesForCategory("food").map { it.id })
        db.categoryRuleDao().deleteByCategoryId("food")
        assertEquals(listOf("r4"), db.categoryRuleDao().getAll().map { it.id })
    }

    // ---- notifications and receipts -------------------------------------------------------

    @Test
    fun notificationDiagnosticsKeepOnlySanitizedFields() = runBlocking {
        val event = NotificationEventEntity("n1", "com.revolut.revolut", Instant.parse("2026-03-01T10:00:00Z"), "Revolut", false, NotificationParseStatus.NEEDS_REVIEW, "t1", "fp", "PENDING_ACCOUNT_REVIEW")
        db.notificationDao().insert(event)
        db.notificationDao().insert(event.copy(id = "n2", processed = true, transactionId = "t2", postedAt = Instant.parse("2020-01-01T00:00:00Z")))

        assertEquals(listOf("n1"), db.notificationDao().getPending().map { it.id })
        db.notificationDao().markProcessedForTransaction("t1")
        assertTrue(db.notificationDao().getPending().isEmpty())

        db.notificationDao().deleteOlderThan(Instant.parse("2021-01-01T00:00:00Z"))
        assertEquals(listOf("n1"), db.notificationDao().getRecentFlow(10).first().map { it.id })

        db.query("PRAGMA table_info(notification_events)", null).use { c ->
            val columns = generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toList()
            assertTrue("no raw text columns", columns.none { it in setOf("title", "text", "bigText", "body") })
        }
    }

    @Test
    fun receiptRepositoryDropsTheImageReferenceAndRawText() = runBlocking {
        val repository = ReceiptRepository(db.receiptDao())
        repository.insertReceipt(
            ReceiptScan(
                id = "r1", imageUri = "content://media/external/images/1", merchant = "Example Market",
                date = LocalDate.of(2026, 3, 28), totalMinor = 124_900L, paymentMethod = PaymentMethod.CARD,
                rawOcrText = "EXAMPLE MARKET FIZETENDO 1 249 Ft"
            )
        )
        val stored: ReceiptEntity = db.receiptDao().getById("r1")!!
        assertNull(stored.imageUri)
        assertNull(stored.rawOcrText)
        assertEquals("Example Market", stored.merchant)
        assertEquals(124_900L, stored.totalMinor)
    }
}
