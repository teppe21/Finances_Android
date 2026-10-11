package com.teppe21.finances.feature.accounts

import com.teppe21.finances.feature.categories.accountTypeLabel
import com.teppe21.finances.feature.categories.accountLabel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teppe21.finances.R
import com.teppe21.finances.core.theme.finances
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.core.money.Money
import com.teppe21.finances.domain.model.SupportedCurrencies
import androidx.compose.material3.FilterChip
import com.teppe21.finances.core.ui.Motion
import com.teppe21.finances.core.ui.AmountRowLayout
import com.teppe21.finances.core.ui.AmountText
import com.teppe21.finances.core.ui.ChipFlow
import com.teppe21.finances.core.ui.CurrencyChips
import com.teppe21.finances.core.ui.DialogColumn
import com.teppe21.finances.core.theme.Radii
import com.teppe21.finances.core.theme.Sizes
import com.teppe21.finances.core.theme.Spacing
import com.teppe21.finances.core.theme.tabular
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.teppe21.finances.domain.model.AccountType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel = viewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                Text(
                    text = stringResource(R.string.accounts_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Text(
                    stringResource(R.string.accounts_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(accounts, key = { it.account.id }) { row ->
                AccountCard(row = row, modifier = Modifier.animateItemPlacement(Motion.medium()))
            }

            item { Spacer(modifier = Modifier.height(88.dp)) }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_account))
        }

        if (showAddDialog) {
            AddAccountDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, inst, type, currency, balanceMinor ->
                    viewModel.addAccount(name, inst, type, currency, balanceMinor)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun AccountCard(row: AccountRow, modifier: Modifier = Modifier) {
    val account = row.account
    val icon = when (account.type) {
        AccountType.CASH -> Icons.Default.Wallet
        AccountType.SAVINGS -> Icons.Default.Savings
        AccountType.CREDIT_CARD -> Icons.Default.CreditCard
        else -> Icons.Default.AccountBalance
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Radii.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.l)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = Radii.control,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(Sizes.leadingIcon + Spacing.xs + Spacing.xxs)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.m))

            // The balance is never squeezed: the name gets what is left, and the balance moves under it
            // when the name would be too narrow (large font, long figure). The status line sits below.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                AmountRowLayout(
                    label = {
                        Column {
                            Text(
                                text = accountLabel(account),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = account.institution,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    amount = {
                        AmountText(
                            text = CurrencyFormatter.formatSigned(row.balanceMinor, account.currency),
                            style = MaterialTheme.typography.titleMedium.tabular()
                        )
                    }
                )
                if (row.unconvertedCount > 0) {
                    Text(
                        stringResource(R.string.accounts_incomplete, row.unconvertedCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (row.pendingCount > 0) {
                    Text(
                        stringResource(R.string.accounts_awaiting_review, row.pendingCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, institution: String, type: AccountType, currency: String, balanceMinor: Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var institution by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("HUF") }
    var type by remember { mutableStateOf(AccountType.BANK) }
    var negative by remember { mutableStateOf(false) }
    val parsed = if (balanceText.isBlank()) 0L else Money.parseUserInput(balanceText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_account)) },
        text = {
            DialogColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.account_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = institution,
                    onValueChange = { institution = it },
                    label = { Text(stringResource(R.string.institution)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.account_type), style = MaterialTheme.typography.labelMedium)
                ChipFlow {
                    AccountType.values().forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(accountTypeLabel(t)) })
                    }
                }
                Text(stringResource(R.string.currency), style = MaterialTheme.typography.labelMedium)
                CurrencyChips(selected = currency, onSelect = { currency = it })
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(15) },
                    label = { Text(stringResource(R.string.opening_balance) + " ($currency)") },
                    isError = parsed == null,
                    supportingText = { if (parsed == null) Text(stringResource(R.string.amount_format_error)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = negative, onCheckedChange = { negative = it })
                    Text(stringResource(R.string.accounts_negative_opening), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minor = parsed ?: return@Button
                    onConfirm(name, institution.ifBlank { name }, type, currency, if (negative) -minor else minor)
                },
                enabled = name.isNotBlank() && parsed != null
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
