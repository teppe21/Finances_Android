package com.teppe21.finances.feature.analytics

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import com.teppe21.finances.domain.model.FinancialStats
import com.teppe21.finances.core.ui.SectionCard
import com.teppe21.finances.core.ui.ValueRow
import com.teppe21.finances.core.ui.ListRow
import com.teppe21.finances.core.ui.AmountText
import com.teppe21.finances.core.theme.amountLarge
import com.teppe21.finances.core.theme.Spacing
import com.teppe21.finances.core.theme.Radii
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import com.teppe21.finances.feature.categories.categoryLabel
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teppe21.finances.R
import com.teppe21.finances.core.theme.finances
import com.teppe21.finances.core.theme.tabular
import com.teppe21.finances.core.ui.InfoBanner
import com.teppe21.finances.core.ui.Motion
import com.teppe21.finances.core.ui.rememberReveal
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.TextButton
import com.teppe21.finances.core.theme.CategoryColorProvider
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.core.utils.DateFormatter
import com.teppe21.finances.domain.model.CategoryBreakdown
import com.teppe21.finances.feature.dashboard.MonthlyTrendsChartCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.atan2
import kotlin.math.sqrt


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val resources = LocalContext.current.resources

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Period filter chips
        item {
            PeriodFilterRow(
                currentPeriod = state.period,
                onSelectPeriod = { viewModel.selectPeriod(it) },
                customStartDate = state.customStartDate,
                customEndDate = state.customEndDate,
                onOpenCustomDialog = { viewModel.openCustomDateDialog() }
            )
        }

        if (state.period == AnalyticsPeriod.MONTH) {
            item(key = "month-navigator") {
                MonthNavigator(
                    modifier = Modifier.animateItemPlacement(Motion.medium()),
                    month = state.selectedMonth,
                    onPrevious = { viewModel.shiftMonth(-1) },
                    onNext = { viewModel.shiftMonth(1) },
                    onCurrent = { viewModel.goToCurrentMonth() }
                )
            }
        }

        if (state.stats.unconvertedCount > 0) {
            item(key = "unconverted-banner") {
                InfoBanner(
                    pluralStringResource(R.plurals.unconverted_banner, state.stats.unconvertedCount, state.stats.unconvertedCount, state.currency),
                    modifier = Modifier.animateItemPlacement(Motion.medium())
                )
            }
        }

        // Headline Metrics Grid
        item(key = "headline") {
            SectionCard(title = stringResource(R.string.analytics_summary), modifier = Modifier.animateItemPlacement(Motion.medium())) {
                ValueRow(stringResource(R.string.income), CurrencyFormatter.format(state.stats.incomeMinor, state.currency), valueColor = MaterialTheme.finances.income)
                ValueRow(stringResource(R.string.expenses), CurrencyFormatter.format(state.stats.expenseMinor, state.currency), valueColor = MaterialTheme.finances.expense)
                ValueRow(
                    stringResource(R.string.net_balance),
                    CurrencyFormatter.formatSigned(state.stats.balanceMinor, state.currency),
                    valueColor = if (state.stats.balanceMinor >= 0) MaterialTheme.finances.income else MaterialTheme.finances.expense
                )
                HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
                ValueRow(stringResource(R.string.avg_expense), CurrencyFormatter.format(state.stats.avgExpenseMinor, state.currency))
                val largest = state.stats.maxExpenseItem
                if (largest.amountMinor > 0) {
                    ValueRow(
                        stringResource(R.string.largest_expense),
                        CurrencyFormatter.format(largest.amountMinor, state.currency),
                        detail = largest.description
                    )
                }
            }
        }

        // Transfers stay visible but apart: they are never income, spending or a merchant.
        if (state.stats.transferCount + state.stats.personalCount > 0) {
            item(key = "transfers") {
                TransfersCard(stats = state.stats, currency = state.currency, modifier = Modifier.animateItemPlacement(Motion.medium()))
            }
        }

        // Interactive Donut Chart & Category Breakdown
        item(key = "donut") {
            SpendingDonutChartCard(
                modifier = Modifier.animateItemPlacement(Motion.medium()),
                breakdown = state.categoryBreakdown.map { b ->
                    state.categories.firstOrNull { it.id == b.categoryId }?.let { b.copy(categoryName = categoryLabel(it, resources)) } ?: b
                },
                totalExpenseMinor = state.stats.expenseMinor,
                selectedCategoryId = state.selectedCategoryId,
                currency = state.currency,
                onCategoryClick = { viewModel.toggleCategorySelection(it) }
            )
        }

        // Top Merchants Card
        if (state.topMerchants.isNotEmpty()) {
            item(key = "merchants") {
                TopMerchantsCard(
                    merchants = state.topMerchants,
                    currency = state.currency,
                    modifier = Modifier.animateItemPlacement(Motion.medium())
                )
            }
        }

        // Monthly trends bar chart
        if (state.monthlyTrends.isNotEmpty()) {
            item(key = "trends") {
                MonthlyTrendsChartCard(
                    trends = state.monthlyTrends,
                    currency = state.currency,
                    modifier = Modifier.animateItemPlacement(Motion.medium())
                )
            }
        }

        item { Spacer(modifier = Modifier.height(88.dp)) }
    }

    // Custom Date Range Dialog
    if (state.showCustomDateDialog) {
        CustomDateRangeDialog(
            initialStart = state.customStartDate,
            initialEnd = state.customEndDate,
            hasError = state.dateValidationError,
            onDismiss = { viewModel.closeCustomDateDialog() },
            onApply = { start, end -> viewModel.applyCustomDateRange(start, end) }
        )
    }
}



