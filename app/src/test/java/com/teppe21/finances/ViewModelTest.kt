package com.teppe21.finances

import android.net.Uri
import com.teppe21.finances.data.local.preferences.UserPreferences
import com.teppe21.finances.domain.fx.RateOrigin
import com.teppe21.finances.domain.fx.RateQuote
import com.teppe21.finances.domain.fx.RateTable
import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.model.AccountType
import com.teppe21.finances.domain.model.Category
import com.teppe21.finances.domain.model.PaymentMethod
import com.teppe21.finances.domain.model.ReceiptScan
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.domain.model.TransactionSource
import com.teppe21.finances.domain.usecase.CalculateFinancialStatsUseCase
import com.teppe21.finances.domain.usecase.DetectRecurringTransactionsUseCase
import com.teppe21.finances.domain.usecase.IngestTransactionUseCase
import com.teppe21.finances.domain.usecase.IngestionInput
import com.teppe21.finances.feature.accounts.AccountsViewModel
import com.teppe21.finances.feature.dashboard.DashboardViewModel
import com.teppe21.finances.feature.notifications.ReviewViewModel
import com.teppe21.finances.feature.receipt.ReceiptTextRecognition
import com.teppe21.finances.feature.receipt.ReceiptViewModel
import com.teppe21.finances.feature.transactions.TransactionSortOrder
import com.teppe21.finances.feature.transactions.TransactionsViewModel
import com.teppe21.finances.support.InMemoryTransactionStore
import com.teppe21.finances.support.MainDispatcherRule
import com.teppe21.finances.support.NoRules
import com.teppe21.finances.support.account
import com.teppe21.finances.support.tx
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private val cash = Account("acc_cash", "Cash Wallet", "Wallet", AccountType.CASH)
private val bank = Account("acc_bank", "Main bank", "Example Bank", AccountType.BANK)
private val food = Category("food", "Food & Groceries")
private val other = Category("other", "Other / Uncategorized")

private fun scan(
    merchant: String? = "Example Market",
    totalMinor: Long? = 124900L,
    payment: PaymentMethod = PaymentMethod.CARD
) = ReceiptScan(
    id = "scan-1",
    merchant = merchant,
    date = LocalDate.of(2026, 3, 28),
    totalMinor = totalMinor,
    paymentMethod = payment,
    rawOcrText = "synthetic receipt text"
)

