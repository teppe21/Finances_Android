package com.teppe21.finances.stocks.ui

import com.teppe21.finances.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.core.ui.SectionCard

@Composable
fun StocksSetupScreen(onDone: () -> Unit, viewModel: StocksSetupViewModel = viewModel()) {
    val context = LocalContext.current
    val hasKey by viewModel.hasKey.collectAsState()
    val check by viewModel.check.collectAsState()
    var key by remember { mutableStateOf("") }

    LaunchedEffect(check) {
        if (check == KeyCheck.Saved) onDone()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        SectionCard(title = stringResource(R.string.stock_why_key)) {
            Text(
                stringResource(R.string.stock_why_key_body),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.stock_how_used),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.stock_what_sent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = {
                FinancesApp.instance.appLock.expectExternalActivity()
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://finnhub.io/register"))) }
            }) { Text(stringResource(R.string.stock_open_finnhub)) }
        }

        SectionCard(title = stringResource(if (hasKey) R.string.stock_replace_key else R.string.stock_enter_key)) {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim().take(128) },
                label = { Text(stringResource(R.string.stock_key_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            when (val c = check) {
                KeyCheck.Checking -> CircularProgressIndicator()
                is KeyCheck.Failed -> {
                    Text(stringResource(c.message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (c.canSaveAnyway) TextButton(onClick = { viewModel.saveWithoutCheck(key) }) { Text(stringResource(R.string.stock_save_unchecked)) }
                }
                else -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.validateAndSave(key) }, enabled = key.isNotBlank() && check != KeyCheck.Checking) {
                    Text(stringResource(R.string.stock_check_save))
                }
            }
            Text(
                stringResource(R.string.stock_key_storage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (hasKey) {
            OutlinedButton(onClick = { viewModel.removeKey() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.stock_remove_key)) }
        }
        Spacer(Modifier.height(24.dp))
    }
}
