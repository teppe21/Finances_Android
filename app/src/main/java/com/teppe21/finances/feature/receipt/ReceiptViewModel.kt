package com.teppe21.finances.feature.receipt

import android.content.Context
import android.graphics.Bitmap
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
import com.teppe21.finances.domain.usecase.IngestionInput
import com.teppe21.finances.native.receipt.ReceiptOcrEngine
import com.teppe21.finances.native.receipt.ReceiptParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ReceiptUiState(
    val isProcessing: Boolean = false,
    val scannedReceipt: ReceiptScan? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedAccountId: String? = null,
    val selectedCategoryId: String? = null,
    val errorMessage: String? = null,
    /** True when the scan came from the built-in sample, not the camera or gallery. */
    val isSample: Boolean = false
)

class ReceiptViewModel : ViewModel() {

    private val app = FinancesApp.instance
    private val receiptRepo = app.receiptRepository
    private val accountRepo = app.accountRepository
    private val catRepo = app.categoryRepository
    private val ingestUseCase = app.ingestTransactionUseCase

    private val _uiState = MutableStateFlow(ReceiptUiState())
    val uiState: StateFlow<ReceiptUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val accounts = accountRepo.getAllAccounts()
            val categories = catRepo.getAllCategories()
            _uiState.value = _uiState.value.copy(
                accounts = accounts,
                categories = categories
            )
        }
    }

    fun processBitmap(context: Context, bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val engine = ReceiptOcrEngine(context)
                val scan = engine.recognizeFromBitmap(bitmap)
                handleScanResult(scan)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = "Text recognition failed. Try again with better lighting."
                )
            }
        }
    }

    fun reportCaptureFailed() {
        _uiState.value = _uiState.value.copy(isProcessing = false, errorMessage = "The photo could not be taken. Try again or pick an image.")
    }

    fun processUri(context: Context, uri: Uri) {
        _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val engine = ReceiptOcrEngine(context)
                val scan = engine.recognizeFromUri(uri)
                handleScanResult(scan)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = "The image could not be read. Try another image."
                )
            }
        }
    }

    fun processDemoText(sampleText: String) {
        _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null)
        viewModelScope.launch {
            val scan = ReceiptParser.parse(sampleText)
            handleScanResult(scan, isSample = true)
        }
    }

    private fun handleScanResult(scan: ReceiptScan, isSample: Boolean = false) {
        val accounts = _uiState.value.accounts
        // If cash detected, suggest Cash Wallet account!
        val suggestedAccount = if (scan.paymentMethod == PaymentMethod.CASH) {
            accounts.firstOrNull { it.type == AccountType.CASH } ?: accounts.firstOrNull()
        } else {
            accounts.firstOrNull { it.type == AccountType.BANK } ?: accounts.firstOrNull()
        }

        _uiState.value = _uiState.value.copy(
            isProcessing = false,
            scannedReceipt = scan,
            selectedAccountId = suggestedAccount?.id,
            selectedCategoryId = "food",
            errorMessage = null,
            isSample = isSample
        )
    }

    fun setSelectedAccount(accountId: String) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId)
    }

    fun setSelectedCategory(categoryId: String) {
        _uiState.value = _uiState.value.copy(selectedCategoryId = categoryId)
    }

    fun clearScan() {
        _uiState.value = _uiState.value.copy(scannedReceipt = null, errorMessage = null)
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
        viewModelScope.launch {
            val scan = _uiState.value.scannedReceipt
            if (scan != null) {
                receiptRepo.insertReceipt(scan.copy(merchant = merchant, totalMinor = amountMinor, date = date, currency = currency))
            }

            ingestUseCase.execute(
                IngestionInput(
                    fingerprint = scan?.id?.let { com.teppe21.finances.domain.usecase.Fingerprints.receipt(it) },
                    notes = if (_uiState.value.isSample) "Demo data" else null,
                    accountId = accountId,
                    date = date,
                    amountMinor = -kotlin.math.abs(amountMinor),
                    currency = currency,
                    direction = TransactionDirection.EXPENSE,
                    description = merchant,
                    merchant = merchant,
                    categoryId = categoryId,
                    source = TransactionSource.RECEIPT,
                    receiptId = scan?.id
                )
            )

            clearScan()
            onSuccess()
        }
    }
}
