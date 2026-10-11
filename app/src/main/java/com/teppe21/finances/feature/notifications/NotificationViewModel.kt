package com.teppe21.finances.feature.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.model.NotificationEvent
import com.teppe21.finances.domain.model.NotificationStatusCode
import com.teppe21.finances.domain.model.RawNotification
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.native.notification.NotificationHelper
import com.teppe21.finances.native.notification.parsing.NotificationRecognizer
import com.teppe21.finances.native.notification.parsing.NotificationSource
import com.teppe21.finances.native.notification.parsing.Recognition
import com.teppe21.finances.native.notification.parsing.SourceType
import com.teppe21.finances.native.notification.parsing.SupportedSources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class NotificationUiState(
    val accessGranted: Boolean = false,
    val disclosureAccepted: Boolean = false,
    val sources: List<NotificationSource> = SupportedSources.ALL,
    val accounts: List<Account> = emptyList(),
    /** institution -> account id */
    val mappings: Map<String, String> = emptyMap(),
    val recentEvents: List<NotificationEvent> = emptyList(),
    val reviewCount: Int = 0
)

/** Result of the in-app sample test; nothing is stored. */
data class SampleResult(val title: String, val details: String)

class NotificationViewModel : ViewModel() {

    private val app = FinancesApp.instance
    private val prefs = app.preferencesRepository

    private val accessGranted = MutableStateFlow(false)
    private val mappings = MutableStateFlow<Map<String, String>>(emptyMap())

    private val _sample = MutableStateFlow<SampleResult?>(null)
    val sample: StateFlow<SampleResult?> = _sample.asStateFlow()

    /** Institutions that can be mapped: every bank source. Wallet payments use the card issuer's mapping. */
    val mappableInstitutions: List<String> =
        SupportedSources.ALL.filter { it.type == SourceType.BANK }.map { it.institution }

    init {
        viewModelScope.launch {
            mappings.value = mappableInstitutions.mapNotNull { inst -> prefs.getBankMapping(inst)?.let { inst to it } }.toMap()
        }
    }

