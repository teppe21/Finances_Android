package com.teppe21.finances.feature.analytics

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teppe21.finances.R
import com.teppe21.finances.core.theme.finances
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
                    "${state.stats.unconvertedCount} transactions are not included: no exchange rate to ${state.currency} is available.",
                    modifier = Modifier.animateItemPlacement(Motion.medium())
                )
            }
        }

        // Headline Metrics Grid
        item(key = "headline") {
            Card(
                modifier = Modifier.fillMaxWidth().animateItemPlacement(Motion.medium()),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.analytics_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.net_balance), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = CurrencyFormatter.formatSigned(state.stats.balanceMinor, state.currency),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (state.stats.balanceMinor >= 0) MaterialTheme.finances.income else MaterialTheme.finances.expense
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.avg_expense), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = CurrencyFormatter.format(state.stats.avgExpenseMinor, state.currency),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.largest_expense), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val maxName = state.stats.maxExpenseItem.description.ifBlank { "-" }
                        Text(
                            text = if (state.stats.maxExpenseItem.amountMinor > 0) "$maxName: ${CurrencyFormatter.format(state.stats.maxExpenseItem.amountMinor, state.currency)}" else "-",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.transfer_volume), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = CurrencyFormatter.format(state.stats.transfersMinor, state.currency),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Interactive Donut Chart & Category Breakdown
        item(key = "donut") {
            SpendingDonutChartCard(
                modifier = Modifier.animateItemPlacement(Motion.medium()),
                breakdown = state.categoryBreakdown,
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

private val MONTH_TITLE = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.US)

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
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Crossfade(
            targetState = month.format(MONTH_TITLE),
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
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
        TextButton(onClick = onCurrent, enabled = !isCurrent) { Text("This month") }
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
                label = { Text("Month") },
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
    val context = LocalContext.current
    val selectedItem = breakdown.find { it.categoryId == selectedCategoryId }
    // The ring draws in clockwise whenever the data set changes; the figures never animate.
    val reveal = rememberReveal(breakdown to totalExpenseMinor)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.spending_distribution),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (selectedCategoryId != null) {
                    Text(
                        text = stringResource(R.string.tap_to_filter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (breakdown.isEmpty() || totalExpenseMinor <= 0L) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_analytics_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Interactive Compose Canvas Donut Chart
                val slices = remember(breakdown, totalExpenseMinor) {
                    var currentStart = -90f
                    breakdown.map { item ->
                        val sweep = (item.totalMinor.toFloat() / totalExpenseMinor.toFloat()) * 360f
                        val sliceData = Triple(item, currentStart, sweep)
                        currentStart += sweep
                        sliceData
                    }
                }

                Box(
                    modifier = Modifier.size(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(slices) {
                                detectTapGestures { offset ->
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val dx = offset.x - cx
                                    val dy = offset.y - cy
                                    val dist = sqrt(dx * dx + dy * dy)
                                    val outerRadius = size.width / 2f

                                    // Inner hole ~ 50% radius
                                    if (dist < outerRadius * 0.45f) {
                                        // Tap in center clears selection
                                        if (selectedCategoryId != null) {
                                            onCategoryClick(selectedCategoryId)
                                        }
                                    } else if (dist <= outerRadius * 1.1f) {
                                        // Calculate angle in degrees [0, 360) starting from -90° (top)
                                        var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                        // Adjust to start at -90
                                        var touchAngle = (angleDeg + 90f + 360f) % 360f

                                        for ((cat, start, sweep) in slices) {
                                            val normalizedStart = (start + 90f + 360f) % 360f
                                            val sliceEnd = normalizedStart + sweep
                                            if (sliceEnd <= 360f) {
                                                if (touchAngle >= normalizedStart && touchAngle < sliceEnd) {
                                                    onCategoryClick(cat.categoryId)
                                                    break
                                                }
                                            } else {
                                                // Wraps around 360
                                                if (touchAngle >= normalizedStart || touchAngle < (sliceEnd % 360f)) {
                                                    onCategoryClick(cat.categoryId)
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        val strokeWidth = 32.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeftOffset = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f)
                        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

                        val revealedDegrees = 360f * reveal.value
                        slices.forEach { (cat, start, sweep) ->
                            val visibleSweep = (revealedDegrees - (start + 90f)).coerceIn(0f, sweep)
                            if (visibleSweep <= 0f) return@forEach
                            val isSelected = cat.categoryId == selectedCategoryId
                            val color = CategoryColorProvider.getColor(cat.categoryId, cat.color)
                            val currentStroke = if (isSelected) strokeWidth * 1.25f else strokeWidth

                            drawArc(
                                color = color,
                                startAngle = start + 0.75f,
                                sweepAngle = (visibleSweep - 1.5f).coerceAtLeast(0.5f),
                                useCenter = false,
                                topLeft = topLeftOffset,
                                size = arcSize,
                                style = Stroke(width = currentStroke, cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Centered Hole Text
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(24.dp)
                            .clickable {
                                if (selectedCategoryId != null) onCategoryClick(selectedCategoryId)
                            }
                    ) {
                        if (selectedItem != null) {
                            val catColor = CategoryColorProvider.getColor(selectedItem.categoryId, selectedItem.color)
                            Text(
                                text = selectedItem.categoryName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = CurrencyFormatter.format(selectedItem.totalMinor, currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${"%.1f".format(selectedItem.percentage)}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = catColor
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.expenses),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = CurrencyFormatter.format(totalExpenseMinor, currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.tap_to_filter),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Category Breakdown list with tap selection
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    breakdown.forEach { item ->
                        val isSelected = item.categoryId == selectedCategoryId
                        val color = CategoryColorProvider.getColor(item.categoryId, item.color)
                        val name = item.categoryName

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else Color.Transparent
                                )
                                .clickable { onCategoryClick(item.categoryId) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    val txSuffix = if (item.transactionCount == 1) "transaction" else "transactions"
                                    Text(
                                        text = "${item.transactionCount} $txSuffix • ${"%.1f".format(item.percentage)}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = CurrencyFormatter.format(item.totalMinor, currency),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopMerchantsCard(
    merchants: List<com.teppe21.finances.feature.analytics.MerchantSpending>,
    currency: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.top_merchants),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            merchants.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(24.dp)
                        )
                        Column {
                            Text(
                                text = item.merchant,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val txSuffix = if (item.count == 1) "transaction" else "transactions"
                            Text(
                                text = "${item.count} $txSuffix",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = CurrencyFormatter.format(item.totalMinor, currency),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
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
