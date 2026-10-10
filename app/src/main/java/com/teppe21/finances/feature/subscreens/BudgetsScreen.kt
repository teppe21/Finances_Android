package com.teppe21.finances.feature.subscreens

import androidx.compose.foundation.layout.Arrangement
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
    val categoryName: String,
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
            BudgetRow(b.id, categories.firstOrNull { it.id == b.categoryId }?.displayName() ?: b.categoryId, b.amountMinor, currency, spent, missing)
        }
        BudgetsState(rows, categories.filter { !it.isIncome }, prefs.currency)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetsState())

    fun add(categoryId: String, limitMinor: Long, currency: String) = viewModelScope.launch {
        dao.insert(BudgetEntity(UUID.randomUUID().toString(), categoryId, limitMinor, "monthly", 0L, null, currency))
    }

    fun delete(id: String) = viewModelScope.launch { dao.deleteById(id) }
}

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
                        "No budgets yet",
                        "Set a monthly limit for a spending category to see how much of it you have used this month."
                    )
                }
            }
            items(state.rows, key = { it.id }) { row ->
                val ratio = if (row.limitMinor > 0) (row.spentMinor.toFloat() / row.limitMinor).coerceIn(0f, 1f) else 0f
                SectionCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(row.categoryName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.delete(row.id) }) { Icon(Icons.Default.Delete, contentDescription = "Delete budget for ${row.categoryName}") }
                    }
                    Text(
                        "${CurrencyFormatter.format(row.spentMinor, row.currency)} of ${CurrencyFormatter.format(row.limitMinor, row.currency)} this month",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (row.spentMinor > row.limitMinor) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    if (row.spentMinor > row.limitMinor) {
                        Text("Over budget", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                    if (row.unconverted > 0) {
                        Text(
                            "${row.unconverted} expenses not included: no exchange rate to ${row.currency}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Add budget")
        }
    }

    if (showAdd) {
        var categoryId by remember { mutableStateOf(state.categories.firstOrNull()?.id) }
        var currency by remember { mutableStateOf(state.displayCurrency) }
        var amount by remember { mutableStateOf("") }
        val parsed = Money.parseUserInput(amount)?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Monthly budget") },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Category", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.categories, key = { it.id }) { cat ->
                            FilterChip(selected = categoryId == cat.id, onClick = { categoryId = cat.id }, label = { Text(cat.displayName()) })
                        }
                    }
                    Text("Currency", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SupportedCurrencies.ALL.forEach { c ->
                            FilterChip(selected = currency == c.code, onClick = { currency = c.code }, label = { Text(c.code) })
                        }
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(15) },
                        label = { Text("Limit per month ($currency)") },
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
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}