@Composable
fun MonthNavigator(
    month: java.time.YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCurrent = month == java.time.YearMonth.now()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_month))
        }
        Crossfade(
            targetState = DateFormatter.monthYear(month),
            animationSpec = Motion.short(),
            modifier = Modifier.weight(1f),
            label = "monthTitle"
        ) { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        IconButton(onClick = onNext, enabled = !isCurrent) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
        }
        TextButton(onClick = onCurrent, enabled = !isCurrent) { Text(stringResource(R.string.filter_this_month)) }
    }
}

@Composable
fun PeriodFilterRow(
    currentPeriod: AnalyticsPeriod,
    onSelectPeriod: (AnalyticsPeriod) -> Unit,
    customStartDate: LocalDate,
    customEndDate: LocalDate,
    onOpenCustomDialog: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentPeriod == AnalyticsPeriod.MONTH,
                onClick = { onSelectPeriod(AnalyticsPeriod.MONTH) },
                label = { Text(stringResource(R.string.period_month)) },
                colors = FilterChipDefaults.filterChipColors()
            )
            FilterChip(
                selected = currentPeriod == AnalyticsPeriod.LAST_3_MONTHS,
                onClick = { onSelectPeriod(AnalyticsPeriod.LAST_3_MONTHS) },
                label = { Text(stringResource(R.string.period_3_months)) }
            )
            FilterChip(
                selected = currentPeriod == AnalyticsPeriod.LAST_6_MONTHS,
                onClick = { onSelectPeriod(AnalyticsPeriod.LAST_6_MONTHS) },
                label = { Text(stringResource(R.string.period_6_months)) }
            )
            FilterChip(
                selected = currentPeriod == AnalyticsPeriod.THIS_YEAR,
                onClick = { onSelectPeriod(AnalyticsPeriod.THIS_YEAR) },
                label = { Text(stringResource(R.string.period_this_year)) }
            )
            FilterChip(
                selected = currentPeriod == AnalyticsPeriod.LAST_YEAR,
                onClick = { onSelectPeriod(AnalyticsPeriod.LAST_YEAR) },
                label = { Text(stringResource(R.string.period_last_year)) }
            )
            FilterChip(
                selected = currentPeriod == AnalyticsPeriod.CUSTOM,
                onClick = { onSelectPeriod(AnalyticsPeriod.CUSTOM) },
                label = { Text(stringResource(R.string.period_custom)) }
            )
        }

        if (currentPeriod == AnalyticsPeriod.CUSTOM) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCustomDialog() }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${DateFormatter.format(customStartDate)}  –  ${DateFormatter.format(customEndDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onOpenCustomDialog, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = stringResource(R.string.custom_date_range),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SpendingDonutChartCard(
    breakdown: List<CategoryBreakdown>,
    totalExpenseMinor: Long,
    selectedCategoryId: String?,
    currency: String,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedItem = breakdown.find { it.categoryId == selectedCategoryId }
    // The ring draws in clockwise whenever the data set changes; the figures never animate.
    val reveal = rememberReveal(breakdown to totalExpenseMinor)
    // The hint is shown until the first tap on a category; after that it has done its job.
    var hintSeen by rememberSaveable { mutableStateOf(false) }
    val select: (String) -> Unit = { id ->
        hintSeen = true
        onCategoryClick(id)
    }

    SectionCard(
        modifier = modifier,
        title = stringResource(R.string.spending_distribution),
        subtitle = if (!hintSeen && breakdown.size > 1) stringResource(R.string.tap_to_filter) else null
    ) {
        if (breakdown.isEmpty() || totalExpenseMinor <= 0L) {
            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_analytics_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@SectionCard
        }

        val segments = remember(breakdown) { donutSegments(breakdown.map { it.totalMinor }) }
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = Spacing.s)
                .size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(segments, selectedCategoryId) {
                        detectTapGestures { offset ->
                            val dx = offset.x - size.width / 2f
                            val dy = offset.y - size.height / 2f
                            val dist = sqrt(dx * dx + dy * dy)
                            val outer = size.width / 2f
                            if (dist < outer * 0.6f) {
                                // The centre clears the selection.
                                selectedCategoryId?.let(select)
                            } else if (dist <= outer) {
                                val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                segments.indexAt(angle)?.let { select(breakdown[it].categoryId) }
                            }
                        }
                    }
            ) {
                val stroke = 28.dp.toPx()
                val diameter = size.minDimension - stroke * 1.3f
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                val revealed = 360f * reveal.value
                segments.forEachIndexed { i, segment ->
                    val visible = (revealed - segment.start).coerceIn(0f, segment.sweep)
                    if (visible <= 0f) return@forEachIndexed
                    val item = breakdown[i]
                    val dimmed = selectedCategoryId != null && item.categoryId != selectedCategoryId
                    drawArc(
                        color = CategoryColorProvider.getColor(item.categoryId, item.color).copy(alpha = if (dimmed) 0.35f else 1f),
                        startAngle = segment.start - 90f,
                        sweepAngle = visible,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        // Flat ends: round caps bulged over the neighbours and overstated small shares.
                        style = Stroke(width = if (item.categoryId == selectedCategoryId) stroke * 1.3f else stroke, cap = StrokeCap.Butt)
                    )
                }
            }

            // The centre shows the total, or the selected category; tapping it clears the selection.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 140.dp)
                    .clip(CircleShape)
                    .clickable(enabled = selectedCategoryId != null) { selectedCategoryId?.let(select) }
                    .padding(Spacing.s)
            ) {
                Text(
                    text = selectedItem?.categoryName ?: stringResource(R.string.expenses),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                // The hole is narrow: a long total steps down in size instead of being cut.
                val centreText = CurrencyFormatter.format(selectedItem?.totalMinor ?: totalExpenseMinor, currency)
                val measurer = rememberTextMeasurer()
                val large = MaterialTheme.typography.amountLarge
                val fits = with(LocalDensity.current) { measurer.measure(centreText, large).size.width.toDp() } <= 124.dp
                AmountText(centreText, style = if (fits) large else MaterialTheme.typography.labelLarge.tabular())
                if (selectedItem != null) {
                    Text(
                        CurrencyFormatter.formatPercent(selectedItem.percentage),
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.s))
        breakdown.forEach { item ->
            val isSelected = item.categoryId == selectedCategoryId
            val count = pluralStringResource(R.plurals.transaction_count, item.transactionCount, item.transactionCount)
            ListRow(
                title = item.categoryName,
                subtitle = "$count · ${CurrencyFormatter.formatPercent(item.percentage)}",
                amount = CurrencyFormatter.format(item.totalMinor, currency),
                titleWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier
                    .clip(Radii.control)
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Button) { select(item.categoryId) }
                    .padding(horizontal = Spacing.s),
                leading = {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(CategoryColorProvider.getColor(item.categoryId, item.color)))
                }
            )
        }
    }
}

