package com.teppe21.finances.stocks.domain

import java.time.Duration
import java.time.Instant

/**
 * Classifies a quote by its provider timestamp. No status is ever derived from the
 * time the app fetched the quote, so a delayed feed can never look live.
 */
object Freshness {
    /** A price at most this old, during an open session, counts as real-time. */
    val LIVE_MAX_AGE: Duration = Duration.ofMinutes(2)
    /** Older than this during an open session: the feed is not keeping up. */
    val DELAYED_MAX_AGE: Duration = Duration.ofMinutes(30)
    /** A closing price older than this (long weekend + holiday) is stale. */
    val LAST_CLOSE_MAX_AGE: Duration = Duration.ofDays(4)

    fun classify(quoteTime: Instant?, now: Instant, marketOpen: Boolean?): DataStatus {
        if (quoteTime == null || quoteTime.epochSecond <= 0) return DataStatus.UNAVAILABLE
        val age = Duration.between(quoteTime, now).let { if (it.isNegative) Duration.ZERO else it }
        return when (marketOpen) {
            true -> when {
                age <= LIVE_MAX_AGE -> DataStatus.LIVE
                age <= DELAYED_MAX_AGE -> DataStatus.DELAYED
                else -> DataStatus.STALE
            }
            false -> if (age <= LAST_CLOSE_MAX_AGE) DataStatus.LAST_CLOSE else DataStatus.STALE
            // Unknown session state (non-US exchanges): never claim real-time.
            null -> when {
                age <= DELAYED_MAX_AGE -> DataStatus.DELAYED
                else -> DataStatus.STALE
            }
        }
    }
}

/**
 * Polling cadence while the Stocks screen is visible. Finnhub's free tier allows about
 * 60 calls per minute; one refresh cycle costs one call per symbol plus one market-status
 * call, so the watchlist is capped and the interval stays at a minute or more.
 */
object RefreshPolicy {
    const val MAX_WATCHLIST_SIZE = 25
    val OPEN_INTERVAL: Duration = Duration.ofSeconds(60)
    val UNKNOWN_INTERVAL: Duration = Duration.ofMinutes(5)
    val MAX_BACKOFF: Duration = Duration.ofMinutes(15)

    /** null means: do not poll automatically (market closed); manual refresh still works. */
    fun nextDelay(marketOpen: Boolean?, consecutiveFailures: Int, rateLimitRetryAfter: Duration?): Duration? {
        if (rateLimitRetryAfter != null) return maxOf(rateLimitRetryAfter, OPEN_INTERVAL)
        if (consecutiveFailures > 0) {
            val backoff = OPEN_INTERVAL.multipliedBy(1L shl (consecutiveFailures - 1).coerceAtMost(4))
            return minOf(backoff, MAX_BACKOFF)
        }
        return when (marketOpen) {
            true -> OPEN_INTERVAL
            false -> null
            null -> UNKNOWN_INTERVAL
        }
    }

    /** Market status only applies to US listings (no exchange suffix in the symbol). */
    fun usesUsMarketStatus(symbol: String): Boolean = !symbol.contains('.') && !symbol.contains(':')
}
