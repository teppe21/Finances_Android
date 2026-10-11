package com.teppe21.finances.stocks.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.teppe21.finances.core.ui.Motion
import com.teppe21.finances.core.ui.pressClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teppe21.finances.core.ui.EmptyState
import com.teppe21.finances.core.ui.InfoBanner
import com.teppe21.finances.stocks.domain.DataStatus
import com.teppe21.finances.stocks.domain.MarketDataError
import com.teppe21.finances.stocks.domain.RefreshPolicy
import com.teppe21.finances.stocks.domain.WatchlistRow
import com.teppe21.finances.stocks.domain.userMessage

const val STOCKS_DISCLAIMER =
    "Quotes come from Finnhub and may be delayed. This is market information for monitoring only, not investment advice."

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StocksScreen(
    onOpenStock: (String) -> Unit,
    onOpenSetup: () -> Unit,
    viewModel: StocksViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val search by viewModel.search.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Poll only while this screen is visible; leaving it or backgrounding the app cancels the loop.
    LaunchedEffect(lifecycleOwner, state.hasKey) {
        if (state.hasKey) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.autoRefreshWhileVisible()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        if (state.loaded && !state.hasKey) {
            item {
                EmptyState(
                    title = "Connect a market data provider",
                    body = "Stock quotes come from Finnhub and need your own free or paid Finnhub API key. " +
                        "The rest of the app works without it.",
                    action = { Button(onClick = onOpenSetup) { Text("Set up API key") } }
                )
            }
        }

        if (state.hasKey) {
            item {
                OutlinedTextField(
                    value = search.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search symbol or company") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (search.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Close, contentDescription = "Clear search") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
            search.message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
            if (search.searching) item { CircularProgressIndicator(Modifier.size(24.dp)) }
            search.error?.let { item { InfoBanner(it.userMessage()) } }
            if (search.query.isNotBlank() && !search.searching && search.error == null && search.results.isEmpty()) {
                item { Text("No matching symbols.", style = MaterialTheme.typography.bodySmall) }
            }
            items(search.results, key = { "s_" + it.symbol }) { result ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.add(result) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(result.displaySymbol, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(result.name, result.type).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Default.Add, contentDescription = "Add ${result.displaySymbol} to watchlist")
                }
                HorizontalDivider()
            }

            item {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Watchlist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(marketLine(state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.refreshing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::manualRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh quotes") }
                    }
                }
            }

            state.error?.let { error ->
                item {
                    InfoBanner(
                        text = error.userMessage() + if (error == MarketDataError.InvalidKey) " Tap to update it." else "",
                        onClick = if (error == MarketDataError.InvalidKey) onOpenSetup else null
                    )
                }
            }

            if (state.rows.isEmpty()) {
                item {
                    EmptyState(
                        title = "Your watchlist is empty",
                        body = "Search for a stock above and tap it to follow its price. Up to ${RefreshPolicy.MAX_WATCHLIST_SIZE} symbols."
                    )
                }
            }

            items(state.rows, key = { it.stock.symbol }) { row ->
                WatchlistRowItem(
                    row = row,
                    onClick = { onOpenStock(row.stock.symbol) },
                    onUp = { viewModel.move(row.stock.symbol, up = true) },
                    onDown = { viewModel.move(row.stock.symbol, up = false) },
                    modifier = Modifier.animateItemPlacement(Motion.medium())
                )
            }
        }

        item {
            Text(
                STOCKS_DISCLAIMER + " Watched stocks are not added to your accounts or balances.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        if (state.hasKey) {
            item { TextButton(onClick = onOpenSetup) { Text("Market data settings") } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun marketLine(state: StocksUiState): String {
    val market = state.market ?: return "US market status not loaded yet"
    val session = when {
        market.isOpen == true -> "US market open" + (market.session?.let { " ($it)" } ?: "")
        market.holiday != null -> "US market closed (${market.holiday})"
        market.isOpen == false -> "US market closed" + (market.session?.let { ", $it session" } ?: "")
        else -> "US market status unknown"
    }
    val refreshed = state.lastRefresh?.let { " · updated " + StockFormat.time(it, java.time.ZoneId.systemDefault()) } ?: ""
    return session + refreshed
}

@Composable
private fun WatchlistRowItem(row: WatchlistRow, onClick: () -> Unit, onUp: () -> Unit, onDown: () -> Unit, modifier: Modifier = Modifier) {
    val quote = row.quote
    val stock = row.stock
    val description = buildString {
        append(stock.displaySymbol)
        stock.name?.let { append(", $it") }
        if (quote != null) {
            append(", ${StockFormat.price(quote.price, stock.currency)}, ${StockFormat.percent(quote.changePercent)} today")
        }
        append(", ${row.status.label}")
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .pressClickable(onClick = onClick)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stock.displaySymbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(stock.name, stock.exchange).joinToString(" · ").ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusChip(row.status, quote?.quoteTime?.let { StockFormat.time(it, java.time.ZoneId.systemDefault()) })
            }
            Column(horizontalAlignment = Alignment.End) {
                if (quote != null) {
                    Text(StockFormat.price(quote.price, stock.currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${StockFormat.change(quote.change)}  ${StockFormat.percent(quote.changePercent)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = changeColor(quote.changePercent)
                    )
                } else {
                    Text("No price", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column {
                IconButton(onClick = onUp, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${stock.displaySymbol} up") }
                IconButton(onClick = onDown, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${stock.displaySymbol} down") }
            }
        }
    }
}

@Composable
fun StatusChip(status: DataStatus, time: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Text(status.label, style = MaterialTheme.typography.labelSmall, color = statusColor(status), fontWeight = FontWeight.SemiBold)
        if (time != null) {
            Spacer(Modifier.width(6.dp))
            Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
