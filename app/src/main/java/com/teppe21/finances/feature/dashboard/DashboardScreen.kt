package com.teppe21.finances.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teppe21.finances.R
import com.teppe21.finances.core.theme.finances
import com.teppe21.finances.core.theme.CategoryColorProvider
import com.teppe21.finances.core.ui.EmptyState
import com.teppe21.finances.core.ui.InfoBanner
import com.teppe21.finances.core.ui.SectionCard
import com.teppe21.finances.core.ui.TransactionRow
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.domain.model.CategoryBreakdown
import com.teppe21.finances.domain.model.MonthlyTrend
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private val MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private val MONTH_SHORT = DateTimeFormatter.ofPattern("MMM", Locale.US)

@Composable
fun DashboardScreen(
    onNavigateToScanReceipt: () -> Unit,
    onNavigateToImportCsv: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToStocks: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onAddTransaction: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            BalanceCard(
                totalBalanceMinor = state.totalBalanceMinor,
                currency = state.currency,
                incomeMinor = state.monthStats.incomeMinor,
                expenseMinor = state.monthStats.expenseMinor,
                month = state.month
            )
        }

        if (state.reviewCount > 0) {
            item {
                InfoBanner(
                    text = if (state.reviewCount == 1) "1 transaction needs review before it is counted."
                    else "${state.reviewCount} transactions need review before they are counted.",
                    icon = Icons.Default.PendingActions,
                    onClick = onNavigateToReview
                )
            }
        }

        val unconverted = state.monthStats.unconvertedCount + state.balanceUnconvertedAccounts
        if (unconverted > 0) {
            item {
                InfoBanner(
                    text = "Some amounts are not included because no exchange rate to ${state.currency} is available. " +
                        "Refresh rates or enter a manual rate in Settings.",
                    onClick = onNavigateToSettings
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction(Modifier.weight(1f), stringResource(R.string.add_transaction), Icons.Default.Add, onAddTransaction)
                QuickAction(Modifier.weight(1f), stringResource(R.string.scan_receipt), Icons.Default.CameraAlt, onNavigateToScanReceipt)
                QuickAction(Modifier.weight(1f), stringResource(R.string.import_statement), Icons.Default.FileOpen, onNavigateToImportCsv)
            }
        }

        if (!state.isLoading && !state.hasAnyTransactions) {
            item {
                EmptyState(
                    title = "No transactions yet",
                    body = "Add one manually, scan a receipt, import a bank CSV, or enable notification recognition under More.",
                    action = { TextButton(onClick = onAddTransaction) { Text(stringResource(R.string.add_transaction)) } }
                )
            }
        }

        if (state.hasAnyTransactions) {
            item { MonthlyTrendsChartCard(state.monthlyTrends, state.currency) }
            if (state.categoryBreakdown.isNotEmpty()) {
                item { CategoryBreakdownCard(state.categoryBreakdown, state.currency, state.month) }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.recent_activity), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onNavigateToTransactions) { Text(stringResource(R.string.see_all)) }
                }
            }
            items(state.recentTransactions, key = { it.id }) { tx ->
                TransactionRow(
                    transaction = tx,
                    displayCurrency = state.currency,
                    convertedAmountMinor = state.recentConverted[tx.id],
                    onClick = onNavigateToTransactions
                )
            }
        }

        item { StocksEntryCard(onNavigateToStocks) }

        if (state.ratesAsOf != null && state.hasAnyTransactions) {
            item {
                Text(
                    text = "Converted amounts use the latest available exchange rates, not the rate on each transaction date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BalanceCard(
    totalBalanceMinor: Long?,
    currency: String,
    incomeMinor: Long,
    expenseMinor: Long,
    month: YearMonth
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.total_balance),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = totalBalanceMinor?.let { CurrencyFormatter.formatSigned(it, currency) } ?: "Unavailable",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "All accounts, opening balances plus recorded transactions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(month.format(MONTH_TITLE), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                MonthFigure(Modifier.weight(1f), stringResource(R.string.income), "+" + CurrencyFormatter.format(incomeMinor, currency), MaterialTheme.finances.income)
                MonthFigure(Modifier.weight(1f), stringResource(R.string.expenses), "-" + CurrencyFormatter.format(expenseMinor, currency), MaterialTheme.finances.expense)
            }
        }
    }
}

@Composable
private fun MonthFigure(modifier: Modifier, label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun QuickAction(modifier: Modifier, title: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun MonthlyTrendsChartCard(trends: List<MonthlyTrend>, currency: String) {
    SectionCard(title = stringResource(R.string.six_months_trends)) {
        val maxVal = max(trends.maxOfOrNull { max(it.incomeMinor, it.expenseMinor) } ?: 0L, 1L)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .semantics {
                    contentDescription = trends.joinToString("; ") {
                        "${YearMonth.parse(it.month).format(MONTH_SHORT)}: income ${CurrencyFormatter.format(it.incomeMinor, currency)}, " +
                            "expenses ${CurrencyFormatter.format(it.expenseMinor, currency)}"
                    }
                },
            verticalAlignment = Alignment.Bottom
        ) {
            trends.forEach { item ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.height(84.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Bar(item.incomeMinor.toFloat() / maxVal, MaterialTheme.finances.income)
                        Bar(item.expenseMinor.toFloat() / maxVal, MaterialTheme.finances.expense)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        YearMonth.parse(item.month).format(MONTH_SHORT),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend("Income", MaterialTheme.finances.income)
            Legend("Expenses", MaterialTheme.finances.expense)
        }
    }
}

@Composable
private fun Bar(ratio: Float, color: androidx.compose.ui.graphics.Color) {
    // Zero stays visibly zero: no artificial minimum height.
    val height = (80f * ratio.coerceIn(0f, 1f)).dp
    Box(
        Modifier
            .width(8.dp)
            .height(height)
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(color)
    )
}

@Composable
private fun Legend(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryBreakdownCard(breakdown: List<CategoryBreakdown>, currency: String, month: YearMonth) {
    SectionCard(title = "Spending by category, ${month.format(MONTH_TITLE)}") {
        breakdown.take(5).forEach { cat ->
            val color = CategoryColorProvider.getColor(cat.categoryId, cat.color)
            Column(Modifier.padding(vertical = 5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(8.dp))
                    Text(cat.categoryName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(
                        "${CurrencyFormatter.format(cat.totalMinor, currency)} · ${cat.percentage.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (cat.percentage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StocksEntryCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Stocks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Watchlist of market quotes. Kept separate from your accounts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
