package com.teppe21.finances.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide lock state. It lives in the Application, so rotation and other activity
 * recreation keep the unlocked state, while process death always starts locked.
 *
 * Leaving the app (Home, Recents, screen off) locks it immediately. Activities the app
 * starts itself (document picker, system settings, browser) are announced with
 * [expectExternalActivity] and do not lock if the user returns within [EXTERNAL_GRACE_MS].
 */
class AppLockController {

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var externalLaunchAt: Long = 0L
    private var stoppedForExternal = false

    fun markUnlocked() {
        _unlocked.value = true
    }

    fun lock() {
        _unlocked.value = false
    }

    /** Call right before starting an activity outside the app on the user's request. */
    fun expectExternalActivity() {
        externalLaunchAt = SystemClock.elapsedRealtime()
    }

    /** Activity.onStop for a non-configuration-change stop. */
    fun onAppStopped() {
        val pending = externalLaunchAt != 0L &&
            SystemClock.elapsedRealtime() - externalLaunchAt < EXTERNAL_LAUNCH_WINDOW_MS
        if (pending) {
            stoppedForExternal = true
        } else {
            lock()
        }
    }

    /** Activity.onStart. */
    fun onAppStarted() {
        if (stoppedForExternal) {
            val away = SystemClock.elapsedRealtime() - externalLaunchAt
            if (away > EXTERNAL_GRACE_MS) lock()
        }
        stoppedForExternal = false
        externalLaunchAt = 0L
    }

    companion object {
        /** The external activity must actually start within this window after the request. */
        private const val EXTERNAL_LAUNCH_WINDOW_MS = 5_000L
        /** Maximum time spent in a picker / settings screen before the app locks anyway. */
        const val EXTERNAL_GRACE_MS = 5 * 60_000L
    }
}
