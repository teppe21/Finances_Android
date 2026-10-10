package com.teppe21.finances.feature.importcsv

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.model.TransactionSource
import com.teppe21.finances.domain.usecase.DeduplicationStatus
import com.teppe21.finances.domain.usecase.Fingerprints
import com.teppe21.finances.domain.usecase.IngestionInput
import com.teppe21.finances.native.csv.CsvParseResult
import com.teppe21.finances.native.csv.CsvParser
import com.teppe21.finances.native.csv.CsvSkipReason
import com.teppe21.finances.native.csv.ParsedCsvRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CsvImportUiState(
    val fileName: String? = null,
    val isAnalyzing: Boolean = false,
    val isImporting: Boolean = false,
    val parsedRows: List<ParsedCsvRow> = emptyList(),
    val skipped: Map<CsvSkipReason, Int> = emptyMap(),
    val charset: String? = null,
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: String? = null,
    /** Rows already imported earlier into the selected account (same file row). */
    val alreadyImportedCount: Int = 0,
    val importedCount: Int = 0,
    val importComplete: Boolean = false,
    val errorMessage: String? = null
) {
    val newCount: Int get() = parsedRows.size - alreadyImportedCount
}

class CsvImportViewModel : ViewModel() {

    private val app = FinancesApp.instance
    private val ingestUseCase = app.ingestTransactionUseCase
    private val txRepo = app.transactionRepository

    private val _uiState = MutableStateFlow(CsvImportUiState())
    val uiState: StateFlow<CsvImportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val accounts = app.accountRepository.getAllAccounts()
            _uiState.value = _uiState.value.copy(accounts = accounts, selectedAccountId = accounts.firstOrNull()?.id)
        }
    }

    fun setSelectedAccount(accountId: String) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId)
        viewModelScope.launch { recountDuplicates() }
    }

    fun loadFromUri(context: Context, uri: Uri, fileName: String?) {
        _uiState.value = _uiState.value.copy(isAnalyzing = true, fileName = fileName, errorMessage = null, importComplete = false)
        viewModelScope.launch {
            try {
                val accountCurrency = selectedAccount()?.currency ?: "HUF"
                val result: CsvParseResult = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { CsvParser.parse(it, accountCurrency) }
                } ?: throw IllegalStateException("The file could not be opened.")

                if (result.rows.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        parsedRows = emptyList(),
                        skipped = result.skipped,
                        errorMessage = "No readable transaction rows were found." +
                            if (result.skippedCount > 0) " ${result.skippedCount} rows could not be read." else ""
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    parsedRows = result.rows,
                    skipped = result.skipped,
                    charset = result.charset
                )
                recountDuplicates()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    errorMessage = "The file could not be read as CSV."
                )
            }
        }
    }

    private fun selectedAccount(): Account? =
        _uiState.value.accounts.firstOrNull { it.id == _uiState.value.selectedAccountId }

    /** Fingerprints per row; identical rows in one file get increasing occurrence numbers. */
    private fun fingerprints(accountId: String, rows: List<ParsedCsvRow>): List<String> {
        val seen = mutableMapOf<String, Int>()
        return rows.map { row ->
            val base = "${row.date}|${row.amountMinor}|${row.currency}|${row.description}"
            val occurrence = seen.getOrDefault(base, 0)
            seen[base] = occurrence + 1
            Fingerprints.csvRow(accountId, row.date, row.amountMinor, row.currency, row.description, occurrence)
        }
    }

    private suspend fun recountDuplicates() {
        val accountId = _uiState.value.selectedAccountId ?: return
        val rows = _uiState.value.parsedRows
        val existing = withContext(Dispatchers.IO) {
            fingerprints(accountId, rows).count { txRepo.findByFingerprint(it) != null }
        }
        _uiState.value = _uiState.value.copy(alreadyImportedCount = existing)
    }

    fun executeImport(onSuccess: () -> Unit) {
        val state = _uiState.value
        val accountId = state.selectedAccountId ?: return
        _uiState.value = state.copy(isImporting = true)
        viewModelScope.launch {
            var imported = 0
            withContext(Dispatchers.IO) {
                state.parsedRows.zip(fingerprints(accountId, state.parsedRows)).forEach { (row, fp) ->
                    val output = ingestUseCase.execute(
                        IngestionInput(
                            accountId = accountId,
                            date = row.date,
                            amountMinor = row.amountMinor,
                            currency = row.currency,
                            direction = row.direction,
                            description = row.description,
                            merchant = row.merchant,
                            source = TransactionSource.CSV,
                            fingerprint = fp
                        )
                    )
                    if (output.status != DeduplicationStatus.EXACT_DUPLICATE) imported++
                }
            }
            _uiState.value = _uiState.value.copy(isImporting = false, importComplete = true, importedCount = imported)
            onSuccess()
        }
    }
}
