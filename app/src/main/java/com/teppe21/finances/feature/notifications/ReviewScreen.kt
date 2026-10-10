package com.teppe21.finances.feature.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teppe21.finances.core.ui.EmptyState
import com.teppe21.finances.core.ui.SectionCard
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.core.utils.DateFormatter
import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.model.Transaction

/**
 * Transactions recognized from notifications that were not counted automatically:
 * unclear account, possible duplicate, or a source whose patterns are not yet verified.
 * Nothing here affects balances until it is confirmed.
 */
@Composable
fun ReviewScreen(viewModel: ReviewViewModel = viewModel()) {
    val pending by viewModel.pending.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        if (pending.isEmpty()) {
            item { EmptyState("Nothing to review", "Recognized transactions that need a decision will appear here.") }
        }
        items(pending, key = { it.id }) { tx ->
            ReviewCard(tx, accounts, onConfirm = { viewModel.confirm(tx, it) }, onDiscard = { viewModel.discard(tx) })
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ReviewCard(tx: Transaction, accounts: List<Account>, onConfirm: (String) -> Unit, onDiscard: () -> Unit) {
    var accountId by remember(tx.id) { mutableStateOf(tx.accountId) }
    var expanded by remember { mutableStateOf(false) }
    val reason = when {
        tx.isUnassigned -> "Choose the account this belongs to."
        tx.notes?.startsWith("Possible duplicate") == true -> "This may be the same payment as another notification. Discard it if so."
        else -> "Check the amount and merchant before confirming."
    }

    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tx.merchant ?: tx.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${DateFormatter.format(tx.date)} · ${tx.source.getDisplayName()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(CurrencyFormatter.formatSigned(tx.amountMinor, tx.currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(reason, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Account:", style = MaterialTheme.typography.bodyMedium)
            Column {
                TextButton(onClick = { expanded = true }) {
                    Text(accounts.firstOrNull { it.id == accountId }?.getDisplayName() ?: "Select")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    accounts.forEach { acc ->
                        DropdownMenuItem(text = { Text(acc.getDisplayName()) }, onClick = {
                            accountId = acc.id
                            expanded = false
                        })
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onConfirm(accountId) }, enabled = accountId.isNotBlank()) { Text("Confirm") }
            OutlinedButton(onClick = onDiscard) { Text("Discard") }
        }
    }
}
