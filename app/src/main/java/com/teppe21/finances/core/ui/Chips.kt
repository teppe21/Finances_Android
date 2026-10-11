package com.teppe21.finances.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.teppe21.finances.core.theme.Spacing
import com.teppe21.finances.domain.model.AppCurrency
import com.teppe21.finances.domain.model.SupportedCurrencies

/**
 * A short, fixed set of choices as chips. They wrap onto further lines instead of being squeezed:
 * in a plain `Row` the last chip of five currencies was given a few dp and its label stacked one
 * letter per line at 360 dp or at a large font scale.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        content = { content() }
    )
}

/** One chip per supported currency; [label] defaults to the code. */
@Composable
fun CurrencyChips(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (AppCurrency) -> String = { it.code }
) {
    ChipFlow(modifier) {
        SupportedCurrencies.ALL.forEach { c ->
            FilterChip(
                selected = selected.equals(c.code, ignoreCase = true),
                onClick = { onSelect(c.code) },
                label = { Text(label(c)) }
            )
        }
    }
}
