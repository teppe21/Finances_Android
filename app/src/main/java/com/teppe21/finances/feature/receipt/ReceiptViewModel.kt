package com.teppe21.finances.feature.receipt

import com.teppe21.finances.R
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.model.AccountType
import com.teppe21.finances.domain.model.Category
import com.teppe21.finances.domain.model.PaymentMethod
import com.teppe21.finances.domain.model.ReceiptScan
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.domain.model.TransactionSource
import com.teppe21.finances.domain.usecase.Fingerprints
import com.teppe21.finances.domain.usecase.IngestionInput
import com.teppe21.finances.native.receipt.ReceiptOcrEngine
import com.teppe21.finances.native.receipt.ReceiptParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ReceiptUiState(
    val isProcessing: Boolean = false,
    val scannedReceipt: ReceiptScan? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedAccountId: String? = null,
    val selectedCategoryId: String? = null,
    /** A string resource describing the last failure. */
    val errorMessage: Int? = null,
    /** True when the scan came from the built-in sample, not the camera or gallery. */
    val isSample: Boolean = false,
    /** True while the confirmed receipt is being written, so the sheet cannot be submitted twice. */
    val isSaving: Boolean = false
)

/** The part of OCR the view model depends on; [ReceiptOcrEngine] is the on-device implementation. */
interface ReceiptTextRecognition {
    suspend fun recognizeCapture(jpeg: ByteArray, rotationDegrees: Int): ReceiptScan
    suspend fun recognizeFromUri(uri: Uri): ReceiptScan
}

class ReceiptViewModel(
    private val ocr: ReceiptTextRecognition,
    private val loadAccounts: suspend () -> List<Account>,
    private val loadCategories: suspend () -> List<Category>,
    private val saveReceipt: suspend (ReceiptScan) -> Unit,
    private val ingest: suspend (IngestionInput) -> Unit
) : ViewModel() {

    /** Used by the default `viewModel()` factory: wires the app-level singletons. */
    constructor() : this(FinancesApp.instance)

    private constructor(app: FinancesApp) : this(
        ocr = object : ReceiptTextRecognition {
            private val engine = ReceiptOcrEngine(app)
            override suspend fun recognizeCapture(jpeg: ByteArray, rotationDegrees: Int) =
                engine.recognizeCapture(jpeg, rotationDegrees)

            override suspend fun recognizeFromUri(uri: Uri) = engine.recognizeFromUri(uri)
        },
        loadAccounts = { app.accountRepository.getAllAccounts() },
        loadCategories = { app.categoryRepository.getAllCategories() },
        saveReceipt = { app.receiptRepository.insertReceipt(it) },
        ingest = { app.ingestTransactionUseCase.execute(it) }
    )

    private val _uiState = MutableStateFlow(ReceiptUiState())
    val uiState: StateFlow<ReceiptUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val accounts = loadAccounts()
            val categories = loadCategories()
            _uiState.update { it.copy(accounts = accounts, categories = categories) }
        }
    }

    /** [jpeg] and [rotationDegrees] come straight from CameraX's in-memory capture. */
    fun processCapture(jpeg: ByteArray, rotationDegrees: Int) = recognize(
        failureMessage = R.string.receipt_error_ocr
    ) { ocr.recognizeCapture(jpeg, rotationDegrees) }

    fun processUri(uri: Uri) = recognize(
        failureMessage = R.string.receipt_error_image
    ) { ocr.recognizeFromUri(uri) }

    private fun recognize(failureMessage: Int, block: suspend () -> ReceiptScan) {
        // A second tap on the shutter while a scan is running would start a parallel OCR run.
        if (_uiState.value.isProcessing) return
        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                handleScanResult(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = failureMessage) }
            }
        }
    }

    fun reportCaptureFailed() {
        _uiState.update {
            it.copy(isProcessing = false, errorMessage = R.string.receipt_error_photo)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun processDemoText(sampleText: String) {
        if (_uiState.value.isProcessing) return
        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
        viewModelScope.launch {
            handleScanResult(ReceiptParser.parse(sampleText), isSample = true)
        }
    }

    private fun handleScanResult(scan: ReceiptScan, isSample: Boolean = false) {
        _uiState.update { state ->
            val accounts = state.accounts
            val suggestedAccount = if (scan.paymentMethod == PaymentMethod.CASH) {
                accounts.firstOrNull { it.type == AccountType.CASH } ?: accounts.firstOrNull()
            } else {
                accounts.firstOrNull { it.type == AccountType.BANK } ?: accounts.firstOrNull()
            }
            state.copy(
                isProcessing = false,
                scannedReceipt = scan,
                selectedAccountId = suggestedAccount?.id,
                selectedCategoryId = "food",
                errorMessage = null,
                isSample = isSample
            )
        }
    }

    fun setSelectedAccount(accountId: String) {
        _uiState.update { it.copy(selectedAccountId = accountId) }
    }

    fun setSelectedCategory(categoryId: String) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun clearScan() {
        _uiState.update { it.copy(scannedReceipt = null, errorMessage = null, isSaving = false) }
    }

    fun confirmSave(
        merchant: String,
        amountMinor: Long,
        date: LocalDate,
        accountId: String,
        categoryId: String,
        currency: String = "HUF",
        onSuccess: () -> Unit
    ) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val scan = state.scannedReceipt
                if (scan != null) {
                    saveReceipt(scan.copy(merchant = merchant, totalMinor = amountMinor, date = date, currency = currency))
                }
                ingest(
                    IngestionInput(
                        fingerprint = scan?.id?.let { Fingerprints.receipt(it) },
                        notes = if (state.isSample) "Demo data" else null,
                        accountId = accountId,
                        date = date,
                        amountMinor = -kotlin.math.abs(amountMinor),
                        currency = currency,
                        direction = TransactionDirection.EXPENSE,
                        description = merchant,
                        merchant = merchant,
                        categoryId = categoryId,
                        source = TransactionSource.RECEIPT,
                        receiptId = scan?.id,
                        categorySetByUser = true
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = R.string.receipt_error_save) }
                return@launch
            }
            clearScan()
            onSuccess()
        }
    }
}
