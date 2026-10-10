package com.teppe21.finances.core.ui

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
import com.teppe21.finances.core.theme.Emerald500
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.core.utils.DateFormatter
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.model.TransactionDirection

val TransferColor = Color(0xFF6366F1)

/** Card with a title, used for every dashboard / settings section. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
            }
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
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
 * One transaction. [convertedAmountMinor] is the amount in [displayCurrency]; null means
 * no exchange rate is available, in which case the original amount is shown with its own
 * currency instead of being relabelled.
 */
@Composable
fun TransactionRow(
    transaction: Transaction,
    displayCurrency: String,
    convertedAmountMinor: Long?,
    onClick: () -> Unit
) {
    val isIncome = transaction.direction == TransactionDirection.INCOME ||
        (transaction.direction == TransactionDirection.REFUND && transaction.amountMinor > 0)
    val isTransfer = transaction.direction == TransactionDirection.TRANSFER
    val sameCurrency = displayCurrency.equals(transaction.currency, ignoreCase = true)
    val title = transaction.merchant?.ifBlank { null } ?: transaction.description

    val primaryAmount = when {
        sameCurrency -> CurrencyFormatter.formatSigned(transaction.amountMinor, transaction.currency)
        convertedAmountMinor != null -> "≈ " + CurrencyFormatter.formatSigned(convertedAmountMinor, displayCurrency)
        else -> CurrencyFormatter.formatSigned(transaction.amountMinor, transaction.currency)
    }
    val details = buildList {
        add(DateFormatter.format(transaction.date))
        add(transaction.source.getDisplayName())
        if (!sameCurrency && convertedAmountMinor != null) add(CurrencyFormatter.formatSigned(transaction.amountMinor, transaction.currency))
        if (!sameCurrency && convertedAmountMinor == null) add("no $displayCurrency rate")
        if (transaction.pending) add("needs review")
    }.joinToString(" · ")

    val accent = when {
        isTransfer -> TransferColor
        isIncome -> Emerald500
        else -> MaterialTheme.colorScheme.onSurface
    }
    val directionLabel = when {
        isTransfer -> "Transfer"
        isIncome -> "Income"
        else -> "Expense"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$directionLabel, $title, $primaryAmount, $details" },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
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
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(CategoryColorProvider.getColor(transaction.categoryId))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                primaryAmount,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1
            )
        }
    }
}
