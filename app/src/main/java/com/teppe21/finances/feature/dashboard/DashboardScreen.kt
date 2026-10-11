package com.teppe21.finances.feature.dashboard

import com.teppe21.finances.core.theme.Radii

import com.teppe21.finances.core.theme.Spacing
import com.teppe21.finances.core.theme.Sizes
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import com.teppe21.finances.core.ui.AmountRowLayout
import com.teppe21.finances.core.ui.AmountText
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
            QuickActions(
                listOf(
                    QuickActionItem(stringResource(R.string.add_transaction), Icons.Default.Add, onAddTransaction),
                    QuickActionItem(stringResource(R.string.scan_receipt), Icons.Default.CameraAlt, onNavigateToScanReceipt),
                    QuickActionItem(stringResource(R.string.import_statement), Icons.Default.FileOpen, onNavigateToImportCsv)
                ),
                place()
            )
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
                    categoryName = state.categories.firstOrNull { it.id == tx.categoryId }?.let { categoryLabel(it) },
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
        val incomeText = CurrencyFormatter.formatSigned(incomeMinor, currency)
        val expenseText = CurrencyFormatter.formatSigned(-expenseMinor, currency)
        // Side by side when both amounts fit in half the width; stacked otherwise (large amounts,
        // large font scales, narrow screens), so an amount is never cut.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val measurer = rememberTextMeasurer()
            val style = MaterialTheme.typography.titleMedium.tabular()
            val density = LocalDensity.current
            val needed = with(density) {
                maxOf(measurer.measure(incomeText, style).size.width, measurer.measure(expenseText, style).size.width).toDp()
            }
            val available = (maxWidth - Spacing.m) / 2 - SummaryTileChrome
            val tiles: @Composable (Modifier) -> Unit = { tileModifier ->
                SummaryTile(tileModifier, stringResource(R.string.income), incomeText, MaterialTheme.finances.income, Icons.Default.ArrowDownward)
                SummaryTile(tileModifier, stringResource(R.string.expenses), expenseText, MaterialTheme.finances.expense, Icons.Default.ArrowUpward)
            }
            if (needed <= available) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) { tiles(Modifier.weight(1f)) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) { tiles(Modifier.fillMaxWidth()) }
            }
        }
    }
}

/** A month figure: a soft tint of its colour, an icon, a word and a signed value, so colour is never the only cue. */
/** Horizontal space a summary tile uses besides its amount: padding, icon and gap. */private val SummaryTileChrome = 70.dp
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

class QuickActionItem(val title: String, val icon: ImageVector, val onClick: () -> Unit)

private val QuickTilePadding = 4.dp
private val QuickTileGap = 8.dp

/**
 * The dashboard shortcuts. They sit side by side while the longest word of every label fits its
 * tile (so a label wraps at spaces, never in the middle of a word); at a large font scale or with
 * a long Hungarian word they stack as full-width rows instead, so the text keeps the user's size.
 */
@Composable
fun QuickActions(items: List<QuickActionItem>, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val tileWidth = (maxWidth - QuickTileGap * (items.size - 1)) / items.size
        val textWidthPx = with(LocalDensity.current) { (tileWidth - QuickTilePadding * 2).roundToPx() }
        val sideBySide = items.all { item ->
            item.title.split(' ').all { word -> measurer.measure(word, style).size.width <= textWidthPx }
        }
        if (sideBySide) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(QuickTileGap)) {
                items.forEach { QuickActionTile(Modifier.weight(1f), it, stacked = false) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(QuickTileGap)) {
                items.forEach { QuickActionTile(Modifier.fillMaxWidth(), it, stacked = true) }
            }
        }
    }
}

@Composable
private fun QuickActionTile(modifier: Modifier, item: QuickActionItem, stacked: Boolean) {
    Surface(
        modifier = modifier
            .heightIn(min = if (stacked) Sizes.row else 84.dp)
            .then(if (stacked) Modifier else Modifier.fillMaxHeight())
            .clip(Radii.card)
            .pressClickable(onClick = item.onClick),
        shape = Radii.card,
        color = MaterialTheme.colorScheme.surface
    ) {
        if (stacked) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(Spacing.m))
                Text(item.title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = QuickTilePadding, vertical = Spacing.s),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(6.dp))
                Text(item.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
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
            Column(Modifier.padding(vertical = 5.dp).semantics(mergeDescendants = true) {}) {
                // The figure is measured first and never truncated; the name takes what is left (two
                // lines) or the figure moves under it at a large font scale.
                AmountRowLayout(
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(8.dp))
                            Text(cat.categoryName, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    amount = {
                        AmountText(
                            "${CurrencyFormatter.format(cat.totalMinor, currency)} · ${CurrencyFormatter.formatPercent(cat.percentage)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold).tabular()
                        )
                    }
                )
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
        modifier = modifier.fillMaxWidth().clip(Radii.card).pressClickable(onClick = onClick),
        shape = Radii.card,
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
