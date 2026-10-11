package com.teppe21.finances.native.csv

import com.teppe21.finances.domain.model.BuiltIns
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.domain.model.TransactionSource
import com.teppe21.finances.domain.usecase.DeduplicationStatus
import com.teppe21.finances.domain.usecase.Fingerprints
import com.teppe21.finances.domain.usecase.IngestionInput
import com.teppe21.finances.domain.usecase.IngestionOutput

/** How parsed CSV rows become ingestion inputs. Shared by the import screen and the tests. */
object CsvImport {

    /** Fingerprints per row; identical rows in one file get increasing occurrence numbers. */
    fun fingerprints(accountId: String, rows: List<ParsedCsvRow>): List<String> {
        val seen = mutableMapOf<String, Int>()
        return rows.map { row ->
            val base = "${row.date}|${row.amountMinor}|${row.currency}|${row.fingerprintText}"
            val occurrence = seen.getOrDefault(base, 0)
            seen[base] = occurrence + 1
            Fingerprints.csvRow(accountId, row.date, row.amountMinor, row.currency, row.fingerprintText, occurrence)
        }
    }

    fun input(row: ParsedCsvRow, accountId: String, fingerprint: String) = IngestionInput(
        accountId = accountId,
        date = row.date,
        amountMinor = row.amountMinor,
        currency = row.currency,
        direction = row.direction,
        description = row.description,
        merchant = row.merchant,
        source = TransactionSource.CSV,
        fingerprint = fingerprint,
        bankType = row.bankType,
        bankCategoryId = row.bankCategoryId
    )
}

/** What one import did, shown when it finishes. */
data class ImportSummary(
    val imported: Int = 0,
    /** Rows that were already in this account from an earlier import of the same file. */
    val alreadyImported: Int = 0,
    /** Rows the parser could not read (bad date, amount, currency) or that never completed. */
    val unreadable: Int = 0,
    val categorized: Int = 0,
    val uncategorized: Int = 0,
    /** Moves between own accounts, exchanges, top-ups, cash withdrawals. */
    val ownTransfers: Int = 0,
    /** Money sent to or received from other people. */
    val personalTransfers: Int = 0
) {
    val skipped: Int get() = alreadyImported + unreadable

    companion object {
        fun of(outputs: List<IngestionOutput>, unreadable: Int): ImportSummary {
            val created = outputs.mapNotNull { it.transaction }
            return ImportSummary(
                imported = created.size,
                alreadyImported = outputs.count { it.status == DeduplicationStatus.EXACT_DUPLICATE },
                unreadable = unreadable,
                categorized = created.count { it.categoryId != BuiltIns.UNCATEGORIZED },
                uncategorized = created.count { it.categoryId == BuiltIns.UNCATEGORIZED },
                ownTransfers = created.count { it.direction == TransactionDirection.TRANSFER },
                personalTransfers = created.count { it.direction == TransactionDirection.PERSONAL_TRANSFER }
            )
        }
    }
}
