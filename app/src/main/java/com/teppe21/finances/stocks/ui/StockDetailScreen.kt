package com.teppe21.finances.stocks.ui

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
import com.teppe21.finances.stocks.domain.userMessage
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                listOfNotNull(row?.stock?.name, row?.stock?.exchange, currency?.let { "Trades in $it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val quote = row?.quote
        SectionCard {
            if (quote == null) {
                Text("No quote available yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(StockFormat.price(quote.price, currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${StockFormat.change(quote.change)}  ${StockFormat.percent(quote.changePercent)} vs previous close",
                    color = changeColor(quote.changePercent),
                    style = MaterialTheme.typography.bodyMedium
                )
                StatusChip(row.status, StockFormat.time(quote.quoteTime, zone))
                Text(StockFormat.statusDescription(row.status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Fact("Open", quote.open?.let { StockFormat.price(it, currency) })
                Fact("High", quote.high?.let { StockFormat.price(it, currency) })
                Fact("Low", quote.low?.let { StockFormat.price(it, currency) })
                Fact("Previous close", quote.previousClose?.let { StockFormat.price(it, currency) })
                Fact("Volume", null)
            }
            Spacer(Modifier.height(6.dp))
            Text(sessionText(usListing, state.market?.isOpen, state.market?.session, state.market?.holiday), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = viewModel::refresh) { Text("Refresh quote") }
        }

        SectionCard(title = "Price history") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HistoryRange.values().forEach { r ->
                    FilterChip(selected = state.range == r, onClick = { viewModel.selectRange(r) }, label = { Text(r.label) })
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
                            "Your Finnhub plan does not include historical prices for this symbol, so no chart can be drawn. " +
                                "The app does not estimate or fill in missing prices."
                        MarketDataError.NoData -> "The provider has no prices for this period (for example, a weekend or holiday for 1D)."
                        null -> "No history loaded."
                        else -> error.userMessage()
                    }
                    InfoBanner(text)
                }
            }
        }

        Text(STOCKS_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { confirmRemove = true }) { Text("Remove from watchlist") }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove $symbol?") },
            text = { Text("The symbol and its cached quote are removed from this device. Your financial records are not affected.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; viewModel.remove(onRemoved) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
        )
    }
}

private fun sessionText(usListing: Boolean, isOpen: Boolean?, session: String?, holiday: String?): String = when {
    !usListing -> "Trading session status is only available for US listings; the label above is based on the quote time."
    holiday != null -> "US market closed for $holiday."
    isOpen == true -> "US market open" + (session?.let { " ($it session)" } ?: "") + ". Quotes are refreshed about once a minute while this app is open."
    isOpen == false -> "US market closed. The price shown is from the last trade the provider reported."
    else -> "US market status unknown."
}

@Composable
private fun Fact(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value ?: "Not provided", style = MaterialTheme.typography.bodyMedium)
    }
}

/** Line chart of real closing prices returned by the provider; nothing is interpolated or invented. */
@Composable
private fun HistoryChart(history: PriceHistory, currency: String?, zone: ZoneId) {
    val points = history.points
    if (points.size < 2) {
        InfoBanner("Only one price point was returned for this period.")
        return
    }
    val first = points.first().close
    val last = points.last().close
    val min = points.minOf { it.close }
    val max = points.maxOf { it.close }
    val changePct = if (first.signum() != 0) last.subtract(first).divide(first, MathContext.DECIMAL64).multiply(BigDecimal(100)) else null
    val pattern = if (history.range == HistoryRange.DAY_1 || history.range == HistoryRange.WEEK_1) "d MMM HH:mm" else "d MMM yyyy"
    val fmt = DateTimeFormatter.ofPattern(pattern, Locale.US)
    val lineColor = changeColor(changePct)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val span = max.subtract(min).toDouble().takeIf { it > 0 } ?: 1.0

    Text(
        "${StockFormat.percent(changePct?.setScale(2, RoundingMode.HALF_UP))} over ${history.range.label} " +
            "(${points.first().time.atZone(zone).format(fmt)} to ${points.last().time.atZone(zone).format(fmt)})",
        style = MaterialTheme.typography.bodyMedium,
        color = lineColor
    )
    Spacer(Modifier.height(8.dp))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics {
                contentDescription = "Price chart for ${history.range.label}: from ${StockFormat.price(first, currency)} " +
                    "to ${StockFormat.price(last, currency)}, low ${StockFormat.price(min, currency)}, high ${StockFormat.price(max, currency)}"
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
        Text("Low ${StockFormat.price(min, currency)}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Text("High ${StockFormat.price(max, currency)}", style = MaterialTheme.typography.labelSmall)
    }
    Text(
        "${points.size} data points from ${history.symbol}. Times shown in ${zone.id}.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
