package com.teppe21.finances.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.teppe21.finances.data.local.dao.AccountDao
import com.teppe21.finances.data.local.dao.BudgetDao
import com.teppe21.finances.data.local.dao.CategoryDao
import com.teppe21.finances.data.local.dao.CategoryRuleDao
import com.teppe21.finances.data.local.dao.NotificationDao
import com.teppe21.finances.data.local.dao.ReceiptDao
import com.teppe21.finances.data.local.dao.RecurringDao
import com.teppe21.finances.data.local.dao.TransactionDao
import com.teppe21.finances.data.local.entity.AccountEntity
import com.teppe21.finances.data.local.entity.BudgetEntity
import com.teppe21.finances.data.local.entity.CategoryEntity
import com.teppe21.finances.data.local.entity.CategoryRuleEntity
import com.teppe21.finances.data.local.entity.NotificationEventEntity
import com.teppe21.finances.data.local.entity.ReceiptEntity
import com.teppe21.finances.data.local.entity.RecurringRuleEntity
import com.teppe21.finances.data.local.entity.TransactionEntity
import com.teppe21.finances.domain.model.AccountType
import com.teppe21.finances.domain.model.BuiltIns
import com.teppe21.finances.data.repository.toEntity
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

import com.teppe21.finances.data.local.dao.ExchangeRateDao
import com.teppe21.finances.data.local.dao.StockQuoteDao
import com.teppe21.finances.data.local.dao.WatchlistDao
import com.teppe21.finances.data.local.entity.ExchangeRateEntity
import com.teppe21.finances.data.local.entity.StockQuoteEntity
import com.teppe21.finances.data.local.entity.WatchlistEntity
import androidx.room.migration.Migration

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        CategoryRuleEntity::class,
        NotificationEventEntity::class,
        ReceiptEntity::class,
        BudgetEntity::class,
        RecurringRuleEntity::class,
        ExchangeRateEntity::class,
        WatchlistEntity::class,
        StockQuoteEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun notificationDao(): NotificationDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun stockQuoteDao(): StockQuoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exchange_rates` (
                        `baseCurrency` TEXT NOT NULL,
                        `targetCurrency` TEXT NOT NULL,
                        `rate` REAL NOT NULL,
                        `rateDate` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `provider` TEXT NOT NULL,
                        PRIMARY KEY(`baseCurrency`, `targetCurrency`, `rateDate`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_externalId` ON `transactions` (`externalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_amountMinor_currency_date` ON `transactions` (`amountMinor`, `currency`, `date`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notification_events_new` (
                        `id` TEXT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `postedAt` INTEGER NOT NULL,
                        `sourceBank` TEXT,
                        `processed` INTEGER NOT NULL,
                        `parseStatus` TEXT NOT NULL,
                        `transactionId` TEXT,
                        `fingerprint` TEXT,
                        `reasonCode` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `notification_events_new` (`id`, `packageName`, `postedAt`, `sourceBank`, `processed`, `parseStatus`, `transactionId`, `fingerprint`, `reasonCode`)
                    SELECT `id`, `packageName`, `postedAt`, `sourceBank`, `processed`, `parseStatus`, `transactionId`, `fingerprint`, NULL
                    FROM `notification_events`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `notification_events`")
                db.execSQL("ALTER TABLE `notification_events_new` RENAME TO `notification_events`")
            }
        }

        /**
         * v5: card suffix column for duplicate detection, stock watchlist and quote cache
         * tables, and removal of raw notification / OCR text stored by earlier versions.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `cardLast4` TEXT")
                db.execSQL("ALTER TABLE `budgets` ADD COLUMN `currency` TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `watchlist` (
                        `symbol` TEXT NOT NULL,
                        `displaySymbol` TEXT NOT NULL,
                        `name` TEXT,
                        `exchange` TEXT,
                        `currency` TEXT,
                        `position` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`symbol`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `stock_quotes` (
                        `symbol` TEXT NOT NULL,
                        `price` TEXT NOT NULL,
                        `change` TEXT,
                        `changePercent` TEXT,
                        `open` TEXT,
                        `high` TEXT,
                        `low` TEXT,
                        `previousClose` TEXT,
                        `quoteTime` INTEGER NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        `provider` TEXT NOT NULL,
                        `currency` TEXT,
                        PRIMARY KEY(`symbol`)
                    )
                    """.trimIndent()
                )
                // Versions up to 4 copied the raw notification text into the description.
                db.execSQL(
                    """
                    UPDATE `transactions`
                    SET `description` = CASE
                        WHEN `merchant` IS NOT NULL AND `merchant` != '' THEN `merchant`
                        ELSE 'Recognized from notification'
                    END
                    WHERE `source` = 'NOTIFICATION'
                    """.trimIndent()
                )
                db.execSQL("UPDATE `receipts` SET `rawOcrText` = NULL, `imageUri` = NULL")
            }
        }

        /**
         * v6: transfer kinds and categorization.
         * - `transactions.categorySetByUser`, set for manual entries and receipts (the user picked
         *   their category), so re-applying rules never overwrites them.
         * - The new built-in categories and keyword rules, inserted only where missing.
         * - Schema-5 transfer rules re-pointed and re-prioritized by id (rules can be deleted in
         *   the app but not edited, so a row with that id is still the original).
         * Existing transactions keep their category and direction until the user re-applies rules.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `categorySetByUser` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `transactions` SET `categorySetByUser` = 1 WHERE `source` IN ('MANUAL', 'RECEIPT')")
                val now = Instant.now().toEpochMilli()
                for (c in BuiltIns.categories.filter { it.id in BuiltIns.SCHEMA_6_CATEGORY_IDS }) {
                    db.execSQL(
                        "INSERT OR IGNORE INTO `categories` (`id`, `name`, `icon`, `color`, `isIncome`, `isDefault`, `createdAt`, `updatedAt`) VALUES (?, ?, ?, ?, ?, 1, ?, ?)",
                        arrayOf(c.id, c.name, c.icon, c.color, if (c.isIncome) 1 else 0, now, now)
                    )
                }
                // The own-account category keeps its id but gets a clearer default name, unless renamed.
                db.execSQL("UPDATE `categories` SET `name` = 'Between own accounts' WHERE `id` = 'transfers' AND `name` = 'Transfers'")
                for (r in BuiltIns.schema6Rules) {
                    db.execSQL(
                        "INSERT OR IGNORE INTO `category_rules` (`id`, `categoryId`, `pattern`, `matchType`, `priority`, `isActive`, `createdAt`) VALUES (?, ?, ?, ?, ?, 1, ?)",
                        arrayOf(r.id, r.categoryId, r.pattern, r.matchType, r.priority, now)
                    )
                }
                for (r in BuiltIns.schema6Updates) {
                    db.execSQL(
                        "UPDATE `category_rules` SET `categoryId` = ?, `priority` = ? WHERE `id` = ?",
                        arrayOf(r.categoryId, r.priority, r.id)
                    )
                }
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val ready = CompletableDeferred<AppDatabase>()
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finances.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .addCallback(prepopulationCallback(ready))
                    .build()
                ready.complete(instance)
                INSTANCE = instance
                instance
            }
        }

        /**
         * Seeds the default accounts, categories and rules when the database file is first
         * created. The seeding coroutine waits on [database] for the instance this callback
         * belongs to, so it never depends on the global [INSTANCE] having been assigned yet.
         */
        internal fun prepopulationCallback(database: Deferred<AppDatabase>): Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        prepopulateDefaults(database.await())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("FinancesDb", "Default data could not be created: ${e.javaClass.simpleName}")
                    }
                }
            }
        }
    }
}

/**
 * Creates the default accounts, categories and keyword rules. Idempotent and transactional:
 * a row that already exists (including one the user has edited or disabled) is never replaced,
 * and the three inserts either all happen or none do.
 */
suspend fun prepopulateDefaults(db: AppDatabase) {
    db.withTransaction { insertMissingDefaults(db) }
}

private suspend fun insertMissingDefaults(db: AppDatabase) {
    val now = Instant.now()

    // 1. Prepopulate default accounts (Bank, Revolut, Cash Wallet) with ZERO balance on clean install
    val accounts = listOf(
        AccountEntity("acc_cash", "Cash Wallet", "Wallet", AccountType.CASH, "HUF", 0L, 0L, true, now, now),
        AccountEntity("acc_otp", "OTP Current Account", "OTP Bank", AccountType.BANK, "HUF", 0L, 0L, true, now, now),
        AccountEntity("acc_revolut", "Revolut", "Revolut", AccountType.BANK, "HUF", 0L, 0L, true, now, now),
        AccountEntity("acc_savings", "Savings", "Treasury", AccountType.SAVINGS, "HUF", 0L, 0L, true, now, now)
    )
    db.accountDao().insertAllIfAbsent(accounts)

    // 2. Default categories and 3. keyword rules, from the shared built-in definitions.
    val categories = BuiltIns.categories.map {
        CategoryEntity(it.id, it.name, it.icon, it.color, it.isIncome, true, now, now)
    }
    db.categoryDao().insertAllIfAbsent(categories)
    db.categoryRuleDao().insertAllIfAbsent(BuiltIns.allRules.map { it.copy(createdAt = now).toEntity() })
}

