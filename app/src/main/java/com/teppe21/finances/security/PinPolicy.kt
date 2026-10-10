package com.teppe21.finances.security

import java.time.Duration
import java.time.Instant

object PinPolicy {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8
    const val FREE_ATTEMPTS = 5
    private val BASE_LOCKOUT: Duration = Duration.ofSeconds(30)
    private val MAX_LOCKOUT: Duration = Duration.ofMinutes(15)

    /** Returns a user-facing error, or null if the PIN is acceptable. */
    fun validateNewPin(pin: String): String? {
        if (pin.length !in MIN_LENGTH..MAX_LENGTH || !pin.all { it in '0'..'9' }) {
            return "Use $MIN_LENGTH to $MAX_LENGTH digits."
        }
        if (pin.toSet().size == 1) return "Avoid repeating the same digit."
        val ascending = pin.zipWithNext().all { (a, b) -> b - a == 1 }
        val descending = pin.zipWithNext().all { (a, b) -> a - b == 1 }
        if (ascending || descending) return "Avoid simple sequences such as 1234."
        return null
    }

    /**
     * Lockout after [failedAttempts] consecutive wrong PINs: none for the first five,
     * then 30 s doubling per further failure, capped at 15 minutes.
     */
    fun lockoutAfter(failedAttempts: Int): Duration {
        if (failedAttempts < FREE_ATTEMPTS) return Duration.ZERO
        val exponent = (failedAttempts - FREE_ATTEMPTS).coerceAtMost(10)
        val seconds = BASE_LOCKOUT.seconds shl exponent
        return if (seconds >= MAX_LOCKOUT.seconds) MAX_LOCKOUT else Duration.ofSeconds(seconds)
    }

    fun remainingLockout(lockedUntilEpochMs: Long, now: Instant): Duration {
        val remaining = lockedUntilEpochMs - now.toEpochMilli()
        return if (remaining > 0) Duration.ofMillis(remaining) else Duration.ZERO
    }
}