@Composable
fun TopMerchantsCard(
    merchants: List<MerchantSpending>,
    currency: String,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier, title = stringResource(R.string.top_merchants)) {
        merchants.forEachIndexed { index, item ->
            ListRow(
                title = item.merchant,
                subtitle = pluralStringResource(R.plurals.transaction_count, item.count, item.count),
                amount = CurrencyFormatter.format(item.totalMinor, currency),
                leading = {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 20.dp)
                    )
                }
            )
        }
    }
}

/** Own-account moves and money exchanged with people, kept out of every spending figure. */
@Composable
private fun TransfersCard(stats: FinancialStats, currency: String, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier, title = stringResource(R.string.analytics_transfers_title), subtitle = stringResource(R.string.analytics_transfers_desc)) {
        if (stats.transferCount > 0) {
            ValueRow(stringResource(R.string.analytics_own_transfers), CurrencyFormatter.format(stats.transfersMinor, currency))
        }
        if (stats.personalCount > 0) {
            ValueRow(stringResource(R.string.analytics_people_sent), CurrencyFormatter.format(stats.personalSentMinor, currency))
            ValueRow(stringResource(R.string.analytics_people_received), CurrencyFormatter.format(stats.personalReceivedMinor, currency))
        }
    }
}

@Composable
fun CustomDateRangeDialog(
    initialStart: LocalDate,
    initialEnd: LocalDate,
    hasError: Boolean,
    onDismiss: () -> Unit,
    onApply: (LocalDate, LocalDate) -> Unit
) {
    var startStr by remember { mutableStateOf(initialStart.format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    var endStr by remember { mutableStateOf(initialEnd.format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    var parseError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_date_range)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = startStr,
                    onValueChange = {
                        startStr = it
                        parseError = false
                    },
                    label = { Text(stringResource(R.string.start_date) + " (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = endStr,
                    onValueChange = {
                        endStr = it
                        parseError = false
                    },
                    label = { Text(stringResource(R.string.end_date) + " (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (hasError || parseError) {
                    Text(
                        text = stringResource(R.string.invalid_date_range),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val start = LocalDate.parse(startStr.trim())
                        val end = LocalDate.parse(endStr.trim())
                        onApply(start, end)
                    } catch (_: Exception) {
                        parseError = true
                    }
                }
            ) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

