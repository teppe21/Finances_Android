package com.teppe21.finances.feature.categories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teppe21.finances.R
import com.teppe21.finances.core.theme.tabular
import com.teppe21.finances.core.ui.EmptyState
import com.teppe21.finances.core.ui.Motion
import com.teppe21.finances.core.ui.SectionCard
import com.teppe21.finances.core.ui.pressClickable
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.domain.usecase.UncategorizedGroup

/**
 * Transactions no rule recognized, grouped by merchant. One choice per group, optionally kept
 * as a rule that also sorts every matching transaction, plus a "re-apply rules" action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UncategorizedScreen(viewModel: UncategorizedViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.message) {
        val text = when (val m = state.message) {
            is RecategorizeMessage.Updated -> resources.getQuantityString(R.plurals.recategorize_updated, m.count, m.count)
            is RecategorizeMessage.RuleSaved -> resources.getQuantityString(R.plurals.recategorize_rule_saved, m.count, m.count)
            null -> return@LaunchedEffect
        }
        // Cleared only after it was shown: clearing first would restart this effect and cancel it.
        snackbar.showSnackbar(text)
        viewModel.messageShown()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "intro") {
                SectionCard {
                    Text(
                        stringResource(R.string.uncategorized_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.heightIn(min = 12.dp))
                    OutlinedButton(onClick = viewModel::reapplyRules, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reapply_rules))
                    }
                    Text(
                        stringResource(R.string.reapply_rules_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            if (!state.loading && state.groups.isEmpty()) {
                item(key = "empty") {
                    EmptyState(stringResource(R.string.uncategorized_empty_title), stringResource(R.string.uncategorized_empty_desc))
                }
            }
            items(state.groups, key = { it.key }) { group ->
                GroupRow(group, onClick = { editing = group.key }, modifier = Modifier.animateItemPlacement(Motion.medium()))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    val group = state.groups.firstOrNull { it.key == editing }
    if (group != null) {
        CategorizeDialog(
            title = group.displayName,
            categories = state.categories,
            initialCategoryId = null,
            suggestedKeyword = group.suggestedKeyword,
            offerRule = true,
            onDismiss = { editing = null },
            onApply = { categoryId, keyword ->
                editing = null
                viewModel.categorize(group, categoryId, keyword)
            }
        )
    }
}

@Composable
private fun GroupRow(group: UncategorizedGroup, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val totals = group.totals.entries.joinToString(" + ") { (currency, minor) -> CurrencyFormatter.format(minor, currency) }
    val count = pluralStringResource(R.plurals.transaction_count, group.count, group.count)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .pressClickable(onClick = onClick)
            .semantics { contentDescription = "${group.displayName}, $count, $totals" },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    group.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                totals,
                style = MaterialTheme.typography.bodyLarge.tabular(),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
