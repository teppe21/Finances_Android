package com.teppe21.finances.security

import java.time.Duration
import java.time.Instant

object PinPolicy {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8
    const val FREE_ATTEMPTS = 5
    private val BASE_LOCKOUT: Duration = Duration.ofSeconds(30)
    private val MAX_LOCKOUT: Duration = Duration.ofMinutes(15)

    /** Why a new PIN is not acceptable; the screen turns it into text. */
    enum class Problem { LENGTH, REPEATED_DIGIT, SEQUENCE }

    /** Returns the problem with [pin], or null if it is acceptable. */
    fun validateNewPin(pin: String): Problem? {
        if (pin.length !in MIN_LENGTH..MAX_LENGTH || !pin.all { it in '0'..'9' }) return Problem.LENGTH
        if (pin.toSet().size == 1) return Problem.REPEATED_DIGIT
        val ascending = pin.zipWithNext().all { (a, b) -> b - a == 1 }
        val descending = pin.zipWithNext().all { (a, b) -> a - b == 1 }
        if (ascending || descending) return Problem.SEQUENCE
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
