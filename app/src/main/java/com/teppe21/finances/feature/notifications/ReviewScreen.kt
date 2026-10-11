package com.teppe21.finances.feature.notifications

import com.teppe21.finances.feature.categories.sourceLabel
import com.teppe21.finances.feature.categories.accountLabel
import com.teppe21.finances.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import com.teppe21.finances.core.theme.finances
import com.teppe21.finances.core.ui.SwipeAction
import com.teppe21.finances.core.ui.SwipeActionBox
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teppe21.finances.core.ui.EmptyState
import com.teppe21.finances.core.ui.Motion
import com.teppe21.finances.core.ui.SectionCard
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.core.utils.DateFormatter
import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.model.Transaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow

/**
 * Transactions recognized from notifications that were not counted automatically:
 * unclear account, possible duplicate, or a source whose patterns are not yet verified.
 * Nothing here affects balances until it is confirmed.
 *
 * Confirming or discarding collapses the card first and only then changes the data, so the
 * list never jumps; a snackbar states what happened.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReviewScreen(viewModel: ReviewViewModel = viewModel()) {
    val pending by viewModel.pending.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            if (pending.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        stringResource(R.string.review_empty_title),
                        stringResource(R.string.review_empty_desc),
                        modifier = Modifier.animateItemPlacement(Motion.medium())
                    )
                }
            }
            items(pending, key = { it.id }) { tx ->
                ReviewCard(
                    tx = tx,
                    accounts = accounts,
                    modifier = Modifier.animateItemPlacement(Motion.medium()),
                    onConfirm = { accountId ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.confirm(tx, accountId)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                context.getString(R.string.review_confirmed),
                                actionLabel = context.getString(R.string.undo),
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.undoConfirm(tx)
                        }
                    },
                    onDiscard = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.discard(tx)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                context.getString(R.string.review_discarded),
                                actionLabel = context.getString(R.string.undo),
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.undoDiscard(tx)
                        }
                    }
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ReviewCard(
    tx: Transaction,
    accounts: List<Account>,
    modifier: Modifier = Modifier,
    onConfirm: (String) -> Unit,
    onDiscard: () -> Unit
) {
    var accountId by remember(tx.id) { mutableStateOf(tx.accountId) }
    var expanded by remember { mutableStateOf(false) }
    // Starts visible; flipping the target plays the exit, then the pending action runs.
    val visibility = remember(tx.id) { MutableTransitionState(true) }
    var pendingAction by remember(tx.id) { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(tx.id) {
        snapshotFlow { visibility.isIdle && !visibility.currentState }.first { it }
        pendingAction?.invoke()
    }
    val reason = when {
        tx.isUnassigned -> stringResource(R.string.review_hint_account)
        tx.notes?.startsWith("Possible duplicate") == true -> stringResource(R.string.review_hint_duplicate)
        else -> stringResource(R.string.review_hint_check)
    }

    val confirm = { pendingAction = { onConfirm(accountId) }; visibility.targetState = false }
    val discard = { pendingAction = onDiscard; visibility.targetState = false }
    val busy = pendingAction != null
    val confirmAction = if (accountId.isNotBlank() && !busy) {
        SwipeAction(stringResource(R.string.review_confirm), Icons.Default.Check, MaterialTheme.finances.income, MaterialTheme.colorScheme.background, confirm)
    } else {
        null // choose an account first: the row resists instead of committing
    }
    val discardAction = if (!busy) {
        SwipeAction(stringResource(R.string.review_discard), Icons.Default.Close, MaterialTheme.finances.expense, MaterialTheme.colorScheme.background, discard)
    } else {
        null
    }

    AnimatedVisibility(
        visibleState = visibility,
        modifier = modifier.testTag("review-card-${tx.id}"),
        enter = Motion.appear(),
        exit = Motion.disappear()
    ) {
        SwipeActionBox(swipeRight = confirmAction, swipeLeft = discardAction) {
        SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tx.merchant ?: tx.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${DateFormatter.format(tx.date)} · ${sourceLabel(tx.source)}",
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
                Text(stringResource(R.string.account) + ":", style = MaterialTheme.typography.bodyMedium)
                Column {
                    TextButton(onClick = { expanded = true }) {
                        Text(accounts.firstOrNull { it.id == accountId }?.let { accountLabel(it) } ?: stringResource(R.string.review_select_account))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(text = { Text(accountLabel(acc)) }, onClick = {
                                accountId = acc.id
                                expanded = false
                            })
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = confirm, enabled = accountId.isNotBlank() && !busy) { Text(stringResource(R.string.review_confirm)) }
                OutlinedButton(onClick = discard, enabled = !busy) { Text(stringResource(R.string.review_discard)) }
            }
            Text(
                stringResource(R.string.review_swipe_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        }
    }
}
