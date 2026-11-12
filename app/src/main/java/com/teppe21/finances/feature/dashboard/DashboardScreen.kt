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
import androidx.compose.material3.FilterChip
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
import com.teppe21.finances.core.ui.ChipFlow
import com.teppe21.finances.core.ui.GroupedBarChart
import com.teppe21.finances.core.ui.LineChart
import com.teppe21.finances.core.ui.Motion
import com.teppe21.finances.core.theme.amount
import com.teppe21.finances.core.theme.amountLarge
import com.teppe21.finances.domain.usecase.BalancePoint
import com.teppe21.finances.domain.usecase.BalanceRange
import com.teppe21.finances.domain.usecase.inRange
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.heading
import com.teppe21.finances.core.ui.animatedFraction
import com.teppe21.finances.core.ui.pressClickable
import com.teppe21.finances.core.ui.SectionCard
import com.teppe21.finances.core.ui.TransactionRow
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.domain.model.CategoryBreakdown
import com.teppe21.finances.domain.model.MonthlyTrend
import java.time.YearMonth


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
        verticalArrangement = Arrangement.spacedBy(Spacing.l)
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

        if (state.balanceSeries.isNotEmpty()) {
            item(key = "balance-trend") { BalanceTrendCard(state.balanceSeries, state.currency, place()) }
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
                    state.previousCategoryTotals, state.currency, state.month, place()
                ) }
            }
            item(key = "recent-header") {
                Row(
                    modifier = place().fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CardLabel(stringResource(R.string.recent_activity))
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

/** Small, quiet card heading: the numbers below it are what should stand out. */
@Composable
internal fun CardLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() }
    )
}

private fun BalanceRange.labelRes(): Int = when (this) {
    BalanceRange.WEEK -> R.string.stock_range_1w
    BalanceRange.MONTH -> R.string.stock_range_1m
    BalanceRange.QUARTER -> R.string.stock_range_3m
    BalanceRange.YEAR -> R.string.stock_range_1y
    BalanceRange.ALL -> R.string.range_all
}

/** Total balance over time. While a finger is on the chart the header shows that day's balance and date. */
@Composable
fun BalanceTrendCard(series: List<BalancePoint>, currency: String, modifier: Modifier = Modifier) {
    var range by rememberSaveable { mutableStateOf(BalanceRange.MONTH) }
    var inspected by remember { mutableStateOf<Int?>(null) }
    val shown = remember(series, range) { series.inRange(range) }
    val values = remember(shown) { shown.map { it.balanceMinor.toFloat() } }
    if (shown.isEmpty()) return
    val point = inspected?.let { shown.getOrNull(it) }
    val change = shown.last().balanceMinor - shown.first().balanceMinor
    val rangeLabel = stringResource(range.labelRes())
    val description = stringResource(
        R.string.balance_chart_description, rangeLabel,
        CurrencyFormatter.format(shown.first().balanceMinor, currency), CurrencyFormatter.format(shown.last().balanceMinor, currency),
        CurrencyFormatter.format(shown.minOf { it.balanceMinor }, currency), CurrencyFormatter.format(shown.maxOf { it.balanceMinor }, currency)
    )
    SectionCard(modifier = modifier) {
        CardLabel(stringResource(R.string.balance_trend_title))
        Spacer(Modifier.height(Spacing.xs))
        Text(
            CurrencyFormatter.formatSigned((point ?: shown.last()).balanceMinor, currency),
            style = MaterialTheme.typography.amountLarge,
            maxLines = 1
        )
        Text(
            if (point != null) DateFormatter.format(point.date) else CurrencyFormatter.formatSigned(change, currency),
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = when {
                point != null -> MaterialTheme.colorScheme.onSurfaceVariant
                change >= 0 -> MaterialTheme.finances.income
                else -> MaterialTheme.finances.expense
            }
        )
        Spacer(Modifier.height(Spacing.m))
        LineChart(
            values = values,
            color = MaterialTheme.colorScheme.primary,
            contentDescription = description,
            modifier = Modifier.fillMaxWidth().height(140.dp),
            onInspect = { inspected = it }
        )
        Spacer(Modifier.height(Spacing.s))
        ChipFlow {
            BalanceRange.values().forEach { r ->
                FilterChip(selected = range == r, onClick = { range = r; inspected = null }, label = { Text(stringResource(r.labelRes())) })
            }
        }
    }
}

