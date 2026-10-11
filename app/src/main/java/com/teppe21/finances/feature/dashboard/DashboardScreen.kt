package com.teppe21.finances.feature.dashboard

import com.teppe21.finances.feature.categories.categoryLabel
import com.teppe21.finances.core.utils.DateFormatter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import com.teppe21.finances.core.theme.tabular
import com.teppe21.finances.core.theme.CategoryColorProvider
import com.teppe21.finances.core.ui.EmptyState
import com.teppe21.finances.core.ui.InfoBanner
import com.teppe21.finances.core.ui.Motion
import com.teppe21.finances.core.ui.animatedFraction
import com.teppe21.finances.core.ui.pressClickable
import com.teppe21.finances.core.ui.SectionCard
import com.teppe21.finances.core.ui.TransactionRow
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.domain.model.CategoryBreakdown
import com.teppe21.finances.domain.model.MonthlyTrend
import java.time.YearMonth
import kotlin.math.max


@OptIn(ExperimentalFoundationApi::class)
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

    // Until the first emission nothing is shown, so "Unavailable" or zero totals never flash
    // before the real figures; the content then fades in once.
    Crossfade(targetState = state.isLoading, animationSpec = Motion.medium(), label = "dashboardLoading") { loading ->
        if (loading) {
            Box(Modifier.fillMaxSize())
        } else {
            DashboardContent(
                state = state,
                onNavigateToScanReceipt = onNavigateToScanReceipt,
                onNavigateToImportCsv = onNavigateToImportCsv,
                onNavigateToReview = onNavigateToReview,
                onNavigateToTransactions = onNavigateToTransactions,
                onNavigateToStocks = onNavigateToStocks,
                onNavigateToSettings = onNavigateToSettings,
                onAddTransaction = onAddTransaction
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onNavigateToScanReceipt: () -> Unit,
    onNavigateToImportCsv: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToStocks: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onAddTransaction: () -> Unit
) {
    val resources = LocalContext.current.resources

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item(key = "balance") {
            BalanceHero(
                totalBalanceMinor = state.totalBalanceMinor,
                currency = state.currency,
                incomeMinor = state.monthStats.incomeMinor,
                expenseMinor = state.monthStats.expenseMinor,
                month = state.month,
                modifier = place()
            )
        }

        if (state.reviewCount > 0) {
            item(key = "review-banner") {
                InfoBanner(
                    text = pluralStringResource(R.plurals.dashboard_review, state.reviewCount, state.reviewCount),
                    icon = Icons.Default.PendingActions,
                    onClick = onNavigateToReview,
                    modifier = place()
                )
            }
        }

        val unconverted = state.monthStats.unconvertedCount + state.balanceUnconvertedAccounts
        if (unconverted > 0) {
            item(key = "rate-banner") {
                InfoBanner(
                    text = stringResource(R.string.dashboard_unconverted, state.currency),
                    onClick = onNavigateToSettings,
                    modifier = place()
                )
            }
        }

        item(key = "quick-actions") {
            Row(place().fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction(Modifier.weight(1f), stringResource(R.string.add_transaction), Icons.Default.Add, onAddTransaction)
                QuickAction(Modifier.weight(1f), stringResource(R.string.scan_receipt), Icons.Default.CameraAlt, onNavigateToScanReceipt)
                QuickAction(Modifier.weight(1f), stringResource(R.string.import_statement), Icons.Default.FileOpen, onNavigateToImportCsv)
            }
        }

        if (!state.hasAnyTransactions) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.no_transactions_title),
                    body = stringResource(R.string.no_transactions_desc),
                    modifier = place(),
                    action = { TextButton(onClick = onAddTransaction) { Text(stringResource(R.string.add_transaction)) } }
                )
            }
        }

        if (state.hasAnyTransactions) {
            item(key = "trends") { MonthlyTrendsChartCard(state.monthlyTrends, state.currency, place()) }
            if (state.categoryBreakdown.isNotEmpty()) {
                item(key = "breakdown") { CategoryBreakdownCard(
                    state.categoryBreakdown.map { b -> state.categories.firstOrNull { it.id == b.categoryId }?.let { b.copy(categoryName = categoryLabel(it, resources)) } ?: b },
                    state.currency, state.month, place()
                ) }
            }
            item(key = "recent-header") {
                Row(
                    modifier = place().fillMaxWidth().padding(top = 4.dp),
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
                    onClick = onNavigateToTransactions,
                    modifier = place()
                )
            }
        }

        item(key = "stocks") { StocksEntryCard(onNavigateToStocks, place()) }

        if (state.ratesAsOf != null && state.hasAnyTransactions) {
            item(key = "rates-note") {
                Text(
                    text = stringResource(R.string.dashboard_rates_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = place()
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BalanceHero(
    totalBalanceMinor: Long?,
    currency: String,
    incomeMinor: Long,
    expenseMinor: Long,
    month: YearMonth,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            stringResource(R.string.total_balance),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = totalBalanceMinor?.let { CurrencyFormatter.formatSigned(it, currency) } ?: stringResource(R.string.unavailable),
            style = MaterialTheme.typography.displayLarge.tabular(),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
        Text(
            text = stringResource(R.string.dashboard_balance_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Text(
            DateFormatter.monthYear(month),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryTile(
                Modifier.weight(1f),
                stringResource(R.string.income),
                "+" + CurrencyFormatter.format(incomeMinor, currency),
                MaterialTheme.finances.income,
                Icons.Default.ArrowDownward
            )
            SummaryTile(
                Modifier.weight(1f),
                stringResource(R.string.expenses),
                "-" + CurrencyFormatter.format(expenseMinor, currency),
                MaterialTheme.finances.expense,
                Icons.Default.ArrowUpward
            )
        }
    }
}

/** A month figure: a soft tint of its colour, an icon, a word and a signed value, so colour is never the only cue. */
@Composable
private fun SummaryTile(
    modifier: Modifier,
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    icon: ImageVector
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(22.dp), color = color.copy(alpha = 0.10f)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    color = color,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun QuickAction(modifier: Modifier, title: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(20.dp))
            .pressClickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
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
fun MonthlyTrendsChartCard(trends: List<MonthlyTrend>, currency: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    SectionCard(modifier = modifier, title = stringResource(R.string.six_months_trends)) {
        val maxVal = max(trends.maxOfOrNull { max(it.incomeMinor, it.expenseMinor) } ?: 0L, 1L)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .semantics {
                    contentDescription = trends.joinToString("; ") {
                        context.getString(R.string.trend_point, DateFormatter.monthShort(YearMonth.parse(it.month)), CurrencyFormatter.format(it.incomeMinor, currency), CurrencyFormatter.format(it.expenseMinor, currency))
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
                        DateFormatter.monthShort(YearMonth.parse(item.month)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(stringResource(R.string.income), MaterialTheme.finances.income)
            Legend(stringResource(R.string.expenses), MaterialTheme.finances.expense)
        }
    }
}

@Composable
private fun Bar(ratio: Float, color: androidx.compose.ui.graphics.Color) {
    // Zero stays visibly zero: no artificial minimum height. The bar grows to its value; the
    // exact figures are in the content description and never animate.
    val height = (80f * animatedFraction(ratio.coerceIn(0f, 1f), "trendBar")).dp
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
private fun CategoryBreakdownCard(
    breakdown: List<CategoryBreakdown>,
    currency: String,
    month: YearMonth,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier, title = stringResource(R.string.dashboard_spending_month, DateFormatter.monthYear(month))) {
        breakdown.take(5).forEach { cat ->
            val color = CategoryColorProvider.getColor(cat.categoryId, cat.color)
            val shown = animatedFraction((cat.percentage / 100f).coerceIn(0f, 1f), "categoryShare")
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
                    progress = { shown },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StocksEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).pressClickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.stocks_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.dashboard_stocks_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Items shift smoothly when a banner or the empty state appears or goes away. */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("ModifierFactoryExtensionFunction") // needs the LazyItemScope receiver
private fun LazyItemScope.place(): Modifier = Modifier.animateItemPlacement(Motion.medium())
