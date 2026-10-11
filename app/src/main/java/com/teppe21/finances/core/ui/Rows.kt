package com.teppe21.finances.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.teppe21.finances.core.theme.Sizes
import com.teppe21.finances.core.theme.Spacing
import com.teppe21.finances.core.theme.amount
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A name on the start side and an amount on the end side. The amount is measured first at its
 * full width and is never truncated; the name gets what is left. When that would leave the name
 * narrower than [minLabelWidth] (scaled with the font scale; very large amounts, narrow screens), the
 * amount moves under the name, still aligned to the end, instead of squeezing it.
 *
 * Before this existed, a long merchant name could take the whole row and leave the amount a
 * width of zero: it then wrapped one character per line, disappeared and made the row tall.
 */
@Composable
fun AmountRowLayout(
    label: @Composable () -> Unit,
    amount: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = Spacing.m,
    minLabelWidth: Dp = Sizes.minLabelWidth
) {
    Layout(
        content = {
            Box(Modifier.layoutId("label")) { label() }
            Box(Modifier.layoutId("amount")) { amount() }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val gapPx = gap.roundToPx()
        val amountPlaceable = measurables.first { it.layoutId == "amount" }.measure(Constraints(maxWidth = width))
        val labelMeasurable = measurables.first { it.layoutId == "label" }
        val room = width - amountPlaceable.width - gapPx
        // The name is text, so the room it needs grows with the font scale.
        val neededForLabel = (minLabelWidth.toPx() * fontScale.coerceAtLeast(1f)).roundToInt()
        if (room >= neededForLabel) {
            val labelPlaceable = labelMeasurable.measure(Constraints(maxWidth = room))
            val height = max(max(labelPlaceable.height, amountPlaceable.height), constraints.minHeight)
            layout(width, height) {
                labelPlaceable.place(0, (height - labelPlaceable.height) / 2)
                amountPlaceable.place(width - amountPlaceable.width, (height - amountPlaceable.height) / 2)
            }
        } else {
            val labelPlaceable = labelMeasurable.measure(Constraints(maxWidth = width))
            val height = max(labelPlaceable.height + amountPlaceable.height, constraints.minHeight)
            layout(width, height) {
                labelPlaceable.place(0, 0)
                amountPlaceable.place(width - amountPlaceable.width, labelPlaceable.height)
            }
        }
    }
}

/** An amount as text: tabular figures, one line, never ellipsized. */
@Composable
fun AmountText(text: String, modifier: Modifier = Modifier, color: Color = LocalContentColor.current, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.amount) {
    Text(text, modifier = modifier, style = style, color = color, maxLines = 1, softWrap = false, textAlign = TextAlign.End)
}

/**
 * The one list row used in cards (category legends, top merchants, summaries): an optional
 * leading element, a title (up to two lines, ellipsized), an optional quieter subtitle, and an
 * amount that is never truncated. Every row has the same minimum height and padding, and reads
 * as one element to screen readers.
 */
@Composable
fun ListRow(
    title: String,
    amount: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
    titleWeight: FontWeight = FontWeight.Medium,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle != null) Sizes.row else Sizes.touch)
            .padding(vertical = Spacing.xs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Spacing.m))
        }
        AmountRowLayout(
            modifier = Modifier.weight(1f),
            label = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    // A third line at large font scales, so a long category or merchant name stays recognisable.
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = titleWeight, maxLines = if (LocalDensity.current.fontScale >= 1.3f) 3 else 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            amount = { AmountText(amount, color = amountColor) }
        )
    }
}

/**
 * A quieter label and a value, for figures in summaries ("Average expense ... 6,529 Ft"), with
 * an optional [detail] line under the label (the merchant of the largest expense).
 */
@Composable
fun ValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    detail: String? = null
) {
    AmountRowLayout(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touch)
            .semantics(mergeDescendants = true) {},
        label = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        amount = { AmountText(value, color = valueColor) }
    )
}
