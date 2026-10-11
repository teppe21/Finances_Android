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
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.teppe21.finances.R
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.core.utils.DateFormatter
import com.teppe21.finances.core.utils.DisplayLocale
import com.teppe21.finances.stocks.domain.HistoryRange
import com.teppe21.finances.stocks.domain.MarketDataError

object StockFormat {

    private fun number(value: BigDecimal, scale: Int): String =
        String.format(DisplayLocale.locale, "%,.${scale}f", value.setScale(scale, RoundingMode.HALF_UP))
            .replace(' ', CurrencyFormatter.NBSP)

    /**
     * Prices keep the provider's precision up to 4 decimals and at least 2; the trading
     * currency is shown as an ISO code because many symbols ($) are ambiguous. English puts
     * the code first (`USD 187.42`), Hungarian after the number (`187,42 USD`).
     */
    fun price(value: BigDecimal, currency: String?): String {
        val scale = value.stripTrailingZeros().scale().coerceIn(2, 4)
        val number = number(value, scale)
        return when {
            currency.isNullOrBlank() -> number
            DisplayLocale.isHungarian -> "$number${CurrencyFormatter.NBSP}$currency"
            else -> "$currency${CurrencyFormatter.NBSP}$number"
        }
    }

    fun change(value: BigDecimal?): String {
        if (value == null) return "–"
        val scale = value.stripTrailingZeros().scale().coerceIn(2, 4)
        val sign = if (value.signum() > 0) "+" else if (value.signum() < 0) "-" else ""
        return sign + number(value.abs(), scale)
    }

    /** "▲ +0.58%" - the arrow carries the direction for readers who cannot rely on colour. */
    fun percent(value: BigDecimal?): String {
        if (value == null) return "–"
        val arrow = when (value.signum()) { 1 -> "▲ "; -1 -> "▼ "; else -> "" }
        val sign = if (value.signum() > 0) "+" else if (value.signum() < 0) "-" else ""
        return arrow + sign + number(value.abs(), 2) + "%"
    }

    /** "15 Sep, 14:05 EDT": the zone is shown because quotes use the exchange's time. */
    fun time(instant: Instant, zone: ZoneId): String {
        val zoned = instant.atZone(zone)
        return DateFormatter.dayTime(zoned) + " " + zoned.format(DateTimeFormatter.ofPattern("z", DisplayLocale.locale))
    }

    fun zoneFor(timezone: String?): ZoneId =
        timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
}

@StringRes
fun DataStatus.labelRes(): Int = when (this) {
    DataStatus.LIVE -> R.string.stock_status_live
    DataStatus.DELAYED -> R.string.stock_status_delayed
    DataStatus.LAST_CLOSE -> R.string.stock_status_last_close
    DataStatus.STALE -> R.string.stock_status_stale
    DataStatus.UNAVAILABLE -> R.string.stock_status_unavailable
    DataStatus.API_KEY_REQUIRED -> R.string.stock_status_key_required
    DataStatus.API_KEY_INVALID -> R.string.stock_status_key_invalid
    DataStatus.RATE_LIMITED -> R.string.stock_status_rate_limited
    DataStatus.NOT_ON_PLAN -> R.string.stock_status_not_on_plan
}

@StringRes
fun DataStatus.descriptionRes(): Int = when (this) {
    DataStatus.LIVE -> R.string.stock_status_live_desc
    DataStatus.DELAYED -> R.string.stock_status_delayed_desc
    DataStatus.LAST_CLOSE -> R.string.stock_status_last_close_desc
    DataStatus.STALE -> R.string.stock_status_stale_desc
    DataStatus.UNAVAILABLE -> R.string.stock_status_unavailable_desc
    DataStatus.API_KEY_REQUIRED -> R.string.stock_status_key_required_desc
    DataStatus.API_KEY_INVALID -> R.string.stock_status_key_invalid_desc
    DataStatus.RATE_LIMITED -> R.string.stock_status_rate_limited_desc
    DataStatus.NOT_ON_PLAN -> R.string.stock_status_not_on_plan_desc
}

@StringRes
fun HistoryRange.labelRes(): Int = when (this) {
    HistoryRange.DAY_1 -> R.string.stock_range_1d
    HistoryRange.WEEK_1 -> R.string.stock_range_1w
    HistoryRange.MONTH_1 -> R.string.stock_range_1m
    HistoryRange.MONTH_3 -> R.string.stock_range_3m
    HistoryRange.YEAR_1 -> R.string.stock_range_1y
}

@Composable
fun errorMessage(error: MarketDataError): String = when (error) {
    MarketDataError.MissingKey -> stringResource(R.string.stock_error_missing_key)
    MarketDataError.InvalidKey -> stringResource(R.string.stock_error_invalid_key)
    is MarketDataError.RateLimited -> stringResource(R.string.stock_error_rate_limited)
    MarketDataError.NotOnPlan -> stringResource(R.string.stock_error_not_on_plan)
    MarketDataError.UnknownSymbol -> stringResource(R.string.stock_error_unknown_symbol)
    MarketDataError.NoData -> stringResource(R.string.stock_error_no_data)
    MarketDataError.Offline -> stringResource(R.string.stock_error_offline)
    MarketDataError.Timeout -> stringResource(R.string.stock_error_timeout)
    is MarketDataError.ProviderUnavailable -> stringResource(R.string.stock_error_unavailable, error.httpCode)
    MarketDataError.MalformedResponse -> stringResource(R.string.stock_error_malformed)
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
