package com.teppe21.finances.feature.settings

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import com.teppe21.finances.R
import androidx.compose.ui.res.stringResource
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
            PolicySection(stringResource(heading), stringResource(body))
        }
        item {
            PolicySection(
                stringResource(R.string.policy_contact_title),
                if (BuildConfig.PRIVACY_CONTACT.isNotBlank()) stringResource(R.string.policy_contact, BuildConfig.PRIVACY_CONTACT)
                else stringResource(R.string.policy_contact_missing)
            )
        }
        if (BuildConfig.PRIVACY_POLICY_URL.isNotBlank()) {
            item {
                OutlinedButton(onClick = {
                    FinancesApp.instance.appLock.expectExternalActivity()
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)))
                    }
                }) { Text(stringResource(R.string.policy_open_online)) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The in-app policy, section by section, as string resources (English and Hungarian). The
 * English text matches docs/privacy-policy.html; the Hungarian one is a translation of it.
 */
object PrivacyPolicy {
    val sections: List<Pair<Int, Int>> = listOf(
        R.string.policy_summary_title to R.string.policy_summary,
        R.string.policy_storage_title to R.string.policy_storage,
        R.string.policy_backup_title to R.string.policy_backup,
        R.string.policy_notifications_title to R.string.policy_notifications,
        R.string.policy_camera_title to R.string.policy_camera,
        R.string.policy_rates_title to R.string.policy_rates,
        R.string.policy_stocks_title to R.string.policy_stocks,
        R.string.policy_sharing_title to R.string.policy_sharing,
        R.string.policy_deleting_title to R.string.policy_deleting,
        R.string.policy_permissions_title to R.string.policy_permissions
    )
}
