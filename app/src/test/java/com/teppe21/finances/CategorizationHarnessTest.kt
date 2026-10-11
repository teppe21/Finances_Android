package com.teppe21.finances

import com.teppe21.finances.domain.model.BuiltIns
import com.teppe21.finances.domain.usecase.CategoryRuleSource
import com.teppe21.finances.domain.usecase.IngestTransactionUseCase
import com.teppe21.finances.native.csv.CsvImport
import com.teppe21.finances.native.csv.CsvParser
import com.teppe21.finances.support.InMemoryTransactionStore
import com.teppe21.finances.support.SyntheticStatements
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Diagnostic harness: imports the synthetic statements through the real parser, the import
 * mapping and the ingestion pipeline with the built-in rules, and reports how many rows end up
 * uncategorized or misclassified. The report is printed and written to
 * build/reports/categorization-harness.txt. docs/IMPORT_FORMATS.md records the numbers before
 * and after the schema-6 categorization work.
 */
class CategorizationHarnessTest {

    data class Outcome(val bank: String, val description: String, val expectedKind: String, val expectedCategory: String, val kind: String, val category: String)

    @Test
    fun `categorization report on synthetic statements`() = runTest {
        val report = StringBuilder()
        val outcomes = mutableListOf<Outcome>()
        var totalRows = 0
        var totalSkipped = 0
        var expectedSkips = 0

        for (statement in SyntheticStatements.all) {
            val store = InMemoryTransactionStore()
            val ingest = IngestTransactionUseCase(store, CategoryRuleSource { BuiltIns.allRules })
            val parsed = CsvParser.parseText(statement.csv, defaultCurrency = "HUF")
            totalRows += statement.rows.size
            totalSkipped += parsed.skippedCount
            expectedSkips += statement.rows.count { it.category == "skip" }
            report.appendLine("## ${statement.bank}: ${statement.rows.size} rows, ${parsed.rows.size} parsed, skipped ${parsed.skipped}")

            val fingerprints = CsvImport.fingerprints("acc", parsed.rows)
            for ((row, fp) in parsed.rows.zip(fingerprints)) {
                val tx = ingest.execute(CsvImport.input(row, "acc", fp)).transaction ?: continue
                val expected = statement.rows[row.line - 2]
                outcomes += Outcome(statement.bank, tx.description, expected.kind, expected.category, tx.direction.name, tx.categoryId ?: "null")
            }
        }

        val imported = outcomes.size
        val uncategorized = outcomes.count { it.category == "other" }
        val wrongKind = outcomes.filter { it.kind != it.expectedKind }
        val wrongCategory = outcomes.filter { it.category != it.expectedCategory }
        val transfersInSpending = outcomes.count { it.expectedKind in TRANSFER_KINDS && it.kind !in TRANSFER_KINDS }
        val spendingAsTransfer = outcomes.count { it.expectedKind !in TRANSFER_KINDS && it.kind in TRANSFER_KINDS }
        val expectedOther = outcomes.count { it.expectedCategory == "other" }

        report.appendLine()
        report.appendLine("rows in files: $totalRows, imported: $imported, skipped: $totalSkipped (rows marked reverted in the file: $expectedSkips)")
        report.appendLine("uncategorized (other): $uncategorized = ${pct(uncategorized, imported)} (rows a person would also leave in other: $expectedOther)")
        report.appendLine("wrong kind: ${wrongKind.size} = ${pct(wrongKind.size, imported)}; transfers counted as income/expense: $transfersInSpending; income/expense counted as transfer: $spendingAsTransfer")
        report.appendLine("wrong category: ${wrongCategory.size} = ${pct(wrongCategory.size, imported)}")
        report.appendLine()
        report.appendLine("bank | description | expected kind/category | got kind/category")
        for (o in outcomes.filter { it.kind != it.expectedKind || it.category != it.expectedCategory }) {
            report.appendLine("${o.bank} | ${o.description} | ${o.expectedKind}/${o.expectedCategory} | ${o.kind}/${o.category}")
        }

        println(report)
        File("build/reports").mkdirs()
        File("build/reports/categorization-harness.txt").writeText(report.toString())

        // Regression guards for the numbers documented in IMPORT_FORMATS.md.
        assertEquals("every row except the reverted one is imported", totalRows - expectedSkips, imported)
        assertEquals("no transfer may count as income or spending", 0, transfersInSpending)
        assertEquals("no income or spending may hide as a transfer", 0, spendingAsTransfer)
        assertTrue("uncategorized rows must be the genuinely unknown ones", uncategorized <= expectedOther)
    }

    /** Reported only: the holdout estimates how the rules generalize, it is not a target. */
    @Test
    fun `holdout report`() = runTest {
        val statement = SyntheticStatements.holdout
        val ingest = IngestTransactionUseCase(InMemoryTransactionStore(), CategoryRuleSource { BuiltIns.allRules })
        val parsed = CsvParser.parseText(statement.csv, defaultCurrency = "HUF")
        val outcomes = parsed.rows.zip(CsvImport.fingerprints("acc", parsed.rows)).mapNotNull { (row, fp) ->
            val tx = ingest.execute(CsvImport.input(row, "acc", fp)).transaction ?: return@mapNotNull null
            val expected = statement.rows[row.line - 2]
            Outcome(statement.bank, tx.description, expected.kind, expected.category, tx.direction.name, tx.categoryId ?: "null")
        }
        val report = StringBuilder()
        report.appendLine("## Holdout: ${statement.rows.size} rows, ${outcomes.size} imported")
        report.appendLine("uncategorized (other): ${outcomes.count { it.category == "other" }} = ${pct(outcomes.count { it.category == "other" }, outcomes.size)} (expected other: ${outcomes.count { it.expectedCategory == "other" }})")
        report.appendLine("wrong kind: ${outcomes.count { it.kind != it.expectedKind }}; wrong category: ${outcomes.count { it.category != it.expectedCategory }}")
        for (o in outcomes.filter { it.kind != it.expectedKind || it.category != it.expectedCategory }) {
            report.appendLine("${o.description} | ${o.expectedKind}/${o.expectedCategory} | ${o.kind}/${o.category}")
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/categorization-holdout.txt").writeText(report.toString())
        assertEquals(statement.rows.size, outcomes.size)
    }

    private fun pct(n: Int, of: Int) = if (of == 0) "n/a" else "%.1f%%".format(n * 100.0 / of)

    private companion object {
        val TRANSFER_KINDS = setOf("TRANSFER", "PERSONAL_TRANSFER")
    }
}
