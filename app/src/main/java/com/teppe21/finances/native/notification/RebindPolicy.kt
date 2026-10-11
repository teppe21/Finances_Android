package com.teppe21.finances.native.notification

/**
 * Bounds how often the listener asks Android to rebind it after a disconnect.
 *
 * At most [maxAttempts] rebind requests are allowed inside any [windowMillis] span, spaced with
 * exponential backoff ([baseDelayMillis] doubling up to [maxDelayMillis]). A connection that
 * stays up for [stableMillis] resets the history, so an occasional disconnect never exhausts
 * the budget while a connect/disconnect loop does. Once exhausted, [nextDelayMillis] returns
 * null until the window passes or a stable connection is seen; Android's own rebind (for
 * example after the user toggles access) still works because nothing here blocks it.
 */
class RebindPolicy(
    private val maxAttempts: Int = 5,
    private val windowMillis: Long = 10 * 60_000L,
    private val baseDelayMillis: Long = 2_000L,
    private val maxDelayMillis: Long = 60_000L,
    private val stableMillis: Long = 60_000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val attempts = ArrayDeque<Long>()
    private var connectedAt: Long? = null

    @Synchronized
    fun onConnected() {
        connectedAt = clock()
    }

    /**
     * Call when the listener was disconnected. Returns how long to wait before requesting a
     * rebind, or null if the retry budget is used up.
     */
    @Synchronized
    fun nextDelayMillis(): Long? {
        val now = clock()
        val wasStable = connectedAt?.let { now - it >= stableMillis } == true
        connectedAt = null
        if (wasStable) attempts.clear()
        while (attempts.isNotEmpty() && now - attempts.first() >= windowMillis) attempts.removeFirst()
        if (attempts.size >= maxAttempts) return null
        val delay = minOf(maxDelayMillis, baseDelayMillis shl attempts.size)
        attempts.addLast(now)
        return delay
    }
}
