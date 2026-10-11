package com.teppe21.finances.feature.notifications

import com.teppe21.finances.feature.categories.accountLabel
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
import com.teppe21.finances.core.ui.InfoBanner
import com.teppe21.finances.core.theme.finances
import com.teppe21.finances.core.ui.SectionCard
import com.teppe21.finances.domain.model.Account
import java.time.ZoneId
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.teppe21.finances.R
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.core.utils.DateFormatter


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
            SectionCard(title = stringResource(R.string.notif_status)) {
                StatusLine(stringResource(R.string.notif_status_access), state.accessGranted)
                StatusLine(stringResource(R.string.notif_status_disclosure), state.disclosureAccepted)
                Spacer(Modifier.height(8.dp))
                val active = state.accessGranted && state.disclosureAccepted
                Text(
                    stringResource(if (active) R.string.notif_active else R.string.notif_inactive),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                if (!state.disclosureAccepted) {
                    Button(onClick = { showDisclosure = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.notif_setup))
                    }
                } else {
                    OutlinedButton(onClick = { viewModel.openSystemSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (state.accessGranted) R.string.notif_manage_access else R.string.notif_grant_access))
                    }
                    TextButton(onClick = { showWithdraw = true }) { Text(stringResource(R.string.notif_stop)) }
                }
            }
        }

        if (state.reviewCount > 0) {
            item {
                InfoBanner(
                    text = pluralStringResource(R.plurals.notif_review_waiting, state.reviewCount, state.reviewCount),
                    icon = Icons.Default.PendingActions,
                    onClick = onOpenReview
                )
            }
        }

        item {
            SectionCard(title = stringResource(R.string.notif_supported_apps)) {
                Text(
                    stringResource(R.string.notif_supported_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.sources.forEach { source ->
                    Spacer(Modifier.height(10.dp))
                    Text(source.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(if (source.verifiedFixtures) R.string.notif_source_verified else R.string.notif_source_unverified),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.notif_accounts)) {
                Text(
                    stringResource(R.string.notif_accounts_desc),
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
            SectionCard(title = stringResource(R.string.notif_try)) {
                Text(
                    stringResource(R.string.notif_try_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.runSample("purchase") }) { Text(stringResource(R.string.notif_sample_purchase)) }
                    OutlinedButton(onClick = { viewModel.runSample("promo") }) { Text(stringResource(R.string.notif_sample_promo)) }
                    OutlinedButton(onClick = { viewModel.runSample("otp") }) { Text(stringResource(R.string.notif_sample_login)) }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.notif_recent)) {
                Text(
                    stringResource(R.string.notif_recent_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.recentEvents.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.notif_recent_empty), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        items(state.recentEvents, key = { it.id }) { event ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(event.sourceBank ?: event.packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(statusLabel(event.statusCode)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    DateFormatter.dayTime(event.postedAt.atZone(ZoneId.systemDefault())),
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
            title = { Text(stringResource(R.string.notif_stop_title)) },
            text = {
                Text(stringResource(R.string.notif_stop_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    showWithdraw = false
                    viewModel.withdrawConsent()
                    viewModel.openSystemSettings(context)
                }) { Text(stringResource(R.string.notif_stop_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showWithdraw = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    sample?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSample() },
            title = {
                Text(
                    when (result) {
                        is SampleResult.Recognized -> stringResource(R.string.sample_recognized_title)
                        is SampleResult.Rejected -> stringResource(R.string.sample_rejected_title, stringResource(statusLabel(result.code)))
                        SampleResult.SettingsUnavailable -> stringResource(R.string.sample_settings_title)
                    }
                )
            },
            text = {
                Text(
                    when (result) {
                        is SampleResult.Recognized -> stringResource(
                            R.string.sample_recognized_body,
                            CurrencyFormatter.formatSigned(result.amountMinor, result.currency),
                            result.merchant ?: stringResource(R.string.unknown)
                        )
                        is SampleResult.Rejected -> stringResource(R.string.sample_rejected_body)
                        SampleResult.SettingsUnavailable -> stringResource(R.string.sample_settings_body)
                    }
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.dismissSample() }) { Text(stringResource(R.string.ok)) } }
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
        title = { Text(stringResource(R.string.notif_disclosure_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.notif_disclosure_1))
                Text(stringResource(R.string.notif_disclosure_2))
                Text(stringResource(R.string.notif_disclosure_3))
                Text(stringResource(R.string.notif_disclosure_4))
                Text(stringResource(R.string.notif_disclosure_5))
            }
        },
        confirmButton = { Button(onClick = onContinue) { Text(stringResource(R.string.continue_label)) } },
        dismissButton = { TextButton(onClick = onNotNow) { Text(stringResource(R.string.not_now)) } }
    )
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (ok) MaterialTheme.finances.success else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.notif_status_line, label, stringResource(if (ok) R.string.yes else R.string.no)), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MappingRow(institution: String, accounts: List<Account>, selectedId: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(institution, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Column {
            TextButton(onClick = { expanded = true }) { Text(selected?.let { accountLabel(it) } ?: stringResource(R.string.notif_mapping_automatic)) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                accounts.forEach { acc ->
                    DropdownMenuItem(
                        text = { Text(accountLabel(acc)) },
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