    val uiState: StateFlow<NotificationUiState> = combine(
        accessGranted,
        prefs.preferencesFlow,
        app.accountRepository.getAllAccountsFlow(),
        mappings,
        combine(app.notificationRepository.getRecentEventsFlow(30), app.transactionRepository.getPendingTransactionsFlow()) { e, p -> e to p.size }
    ) { granted, p, accounts, maps, eventsAndReview ->
        NotificationUiState(
            accessGranted = granted,
            disclosureAccepted = p.notificationDisclosureAccepted,
            accounts = accounts,
            mappings = maps,
            recentEvents = eventsAndReview.first,
            reviewCount = eventsAndReview.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationUiState())

    fun refreshAccess(context: Context) {
        accessGranted.value = NotificationHelper.isNotificationAccessGranted(context)
    }

    /** Called only from the disclosure's explicit "Continue" button. */
    fun acceptDisclosureAndOpenSettings(context: Context) {
        viewModelScope.launch {
            prefs.setNotificationDisclosureAccepted(true)
            openSystemSettings(context)
        }
    }

    fun openSystemSettings(context: Context) {
        app.appLock.expectExternalActivity()
        if (!NotificationHelper.openNotificationListenerSettings(context)) {
            _sample.value = SampleResult(
                "Settings could not be opened",
                "Open Android Settings > Notifications > Device & app notifications (or Notification access) and enable Finances."
            )
        }
    }

    /** Stops processing immediately, even before Android access is revoked. */
    fun withdrawConsent() {
        viewModelScope.launch { prefs.setNotificationDisclosureAccepted(false) }
    }

    fun setMapping(institution: String, accountId: String) {
        viewModelScope.launch {
            prefs.setBankMapping(institution, accountId)
            mappings.value = mappings.value + (institution to accountId)
        }
    }

    /** Runs the recognizer on a built-in sample so the user can see what would happen. Nothing is stored. */
    fun runSample(kind: String) {
        val now = Instant.now()
        val raw = when (kind) {
            "promo" -> RawNotification("com.revolut.revolut", "7 days left to earn", "Get HUF22,500 for each eligible friend you refer. T&Cs apply.", null, now)
            "otp" -> RawNotification("hu.otpbank.smartbank", "OTP Bank", "Az Ön belépési kódja: 981245. Ne adja ki senkinek!", null, now)
            else -> RawNotification("com.revolut.revolut", "Sample Grocery", "You spent HUF4,590\nHUF balance: HUF120,000.00", null, now)
        }
        _sample.value = when (val r = NotificationRecognizer().recognize(raw)) {
            is Recognition.Recognized -> SampleResult(
                "Recognized as a transaction",
                "Amount: ${com.teppe21.finances.core.utils.CurrencyFormatter.formatSigned(r.transaction.amountMinor, r.transaction.currency)}\n" +
                    "Merchant: ${r.transaction.merchant ?: "unknown"}\nThe balance line was ignored. This sample was not saved."
            )
            is Recognition.Rejected -> SampleResult("Rejected: ${statusLabel(r.code)}", "Nothing would be stored except this status code.")
        }
    }

    fun dismissSample() {
        _sample.value = null
    }
}

class ReviewViewModel(
    pendingFlow: Flow<List<Transaction>>,
    accountsFlow: Flow<List<Account>>,
    private val updateTransaction: suspend (Transaction) -> Unit,
    private val deleteTransaction: suspend (String) -> Unit,
    private val markProcessed: suspend (String) -> Unit,
    private val clock: () -> Instant = Instant::now,
    private val restoreTransaction: suspend (Transaction) -> Unit = {}
) : ViewModel() {

    /** Used by the default `viewModel()` factory: wires the app-level repositories. */
    constructor() : this(FinancesApp.instance)

    private constructor(app: FinancesApp) : this(
        pendingFlow = app.transactionRepository.getPendingTransactionsFlow(),
        accountsFlow = app.accountRepository.getAllAccountsFlow(),
        updateTransaction = { app.transactionRepository.update(it) },
        deleteTransaction = { app.transactionRepository.deleteById(it) },
        markProcessed = { app.notificationRepository.markProcessedForTransaction(it) },
        restoreTransaction = { app.transactionRepository.insertIfAbsent(it) }
    )

    val pending: StateFlow<List<Transaction>> = pendingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<Account>> = accountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun confirm(tx: Transaction, accountId: String) {
        if (accountId.isBlank()) return
        viewModelScope.launch {
            updateTransaction(tx.copy(accountId = accountId, pending = false, notes = null, updatedAt = clock()))
            markProcessed(tx.id)
        }
    }

    fun discard(tx: Transaction) {
        viewModelScope.launch {
            deleteTransaction(tx.id)
            markProcessed(tx.id)
        }
    }

    /** Puts a just-confirmed transaction back in the queue exactly as it was. */
    fun undoConfirm(original: Transaction) {
        viewModelScope.launch { updateTransaction(original) }
    }

    /** Brings back a just-discarded transaction (never replaces one that exists again). */
    fun undoDiscard(original: Transaction) {
        viewModelScope.launch { restoreTransaction(original) }
    }
}

fun statusLabel(code: NotificationStatusCode?): String = when (code) {
    NotificationStatusCode.ACCEPTED_EXPENSE -> "Expense recorded"
    NotificationStatusCode.ACCEPTED_INCOME -> "Income recorded"
    NotificationStatusCode.ACCEPTED_TRANSFER -> "Transfer recorded"
    NotificationStatusCode.ACCEPTED_REFUND -> "Refund recorded"
    NotificationStatusCode.REJECTED_PROMOTIONAL -> "Promotion ignored"
    NotificationStatusCode.REJECTED_SECURITY_MESSAGE -> "Security message ignored"
    NotificationStatusCode.REJECTED_UNSUPPORTED_PACKAGE -> "Unsupported app"
    NotificationStatusCode.REJECTED_NON_TRANSACTION -> "Not a transaction"
    NotificationStatusCode.REJECTED_MALFORMED_TRANSACTION -> "Could not read amount"
    NotificationStatusCode.DUPLICATE_EXISTING_TRANSACTION -> "Duplicate of an existing transaction"
    NotificationStatusCode.PENDING_POSSIBLE_DUPLICATE -> "Possible duplicate, needs review"
    NotificationStatusCode.PENDING_ACCOUNT_REVIEW -> "Account unclear, needs review"
    NotificationStatusCode.PENDING_LOW_CONFIDENCE -> "Needs review"
    NotificationStatusCode.PROCESSING_TIMEOUT -> "Not recorded: processing timed out"
    NotificationStatusCode.PROCESSING_ERROR -> "Not recorded: processing failed"
    null -> "Unknown"
}
