package com.teppe21.finances.native.notification

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.domain.model.NotificationEvent
import com.teppe21.finances.domain.model.NotificationParseStatus
import com.teppe21.finances.domain.model.NotificationStatusCode
import com.teppe21.finances.domain.model.RawNotification
import com.teppe21.finances.native.notification.parsing.SupportedSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Receives posted notifications and hands supported ones to the processing pipeline.
 *
 * - Notifications from packages outside the supported list are dropped here, before any
 *   text is read.
 * - Only title, text and big text are read; other extras are ignored.
 * - Nothing from the notification text is logged or written to disk.
 * - Supported notifications go through a bounded, strictly serial queue, so a wallet and a
 *   bank notification arriving together are deduplicated against each other deterministically
 *   and a stuck item cannot pile up unbounded work.
 * - A failed or timed-out item is recorded as a sanitized failure status (package and time
 *   only), never as a success, and never touches an existing transaction.
 * - After Android disconnects the listener, a bounded number of rebind requests is made.
 */
class BankNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rebindPolicy = RebindPolicy()

    private val queue = SerialWorkQueue<RawNotification>(
        scope = scope,
        process = ::processNotification,
        onFailure = ::recordFailure,
        onDropped = { count -> Log.w(TAG, "Notification backlog full; dropped $count") }
    )

    override fun onDestroy() {
        queue.close()
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        rebindPolicy.onConnected()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        val delayMillis = rebindPolicy.nextDelayMillis()
        if (delayMillis == null) {
            Log.w(TAG, "Listener disconnected; rebind budget used up, waiting for Android to rebind")
            return
        }
        // The service may be destroyed right after this callback, so the delayed request runs on
        // the application scope rather than on this service's scope.
        val component = ComponentName(this, BankNotificationListenerService::class.java)
        val appScope = (application as FinancesApp).appScope
        appScope.launch {
            delay(delayMillis)
            try {
                requestRebind(component)
            } catch (e: Exception) {
                Log.w(TAG, "Rebind request failed: ${e.javaClass.simpleName}")
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName ?: return
        if (SupportedSources.forPackage(packageName) == null) return
        val notification = sbn.notification ?: return
        // Group summaries repeat the content of their children.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras ?: return
        queue.offer(
            RawNotification(
                packageName = packageName,
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                postedAt = Instant.ofEpochMilli(sbn.postTime),
                notificationKey = sbn.key
            )
        )
    }

    private suspend fun processNotification(raw: RawNotification) {
        val app = application as FinancesApp
        // The in-app disclosure must have been accepted; Android access alone is not enough.
        if (!app.preferencesRepository.preferencesFlow.first().notificationDisclosureAccepted) return
        app.processNotificationUseCase.process(raw)
    }

    private suspend fun recordFailure(raw: RawNotification, failure: SerialWorkQueue.Failure) {
        val code = when (failure) {
            SerialWorkQueue.Failure.TIMED_OUT -> NotificationStatusCode.PROCESSING_TIMEOUT
            SerialWorkQueue.Failure.ERROR -> NotificationStatusCode.PROCESSING_ERROR
        }
        // Only the exception type would ever be logged: messages can contain parsed text.
        Log.w(TAG, "Notification processing failed: $code")
        val app = application as FinancesApp
        app.notificationRepository.record(
            NotificationEvent(
                id = UUID.randomUUID().toString(),
                packageName = raw.packageName,
                postedAt = raw.postedAt,
                sourceBank = SupportedSources.forPackage(raw.packageName)?.displayName,
                processed = true,
                parseStatus = NotificationParseStatus.FAILED,
                reasonCode = code.name
            )
        )
    }

    companion object {
        private const val TAG = "FinancesNotif"
    }
}
