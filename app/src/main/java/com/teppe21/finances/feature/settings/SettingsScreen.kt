package com.teppe21.finances.feature.settings

import com.teppe21.finances.core.theme.Radii

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teppe21.finances.BuildConfig
import com.teppe21.finances.data.fx.FxSyncState
import com.teppe21.finances.data.local.preferences.LockType
import com.teppe21.finances.data.local.preferences.PinCheck
import com.teppe21.finances.core.ui.DialogColumn
import com.teppe21.finances.core.ui.ChipFlow
import com.teppe21.finances.core.ui.CurrencyChips
import com.teppe21.finances.core.ui.SectionCard
import com.teppe21.finances.core.ui.pressClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import com.teppe21.finances.domain.fx.RateOrigin
import com.teppe21.finances.domain.fx.RateTable
import com.teppe21.finances.domain.model.SupportedCurrencies
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.ZoneId
import android.content.Context
import androidx.compose.ui.res.stringResource
import com.teppe21.finances.R
import com.teppe21.finances.core.utils.DateFormatter


@Composable
fun SettingsScreen(
    onOpenNotifications: () -> Unit,
    onOpenMarketData: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = viewModel.preferences.collectAsState().value ?: return
    val table by viewModel.rateTable.collectAsState()
    val sync by viewModel.syncState.collectAsState()
    val lastFetch by viewModel.lastFetch.collectAsState()
    val message by viewModel.message.collectAsState()

    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            SectionCard(title = stringResource(R.string.settings_appearance)) {
                ChipFlow {
                    listOf("system" to R.string.settings_theme_system, "light" to R.string.settings_theme_light, "dark" to R.string.settings_theme_dark).forEach { (key, label) ->
                        FilterChip(selected = prefs.theme == key, onClick = { viewModel.setTheme(key) }, label = { Text(stringResource(label)) })
                    }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.currency)) {
                Text(stringResource(R.string.settings_display_currency), style = MaterialTheme.typography.bodyMedium)
                CurrencyChips(selected = prefs.currency, onSelect = { viewModel.setCurrency(it) })
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.settings_exchange_rates), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(rateSummary(table, sync, LocalContext.current), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                lastFetch?.let {
                    Text(
                        stringResource(R.string.settings_last_fetched, DateFormatter.dateTime(it.atZone(ZoneId.systemDefault()))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    stringResource(R.string.settings_rates_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = stringResource(R.string.settings_auto_rates),
                    subtitle = stringResource(R.string.settings_auto_rates_desc),
                    checked = prefs.autoSyncFxRates,
                    onChange = { viewModel.setAutoSyncFxRates(it) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.refreshExchangeRates() }, enabled = sync != FxSyncState.SYNCING) {
                        Text(stringResource(if (sync == FxSyncState.SYNCING) R.string.settings_updating else R.string.settings_update_now))
                    }
                    OutlinedButton(onClick = { dialog = SettingsDialog.ManualRates }) { Text(stringResource(R.string.settings_manual_rates)) }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_security)) {
                val lockLabel = when {
                    !prefs.lockEnabled -> stringResource(R.string.settings_lock_off)
                    prefs.appLockType == LockType.BIOMETRIC -> stringResource(R.string.settings_lock_pin_biometric)
                    else -> stringResource(R.string.settings_lock_pin)
                }
                Text(stringResource(R.string.settings_app_lock, lockLabel), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_lock_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { dialog = SettingsDialog.SetPin }) { Text(stringResource(if (prefs.hasPin) R.string.settings_change_pin else R.string.settings_set_pin)) }
                    if (prefs.lockEnabled) {
                        OutlinedButton(onClick = { dialog = SettingsDialog.DisableLock }) { Text(stringResource(R.string.settings_turn_off)) }
                    }
                }
                if (prefs.hasPin) {
                    SwitchRow(
                        title = stringResource(R.string.settings_biometric),
                        subtitle = stringResource(if (biometricAvailable) R.string.settings_biometric_desc else R.string.settings_biometric_unavailable),
                        checked = prefs.appLockType == LockType.BIOMETRIC,
                        enabled = biometricAvailable,
                        onChange = { viewModel.setBiometric(it) }
                    )
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_notifications)) {
                LinkRow(stringResource(R.string.notif_title), stringResource(R.string.settings_notifications_desc), onOpenNotifications)
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_market_data)) {
                LinkRow(
                    stringResource(R.string.settings_stocks_key),
                    stringResource(if (prefs.hasStocksApiKey) R.string.settings_stocks_key_saved else R.string.settings_stocks_key_missing),
                    onOpenMarketData
                )
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_data)) {
                Text(
                    stringResource(R.string.settings_demo_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.loadDemoData() }) { Text(stringResource(R.string.settings_demo_add)) }
                    OutlinedButton(onClick = { viewModel.clearDemoData() }) { Text(stringResource(R.string.settings_demo_remove)) }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                OutlinedButton(onClick = { dialog = SettingsDialog.DeleteFinancial }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_delete_data), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { dialog = SettingsDialog.FullReset }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_reset_app), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_privacy)) {
                Text(stringResource(R.string.settings_privacy_storage), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.settings_privacy_network), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                LinkRow(stringResource(R.string.privacy_policy), stringResource(R.string.settings_privacy_read), onOpenPrivacyPolicy)
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_about_sources),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    when (dialog) {
        SettingsDialog.SetPin -> SetPinDialog(viewModel, requireCurrent = prefs.hasPin, onClose = { dialog = null })
        SettingsDialog.DisableLock -> DisableLockDialog(viewModel, hasPin = prefs.hasPin, onClose = { dialog = null })
        SettingsDialog.ManualRates -> ManualRatesDialog(table, viewModel, onClose = { dialog = null })
        SettingsDialog.DeleteFinancial -> ConfirmDialog(
            title = stringResource(R.string.settings_delete_title),
            body = stringResource(R.string.settings_delete_body),
            confirm = stringResource(R.string.delete),
            onConfirm = { viewModel.deleteFinancialData() },
            onClose = { dialog = null }
        )
        SettingsDialog.FullReset -> ConfirmDialog(
            title = stringResource(R.string.settings_reset_title),
            body = stringResource(R.string.settings_reset_body),
            confirm = stringResource(R.string.settings_reset_confirm),
            onConfirm = { viewModel.fullReset() },
            onClose = { dialog = null }
        )
        null -> Unit
    }
}

