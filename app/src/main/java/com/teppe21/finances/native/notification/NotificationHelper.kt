package com.teppe21.finances.native.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * resolveActivity() is not used: with package visibility rules on Android 11+ it can
 * return null for settings screens that do exist, so each intent is simply tried.
 */
object NotificationHelper {

    fun isNotificationAccessGranted(context: Context): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledPackages.contains(context.packageName)
    }

    fun openNotificationListenerSettings(context: Context): Boolean {
        val componentName = ComponentName(context, BankNotificationListenerService::class.java)

        // 1. Try App-Specific Notification Listener Detail Settings (Android 11+ / API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val detailIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                    putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, componentName.flattenToString())
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(detailIntent)
                return true
            } catch (_: Exception) {
                // Some OEM builds do not implement the per-app screen; fall through.
            }
        }

        // 2. Try General Notification Listener Settings
        try {
            val generalIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(generalIntent)
            return true
        } catch (_: Exception) {
            // Fall back to the main settings screen below.
        }

        // 3. Fallback to general system settings
        return try {
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