private class FakeOcr(
    var captureResult: suspend () -> ReceiptScan = { scan() }
) : ReceiptTextRecognition {
    var captures = 0
    var lastRotation: Int? = null
    override suspend fun recognizeCapture(jpeg: ByteArray, rotationDegrees: Int): ReceiptScan {
        captures++
        lastRotation = rotationDegrees
        return captureResult()
    }

    override suspend fun recognizeFromUri(uri: Uri): ReceiptScan = error("not used in JVM tests")
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val savedReceipts = mutableListOf<ReceiptScan>()
    private val store = InMemoryTransactionStore()
    private val ingestUseCase = IngestTransactionUseCase(store, NoRules)
    private var failIngest = false
    private var ingestGate: CompletableDeferred<Unit>? = null
    private var ingestCalls = 0

    private fun viewModel(ocr: ReceiptTextRecognition = FakeOcr()) = ReceiptViewModel(
        ocr = ocr,
        loadAccounts = { listOf(cash, bank) },
        loadCategories = { listOf(food, other) },
        saveReceipt = { savedReceipts += it },
        ingest = {
            ingestCalls++
            ingestGate?.await()
            if (failIngest) error("database unavailable")
            ingestUseCase.execute(it)
        }
    )

    @Test
    fun `accounts and categories load and the first state is idle`() {
        val state = viewModel().uiState.value
        assertEquals(listOf(cash, bank), state.accounts)
        assertEquals(listOf(food, other), state.categories)
        assertFalse(state.isProcessing)
        assertNull(state.scannedReceipt)
        assertNull(state.errorMessage)
    }

    @Test
    fun `a capture is recognized with the camera rotation and offers the bank account`() {
        val ocr = FakeOcr()
        val vm = viewModel(ocr)
        vm.processCapture(ByteArray(8), 90)
        val state = vm.uiState.value
        assertEquals(90, ocr.lastRotation)
        assertFalse(state.isProcessing)
        assertEquals(124900L, state.scannedReceipt?.totalMinor)
        assertEquals("acc_bank", state.selectedAccountId)
        assertFalse(state.isSample)
    }

    @Test
    fun `a cash receipt preselects the cash account`() {
        val vm = viewModel(FakeOcr(captureResult = { scan(payment = PaymentMethod.CASH) }))
        vm.processCapture(ByteArray(8), 0)
        assertEquals("acc_cash", vm.uiState.value.selectedAccountId)
    }

    @Test
    fun `a receipt without a recognized total stays empty for the user to fill in`() {
        val vm = viewModel(FakeOcr(captureResult = { scan(totalMinor = null) }))
        vm.processCapture(ByteArray(8), 0)
        assertNull(vm.uiState.value.scannedReceipt?.totalMinor)
    }

    @Test
    fun `a recognition failure shows a message and leaves nothing to save`() {
        val vm = viewModel(FakeOcr(captureResult = { throw java.io.IOException("decode failed") }))
        vm.processCapture(ByteArray(8), 0)
        val state = vm.uiState.value
        assertFalse(state.isProcessing)
        assertNull(state.scannedReceipt)
        assertNotNull(state.errorMessage)
        vm.dismissError()
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `a failed capture is reported without starting a scan`() {
        val vm = viewModel()
        vm.reportCaptureFailed()
        assertNotNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isProcessing)
    }

    @Test
    fun `a second shutter tap while a scan runs is ignored`() = runTest {
        val gate = CompletableDeferred<ReceiptScan>()
        val ocr = FakeOcr(captureResult = { gate.await() })
        val vm = viewModel(ocr)
        vm.processCapture(ByteArray(8), 0)
        assertTrue(vm.uiState.value.isProcessing)
        vm.processCapture(ByteArray(8), 0)
        assertEquals(1, ocr.captures)

        gate.complete(scan())
        assertFalse(vm.uiState.value.isProcessing)
        assertNotNull(vm.uiState.value.scannedReceipt)
    }

    @Test
    fun `the sample receipt is marked as demo data`() {
        val vm = viewModel()
        vm.processDemoText("SPAR\n2026.03.28\nFIZETENDO: 1 249 Ft")
        val state = vm.uiState.value
        assertTrue(state.isSample)
        assertEquals(124900L, state.scannedReceipt?.totalMinor)
    }

    @Test
    fun `confirming saves the corrected receipt and an expense without image or text`() {
        val vm = viewModel()
        vm.processCapture(ByteArray(8), 0)
        var done = false
        vm.confirmSave("Corner Shop", 125000L, LocalDate.of(2026, 3, 29), "acc_bank", "food", "HUF") { done = true }

        assertTrue(done)
        val saved = savedReceipts.single()
        assertEquals("Corner Shop", saved.merchant)
        assertEquals(125000L, saved.totalMinor)
        assertEquals(LocalDate.of(2026, 3, 29), saved.date)

        val row: Transaction = store.rows.single()
        assertEquals(-125000L, row.amountMinor)
        assertEquals(TransactionDirection.EXPENSE, row.direction)
        assertEquals(TransactionSource.RECEIPT, row.source)
        assertEquals("acc_bank", row.accountId)
        assertEquals("food", row.categoryId)
        assertEquals("scan-1", row.receiptId)
        assertNull(vm.uiState.value.scannedReceipt)
    }

    @Test
    fun `the same scan cannot be saved twice`() = runTest {
        ingestGate = CompletableDeferred()
        val vm = viewModel()
        vm.processCapture(ByteArray(8), 0)
        vm.confirmSave("Shop", 100L, LocalDate.of(2026, 3, 29), "acc_bank", "food") {}
        // Double tap on Save while the first save is still running.
        vm.confirmSave("Shop", 100L, LocalDate.of(2026, 3, 29), "acc_bank", "food") {}
        assertEquals(1, ingestCalls)
        ingestGate!!.complete(Unit)
        assertEquals(1, store.rows.size)
    }

    @Test
    fun `a save failure keeps the review open and reports the error`() {
        failIngest = true
        val vm = viewModel()
        vm.processCapture(ByteArray(8), 0)
        var done = false
        vm.confirmSave("Shop", 100L, LocalDate.of(2026, 3, 29), "acc_bank", "food") { done = true }

        assertFalse(done)
        val state = vm.uiState.value
        assertNotNull(state.scannedReceipt)
        assertFalse(state.isSaving)
        assertNotNull(state.errorMessage)
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `demo receipts are saved with a demo note`() {
        val vm = viewModel()
        vm.processDemoText("SPAR\nFIZETENDO: 1 249 Ft")
        vm.confirmSave("Spar", 124900L, LocalDate.of(2026, 3, 28), "acc_bank", "food") {}
        assertEquals("Demo data", store.rows.single().notes)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.now()
    private val transactions = MutableStateFlow<List<Transaction>>(emptyList())
    private val rates = MutableStateFlow(RateTable())
    private val prefs = MutableStateFlow(UserPreferences(currency = "HUF"))
    private val ingested = mutableListOf<IngestionInput>()
    private val deleted = mutableListOf<Transaction>()

    private fun viewModel() = TransactionsViewModel(
        transactionsFlow = transactions,
        categoriesFlow = flowOf(listOf(food, other)),
        accountsFlow = flowOf(listOf(cash, bank)),
        preferencesFlow = prefs,
        ratesFlow = rates,
        recurringUseCase = DetectRecurringTransactionsUseCase(),
        ingest = { ingested += it },
        delete = { deleted += it }
    )


    @Test
    fun `empty database gives an empty list`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val state = vm.uiState.value
        assertTrue(state.filteredTransactions.isEmpty())
        assertEquals("HUF", state.currency)
        assertEquals(0, state.unconvertedCount)
    }

    @Test
    fun `default period shows this month newest first and filters apply together`() = runTest {
        transactions.value = listOf(
            tx("old", -100L, date = today.minusMonths(5), categoryId = "food"),
            tx("a", -200L, date = today, categoryId = "food"),
            tx("b", -300L, date = today, categoryId = "other"),
            tx("c", -400L, date = today.withDayOfMonth(1), categoryId = "food")
        )
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(listOf("a", "b", "c").toSet(), vm.uiState.value.filteredTransactions.map { it.id }.toSet())
        assertFalse(vm.uiState.value.filteredTransactions.any { it.id == "old" })

        vm.setCategory("food")
        assertEquals(setOf("a", "c"), vm.uiState.value.filteredTransactions.map { it.id }.toSet())

        vm.setPeriod("6_months")
        assertEquals(setOf("old", "a", "c"), vm.uiState.value.filteredTransactions.map { it.id }.toSet())

        vm.setSearchQuery("zzz")
        assertTrue(vm.uiState.value.filteredTransactions.isEmpty())
    }

    @Test
    fun `sorting by amount uses converted values and puts unconvertible amounts last`() = runTest {
        rates.value = RateTable(
            mapOf(
                "HUF" to RateQuote(BigDecimal("400"), Instant.EPOCH, "test", RateOrigin.MANUAL),
                "USD" to RateQuote(BigDecimal("1.1"), Instant.EPOCH, "test", RateOrigin.MANUAL)
            )
        )
        transactions.value = listOf(
            tx("small-huf", -10_000L, date = today),
            tx("usd", -1_000L, date = today, currency = "USD"),
            tx("chf-no-rate", -999_999L, date = today, currency = "CHF")
        )
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.setSortOrder(TransactionSortOrder.AMOUNT_DESC)
        val state = vm.uiState.value
        assertEquals("chf-no-rate", state.filteredTransactions.last().id)
        assertEquals(1, state.unconvertedCount)
        assertNull(state.convertedAmounts["chf-no-rate"])
        assertEquals(-10_000L, state.convertedAmounts["small-huf"])
    }

    @Test
    fun `adding a transaction hands a manual ingestion to the pipeline`() = runTest {
        val vm = viewModel()
        vm.addManualTransaction(
            accountId = "acc_bank",
            amountMinor = -250_000L,
            currency = "HUF",
            direction = TransactionDirection.EXPENSE,
            description = "Groceries",
            merchant = "Example Market",
            categoryId = "food",
            date = LocalDate.of(2026, 3, 28)
        )
        val input = ingested.single()
        assertEquals(TransactionSource.MANUAL, input.source)
        assertEquals(-250_000L, input.amountMinor)
        assertEquals("acc_bank", input.accountId)
        assertEquals("food", input.categoryId)
    }

    @Test
    fun `deleting a transaction removes it and clears the selection`() = runTest {
        val t = tx("a", -100L, date = today)
        transactions.value = listOf(t)
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.selectTransaction(t)
        assertEquals(t, vm.uiState.value.selectedTransaction)
        vm.deleteTransaction(t)
        assertEquals(listOf(t), deleted)
        assertNull(vm.uiState.value.selectedTransaction)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.now()

    private fun viewModel(
        transactions: Flow<List<Transaction>>,
        accounts: List<Account> = listOf(bank),
        rates: RateTable = RateTable(),
        currency: String = "HUF"
    ) = DashboardViewModel(
        transactions = transactions,
        categories = flowOf(listOf(food, other)),
        accounts = flowOf(accounts),
        preferences = flowOf(UserPreferences(currency = currency)),
        rates = flowOf(rates),
        stats = CalculateFinancialStatsUseCase()
    )

    @Test
    fun `the first state is loading and has no data`() = runTest {
        val vm = viewModel(MutableStateFlow(emptyList()))
        // No subscriber yet: stateIn keeps the initial value.
        val initial = vm.uiState.value
        assertTrue(initial.isLoading)
        assertFalse(initial.hasAnyTransactions)
    }

    @Test
    fun `an empty database loads to a zero balance with the empty flag`() = runTest {
        val vm = viewModel(flowOf(emptyList()))
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasAnyTransactions)
        assertEquals(0L, state.totalBalanceMinor)
        assertEquals(0, state.reviewCount)
        job.cancel()
    }

    @Test
    fun `balance and month totals count confirmed transactions only and pending ones go to review`() = runTest {
        val opening = Account("acc_bank", "Main", "Example Bank", AccountType.BANK, openingBalanceMinor = 1_000_000L)
        val vm = viewModel(
            flowOf(
                listOf(
                    tx("salary", 500_000L, date = today, accountId = "acc_bank"),
                    tx("shop", -120_000L, date = today, accountId = "acc_bank", categoryId = "food"),
                    tx("waiting", -999_000L, date = today, accountId = "acc_bank", pending = true)
                )
            ),
            accounts = listOf(opening)
        )
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val state = vm.uiState.value
        assertEquals(1_000_000L + 500_000L - 120_000L, state.totalBalanceMinor)
        assertEquals(500_000L, state.monthStats.incomeMinor)
        assertEquals(120_000L, state.monthStats.expenseMinor)
        assertEquals(1, state.reviewCount)
        assertTrue(state.hasAnyTransactions)
        assertEquals(3, state.recentTransactions.size)
        job.cancel()
    }

    @Test
    fun `a balance that cannot be converted is unknown rather than wrong`() = runTest {
        val eurAccount = Account("acc_eur", "Euro", "Example Bank", AccountType.BANK, currency = "EUR", openingBalanceMinor = 10_000L)
        val vm = viewModel(flowOf(emptyList()), accounts = listOf(bank, eurAccount), currency = "HUF")
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val state = vm.uiState.value
        assertNull("no EUR to HUF rate: the total must not be shown as a number", state.totalBalanceMinor)
        assertEquals(1, state.balanceUnconvertedAccounts)
        job.cancel()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsAndReviewViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `account rows add confirmed transactions to the opening balance and count pending ones`() = runTest {
        val acc = Account("a1", "Main", "Example Bank", AccountType.BANK, openingBalanceMinor = 100_000L)
        val vm = AccountsViewModel(
            accountsFlow = flowOf(listOf(acc)),
            transactionsFlow = flowOf(
                listOf(
                    tx("in", 50_000L, accountId = "a1"),
                    tx("out", -20_000L, accountId = "a1"),
                    tx("pending", -9_999_000L, accountId = "a1", pending = true),
                    tx("elsewhere", -1L, accountId = "other")
                )
            ),
            ratesFlow = flowOf(RateTable()),
            stats = CalculateFinancialStatsUseCase(),
            insertAccount = {}
        )
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.accounts.collect {} }
        val row = vm.accounts.value.single()
        assertEquals(130_000L, row.balanceMinor)
        assertEquals(1, row.pendingCount)
        job.cancel()
    }

    @Test
    fun `adding an account trims names and keeps the opening balance`() = runTest {
        val inserted = mutableListOf<Account>()
        val vm = AccountsViewModel(
            accountsFlow = flowOf(emptyList()),
            transactionsFlow = flowOf(emptyList()),
            ratesFlow = flowOf(RateTable()),
            stats = CalculateFinancialStatsUseCase(),
            insertAccount = { inserted += it }
        )
        vm.addAccount("  Travel card ", " Example Bank ", AccountType.CREDIT_CARD, "EUR", 12_345L)
        val account = inserted.single()
        assertEquals("Travel card", account.name)
        assertEquals("Example Bank", account.institution)
        assertEquals("EUR", account.currency)
        assertEquals(12_345L, account.openingBalanceMinor)
        assertEquals(12_345L, account.currentBalanceMinor)
    }

    private val pendingTx = tx("p1", -5_000L, pending = true, accountId = "")

    @Test
    fun `confirming a pending transaction assigns the account and clears the pending state`() = runTest {
        val updated = mutableListOf<Transaction>()
        val processed = mutableListOf<String>()
        val vm = ReviewViewModel(
            pendingFlow = flowOf(listOf(pendingTx)),
            accountsFlow = flowOf(listOf(bank)),
            updateTransaction = { updated += it },
            deleteTransaction = { error("must not delete") },
            markProcessed = { processed += it },
            clock = { Instant.parse("2026-03-28T10:00:00Z") }
        )
        vm.confirm(pendingTx, "acc_bank")
        val result = updated.single()
        assertEquals("acc_bank", result.accountId)
        assertFalse(result.pending)
        assertEquals(Instant.parse("2026-03-28T10:00:00Z"), result.updatedAt)
        assertEquals(listOf("p1"), processed)
    }

    @Test
    fun `confirming without an account does nothing`() = runTest {
        val updated = mutableListOf<Transaction>()
        val vm = ReviewViewModel(
            pendingFlow = flowOf(listOf(pendingTx)),
            accountsFlow = flowOf(listOf(bank)),
            updateTransaction = { updated += it },
            deleteTransaction = { },
            markProcessed = { }
        )
        vm.confirm(pendingTx, " ")
        assertTrue(updated.isEmpty())
    }

    @Test
    fun `rejecting a pending transaction deletes it and marks the notification handled`() = runTest {
        val deleted = mutableListOf<String>()
        val processed = mutableListOf<String>()
        val vm = ReviewViewModel(
            pendingFlow = flowOf(listOf(pendingTx)),
            accountsFlow = flowOf(listOf(bank)),
            updateTransaction = { error("must not update") },
            deleteTransaction = { deleted += it },
            markProcessed = { processed += it }
        )
        vm.discard(pendingTx)
        assertEquals(listOf("p1"), deleted)
        assertEquals(listOf("p1"), processed)
    }
}
