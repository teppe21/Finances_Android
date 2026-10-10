package com.teppe21.finances.stocks.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import com.teppe21.finances.stocks.domain.DataStatus
import com.teppe21.finances.core.theme.finances
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object StockFormat {

    /**
     * Prices keep the provider's precision up to 4 decimals and at least 2; the trading
     * currency is shown as an ISO code because many symbols ($) are ambiguous.
     */
    fun price(value: BigDecimal, currency: String?): String {
        val scale = value.stripTrailingZeros().scale().coerceIn(2, 4)
        val number = String.format(Locale.US, "%,.${scale}f", value.setScale(scale, RoundingMode.HALF_UP))
        return if (currency.isNullOrBlank()) number else "$currency $number"
    }

    fun change(value: BigDecimal?): String {
        if (value == null) return "n/a"
        val scale = value.stripTrailingZeros().scale().coerceIn(2, 4)
        val sign = if (value.signum() > 0) "+" else if (value.signum() < 0) "-" else ""
        return sign + String.format(Locale.US, "%,.${scale}f", value.abs().setScale(scale, RoundingMode.HALF_UP))
    }

    /** "▲ +0.58%" - the arrow carries the direction for readers who cannot rely on colour. */
    fun percent(value: BigDecimal?): String {
        if (value == null) return "n/a"
        val arrow = when (value.signum()) { 1 -> "▲ "; -1 -> "▼ "; else -> "" }
        val sign = if (value.signum() > 0) "+" else if (value.signum() < 0) "-" else ""
        return arrow + sign + value.abs().setScale(2, RoundingMode.HALF_UP).toPlainString() + "%"
    }

    fun time(instant: Instant, zone: ZoneId): String =
        instant.atZone(zone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm z", Locale.US))

    fun zoneFor(timezone: String?): ZoneId =
        timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

    fun statusDescription(status: DataStatus): String = when (status) {
        DataStatus.LIVE -> "Price from the last two minutes during an open session"
        DataStatus.DELAYED -> "Price is several minutes old"
        DataStatus.LAST_CLOSE -> "Market closed; last traded price"
        DataStatus.STALE -> "Price is old; it may not reflect the market"
        DataStatus.UNAVAILABLE -> "No price available"
        DataStatus.API_KEY_REQUIRED -> "Add an API key to load prices"
        DataStatus.API_KEY_INVALID -> "The API key was rejected"
        DataStatus.RATE_LIMITED -> "Provider limit reached; updates paused"
        DataStatus.NOT_ON_PLAN -> "Your provider plan does not include this data"
    }
}

@Composable
fun changeColor(value: BigDecimal?): Color = when {
    value == null || value.signum() == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
    value.signum() > 0 -> MaterialTheme.finances.marketUp
    else -> MaterialTheme.finances.marketDown
}

@Composable
fun statusColor(status: DataStatus): Color = when (status) {
    DataStatus.LIVE -> MaterialTheme.finances.marketUp
    DataStatus.DELAYED, DataStatus.LAST_CLOSE -> MaterialTheme.colorScheme.onSurfaceVariant
    DataStatus.STALE, DataStatus.RATE_LIMITED -> MaterialTheme.finances.warning
    else -> MaterialTheme.colorScheme.error
}
