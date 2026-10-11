package com.teppe21.finances.feature.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.teppe21.finances.R
import com.teppe21.finances.core.theme.CategoryColorProvider
import com.teppe21.finances.domain.model.BuiltIns
import com.teppe21.finances.domain.model.Category

/**
 * Picks a category for one transaction or a group of them. With [offerRule] the user can also
 * keep the choice as a keyword rule ([suggestedKeyword], editable) that is applied to every
 * matching transaction; [onApply] receives the keyword, or null when no rule is wanted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorizeDialog(
    title: String,
    categories: List<Category>,
    initialCategoryId: String?,
    suggestedKeyword: String,
    offerRule: Boolean,
    onDismiss: () -> Unit,
    onApply: (categoryId: String, ruleKeyword: String?) -> Unit
) {
    var selected by rememberSaveable { mutableStateOf(initialCategoryId?.takeIf { it != BuiltIns.UNCATEGORIZED }) }
    var createRule by rememberSaveable { mutableStateOf(offerRule) }
    var keyword by rememberSaveable { mutableStateOf(suggestedKeyword) }
    val choices = remember(categories) { categories.filter { it.id != BuiltIns.UNCATEGORIZED }.sortedBy { it.displayName().lowercase() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.categorize_title, title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    choices.forEach { cat ->
                        FilterChip(
                            selected = selected == cat.id,
                            onClick = { selected = cat.id },
                            label = { Text(cat.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Spacer(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(CategoryColorProvider.getColor(cat))
                                )
                            }
                        )
                    }
                }
                if (offerRule) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = createRule, role = Role.Switch, onValueChange = { createRule = it }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.categorize_create_rule), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.categorize_create_rule_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = createRule, onCheckedChange = null)
                    }
                    if (createRule) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = keyword,
                            onValueChange = { keyword = it },
                            label = { Text(stringResource(R.string.categorize_keyword)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selected?.let { onApply(it, if (offerRule && createRule) keyword.trim().ifBlank { null } else null) } },
                enabled = selected != null && (!offerRule || !createRule || keyword.isNotBlank())
            ) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
