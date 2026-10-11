package com.teppe21.finances

import android.app.Application
import com.teppe21.finances.data.fx.ExchangeRateRepository
import com.teppe21.finances.data.local.database.AppDatabase
import com.teppe21.finances.data.local.database.pruneNotificationEvents
import com.teppe21.finances.data.local.preferences.UserPreferencesRepository
import com.teppe21.finances.data.repository.AccountRepository
import com.teppe21.finances.data.repository.CategoryRepository
import com.teppe21.finances.data.repository.NotificationRepository
import com.teppe21.finances.data.repository.ReceiptRepository
import com.teppe21.finances.data.repository.TransactionRepository
import com.teppe21.finances.domain.usecase.CalculateFinancialStatsUseCase
import com.teppe21.finances.domain.usecase.CategorizeTransactionUseCase
import com.teppe21.finances.domain.usecase.DetectRecurringTransactionsUseCase
import com.teppe21.finances.domain.usecase.DuplicateDetector
import com.teppe21.finances.domain.usecase.IngestTransactionUseCase
import com.teppe21.finances.domain.usecase.Recategorizer
import com.teppe21.finances.domain.usecase.ProcessNotificationUseCase
import com.teppe21.finances.domain.usecase.WalletBankPairPolicy
import com.teppe21.finances.native.notification.parsing.NotificationRecognizer
import com.teppe21.finances.security.AppLockController
import com.teppe21.finances.stocks.data.local.RoomQuoteCache
import com.teppe21.finances.stocks.data.local.RoomWatchlistStore
import com.teppe21.finances.stocks.data.provider.FinnhubProvider
import com.teppe21.finances.stocks.domain.ApiKeySource
import com.teppe21.finances.stocks.domain.StockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container; the app is small enough not to need a DI framework. */
class FinancesApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var transactionRepository: TransactionRepository
        private set
    lateinit var accountRepository: AccountRepository
        private set
    lateinit var categoryRepository: CategoryRepository
        private set
    lateinit var notificationRepository: NotificationRepository
        private set
    lateinit var receiptRepository: ReceiptRepository
        private set
    lateinit var preferencesRepository: UserPreferencesRepository
        private set
    lateinit var exchangeRateRepository: ExchangeRateRepository
        private set
    lateinit var stockRepository: StockRepository
        private set

    lateinit var calculateStatsUseCase: CalculateFinancialStatsUseCase
        private set
    lateinit var ingestTransactionUseCase: IngestTransactionUseCase
        private set
    lateinit var processNotificationUseCase: ProcessNotificationUseCase
        private set
    lateinit var detectRecurringUseCase: DetectRecurringTransactionsUseCase
        private set
    lateinit var recategorizer: Recategorizer
        private set

    val appLock = AppLockController()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        transactionRepository = TransactionRepository(database.transactionDao())
        accountRepository = AccountRepository(database.accountDao())
        categoryRepository = CategoryRepository(database.categoryDao(), database.categoryRuleDao())
        notificationRepository = NotificationRepository(database.notificationDao())
        receiptRepository = ReceiptRepository(database.receiptDao())
        preferencesRepository = UserPreferencesRepository(this)
        exchangeRateRepository = ExchangeRateRepository(
            dao = database.exchangeRateDao(),
            prefsRepo = preferencesRepository
        )

        calculateStatsUseCase = CalculateFinancialStatsUseCase()
        detectRecurringUseCase = DetectRecurringTransactionsUseCase()
        ingestTransactionUseCase = IngestTransactionUseCase(
            store = transactionRepository,
            rules = categoryRepository,
            detector = DuplicateDetector(WalletBankPairPolicy),
            categorizeUseCase = CategorizeTransactionUseCase()
        )
        recategorizer = Recategorizer(
            transactions = { transactionRepository.getAllTransactions() },
            updateAll = { transactionRepository.updateAll(it) },
            rules = categoryRepository,
            addUserRule = { categoryId, keyword -> categoryRepository.addUserRule(categoryId, keyword) }
        )
        processNotificationUseCase = ProcessNotificationUseCase(
            recognizer = NotificationRecognizer(),
            accounts = { accountRepository.getAllAccounts() },
            mappedAccountId = { institution -> preferencesRepository.getBankMapping(institution) },
            ingest = ingestTransactionUseCase,
            events = notificationRepository
        )
        stockRepository = StockRepository(
            provider = FinnhubProvider(),
            watchlist = RoomWatchlistStore(database.watchlistDao()),
            quotes = RoomQuoteCache(database.stockQuoteDao()),
            keys = ApiKeySource { preferencesRepository.getStocksApiKey()?.takeIf { it.isNotBlank() } }
        )

        appScope.launch { pruneNotificationEvents(database) }
    }

    companion object {
        lateinit var instance: FinancesApp
            private set
    }
}