@Composable
fun MonthlyTrendsChartCard(trends: List<MonthlyTrend>, currency: String, modifier: Modifier = Modifier) {
    if (trends.isEmpty()) return
    val context = LocalContext.current
    var picked by remember(trends.size) { mutableIntStateOf(trends.lastIndex) }
    val current = trends[picked.coerceIn(0, trends.lastIndex)]
    val labels = remember(trends) { trends.map { DateFormatter.monthShort(YearMonth.parse(it.month)) } }
    SectionCard(modifier = modifier) {
        CardLabel(stringResource(R.string.six_months_trends))
        Spacer(Modifier.height(Spacing.xs))
        Text(
            DateFormatter.monthYear(YearMonth.parse(current.month)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xs))
        // Wraps instead of shrinking: an amount is never cut.
        ChipFlow {
            TrendFigure(stringResource(R.string.income), CurrencyFormatter.format(current.incomeMinor, currency), MaterialTheme.finances.income)
            TrendFigure(stringResource(R.string.expenses), CurrencyFormatter.format(current.expenseMinor, currency), MaterialTheme.finances.expense)
            TrendFigure(
                stringResource(R.string.net_short), CurrencyFormatter.formatSigned(current.balanceMinor, currency),
                if (current.balanceMinor >= 0) MaterialTheme.finances.income else MaterialTheme.finances.expense
            )
        }
        Spacer(Modifier.height(Spacing.l))
        GroupedBarChart(
            labels = labels,
            first = trends.map { it.incomeMinor.toFloat() },
            second = trends.map { it.expenseMinor.toFloat() },
            firstColor = MaterialTheme.finances.income,
            secondColor = MaterialTheme.finances.expense,
            selectedIndex = picked,
            onSelect = { picked = it },
            contentDescription = trends.joinToString("; ") {
                context.getString(R.string.trend_point, DateFormatter.monthShort(YearMonth.parse(it.month)), CurrencyFormatter.format(it.incomeMinor, currency), CurrencyFormatter.format(it.expenseMinor, currency))
            }
        )
        Spacer(Modifier.height(Spacing.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Legend(stringResource(R.string.income), MaterialTheme.finances.income)
            Legend(stringResource(R.string.expenses), MaterialTheme.finances.expense)
        }
    }
}

@Composable
private fun TrendFigure(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.amount, color = color, maxLines = 1)
    }
}

@Composable
private fun Legend(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Top five categories plus the rest as "Other", each as a share bar and, when last month had spending, its change. */
@Composable
private fun CategoryBreakdownCard(
    breakdown: List<CategoryBreakdown>,
    previousTotals: Map<String, Long>,
    currency: String,
    month: YearMonth,
    modifier: Modifier = Modifier
) {
    val rest = breakdown.drop(5)
    SectionCard(modifier = modifier) {
        CardLabel(stringResource(R.string.dashboard_spending_month, DateFormatter.monthYear(month)))
        Spacer(Modifier.height(Spacing.s))
        breakdown.take(5).forEach { cat ->
            val previous = previousTotals[cat.categoryId] ?: 0L
            ShareRow(
                name = cat.categoryName,
                color = CategoryColorProvider.getColor(cat.categoryId, cat.color),
                totalMinor = cat.totalMinor,
                percentage = cat.percentage,
                currency = currency,
                // Whole-percent change on Long minor units; a category with no spending last month has no baseline.
                changePercent = if (previous > 0) ((cat.totalMinor - previous) * 100 / previous).toInt() else null
            )
        }
        if (rest.isNotEmpty()) {
            ShareRow(
                name = stringResource(R.string.chart_other),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                totalMinor = rest.sumOf { it.totalMinor },
                percentage = rest.sumOf { it.percentage.toDouble() }.toFloat(),
                currency = currency,
                changePercent = null
            )
        }
    }
}

@Composable
private fun ShareRow(name: String, color: androidx.compose.ui.graphics.Color, totalMinor: Long, percentage: Float, currency: String, changePercent: Int?) {
    val shown = animatedFraction((percentage / 100f).coerceIn(0f, 1f), "categoryShare")
    Column(Modifier.padding(vertical = Spacing.s).semantics(mergeDescendants = true) {}) {
        // The figure is measured first and never truncated; the name takes what is left (two
        // lines) or the figure moves under it at a large font scale.
        AmountRowLayout(
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(8.dp))
                    Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
            amount = {
                AmountText(
                    "${CurrencyFormatter.format(totalMinor, currency)} · ${CurrencyFormatter.formatPercent(percentage)}",
                    style = MaterialTheme.typography.amount
                )
            }
        )
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(color))
        }
        if (changePercent != null) {
            Text(
                stringResource(R.string.category_vs_last_month, (if (changePercent > 0) "+" else "") + "$changePercent%"),
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
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
