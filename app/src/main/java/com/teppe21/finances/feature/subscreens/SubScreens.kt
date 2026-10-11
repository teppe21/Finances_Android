package com.teppe21.finances.feature.subscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.core.theme.finances
import com.teppe21.finances.core.ui.EmptyState
import com.teppe21.finances.core.ui.ListRow
import com.teppe21.finances.core.ui.SectionCard
import com.teppe21.finances.R
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.core.utils.DateFormatter
import java.time.format.DateTimeFormatter

@Composable
fun RecurringScreen() {
    val app = FinancesApp.instance
    val transactions by app.transactionRepository.getAllTransactionsFlow().collectAsState(initial = emptyList())
    val recurringResult = remember(transactions) {
        app.detectRecurringUseCase.execute(transactions)
    }
    val detectedRules = recurringResult.detectedRules

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Text(
                text = stringResource(R.string.recurring),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (detectedRules.isNotEmpty()) {
            items(detectedRules) { rule ->
                val nextFormatted = rule.nextDate?.let { DateFormatter.format(it) } ?: ""
                SectionCard {
                    // The shared row: the amount is never squeezed or set in monospace, the name is ellipsized.
                    ListRow(
                        title = rule.merchant ?: rule.descriptionPattern,
                        subtitle = if (nextFormatted.isNotBlank()) stringResource(R.string.recurring_monthly_next, nextFormatted) else stringResource(R.string.recurring_monthly),
                        amount = CurrencyFormatter.format(rule.estimatedAmountMinor, rule.currency),
                        titleWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            // Empty State
            item { EmptyState(stringResource(R.string.no_recurring_yet), stringResource(R.string.no_recurring_desc)) }
        }

        item { Spacer(modifier = Modifier.height(88.dp)) }
    }
}
