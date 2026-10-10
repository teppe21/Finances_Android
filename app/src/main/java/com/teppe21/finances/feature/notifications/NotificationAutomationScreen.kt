package com.teppe21.finances.feature.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teppe21.finances.core.theme.Emerald500
import com.teppe21.finances.core.ui.InfoBanner
import com.teppe21.finances.core.ui.SectionCard
import com.teppe21.finances.domain.model.Account
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EVENT_TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.US)

@Composable
fun NotificationAutomationScreen(
    onOpenReview: () -> Unit,
    viewModel: NotificationViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsState()
    val sample by viewModel.sample.collectAsState()
    var showDisclosure by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }

    // Re-check access whenever the user returns from Android settings.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            SectionCard(title = "Status") {
                StatusLine("Android notification access", state.accessGranted)
                StatusLine("In-app disclosure accepted", state.disclosureAccepted)
                Spacer(Modifier.height(8.dp))
                val active = state.accessGranted && state.disclosureAccepted
                Text(
                    if (active) "Supported notifications are being processed on this device."
                    else "Notifications are not being processed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                if (!state.disclosureAccepted) {
                    Button(onClick = { showDisclosure = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Set up notification access")
                    }
                } else {
                    OutlinedButton(onClick = { viewModel.openSystemSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.accessGranted) "Manage access in Android settings" else "Grant access in Android settings")
                    }
                    TextButton(onClick = { showWithdraw = true }) { Text("Stop using notifications") }
                }
            }
        }

        if (state.reviewCount > 0) {
            item {
                InfoBanner(
                    text = "${state.reviewCount} recognized transactions are waiting for review.",
                    icon = Icons.Default.PendingActions,
                    onClick = onOpenReview
                )
            }
        }

        item {
            SectionCard(title = "Supported apps") {
                Text(
                    "Only notifications from these exact Android packages are read. Everything else is ignored without being stored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.sources.forEach { source ->
                    Spacer(Modifier.height(10.dp))
                    Text(source.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (source.verifiedFixtures) "Tested with real notification samples"
                        else "Recognized transactions always go to the review queue (patterns not yet verified on real notifications)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionCard(title = "Accounts for recognized transactions") {
                Text(
                    "Choose which account receives each bank's transactions. Without a choice, a transaction is assigned " +
                        "automatically only when exactly one account belongs to that bank; otherwise it waits for review. " +
                        "Wallet payments use the account of the card's bank.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                viewModel.mappableInstitutions.forEach { institution ->
                    Spacer(Modifier.height(8.dp))
                    MappingRow(institution, state.accounts, state.mappings[institution]) { viewModel.setMapping(institution, it) }
                }
            }
        }

        item {
            SectionCard(title = "Try it without a real notification") {
                Text(
                    "Runs the recognizer on a built-in sample. Nothing is saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.runSample("purchase") }) { Text("Purchase") }
                    OutlinedButton(onClick = { viewModel.runSample("promo") }) { Text("Promotion") }
                    OutlinedButton(onClick = { viewModel.runSample("otp") }) { Text("Login code") }
                }
            }
        }

        item {
            SectionCard(title = "Recent processing results") {
                Text(
                    "Only the app, time and outcome are kept (for 30 days). Notification text is never stored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.recentEvents.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Nothing processed yet.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        items(state.recentEvents, key = { it.id }) { event ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(event.sourceBank ?: event.packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(statusLabel(event.statusCode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    event.postedAt.atZone(ZoneId.systemDefault()).format(EVENT_TIME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showDisclosure) {
        NotificationDisclosureDialog(
            onNotNow = { showDisclosure = false },
            onContinue = {
                showDisclosure = false
                viewModel.acceptDisclosureAndOpenSettings(context)
            }
        )
    }

    if (showWithdraw) {
        AlertDialog(
            onDismissRequest = { showWithdraw = false },
            title = { Text("Stop using notifications?") },
            text = {
                Text(
                    "Finances will stop processing notifications immediately. To also remove the Android permission, " +
                        "turn Finances off on the notification access screen that opens next."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWithdraw = false
                    viewModel.withdrawConsent()
                    viewModel.openSystemSettings(context)
                }) { Text("Stop and open settings") }
            },
            dismissButton = { TextButton(onClick = { showWithdraw = false }) { Text("Cancel") } }
        )
    }

    sample?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSample() },
            title = { Text(result.title) },
            text = { Text(result.details) },
            confirmButton = { TextButton(onClick = { viewModel.dismissSample() }) { Text("OK") } }
        )
    }
}

/**
 * Prominent disclosure shown before Android's notification access screen. Only "Continue"
 * records consent and opens settings; dismissing the dialog does not.
 */
@Composable
fun NotificationDisclosureDialog(onNotNow: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text("Notification access") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Finances uses Android Notification Access to recognize transactions from supported banking and " +
                        "payment apps: Revolut, Google Wallet, OTP Bank, Wise, Erste Bank (George) and MBH Bank."
                )
                Text(
                    "Notification contents can contain sensitive financial information. Finances reads the title and " +
                        "text of notifications from those apps only, and processes them on this device to find the amount, " +
                        "currency, merchant, card ending and time of a payment."
                )
                Text(
                    "Notifications from all other apps are ignored. Promotional messages and security messages such as " +
                        "login codes are rejected and never stored. Raw notification text is not saved; only the recognized " +
                        "transaction and a short status record (app, time, outcome) are kept on this device."
                )
                Text("Notification contents are not sent to any server.")
                Text("You can stop this here at any time, and revoke Notification Access in Android Settings.")
            }
        },
        confirmButton = { Button(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onNotNow) { Text("Not now") } }
    )
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (ok) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("$label: ${if (ok) "yes" else "no"}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MappingRow(institution: String, accounts: List<Account>, selectedId: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(institution, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Column {
            TextButton(onClick = { expanded = true }) { Text(selected?.getDisplayName() ?: "Automatic") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                accounts.forEach { acc ->
                    DropdownMenuItem(
                        text = { Text(acc.getDisplayName()) },
                        onClick = {
                            expanded = false
                            onSelect(acc.id)
                        }
                    )
                }
            }
        }
    }
}
