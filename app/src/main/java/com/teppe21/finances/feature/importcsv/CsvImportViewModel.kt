package com.teppe21.finances.feature.importcsv

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.native.csv.CsvImport
import com.teppe21.finances.native.csv.CsvParseResult
import com.teppe21.finances.native.csv.CsvParser
import com.teppe21.finances.native.csv.CsvSkipReason
import com.teppe21.finances.native.csv.ImportSummary
import com.teppe21.finances.native.csv.ParsedCsvRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Why a file could not be imported at all; the screen turns it into text. */
enum class CsvImportError { NO_ROWS, UNREADABLE_FILE }

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
    /** Set when an import has finished; the screen shows it until the next file. */
    val summary: ImportSummary? = null,
    val error: CsvImportError? = null
) {
    val newCount: Int get() = parsedRows.size - alreadyImportedCount
    val importComplete: Boolean get() = summary != null
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
        _uiState.value = _uiState.value.copy(isAnalyzing = true, fileName = fileName, error = null, summary = null)
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
                        error = CsvImportError.NO_ROWS
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
                _uiState.value = _uiState.value.copy(isAnalyzing = false, error = CsvImportError.UNREADABLE_FILE)
            }
        }
    }

    private fun selectedAccount(): Account? =
        _uiState.value.accounts.firstOrNull { it.id == _uiState.value.selectedAccountId }

    private suspend fun recountDuplicates() {
        val accountId = _uiState.value.selectedAccountId ?: return
        val rows = _uiState.value.parsedRows
        val existing = withContext(Dispatchers.IO) {
            CsvImport.fingerprints(accountId, rows).count { txRepo.findByFingerprint(it) != null }
        }
        _uiState.value = _uiState.value.copy(alreadyImportedCount = existing)
    }

    fun executeImport() {
        val state = _uiState.value
        val accountId = state.selectedAccountId ?: return
        if (state.isImporting) return
        _uiState.value = state.copy(isImporting = true)
        viewModelScope.launch {
            val outputs = withContext(Dispatchers.IO) {
                val rows = state.parsedRows
                rows.zip(CsvImport.fingerprints(accountId, rows)).map { (row, fp) ->
                    ingestUseCase.execute(CsvImport.input(row, accountId, fp))
                }
            }
            _uiState.value = _uiState.value.copy(
                isImporting = false,
                summary = ImportSummary.of(outputs, unreadable = state.skipped.values.sum()),
                parsedRows = emptyList(),
                alreadyImportedCount = 0
            )
        }
    }

    /** Clears the finished import so another file can be chosen. */
    fun reset() {
        _uiState.value = CsvImportUiState(accounts = _uiState.value.accounts, selectedAccountId = _uiState.value.selectedAccountId)
    }

}
