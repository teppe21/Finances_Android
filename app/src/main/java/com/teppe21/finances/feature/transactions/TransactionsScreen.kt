package com.teppe21.finances.feature.transactions

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.focus.onFocusChanged
import com.teppe21.finances.core.ui.QuickReturnScaffold
import com.teppe21.finances.feature.categories.accountLabel
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import com.teppe21.finances.feature.categories.CategorizeDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.teppe21.finances.feature.categories.categoryLabel
import com.teppe21.finances.feature.categories.sourceLabel
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teppe21.finances.R
import com.teppe21.finances.core.theme.Radii
import com.teppe21.finances.core.theme.Spacing
import com.teppe21.finances.core.theme.finances
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import com.teppe21.finances.core.money.Money
import com.teppe21.finances.core.ui.InfoBanner
import com.teppe21.finances.core.ui.ShrinkToFitText
import com.teppe21.finances.core.ui.CurrencyChips
import com.teppe21.finances.core.ui.DialogColumn
import com.teppe21.finances.core.theme.tabular
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.teppe21.finances.core.ui.Motion
import com.teppe21.finances.core.ui.pressClickable
import com.teppe21.finances.core.ui.TransactionRow
import com.teppe21.finances.core.theme.CategoryColorProvider
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.core.utils.DateFormatter
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.model.TransactionDirection
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    openAddDialog: Boolean = false,
    viewModel: TransactionsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showAddModal by rememberSaveable { mutableStateOf(openAddDialog) }
    var showSortModal by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var categorizing by remember { mutableStateOf<Transaction?>(null) }

    val listState = rememberLazyListState()
    // The search field stays put while it has focus, so typing never scrolls it out from under the keyboard.
    var searchFocused by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Search and period filters sit over the top of the list: they leave when the list is scrolled down
        // (pinned, they took about a third of the screen at a large font scale) and return on the first scroll back up.
        QuickReturnScaffold(
            listState = listState,
            pinned = searchFocused,
            header = {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.l, end = Spacing.l, top = Spacing.s)
                        .onFocusChanged { searchFocused = it.isFocused },
                    placeholder = { Text(stringResource(R.string.search_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    shape = Radii.card
                )
                Spacer(Modifier.height(Spacing.s + Spacing.xxs))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = Spacing.l),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedPeriod == "this_month",
                            onClick = { viewModel.setPeriod("this_month") },
                            label = { Text(stringResource(R.string.filter_this_month)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.selectedPeriod == "last_month",
                            onClick = { viewModel.setPeriod("last_month") },
                            label = { Text(stringResource(R.string.filter_last_month)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.selectedPeriod == "3_months",
                            onClick = { viewModel.setPeriod("3_months") },
                            label = { Text(stringResource(R.string.filter_3_months)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.selectedPeriod == "all",
                            onClick = { viewModel.setPeriod("all") },
                            label = { Text(stringResource(R.string.filter_all)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.onlyRecurring,
                            onClick = { viewModel.toggleRecurring() },
                            leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(stringResource(R.string.filter_recurring)) }
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.s))
            }
        ) { headerPadding ->
        LazyColumn(
            state = listState,
            contentPadding = headerPadding,
            verticalArrangement = Arrangement.spacedBy(Spacing.m - Spacing.xxs),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.l)
        ) {
            item(key = "sort") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = true,
                        onClick = { showSortModal = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.sort_by),
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            val sortLabel = when (state.sortOrder) {
                                TransactionSortOrder.NEWEST -> stringResource(R.string.sort_newest)
                                TransactionSortOrder.OLDEST -> stringResource(R.string.sort_oldest)
                                TransactionSortOrder.AMOUNT_DESC -> stringResource(R.string.sort_amount_desc)
                                TransactionSortOrder.AMOUNT_ASC -> stringResource(R.string.sort_amount_asc)
                                TransactionSortOrder.MERCHANT_ASC -> stringResource(R.string.sort_merchant_asc)
                                TransactionSortOrder.MERCHANT_DESC -> stringResource(R.string.sort_merchant_desc)
                            }
                            Text(sortLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    )

                    val count = state.filteredTransactions.size

                    Text(
                        text = pluralStringResource(R.plurals.transaction_count, count, count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.filteredTransactions.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_transactions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                        if (state.unconvertedCount > 0) {
                            item(key = "unconverted-banner") {
                                InfoBanner(
                                    pluralStringResource(R.plurals.unconverted_shown_original, state.unconvertedCount, state.unconvertedCount, state.currency),
                                    modifier = Modifier.animateItemPlacement(Motion.medium())
                                )
                            }
                        }
                        items(state.filteredTransactions, key = { it.id }) { tx ->
                            TransactionRow(
                                transaction = tx,
                                displayCurrency = state.currency,
                                convertedAmountMinor = state.convertedAmounts[tx.id],
                                categoryName = state.categories.firstOrNull { it.id == tx.categoryId }?.let { categoryLabel(it) },
                                onClick = { viewModel.selectTransaction(tx) },
                                modifier = Modifier.animateItemPlacement(Motion.medium())
                            )
                        }
            }
            item { Spacer(modifier = Modifier.height(88.dp)) }
        }
        }

        // Floating Action Button for New Transaction
        FloatingActionButton(
            onClick = { showAddModal = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_transaction))
        }

        // Sort Options Bottom Sheet
        if (showSortModal) {
            ModalBottomSheet(
                onDismissRequest = { showSortModal = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sort_by),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    val sortOptions = listOf(
                        TransactionSortOrder.NEWEST to R.string.sort_newest,
                        TransactionSortOrder.OLDEST to R.string.sort_oldest,
                        TransactionSortOrder.AMOUNT_DESC to R.string.sort_amount_desc,
                        TransactionSortOrder.AMOUNT_ASC to R.string.sort_amount_asc,
                        TransactionSortOrder.MERCHANT_ASC to R.string.sort_merchant_asc,
                        TransactionSortOrder.MERCHANT_DESC to R.string.sort_merchant_desc
                    )
                    sortOptions.forEach { (order, stringRes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(Radii.control)
                                .clickable {
                                    viewModel.setSortOrder(order)
                                    showSortModal = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(stringRes),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (state.sortOrder == order) FontWeight.Bold else FontWeight.Normal,
                                color = if (state.sortOrder == order) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (state.sortOrder == order) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }

        // Transaction Detail Bottom Sheet
        if (state.selectedTransaction != null) {
            val selected = state.selectedTransaction!!
            val sheetState = rememberModalBottomSheetState()

            ModalBottomSheet(
                onDismissRequest = { viewModel.selectTransaction(null) },
                sheetState = sheetState
            ) {
                TransactionDetailContent(
                    transaction = selected,
                    displayCurrency = state.currency,
                    convertedAmountMinor = state.convertedAmounts[selected.id],
                    categoryName = state.categories.firstOrNull { it.id == selected.categoryId }?.let { categoryLabel(it) } ?: stringResource(R.string.category_name_other),
                    accountName = state.accounts.firstOrNull { it.id == selected.accountId }?.let { accountLabel(it) } ?: stringResource(R.string.account_not_assigned),
                    onDelete = { transactionToDelete = selected },
                    onChangeCategory = { categorizing = selected },
                    onClose = { viewModel.selectTransaction(null) }
                )
            }
        }

        categorizing?.let { tx ->
            CategorizeDialog(
                title = tx.merchant ?: tx.description,
                categories = state.categories,
                initialCategoryId = tx.categoryId,
                suggestedKeyword = (tx.merchant ?: tx.description).trim().lowercase(),
                offerRule = true,
                onDismiss = { categorizing = null },
                onApply = { categoryId, keyword ->
                    categorizing = null
                    viewModel.changeCategory(tx, categoryId, keyword)
                }
            )
        }

        // Add Transaction Modal
        if (showAddModal) {
            AddTransactionDialog(
                accounts = state.accounts,
                categories = state.categories,
                defaultCurrency = state.currency,
                onDismiss = { showAddModal = false },
                onConfirm = { accId, amtMinor, curr, dir, desc, merch, catId, date ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.addManualTransaction(accId, amtMinor, curr, dir, desc, merch, catId, date)
                    showAddModal = false
                }
            )
        }

        // Delete Confirmation Dialog
        if (transactionToDelete != null) {
            AlertDialog(
                onDismissRequest = { transactionToDelete = null },
                title = { Text(stringResource(R.string.delete_confirm_title)) },
                text = { Text(stringResource(R.string.delete_confirm_msg)) },
                confirmButton = {
                    Button(
                        onClick = {
                            transactionToDelete?.let { viewModel.deleteTransaction(it) }
                            transactionToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { transactionToDelete = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun TransactionDetailContent(
    transaction: Transaction,
    displayCurrency: String = transaction.currency,
    convertedAmountMinor: Long?,
    categoryName: String,
    accountName: String,
    onDelete: () -> Unit,
    onChangeCategory: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    val isTransfer = transaction.direction.isTransfer
    val amountColor = when {
        isTransfer -> MaterialTheme.finances.transfer
        transaction.amountMinor > 0 -> MaterialTheme.finances.income
        else -> MaterialTheme.finances.expense
    }
    // The sheet can be taller than the screen at a large font scale, so it scrolls.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = transaction.merchant ?: transaction.description,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
        }

        // The stored amount in its own currency is always the primary figure. It stays on one line
        // and steps down in size for a very long figure.
        ShrinkToFitText(
            text = CurrencyFormatter.formatSigned(transaction.amountMinor, transaction.currency),
            style = MaterialTheme.typography.headlineMedium.tabular().copy(fontWeight = FontWeight.Bold),
            color = amountColor,
            floorSp = 16f,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        if (isTransfer) {
            // Transfers are shown apart from income and spending, here and in the analytics.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    if (transaction.direction == TransactionDirection.PERSONAL_TRANSFER) stringResource(R.string.kind_personal_transfer) else stringResource(R.string.kind_transfer),
                    style = MaterialTheme.typography.labelLarge,
                    color = amountColor
                )
                Text(stringResource(R.string.analytics_transfers_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!displayCurrency.equals(transaction.currency, ignoreCase = true)) {
            DetailRow(
                label = stringResource(R.string.detail_in_currency, displayCurrency),
                value = convertedAmountMinor?.let { "≈ " + CurrencyFormatter.formatSigned(it, displayCurrency) }
                    ?: stringResource(R.string.no_rate_available)
            )
        }

        DetailRow(label = stringResource(R.string.date), value = DateFormatter.format(transaction.date))
        if (onChangeCategory != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.category), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(categoryName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (transaction.categorySetByUser) {
                        Text(stringResource(R.string.category_set_by_you), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onChangeCategory) { Text(stringResource(R.string.change_category)) }
            }
        } else {
            DetailRow(label = stringResource(R.string.category), value = categoryName)
        }
        DetailRow(label = stringResource(R.string.account), value = accountName)
        DetailRow(label = stringResource(R.string.source), value = sourceLabel(transaction.source))
        if (transaction.pending) {
            DetailRow(label = stringResource(R.string.detail_status), value = stringResource(R.string.detail_needs_review))
        }
        if (!transaction.notes.isNullOrBlank()) {
            DetailRow(label = stringResource(R.string.note), value = transaction.notes)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onDelete,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.finances.expense),
            modifier = Modifier.fillMaxWidth(),
            shape = Radii.control
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.delete))
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    // Neither side may push the other out: a long note or account name wraps (right-aligned) and the
    // label keeps its share, so the two never collide at a large font scale.
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f, fill = false).padding(start = Spacing.m)
        )
    }
}

@Composable
fun AddTransactionDialog(
    accounts: List<com.teppe21.finances.domain.model.Account>,
    categories: List<com.teppe21.finances.domain.model.Category>,
    defaultCurrency: String = "HUF",
    onDismiss: () -> Unit,
    onConfirm: (
        accountId: String,
        amountMinor: Long,
        currency: String,
        direction: TransactionDirection,
        description: String,
        merchant: String?,
        categoryId: String?,
        date: LocalDate
    ) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    val manualEntryLabel = stringResource(R.string.manual_entry)
    var selectedCurrency by remember { mutableStateOf(defaultCurrency) }
    var descriptionText by remember { mutableStateOf("") }
    var merchantText by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "acc_otp") }
    var selectedCategoryId by remember { mutableStateOf("food") }
    val parsedAmount = Money.parseUserInput(amountText)?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_transaction)) },
        text = {
            DialogColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val expenseColor by animateColorAsState(
                        if (isExpense) MaterialTheme.finances.expense else MaterialTheme.colorScheme.surfaceVariant,
                        Motion.short(), label = "expenseToggle"
                    )
                    val incomeColor by animateColorAsState(
                        if (!isExpense) MaterialTheme.finances.income else MaterialTheme.colorScheme.surfaceVariant,
                        Motion.short(), label = "incomeToggle"
                    )
                    Button(
                        onClick = { isExpense = true },
                        colors = ButtonDefaults.buttonColors(containerColor = expenseColor),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.expenses))
                    }
                    Button(
                        onClick = { isExpense = false },
                        colors = ButtonDefaults.buttonColors(containerColor = incomeColor),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.income))
                    }
                }

                Text(
                    text = stringResource(R.string.currency),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CurrencyChips(
                    selected = selectedCurrency,
                    onSelect = { selectedCurrency = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { "${it.code} (${it.symbol})" }
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        amountText = input.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(15)
                    },
                    label = { Text(stringResource(R.string.amount) + " (${selectedCurrency})") },
                    isError = amountText.isNotBlank() && parsedAmount == null,
                    supportingText = {
                        if (amountText.isNotBlank() && parsedAmount == null) Text(stringResource(R.string.amount_format_error))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                val context = LocalContext.current
                Text(
                    text = stringResource(R.string.category),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(categoryLabel(cat), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = merchantText,
                    onValueChange = { merchantText = it },
                    label = { Text(stringResource(R.string.merchant)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = { Text(stringResource(R.string.note)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minor = parsedAmount ?: return@Button
                    val dir = if (isExpense) TransactionDirection.EXPENSE else TransactionDirection.INCOME
                    val desc = descriptionText.ifBlank { merchantText.ifBlank { manualEntryLabel } }
                    onConfirm(selectedAccountId, if (isExpense) -minor else minor, selectedCurrency, dir, desc, merchantText.ifBlank { null }, selectedCategoryId, LocalDate.now())
                },
                enabled = parsedAmount != null && accounts.isNotEmpty()
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
