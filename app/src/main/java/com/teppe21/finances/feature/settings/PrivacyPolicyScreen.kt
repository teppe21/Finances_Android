package com.teppe21.finances.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teppe21.finances.BuildConfig
import com.teppe21.finances.FinancesApp

/**
 * In-app copy of the privacy policy, so it is readable offline. It mirrors
 * docs/privacy-policy.html; keep both in sync. The online link is only shown when a
 * verified public URL is configured at build time (PRIVACY_POLICY_URL).
 */
@Composable
fun PrivacyPolicyScreen() {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        items(PrivacyPolicy.sections) { (heading, body) ->
            Column {
                Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (BuildConfig.PRIVACY_POLICY_URL.isNotBlank()) {
            item {
                OutlinedButton(onClick = {
                    FinancesApp.instance.appLock.expectExternalActivity()
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)))
                    }
                }) { Text("Open the online version") }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

object PrivacyPolicy {
    val sections: List<Pair<String, String>> = listOf(
        "Summary" to
            "Finances is a personal finance app that keeps your financial records on your device. It has no user accounts, " +
            "no analytics, no advertising and no crash-reporting SDK. The only network requests are for exchange rates and, " +
            "if you enable it with your own API key, stock market data.",
        "Data stored on your device" to
            "Transactions, accounts, categories, keyword rules, budgets, receipt totals, notification processing results, " +
            "exchange rates, your stock watchlist and cached quotes, and your settings are stored in the app's private storage. " +
            "Your PIN is never stored; only a salted PBKDF2 verifier bound to an Android Keystore key is kept. The market data " +
            "API key is encrypted with an Android Keystore key.",
        "Backup" to
            "Automatic Android cloud backup and device-to-device transfer are disabled for this app. Uninstalling the app or " +
            "clearing its storage deletes the data permanently.",
        "Notification access" to
            "If you enable it after reading the in-app disclosure, the app reads the title and text of notifications from " +
            "supported banking and payment apps (Revolut, Google Wallet, OTP Bank, Wise, Erste Bank, MBH Bank) to recognize " +
            "transactions on the device. Notifications from other apps are ignored. Promotional and security messages are " +
            "rejected. Raw notification text is not stored; the recognized transaction and a short status record (app, time, " +
            "outcome) are kept, the status records for 30 days. Notification contents are never sent anywhere. You can stop " +
            "this in the app and revoke access in Android Settings.",
        "Camera and receipts" to
            "The camera is used only when you start receipt scanning. Text recognition runs on the device with Google ML Kit. " +
            "The app stores the merchant, date and amounts you confirm; it does not keep the photo or the recognized text.",
        "Exchange rates" to
            "To convert between currencies the app requests reference rates from the Frankfurter API (European Central Bank " +
            "data) and, if that fails, from open.er-api.com. These requests contain only currency codes. Like any web request " +
            "they reveal your IP address to the service. You can turn automatic fetching off in Settings.",
        "Stock market data" to
            "The Stocks feature is optional and off until you add your own Finnhub API key. When used, the app sends the " +
            "symbols on your watchlist and the text you type into stock search to Finnhub, together with your API key and your " +
            "IP address. Finnhub's own privacy policy applies to that data. Watchlist and quotes are stored locally and are " +
            "never combined with your financial records.",
        "Sharing" to
            "The app does not sell or share your data. Apart from the requests described above, nothing leaves your device.",
        "Deleting data" to
            "Settings > Data management > Delete all financial data removes your financial records while keeping the app " +
            "lock and settings. Reset app completely removes everything, including the PIN, API key and watchlist.",
        "Permissions" to
            "Internet (exchange rates, optional market data), camera (only during receipt scanning), notification access " +
            "(only if you enable it), and biometric authentication (only if you enable biometric unlock).",
        "Contact" to
            if (BuildConfig.PRIVACY_CONTACT.isNotBlank()) "Questions about this policy: ${BuildConfig.PRIVACY_CONTACT}"
            else "Contact details have not been configured for this build."
    )
}
