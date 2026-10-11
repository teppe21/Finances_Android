package com.teppe21.finances.feature.settings

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
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
private val DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.US)

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
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
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
            SectionCard(title = "Appearance") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                        FilterChip(selected = prefs.theme == key, onClick = { viewModel.setTheme(key) }, label = { Text(label) })
                    }
                }
            }
        }

        item {
            SectionCard(title = "Currency") {
                Text("Display currency", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SupportedCurrencies.ALL.forEach { c ->
                        FilterChip(selected = prefs.currency == c.code, onClick = { viewModel.setCurrency(c.code) }, label = { Text(c.code) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Exchange rates", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(rateSummary(table, sync), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                lastFetch?.let {
                    Text(
                        "Last fetched ${it.atZone(ZoneId.systemDefault()).format(DATE_TIME)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Totals use the latest rate for every transaction, not the historical rate on the transaction date. " +
                        "Only currency codes are sent to the rate service.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = "Fetch rates automatically",
                    subtitle = "When off, the app makes no exchange-rate requests; cached and manual rates are used.",
                    checked = prefs.autoSyncFxRates,
                    onChange = { viewModel.setAutoSyncFxRates(it) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.refreshExchangeRates() }, enabled = sync != FxSyncState.SYNCING) {
                        Text(if (sync == FxSyncState.SYNCING) "Updating..." else "Update now")
                    }
                    OutlinedButton(onClick = { dialog = SettingsDialog.ManualRates }) { Text("Manual rates") }
                }
            }
        }

        item {
            SectionCard(title = "Security") {
                val lockLabel = when {
                    !prefs.lockEnabled -> "Off"
                    prefs.appLockType == LockType.BIOMETRIC -> "PIN and biometrics"
                    else -> "PIN"
                }
                Text("App lock: $lockLabel", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "The app locks whenever you leave it. While the lock is on, screenshots and the Recents preview are blocked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { dialog = SettingsDialog.SetPin }) { Text(if (prefs.hasPin) "Change PIN" else "Set PIN") }
                    if (prefs.lockEnabled) {
                        OutlinedButton(onClick = { dialog = SettingsDialog.DisableLock }) { Text("Turn off") }
                    }
                }
                if (prefs.hasPin) {
                    SwitchRow(
                        title = "Unlock with biometrics",
                        subtitle = if (biometricAvailable) "Your PIN remains the fallback." else "No biometric unlock is set up on this device.",
                        checked = prefs.appLockType == LockType.BIOMETRIC,
                        enabled = biometricAvailable,
                        onChange = { viewModel.setBiometric(it) }
                    )
                }
            }
        }

        item {
            SectionCard(title = "Notifications") {
                LinkRow("Notification recognition", "Access, supported apps, account mapping and recent results", onOpenNotifications)
            }
        }

        item {
            SectionCard(title = "Market data") {
                LinkRow(
                    "Stocks API key",
                    if (prefs.hasStocksApiKey) "Finnhub key saved on this device" else "Not set. Required for stock quotes.",
                    onOpenMarketData
                )
            }
        }

        item {
            SectionCard(title = "Data management") {
                Text(
                    "Demo data adds clearly labelled sample accounts and transactions for trying the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.loadDemoData() }) { Text("Add demo data") }
                    OutlinedButton(onClick = { viewModel.clearDemoData() }) { Text("Remove demo data") }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                OutlinedButton(onClick = { dialog = SettingsDialog.DeleteFinancial }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete all financial data", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { dialog = SettingsDialog.FullReset }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset app completely", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            SectionCard(title = "Privacy") {
                Text(
                    "Your transactions, accounts, categories and receipts are stored only in this app's private storage " +
                        "and are excluded from Android backup and device transfer. The app has no account system, " +
                        "analytics or advertising SDK.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Network use: exchange rates (currency codes only) from the Frankfurter API with open.er-api.com as fallback, " +
                        "and, if you add an API key, stock symbols and search text sent to Finnhub.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                LinkRow("Privacy policy", "Read the full policy", onOpenPrivacyPolicy)
            }
        }

        item {
            SectionCard(title = "About") {
                Text("Finances ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Exchange rates: European Central Bank reference rates via Frankfurter. Market data: Finnhub. " +
                        "Stock information is for monitoring only and is not investment advice.",
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
            title = "Delete all financial data?",
            body = "This permanently deletes every transaction, account, category, keyword rule, budget, recurring rule, " +
                "receipt record and notification result on this device, and the bank-to-account mappings. Default accounts " +
                "and categories are recreated empty.\n\nKept: app lock and PIN, settings, exchange rates, stock watchlist " +
                "and API key.\n\nThis cannot be undone.",
            confirm = "Delete",
            onConfirm = { viewModel.deleteFinancialData() },
            onClose = { dialog = null }
        )
        SettingsDialog.FullReset -> ConfirmDialog(
            title = "Reset the app?",
            body = "This deletes all financial data, the stock watchlist, the market data API key, the app lock and every " +
                "setting, returning the app to its first-launch state. This cannot be undone.",
            confirm = "Reset everything",
            onConfirm = { viewModel.fullReset() },
            onClose = { dialog = null }
        )
        null -> Unit
    }
}

private enum class SettingsDialog { SetPin, DisableLock, ManualRates, DeleteFinancial, FullReset }

private fun rateSummary(table: RateTable, sync: FxSyncState): String {
    if (table.isEmpty) {
        return when (sync) {
            FxSyncState.SYNCING -> "Fetching rates..."
            FxSyncState.DISABLED -> "No rates yet and automatic fetching is off. Amounts in other currencies cannot be converted."
            else -> "No rates available yet. Amounts in other currencies cannot be converted until rates are fetched or entered."
        }
    }
    val manual = table.quotes.filterValues { it.origin == RateOrigin.MANUAL }.keys
    val fetched = table.quotes.values.filter { it.origin != RateOrigin.MANUAL }
    val parts = mutableListOf<String>()
    fetched.maxByOrNull { it.asOf }?.let {
        parts += "${it.provider}, rates of ${it.asOf.atZone(ZoneId.of("UTC")).format(DATE)}"
    }
    if (manual.isNotEmpty()) parts += "manual: ${manual.sorted().joinToString()}"
    if (sync == FxSyncState.FAILED) parts += "last update failed, using stored rates"
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
            .clip(RoundedCornerShape(12.dp))
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
    var current by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (requireCurrent) "Change PIN" else "Set PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (requireCurrent) PinField(current, "Current PIN") { current = it }
                PinField(pin, "New PIN (4 to 8 digits)") { pin = it }
                PinField(confirm, "Repeat new PIN") { confirm = it }
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
                            is PinCheck.LockedOut -> { error = "Too many attempts. Wait ${check.remaining.seconds} s."; busy = false; return@launch }
                            else -> { error = "Current PIN is incorrect."; busy = false; return@launch }
                        }
                    }
                    error = viewModel.savePin(pin, confirm)
                    busy = false
                    if (error == null) onClose()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

@Composable
private fun DisableLockDialog(viewModel: SettingsViewModel, hasPin: Boolean, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Turn off app lock?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Anyone with access to this phone will be able to open Finances.")
                if (hasPin) PinField(pin, "Current PIN") { pin = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    if (hasPin) {
                        when (viewModel.checkPin(pin)) {
                            PinCheck.Success -> Unit
                            is PinCheck.LockedOut -> { error = "Too many attempts. Try again later."; return@launch }
                            else -> { error = "Incorrect PIN."; return@launch }
                        }
                    }
                    viewModel.disableLock()
                    onClose()
                }
            }) { Text("Turn off") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

@Composable
private fun ManualRatesDialog(table: RateTable, viewModel: SettingsViewModel, onClose: () -> Unit) {
    val codes = SupportedCurrencies.ALL.map { it.code }.filter { it != RateTable.BASE }
    val inputs = remember { mutableStateOf(codes.associateWith { code -> table.quotes[code]?.takeIf { it.origin == RateOrigin.MANUAL }?.rate?.toPlainString().orEmpty() }) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Manual exchange rates") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Rates are per 1 EUR. A manual rate overrides fetched rates for that currency. Leave empty to use fetched rates.",
                    style = MaterialTheme.typography.bodySmall
                )
                codes.forEach { code ->
                    val fetched = table.quotes[code]?.takeIf { it.origin != RateOrigin.MANUAL }?.rate?.toPlainString()
                    OutlinedTextField(
                        value = inputs.value.getValue(code),
                        onValueChange = { v -> inputs.value = inputs.value + (code to v.filter { it.isDigit() || it == '.' }.take(14)) },
                        label = { Text("1 EUR = ? $code") },
                        placeholder = { Text(fetched ?: "no fetched rate") },
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
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
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
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}
