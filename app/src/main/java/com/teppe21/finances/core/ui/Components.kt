package com.teppe21.finances.core.ui

import com.teppe21.finances.core.theme.Sizes
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.heading
import com.teppe21.finances.core.theme.Spacing
import com.teppe21.finances.core.theme.Radii
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.teppe21.finances.core.theme.CategoryColorProvider
import com.teppe21.finances.core.theme.finances
import com.teppe21.finances.core.theme.tabular
import androidx.compose.ui.text.style.TextAlign
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.core.utils.DateFormatter
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.R
import com.teppe21.finances.feature.categories.sourceLabel
import androidx.compose.ui.res.stringResource
import com.teppe21.finances.domain.model.TransactionDirection

/**
 * Card with a title, used for every section. An optional [subtitle] sits on its own line under
 * the title in quieter text (hints, explanations), never next to it.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Radii.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.l)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            }
            if (subtitle != null) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (title != null || subtitle != null) Spacer(Modifier.height(Spacing.s))
            content()
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    SectionCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                Spacer(Modifier.height(4.dp))
                action()
            }
        }
    }
}

/** Neutral information strip (exchange-rate gaps, review queue, stale data). */
@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Default.Info, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(16.dp)).pressClickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/**
 * One transaction: the merchant (one line, ellipsized) with the amount on the same line, and
 * under it, quieter, the category, date and any status. The amount is never truncated (see
 * [AmountRowLayout]). [convertedAmountMinor] is the amount in [displayCurrency]; null means no
 * exchange rate is available, and the original amount is then shown in its own currency.
 */
@Composable
fun TransactionRow(
    transaction: Transaction,
    displayCurrency: String,
    convertedAmountMinor: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    categoryName: String? = null
) {
    val isIncome = transaction.direction == TransactionDirection.INCOME ||
        (transaction.direction == TransactionDirection.REFUND && transaction.amountMinor > 0)
    val isTransfer = transaction.direction.isTransfer
    val sameCurrency = displayCurrency.equals(transaction.currency, ignoreCase = true)
    val title = transaction.merchant?.ifBlank { null } ?: transaction.description

    val primaryAmount = when {
        sameCurrency -> CurrencyFormatter.formatSigned(transaction.amountMinor, transaction.currency)
        convertedAmountMinor != null -> "≈ " + CurrencyFormatter.formatSigned(convertedAmountMinor, displayCurrency)
        else -> CurrencyFormatter.formatSigned(transaction.amountMinor, transaction.currency)
    }
    val details = buildList {
        categoryName?.let { add(it) }
        add(DateFormatter.format(transaction.date))
        if (!sameCurrency && convertedAmountMinor != null) add(CurrencyFormatter.formatSigned(transaction.amountMinor, transaction.currency))
        if (!sameCurrency && convertedAmountMinor == null) add(stringResource(R.string.row_no_rate, displayCurrency))
        if (transaction.pending) add(stringResource(R.string.row_needs_review))
    }.joinToString(" · ")

    val accent = when {
        isTransfer -> MaterialTheme.finances.transfer
        isIncome -> MaterialTheme.finances.income
        else -> MaterialTheme.finances.expense
    }
    val directionLabel = when {
        transaction.direction == TransactionDirection.PERSONAL_TRANSFER -> stringResource(R.string.kind_personal_transfer)
        isTransfer -> stringResource(R.string.kind_transfer)
        isIncome -> stringResource(R.string.kind_income)
        else -> stringResource(R.string.kind_expense)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radii.row)
            .pressClickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$directionLabel, $title, $primaryAmount, $details" },
        shape = Radii.row,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = Sizes.row + Spacing.s)
                .padding(horizontal = Spacing.m + Spacing.xxs, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(Sizes.leadingIcon)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isTransfer -> Icons.Default.SwapHoriz
                        isIncome -> Icons.Default.ArrowDownward
                        else -> Icons.Default.ArrowUpward
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                AmountRowLayout(
                    label = {
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    amount = { AmountText(primaryAmount, color = accent) }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(CategoryColorProvider.getColor(transaction.categoryId))
                    )
                    Spacer(Modifier.width(Spacing.xs + Spacing.xxs))
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