private enum class SettingsDialog { SetPin, DisableLock, ManualRates, DeleteFinancial, FullReset }

private fun rateSummary(table: RateTable, sync: FxSyncState, context: Context): String {
    if (table.isEmpty) {
        return context.getString(
            when (sync) {
                FxSyncState.SYNCING -> R.string.settings_rates_fetching
                FxSyncState.DISABLED -> R.string.settings_rates_none_off
                else -> R.string.settings_rates_none
            }
        )
    }
    val manual = table.quotes.filterValues { it.origin == RateOrigin.MANUAL }.keys
    val fetched = table.quotes.values.filter { it.origin != RateOrigin.MANUAL }
    val parts = mutableListOf<String>()
    fetched.maxByOrNull { it.asOf }?.let {
        parts += context.getString(R.string.settings_rates_of, it.provider, DateFormatter.format(it.asOf.atZone(ZoneId.of("UTC")).toLocalDate()))
    }
    if (manual.isNotEmpty()) parts += context.getString(R.string.settings_rates_manual, manual.sorted().joinToString())
    if (sync == FxSyncState.FAILED) parts += context.getString(R.string.settings_rates_failed)
    return parts.joinToString("; ")
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radii.control)
            .pressClickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> if (input.length <= 8 && input.all { it.isDigit() }) onChange(input) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SetPinDialog(viewModel: SettingsViewModel, requireCurrent: Boolean, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var current by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(if (requireCurrent) R.string.settings_change_pin else R.string.settings_set_pin)) },
        text = {
            DialogColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (requireCurrent) PinField(current, stringResource(R.string.pin_current)) { current = it }
                PinField(pin, stringResource(R.string.pin_new)) { pin = it }
                PinField(confirm, stringResource(R.string.pin_repeat)) { confirm = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    if (requireCurrent) {
                        when (val check = viewModel.checkPin(current)) {
                            PinCheck.Success -> Unit
                            is PinCheck.LockedOut -> { error = context.getString(R.string.pin_error_wait, check.remaining.seconds); busy = false; return@launch }
                            else -> { error = context.getString(R.string.pin_error_current_wrong); busy = false; return@launch }
                        }
                    }
                    error = viewModel.savePin(pin, confirm)?.let { context.getString(it) }
                    busy = false
                    if (error == null) onClose()
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun DisableLockDialog(viewModel: SettingsViewModel, hasPin: Boolean, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.settings_lock_off_title)) },
        text = {
            DialogColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_lock_off_body))
                if (hasPin) PinField(pin, stringResource(R.string.pin_current)) { pin = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    if (hasPin) {
                        when (viewModel.checkPin(pin)) {
                            PinCheck.Success -> Unit
                            is PinCheck.LockedOut -> { error = context.getString(R.string.pin_error_later); return@launch }
                            else -> { error = context.getString(R.string.pin_error_wrong); return@launch }
                        }
                    }
                    viewModel.disableLock()
                    onClose()
                }
            }) { Text(stringResource(R.string.settings_turn_off)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ManualRatesDialog(table: RateTable, viewModel: SettingsViewModel, onClose: () -> Unit) {
    val codes = SupportedCurrencies.ALL.map { it.code }.filter { it != RateTable.BASE }
    val inputs = remember { mutableStateOf(codes.associateWith { code -> table.quotes[code]?.takeIf { it.origin == RateOrigin.MANUAL }?.rate?.toPlainString().orEmpty() }) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.settings_manual_rates_title)) },
        text = {
            DialogColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.settings_manual_rates_note),
                    style = MaterialTheme.typography.bodySmall
                )
                codes.forEach { code ->
                    val fetched = table.quotes[code]?.takeIf { it.origin != RateOrigin.MANUAL }?.rate?.toPlainString()
                    OutlinedTextField(
                        value = inputs.value.getValue(code),
                        onValueChange = { v -> inputs.value = inputs.value + (code to v.filter { it.isDigit() || it == '.' }.take(14)) },
                        label = { Text(stringResource(R.string.settings_rate_label, code)) },
                        placeholder = { Text(fetched ?: stringResource(R.string.settings_no_fetched_rate)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                inputs.value.forEach { (code, text) ->
                    val value = text.toBigDecimalOrNull()
                    if (value != null && value > BigDecimal.ZERO) viewModel.setManualRate(code, value)
                    else if (text.isBlank()) viewModel.clearManualRate(code)
                }
                onClose()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onClose()
            }) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } }
    )
}
