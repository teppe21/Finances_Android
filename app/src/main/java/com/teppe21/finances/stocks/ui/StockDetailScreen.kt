package com.teppe21.finances.stocks.ui

import com.teppe21.finances.core.utils.DateFormatter
import com.teppe21.finances.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teppe21.finances.core.ui.InfoBanner
import com.teppe21.finances.core.ui.SectionCard
import com.teppe21.finances.stocks.domain.HistoryRange
import com.teppe21.finances.stocks.domain.MarketDataError
import com.teppe21.finances.stocks.domain.PriceHistory
import com.teppe21.finances.stocks.domain.RefreshPolicy
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.ZoneId

@Composable
fun StockDetailScreen(symbol: String, onRemoved: () -> Unit, viewModel: StockDetailViewModel = viewModel()) {
    LaunchedEffect(symbol) { viewModel.load(symbol) }
    val state by viewModel.state.collectAsState()
    var confirmRemove by remember { mutableStateOf(false) }
    val row = state.row
    val currency = row?.stock?.currency
    val usListing = RefreshPolicy.usesUsMarketStatus(symbol)
    val zone = if (usListing) StockFormat.zoneFor(state.market?.timezone ?: "America/New_York") else ZoneId.systemDefault()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        Column {
            Text(row?.stock?.displaySymbol ?: symbol, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(row?.stock?.name, row?.stock?.exchange, currency?.let { stringResource(R.string.stock_trades_in, it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val quote = row?.quote
        SectionCard {
            if (quote == null) {
                Text(stringResource(R.string.stock_no_quote), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(StockFormat.price(quote.price, currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.stock_vs_previous_close, StockFormat.change(quote.change), StockFormat.percent(quote.changePercent)),
                    color = changeColor(quote.changePercent),
                    style = MaterialTheme.typography.bodyMedium
                )
                StatusChip(row.status, StockFormat.time(quote.quoteTime, zone))
                Text(stringResource(row.status.descriptionRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Fact(stringResource(R.string.stock_open), quote.open?.let { StockFormat.price(it, currency) })
                Fact(stringResource(R.string.stock_high), quote.high?.let { StockFormat.price(it, currency) })
                Fact(stringResource(R.string.stock_low), quote.low?.let { StockFormat.price(it, currency) })
                Fact(stringResource(R.string.stock_previous_close), quote.previousClose?.let { StockFormat.price(it, currency) })
                Fact(stringResource(R.string.stock_volume), null)
            }
            Spacer(Modifier.height(6.dp))
            Text(sessionText(usListing, state.market?.isOpen, state.market?.session, state.market?.holiday), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.stock_refresh_quote)) }
        }

        SectionCard(title = stringResource(R.string.stock_price_history)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HistoryRange.values().forEach { r ->
                    FilterChip(selected = state.range == r, onClick = { viewModel.selectRange(r) }, label = { Text(stringResource(r.labelRes())) })
                }
            }
            Spacer(Modifier.height(10.dp))
            val history = state.history
            when {
                state.historyLoading -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                history != null -> HistoryChart(history, currency, zone)
                else -> {
                    val error = state.historyError
                    val text = when (error) {
                        MarketDataError.NotOnPlan ->
                            stringResource(R.string.stock_history_not_on_plan)
                        MarketDataError.NoData -> stringResource(R.string.stock_history_no_data)
                        null -> stringResource(R.string.stock_history_none)
                        else -> errorMessage(error)
                    }
                    InfoBanner(text)
                }
            }
        }

        Text(stringResource(R.string.stock_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { confirmRemove = true }) { Text(stringResource(R.string.stock_remove_from_watchlist)) }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.stock_remove_title, symbol)) },
            text = { Text(stringResource(R.string.stock_remove_body)) },
            confirmButton = { TextButton(onClick = { confirmRemove = false; viewModel.remove(onRemoved) }) { Text(stringResource(R.string.stock_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun sessionText(usListing: Boolean, isOpen: Boolean?, session: String?, holiday: String?): String = when {
    !usListing -> stringResource(R.string.stock_session_non_us)
    holiday != null -> stringResource(R.string.stock_session_holiday, holiday)
    isOpen == true && session != null -> stringResource(R.string.stock_session_open_named, session)
    isOpen == true -> stringResource(R.string.stock_session_open)
    isOpen == false -> stringResource(R.string.stock_session_closed)
    else -> stringResource(R.string.stock_session_unknown)
}

@Composable
private fun Fact(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value ?: stringResource(R.string.stock_not_provided), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Line chart of real closing prices returned by the provider; nothing is interpolated or invented. */
@Composable
private fun HistoryChart(history: PriceHistory, currency: String?, zone: ZoneId) {
    val points = history.points
    if (points.size < 2) {
        InfoBanner(stringResource(R.string.stock_one_point))
        return
    }
    val first = points.first().close
    val last = points.last().close
    val min = points.minOf { it.close }
    val max = points.maxOf { it.close }
    val changePct = if (first.signum() != 0) last.subtract(first).divide(first, MathContext.DECIMAL64).multiply(BigDecimal(100)) else null
    val shortRange = history.range == HistoryRange.DAY_1 || history.range == HistoryRange.WEEK_1
    fun fmt(t: java.time.Instant) = t.atZone(zone).let { if (shortRange) DateFormatter.dayTime(it) else DateFormatter.format(it.toLocalDate()) }
    val rangeLabel = stringResource(history.range.labelRes())
    val lineColor = changeColor(changePct)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val span = max.subtract(min).toDouble().takeIf { it > 0 } ?: 1.0
    val chartDescription = stringResource(R.string.stock_chart_description, rangeLabel, StockFormat.price(first, currency), StockFormat.price(last, currency), StockFormat.price(min, currency), StockFormat.price(max, currency))

    Text(
        stringResource(R.string.stock_change_over, StockFormat.percent(changePct?.setScale(2, RoundingMode.HALF_UP)), rangeLabel, fmt(points.first().time), fmt(points.last().time)),
        style = MaterialTheme.typography.bodyMedium,
        color = lineColor
    )
    Spacer(Modifier.height(8.dp))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics {
                contentDescription = chartDescription
            }
    ) {
        val w = size.width
        val h = size.height
        drawLine(gridColor, Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1f)
        drawLine(gridColor, Offset(0f, h), Offset(w, h), strokeWidth = 1f)
        val startMs = points.first().time.toEpochMilli().toDouble()
        val spanMs = (points.last().time.toEpochMilli() - points.first().time.toEpochMilli()).toDouble().takeIf { it > 0 } ?: 1.0
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = ((p.time.toEpochMilli() - startMs) / spanMs * w).toFloat()
            val y = (h - (p.close.subtract(min).toDouble() / span) * h).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3f))
    }
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.stock_low_value, StockFormat.price(min, currency)), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.stock_high_value, StockFormat.price(max, currency)), style = MaterialTheme.typography.labelSmall)
    }
    Text(
        pluralStringResource(R.plurals.stock_points, points.size, points.size, history.symbol, zone.id),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
