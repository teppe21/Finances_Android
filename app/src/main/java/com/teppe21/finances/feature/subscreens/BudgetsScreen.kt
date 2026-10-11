package com.teppe21.finances.feature.subscreens

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.teppe21.finances.R
import com.teppe21.finances.feature.categories.categoryLabel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import com.teppe21.finances.core.ui.Motion
import com.teppe21.finances.core.ui.animatedFraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.core.money.Money
import com.teppe21.finances.core.ui.EmptyState
import com.teppe21.finances.core.ui.SectionCard
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.data.local.entity.BudgetEntity
import com.teppe21.finances.domain.model.Category
import com.teppe21.finances.domain.model.SupportedCurrencies
import com.teppe21.finances.domain.model.TransactionDirection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.UUID
import kotlin.math.abs

data class BudgetRow(
    val id: String,
    /** The category (null if it was deleted), named on screen in the app language. */
    val category: Category?,
    val categoryId: String,
    val limitMinor: Long,
    val currency: String,
    /** Confirmed expenses this month in the budget currency. */
    val spentMinor: Long,
    val unconverted: Int
)

data class BudgetsState(val rows: List<BudgetRow> = emptyList(), val categories: List<Category> = emptyList(), val displayCurrency: String = "HUF")

class BudgetsViewModel : ViewModel() {
    private val app = FinancesApp.instance
    private val dao = app.database.budgetDao()

    val state: StateFlow<BudgetsState> = combine(
        dao.getAllFlow(),
        app.transactionRepository.getAllTransactionsFlow(),
        app.categoryRepository.getAllCategoriesFlow(),
        app.exchangeRateRepository.tableFlow,
        app.preferencesRepository.preferencesFlow
    ) { budgets, transactions, categories, table, prefs ->
        val month = YearMonth.now()
        val monthExpenses = transactions.filter {
            !it.pending && it.direction == TransactionDirection.EXPENSE && YearMonth.from(it.date) == month
        }
        val rows = budgets.map { b ->
            val currency = b.currency ?: "HUF"
            var spent = 0L
            var missing = 0
            monthExpenses.filter { it.categoryId == b.categoryId }.forEach { t ->
                val converted = table.convert(abs(t.amountMinor), t.currency, currency)
                if (converted == null) missing++ else spent += converted
            }
            BudgetRow(b.id, categories.firstOrNull { it.id == b.categoryId }, b.categoryId, b.amountMinor, currency, spent, missing)
        }
        BudgetsState(rows, categories.filter { !it.isIncome }, prefs.currency)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetsState())

    fun add(categoryId: String, limitMinor: Long, currency: String) = viewModelScope.launch {
        dao.insert(BudgetEntity(UUID.randomUUID().toString(), categoryId, limitMinor, "monthly", 0L, null, currency))
    }

    fun delete(id: String) = viewModelScope.launch { dao.deleteById(id) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            if (state.rows.isEmpty()) {
                item {
                    EmptyState(
                        stringResource(R.string.budgets_empty_title),
                        stringResource(R.string.budgets_empty_desc)
                    )
                }
            }
            items(state.rows, key = { it.id }) { row ->
                val name = row.category?.let { categoryLabel(it) } ?: row.categoryId
                val ratio = if (row.limitMinor > 0) (row.spentMinor.toFloat() / row.limitMinor).coerceIn(0f, 1f) else 0f
                val shownRatio = animatedFraction(ratio, "budgetUsed")
                val barColor by animateColorAsState(
                    if (row.spentMinor > row.limitMinor) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    Motion.short(), label = "budgetColor"
                )
                // Explanations that appear or go away (over budget, missing rates) open and close the card
                // smoothly; the figures above them are always exact.
                SectionCard(modifier = Modifier.animateItemPlacement(Motion.medium()).animateContentSize(Motion.medium())) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.delete(row.id) }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.budget_delete, name)) }
                    }
                    Text(
                        stringResource(R.string.budget_progress, CurrencyFormatter.format(row.spentMinor, row.currency), CurrencyFormatter.format(row.limitMinor, row.currency)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { shownRatio },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    if (row.spentMinor > row.limitMinor) {
                        Text(stringResource(R.string.budget_over), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                    if (row.unconverted > 0) {
                        Text(
                            pluralStringResource(R.plurals.budget_unconverted, row.unconverted, row.unconverted, row.currency),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.budget_add))
        }
    }

    if (showAdd) {
        var categoryId by remember { mutableStateOf(state.categories.firstOrNull()?.id) }
        var currency by remember { mutableStateOf(state.displayCurrency) }
        var amount by remember { mutableStateOf("") }
        val parsed = Money.parseUserInput(amount)?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.budget_dialog_title)) },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.category), style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.categories, key = { it.id }) { cat ->
                            FilterChip(selected = categoryId == cat.id, onClick = { categoryId = cat.id }, label = { Text(categoryLabel(cat)) })
                        }
                    }
                    Text(stringResource(R.string.currency), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SupportedCurrencies.ALL.forEach { c ->
                            FilterChip(selected = currency == c.code, onClick = { currency = c.code }, label = { Text(c.code) })
                        }
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(15) },
                        label = { Text(stringResource(R.string.budget_limit, currency)) },
                        isError = amount.isNotBlank() && parsed == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(enabled = parsed != null && categoryId != null, onClick = {
                    viewModel.add(categoryId!!, parsed!!, currency)
                    showAdd = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
