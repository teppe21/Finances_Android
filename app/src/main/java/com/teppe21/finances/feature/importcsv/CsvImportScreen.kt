package com.teppe21.finances.feature.importcsv

import com.teppe21.finances.feature.categories.accountLabel
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import com.teppe21.finances.core.theme.tabular
import com.teppe21.finances.core.ui.SectionCard
import com.teppe21.finances.native.csv.ImportSummary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teppe21.finances.R
import com.teppe21.finances.core.theme.finances
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.native.csv.CsvSkipReason
import androidx.compose.foundation.lazy.items

@Composable
fun CsvImportScreen(
    onViewTransactions: () -> Unit,
    onReviewUncategorized: () -> Unit,
    viewModel: CsvImportViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "statement.csv"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) fileName = cursor.getString(nameIdx)
                }
            }
            viewModel.loadFromUri(context, uri, fileName)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        state.summary?.let { summary ->
            item(key = "summary") {
                ImportSummaryCard(
                    summary = summary,
                    onReviewUncategorized = onReviewUncategorized,
                    onViewTransactions = onViewTransactions,
                    onImportAnother = viewModel::reset
                )
            }
        }

        // 1. File Picker Card
        if (state.summary == null) item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.import_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.select_csv_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            FinancesApp.instance.appLock.expectExternalActivity()
                            filePicker.launch(arrayOf("text/comma-separated-values", "text/csv", "text/plain", "*/*"))
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.select_csv_file))
                    }
                }
            }
        }

        // 2. Loading Spinner
        if (state.isAnalyzing) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // 3. Error Message
        val error = state.error
        if (error != null) {
            item {
                val skippedCount = state.skipped.values.sum()
                val message = when (error) {
                    CsvImportError.NO_ROWS -> stringResource(R.string.import_error_no_rows) +
                        if (skippedCount > 0) " " + stringResource(R.string.import_error_rows_unreadable, skippedCount) else ""
                    CsvImportError.UNREADABLE_FILE -> stringResource(R.string.import_error_file)
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        // 4. Analysis Results & Confirmation
        if (state.parsedRows.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = stringResource(R.string.selected_file, state.fileName ?: "statement.csv"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.detected_rows, state.parsedRows.size),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.new_items, state.newCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.finances.success,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.import_already_imported, state.alreadyImportedCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.finances.warning
                        )
                        if (state.skipped.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val reasons = state.skipped.entries.map { (reason, count) -> skipReasonText(reason, count) }
                            Text(
                                text = stringResource(R.string.import_skipped_intro) + " " + reasons.joinToString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        state.charset?.let {
                            Text(stringResource(R.string.import_encoding, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.account) + ":",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.accounts, key = { it.id }) { acc ->
                                FilterChip(
                                    selected = state.selectedAccountId == acc.id,
                                    onClick = { viewModel.setSelectedAccount(acc.id) },
                                    label = { Text("${accountLabel(acc)} (${acc.currency})") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = viewModel::executeImport,
                            enabled = !state.isImporting && state.newCount > 0,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isImporting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.import_confirm))
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

/** The result of an import: what was added, skipped, sorted, and what is left to sort. */
@Composable
private fun ImportSummaryCard(
    summary: ImportSummary,
    onReviewUncategorized: () -> Unit,
    onViewTransactions: () -> Unit,
    onImportAnother: () -> Unit
) {
    SectionCard(title = stringResource(R.string.import_done_title)) {
        SummaryLine(stringResource(R.string.import_summary_imported), summary.imported)
        SummaryLine(
            stringResource(R.string.import_summary_skipped),
            summary.skipped,
            detail = stringResource(R.string.import_summary_skipped_detail, summary.alreadyImported, summary.unreadable).takeIf { summary.skipped > 0 }
        )
        SummaryLine(stringResource(R.string.import_summary_categorized), summary.categorized)
        SummaryLine(
            stringResource(R.string.import_summary_uncategorized),
            summary.uncategorized,
            emphasize = summary.uncategorized > 0
        )
        SummaryLine(
            stringResource(R.string.import_summary_transfers),
            summary.ownTransfers + summary.personalTransfers,
            detail = stringResource(R.string.import_summary_transfers_detail, summary.ownTransfers, summary.personalTransfers)
                .takeIf { summary.ownTransfers + summary.personalTransfers > 0 }
        )
        Spacer(Modifier.height(16.dp))
        if (summary.uncategorized > 0) {
            Button(onClick = onReviewUncategorized, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_review_uncategorized))
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onViewTransactions, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.import_view_transactions))
        }
        TextButton(onClick = onImportAnother, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.import_another))
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: Int, detail: String? = null, emphasize: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium.tabular(),
            fontWeight = FontWeight.SemiBold,
            color = if (emphasize) MaterialTheme.finances.warning else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun skipReasonText(reason: CsvSkipReason, count: Int): String = when (reason) {
    CsvSkipReason.INVALID_DATE -> stringResource(R.string.skip_invalid_date, count)
    CsvSkipReason.AMBIGUOUS_DATE -> stringResource(R.string.skip_ambiguous_date, count)
    CsvSkipReason.INVALID_AMOUNT -> stringResource(R.string.skip_invalid_amount, count)
    CsvSkipReason.ZERO_AMOUNT -> stringResource(R.string.skip_zero_amount, count)
    CsvSkipReason.UNSUPPORTED_CURRENCY -> stringResource(R.string.skip_unsupported_currency, count)
    CsvSkipReason.TOO_FEW_COLUMNS -> stringResource(R.string.skip_too_few_columns, count)
    CsvSkipReason.NOT_COMPLETED -> stringResource(R.string.skip_not_completed, count)
}
