package com.teppe21.finances.native.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.domain.model.RawNotification
import com.teppe21.finances.native.notification.parsing.SupportedSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Receives posted notifications and hands supported ones to the processing pipeline.
 *
 * - Notifications from packages outside the supported list are dropped here, before any
 *   text is read.
 * - Only title, text and big text are read; other extras are ignored.
 * - Nothing from the notification text is logged or written to disk.
 */
class BankNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Serialize processing so that a wallet and a bank notification arriving together
    // are deduplicated against each other deterministically.
    private val processing = Mutex()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName ?: return
        if (SupportedSources.forPackage(packageName) == null) return
        val notification = sbn.notification ?: return
        // Group summaries repeat the content of their children.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras ?: return
        val raw = RawNotification(
            packageName = packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            postedAt = Instant.ofEpochMilli(sbn.postTime),
            notificationKey = sbn.key
        )

        scope.launch {
            try {
                val app = application as FinancesApp
                // The in-app disclosure must have been accepted; Android access alone is not enough.
                if (!app.preferencesRepository.preferencesFlow.first().notificationDisclosureAccepted) return@launch
                processing.withLock {
                    app.processNotificationUseCase.process(raw)
                }
            } catch (e: Exception) {
                // Exception messages can contain parsed text, so only the type is logged.
                Log.w(TAG, "Notification processing failed: ${e.javaClass.simpleName}")
            }
        }
    }

    companion object {
        private const val TAG = "FinancesNotif"
    }
}
