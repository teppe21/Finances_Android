package com.teppe21.finances

import com.teppe21.finances.core.utils.TextNormalizer
import com.teppe21.finances.domain.model.BuiltIns
import com.teppe21.finances.domain.model.CategoryRule
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.domain.model.TransactionSource
import com.teppe21.finances.domain.usecase.CalculateFinancialStatsUseCase
import com.teppe21.finances.domain.usecase.CategorizeTransactionUseCase
import com.teppe21.finances.domain.usecase.CategoryRuleSource
import com.teppe21.finances.domain.usecase.ClassificationInput
import com.teppe21.finances.domain.usecase.Fingerprints
import com.teppe21.finances.domain.usecase.IngestTransactionUseCase
import com.teppe21.finances.domain.usecase.IngestionInput
import com.teppe21.finances.domain.usecase.Recategorizer
import com.teppe21.finances.domain.usecase.ReapplyRulesUseCase
import com.teppe21.finances.domain.usecase.UncategorizedGroups
import com.teppe21.finances.domain.usecase.withUserCategory
import com.teppe21.finances.native.csv.CsvImport
import com.teppe21.finances.native.csv.CsvParser
import com.teppe21.finances.native.csv.CsvSkipReason
import com.teppe21.finances.native.csv.ImportSummary
import com.teppe21.finances.support.InMemoryTransactionStore
import com.teppe21.finances.support.tx
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The five cases seen on a real device (with invented names and amounts) and the matching rules behind them. */
class TransferAndCategoryTest {

    private val rules = BuiltIns.allRules
    private val categorize = CategorizeTransactionUseCase()

    private suspend fun ingestCsvLike(description: String, amountMinor: Long, currency: String = "HUF", bankType: String? = null, store: InMemoryTransactionStore = InMemoryTransactionStore(), accountId: String = "acc"): Transaction {
        val ingest = IngestTransactionUseCase(store, CategoryRuleSource { rules })
        return ingest.execute(
            IngestionInput(
                accountId = accountId,
                date = LocalDate.of(2026, 9, 10),
                amountMinor = amountMinor,
                currency = currency,
                direction = if (amountMinor > 0) TransactionDirection.INCOME else TransactionDirection.EXPENSE,
                description = description,
                merchant = TextNormalizer.cleanMerchantName(description),
                source = TransactionSource.CSV,
                fingerprint = Fingerprints.csvRow(accountId, LocalDate.of(2026, 9, 10), amountMinor, currency, description, 0),
                bankType = bankType
            )
        ).transaction!!
    }

    @Test
    fun `balance migration is an own-account transfer, never spending or a merchant`() = runTest {
        val t = ingestCsvLike("Balance migration to another region or legal entity", -1_234_56L, bankType = "TRANSFER")
        assertEquals(TransactionDirection.TRANSFER, t.direction)
        assertEquals("transfers", t.categoryId)
        assertNull(t.merchant)
    }

    @Test
    fun `currency exchange legs are transfers and stay out of income, spending and trends`() = runTest {
        val huf = ingestCsvLike("Exchanged to EUR", -98_765_00L)
        val eur = ingestCsvLike("Exchanged to EUR", 250_00L, currency = "EUR")
        assertEquals(TransactionDirection.TRANSFER, huf.direction)
        assertEquals(TransactionDirection.TRANSFER, eur.direction)
        assertEquals(TransactionDirection.TRANSFER, ingestCsvLike("Exchanged from USD", 40_00L, currency = "USD").direction)
        assertEquals(TransactionDirection.TRANSFER, ingestCsvLike("Átváltás EUR", -40_000_00L).direction)
        assertEquals(TransactionDirection.TRANSFER, ingestCsvLike("ÁTVÁLTÁS", -1_00L).direction)
        assertEquals(TransactionDirection.TRANSFER, ingestCsvLike("Currency exchange", -1_00L).direction)

        val stats = CalculateFinancialStatsUseCase()
        val oneCurrency = listOf(huf, huf.copy(id = "x", amountMinor = 98_765_00L, fingerprint = "x"))
        val s = stats.execute(oneCurrency)
        assertEquals(0L, s.incomeMinor)
        assertEquals(0L, s.expenseMinor)
        assertEquals("only the outgoing leg is counted", 98_765_00L, s.transfersMinor)
        assertTrue(stats.calculateCategoryBreakdown(oneCurrency, emptyList()).isEmpty())
        assertTrue(stats.calculateMonthlyTrends(oneCurrency, 1, LocalDate.of(2026, 9, 30)).all { it.incomeMinor == 0L && it.expenseMinor == 0L })
    }

    @Test
    fun `state treasury is government and taxes, with the sign deciding income or expense`() = runTest {
        val paid = ingestCsvLike("Magyar Államkincstár", -57_921_00L)
        assertEquals("taxes", paid.categoryId)
        assertEquals(TransactionDirection.EXPENSE, paid.direction)
        val received = ingestCsvLike("Magyar Államkincstár · Családi pótlék", 20_000_00L)
        assertEquals("taxes", received.categoryId)
        assertEquals(TransactionDirection.INCOME, received.direction)
        assertEquals("taxes", ingestCsvLike("NAV · SZJA befizetés", -35_000_00L).categoryId)
    }

    @Test
    fun `transfer to a person is a personal transfer, not spending and not a merchant`() = runTest {
        val sent = ingestCsvLike("Transfer to Példa Anna", -12_000_00L, bankType = "TRANSFER")
        assertEquals(TransactionDirection.PERSONAL_TRANSFER, sent.direction)
        assertEquals(BuiltIns.PERSONAL_TRANSFER_CATEGORY, sent.categoryId)
        assertNull("a person's name is not a merchant", sent.merchant)
        val received = ingestCsvLike("Payment from Minta Gábor", 8_000_00L)
        assertEquals(TransactionDirection.PERSONAL_TRANSFER, received.direction)
        // A short merchant keyword inside a surname must not win ("mol" in "Molnár").
        assertEquals(TransactionDirection.PERSONAL_TRANSFER, ingestCsvLike("Transfer to Molnár Teszt", -4_000_00L).direction)

        val s = CalculateFinancialStatsUseCase().execute(listOf(sent, received))
        assertEquals(0L, s.expenseMinor)
        assertEquals(0L, s.incomeMinor)
        assertEquals(12_000_00L, s.personalSentMinor)
        assertEquals(8_000_00L, s.personalReceivedMinor)
    }

    @Test
    fun `an electronics brand is electronics, and a rule from one transaction sorts the existing ones`() = runTest {
        assertEquals("electronics", ingestCsvLike("EarFun", -15_990_00L).categoryId)

        // A merchant no rule knows, imported twice, then sorted with one "create rule" action.
        val store = InMemoryTransactionStore()
        val a = ingestCsvLike("Sample Gadget Shop", -9_990_00L, store = store)
        ingestCsvLike("SAMPLE GADGET SHOP 0042 BUDAPEST", -4_990_00L, store = store, accountId = "acc2")
        assertEquals("other", a.categoryId)
        val userRules = mutableListOf<CategoryRule>()
        val recategorizer = Recategorizer(
            transactions = { store.rows.toList() },
            updateAll = { list -> list.forEach { store.update(it) } },
            rules = CategoryRuleSource { rules + userRules },
            addUserRule = { c, k -> CategoryRule("user_1", c, k, priority = CategoryRule.PRIORITY_USER).also { userRules += it } }
        )
        val changed = recategorizer.createRuleAndApply("electronics", "sample gadget shop")
        assertEquals(2, changed!!.size)
        assertTrue(store.rows.all { it.categoryId == "electronics" })
    }

    @Test
    fun `ber must not match Uber, and short keywords must be whole words`() {
        val r = listOf(CategoryRule("r1", "income", "bér"), CategoryRule("r2", "transport", "uber"), CategoryRule("r3", "transport", "mol"))
        fun cat(text: String, amount: Long = -1) = categorize.classify(ClassificationInput(text, signedAmountMinor = amount), r).categoryId
        assertEquals("transport", cat("UBER *TRIP"))
        assertFalse(categorize.matches(r[0], ClassificationInput("Uber", signedAmountMinor = 1)))
        assertEquals("income", cat("Havi bér", amount = 1))
        assertFalse("bér must not match bérleti", categorize.matches(r[0], ClassificationInput("Bérleti díj", signedAmountMinor = 1)))
        assertTrue(categorize.matches(r[0], ClassificationInput("Havi bér", signedAmountMinor = 1)))
        assertEquals("transport", cat("MOL TÖLTŐÁLLOMÁS 123"))
        assertEquals("transport", cat("MOL-nál tankolás"))
        assertEquals("other", cat("Molnár Pékség"))
        assertEquals("other", cat("Kasparov chess club"))
    }

    @Test
    fun `text is folded for case, accents, apostrophes and joined words`() {
        fun cat(text: String) = categorize.classify(ClassificationInput(text, signedAmountMinor = -1), rules).categoryId
        assertEquals("dining", cat("McDonald's"))
        assertEquals("dining", cat("MCDONALDS 0042"))
        assertEquals("dining", cat("BURGERKING 0123"))
        assertEquals("electronics", cat("MEDIAMARKT ARKAD"))
        assertEquals("food", cat("SPAR 1234 BUDAPEST HU"))
        assertEquals("food", cat("CARD PAYMENT TO SPAR 1234 BUDAPEST HU"))
        assertEquals("transfers", cat("SAJÁT SZÁMLÁK KÖZÖTTI ÁTUTALÁS"))
        assertEquals("transfers", cat("Saját számlák közötti átvezetés"))
        assertEquals("transport", cat("MÁV-START JEGYAUTOMATA"))
    }

    @Test
    fun `longer keywords win ties and exact rules match a whole field`() {
        fun cat(text: String, merchant: String? = null) = categorize.classify(ClassificationInput(text, merchant, signedAmountMinor = -1), rules).categoryId
        assertEquals("dining", cat("Uber Eats"))
        assertEquals("transport", cat("Uber"))
        assertEquals("dining", cat("Bolt Food"))
        assertEquals("transport", cat("Bolt", merchant = "Bolt"))
        assertEquals("a corner shop (bolt) is not a taxi", "other", cat("Sarki bolt"))
    }

    @Test
    fun `salary keywords never turn a payment into income`() {
        fun c(text: String, amount: Long) = categorize.classify(ClassificationInput(text, signedAmountMinor = amount), rules)
        assertEquals("income", c("Fizetés szeptember", 450_000_00).categoryId)
        val payment = c("Kártyás fizetés", -2_000_00)
        assertEquals("other", payment.categoryId)
        assertEquals(TransactionDirection.EXPENSE, payment.direction)
    }

    @Test
    fun `user rules beat built-in rules, including transfer wording`() {
        val user = CategoryRule("u", "housing", "transfer to landlord", priority = CategoryRule.PRIORITY_USER)
        val c = categorize.classify(ClassificationInput("Transfer to Landlord Sample", signedAmountMinor = -150_000_00), rules + user)
        assertEquals("housing", c.categoryId)
        assertEquals(TransactionDirection.EXPENSE, c.direction)
        val spar = CategoryRule("u2", "dining", "spar", priority = CategoryRule.PRIORITY_USER)
        assertEquals("dining", categorize.classify(ClassificationInput("SPAR", signedAmountMinor = -1), rules + spar).categoryId)
    }

    @Test
    fun `bank type columns decide what wording alone cannot`() {
        fun c(text: String, type: String?, amount: Long = -1_000_00) = categorize.classify(ClassificationInput(text, bankType = type, signedAmountMinor = amount), rules)
        assertEquals("transfers", c("Some text", "EXCHANGE").categoryId)
        assertEquals("cash", c("Raiffeisen", "ATM").categoryId)
        assertEquals("fees", c("Premium plan", "FEE").categoryId)
        assertEquals("transfers", c("By card", "TOPUP", 50_000_00).categoryId)
        assertEquals(TransactionDirection.PERSONAL_TRANSFER, c("To JOHN SAMPLE", "TRANSFER").direction)
        assertEquals("cash", c("OTP ATM 1234", "Készpénzfelvétel").categoryId)
        assertEquals("fees", c("", "Számlavezetési díj").categoryId)
        // A card purchase is never a transfer by keyword alone.
        assertEquals(TransactionDirection.EXPENSE, c("Transfer Bar & Grill", "CARD_PAYMENT").direction)
        assertEquals(TransactionDirection.EXPENSE, c("Transfer Bar & Grill", "Vásárlás kártyával").direction)
        // ... but a cash withdrawal with a card still is.
        assertEquals("cash", c("OTP ATM", "Készpénzfelvétel kártyával").categoryId)
    }

    @Test
    fun `own-account transfer legs are linked once`() = runTest {
        val store = InMemoryTransactionStore()
        val out = ingestCsvLike("Saját számlák közötti átutalás", -100_000_00L, store = store, accountId = "otp")
        val inLeg = ingestCsvLike("Saját számlák közötti átutalás", 100_000_00L, store = store, accountId = "revolut")
        val outStored = store.rows.first { it.id == out.id }
        assertNotNull(inLeg.transferId)
        assertEquals(inLeg.transferId, outStored.transferId)
        // A third, unrelated leg finds nothing left to link.
        val third = ingestCsvLike("Saját számlák közötti átutalás", 100_000_00L, store = store, accountId = "cash")
        assertNull(third.transferId)
    }

    @Test
    fun `every built-in rule id is unique and every rule points at a built-in category`() {
        val ids = BuiltIns.allRules.map { it.id }
        assertEquals(emptyList<String>(), ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.toList())
        val categories = BuiltIns.categories.map { it.id }.toSet()
        assertTrue(BuiltIns.allRules.all { it.categoryId in categories })
    }
}

class CsvBankHintsTest {

    @Test
    fun `value date column is not taken as the amount`() {
        val csv = """
            Számlaszám;Tranzakció típusa;Könyvelés dátuma;Értéknap;Összeg;Pénznem;Partner neve;Partner számlaszáma;Közlemény
            1;Vásárlás kártyával;2026.09.03.;2026.09.03.;-12 345;HUF;;;LIDL 0123 BUDAPEST HU
            1;Átutalás;2026.09.04.;2026.09.04.;-150 000;HUF;Bérbeadó Minta;11111111-22222222;Bérleti díj
        """.trimIndent()
        val result = CsvParser.parseText(csv)
        assertEquals(0, result.skippedCount)
        assertEquals(-1_234_500L, result.rows[0].amountMinor)
        assertEquals("Vásárlás kártyával", result.rows[0].bankType)
        assertEquals("LIDL 0123 BUDAPEST HU", result.rows[0].description)
        assertEquals("LIDL", result.rows[0].merchant)
        // Partner and remark are both kept; the account number is not a description.
        assertEquals("Bérbeadó Minta · Bérleti díj", result.rows[1].description)
    }

    @Test
    fun `reverted and declined rows are skipped and counted`() {
        val csv = """
            Type,Product,Started Date,Completed Date,Description,Amount,Fee,Currency,State,Balance
            CARD_PAYMENT,Current,2026-09-01 10:00:00,2026-09-02 10:00:00,Spar,-10.00,0.00,EUR,COMPLETED,90.00
            CARD_PAYMENT,Current,2026-09-01 11:00:00,,Lidl,-5.00,0.00,EUR,REVERTED,
            CARD_PAYMENT,Current,2026-09-01 12:00:00,,Aldi,-5.00,0.00,EUR,DECLINED,
            CARD_PAYMENT,Current,2026-09-01 13:00:00,,Penny,-5.00,0.00,EUR,PENDING,
        """.trimIndent()
        val result = CsvParser.parseText(csv)
        assertEquals(listOf("Spar", "Penny"), result.rows.map { it.description })
        assertEquals(2, result.skipped[CsvSkipReason.NOT_COMPLETED])
        assertEquals("CARD_PAYMENT", result.rows[0].bankType)
    }

    @Test
    fun `wise ids give the type and the repeated merchant column is dropped`() {
        val csv = """
            TransferWise ID,Date,Amount,Currency,Description,Payment Reference,Running Balance,Payee Name,Payee Account Number,Merchant
            CARD-1001,15-09-2026,-12.50,EUR,Card transaction of 12.50 EUR issued by Lidl Wien,,500.00,,,Lidl Wien
            TRANSFER-1002,16-09-2026,-100.00,EUR,Sent money to Minta Gabor,lunch,400.00,Minta Gabor,HU00,
        """.trimIndent()
        val rows = CsvParser.parseText(csv, defaultCurrency = "EUR").rows
        assertEquals("CARD", rows[0].bankType)
        assertEquals("Card transaction of 12.50 EUR issued by Lidl Wien", rows[0].description)
        assertEquals("Lidl Wien", rows[0].merchant)
        assertEquals("TRANSFER", rows[1].bankType)
        assertEquals("Sent money to Minta Gabor · lunch", rows[1].description)
    }

    @Test
    fun `a category column naming a built-in category is used as a hint`() {
        val csv = """
            Date,Description,Amount,Category
            2026-09-01,Corner Store 12,-4.00,Groceries
            2026-09-02,Something,-4.00,General
        """.trimIndent()
        val rows = CsvParser.parseText(csv, defaultCurrency = "EUR").rows
        assertEquals("food", rows[0].bankCategoryId)
        assertNull(rows[1].bankCategoryId)
    }

    @Test
    fun `fingerprints keep using the column earlier versions read, so re-imports add nothing`() {
        // Before schema 6 an MBH-style file used the remark column only (empty here).
        val csv = """
            Dátum;Megnevezés;Terhelés;Jóváírás;Pénznem;Közlemény
            2026.09.02.;VÁSÁRLÁS POS: ALDI 123;4 560;;HUF;
        """.trimIndent()
        val row = CsvParser.parseText(csv).rows.single()
        assertEquals("VÁSÁRLÁS POS: ALDI 123", row.description)
        assertEquals("Imported transaction", row.fingerprintText)
        val old = Fingerprints.csvRow("acc", row.date, row.amountMinor, row.currency, "Imported transaction", 0)
        assertEquals(old, CsvImport.fingerprints("acc", listOf(row)).single())
    }

    @Test
    fun `merchant cleaning removes card noise but never the whole name`() {
        assertEquals("SPAR", TextNormalizer.cleanMerchantName("SPAR 1234 BUDAPEST HU"))
        assertEquals("AUCHAN", TextNormalizer.cleanMerchantName("CARD PAYMENT TO AUCHAN SOROKSAR HU"))
        assertEquals("SPAR", TextNormalizer.cleanMerchantName("ERSTE KARTYAS VASARLAS SPAR 3311 DEBRECEN"))
        assertEquals("ALDI", TextNormalizer.cleanMerchantName("VÁSÁRLÁS POS: ALDI 123"))
        assertEquals("LIDL", TextNormalizer.cleanMerchantName("LIDL 0123 BUDAPEST HU 2026.09.02 *1234"))
        assertEquals("Lidl Wien", TextNormalizer.cleanMerchantName("Card transaction of 12.50 EUR issued by Lidl Wien"))
        assertEquals("Bolt Food", TextNormalizer.cleanMerchantName("Bolt Food"))
        assertEquals("Budapest", TextNormalizer.cleanMerchantName("Budapest"))
        assertEquals("Magyar Államkincstár", TextNormalizer.cleanMerchantName("Magyar Államkincstár"))
    }
}

class RecategorizationTest {

    private val now = Instant.parse("2026-10-01T00:00:00Z")

    private fun row(id: String, description: String, amount: Long, category: String? = "other", direction: TransactionDirection = if (amount > 0) TransactionDirection.INCOME else TransactionDirection.EXPENSE, byUser: Boolean = false) =
        tx(id, amount, direction = direction, categoryId = category).copy(description = description, merchant = description, categorySetByUser = byUser)

    @Test
    fun `re-applying rules fixes kinds and categories but never touches a category the user chose`() {
        val rows = listOf(
            row("exchange", "Exchanged to EUR", -100_00),
            row("person", "Transfer to Példa Anna", -50_00, category = "transfers", direction = TransactionDirection.EXPENSE),
            row("mine", "Exchanged to EUR", -100_00, category = "shopping", byUser = true),
            row("spar", "SPAR 1234", -10_00)
        )
        val changed = ReapplyRulesUseCase().execute(rows, BuiltIns.allRules, now = now).associateBy { it.id }
        assertEquals(TransactionDirection.TRANSFER, changed.getValue("exchange").direction)
        assertEquals(TransactionDirection.PERSONAL_TRANSFER, changed.getValue("person").direction)
        assertNull(changed.getValue("person").merchant)
        assertEquals("food", changed.getValue("spar").categoryId)
        assertFalse("user-chosen category", "mine" in changed)
    }

    @Test
    fun `re-applying never moves a categorized row back to other`() {
        // Placed by a bank type column at import, which is not stored: the rules cannot place it again.
        val row = row("atm", "Raiffeisen 0042", -20_000_00, category = "cash", direction = TransactionDirection.TRANSFER)
        assertTrue(ReapplyRulesUseCase().execute(listOf(row), BuiltIns.allRules, now = now).isEmpty())
    }

    @Test
    fun `a category chosen by hand sets the kind and is protected`() {
        val t = row("t", "Sample Landlord", -150_000_00).withUserCategory(BuiltIns.PERSONAL_TRANSFER_CATEGORY, now)
        assertEquals(TransactionDirection.PERSONAL_TRANSFER, t.direction)
        assertTrue(t.categorySetByUser)
        val back = t.withUserCategory("housing", now)
        assertEquals(TransactionDirection.EXPENSE, back.direction)
        val refund = row("r", "Decathlon", 50_00, direction = TransactionDirection.REFUND).withUserCategory("shopping", now)
        assertEquals(TransactionDirection.REFUND, refund.direction)
    }

    @Test
    fun `uncategorized rows are grouped by folded merchant, largest group first`() {
        val groups = UncategorizedGroups.from(
            listOf(
                row("a", "Szép Virág", -1_00),
                row("b", "SZÉP VIRÁG", -2_00),
                row("c", "Kis Bolt", -3_00),
                row("d", "Spar", -4_00, category = "food"),
                row("e", "Transfer to X", -5_00, direction = TransactionDirection.PERSONAL_TRANSFER)
            )
        )
        assertEquals(listOf("szep virag", "kis bolt"), groups.map { it.key })
        assertEquals(2, groups.first().count)
        assertEquals(mapOf("HUF" to 3_00L), groups.first().totals)
    }

    @Test
    fun `import summary counts what happened`() = runTest {
        val store = InMemoryTransactionStore()
        val ingest = IngestTransactionUseCase(store, CategoryRuleSource { BuiltIns.allRules })
        val csv = """
            Date;Description;Amount;Currency
            2026-09-01;SPAR;-1000;HUF
            2026-09-02;Exchanged to EUR;-5000;HUF
            2026-09-03;Transfer to Példa Anna;-2000;HUF
            2026-09-04;Unknown Shop 1;-300;HUF
            yesterday;Broken;-1;HUF
        """.trimIndent()
        val parsed = CsvParser.parseText(csv)
        suspend fun run() = parsed.rows.zip(CsvImport.fingerprints("acc", parsed.rows)).map { (r, fp) -> ingest.execute(CsvImport.input(r, "acc", fp)) }
        val first = ImportSummary.of(run(), unreadable = parsed.skippedCount)
        assertEquals(ImportSummary(imported = 4, alreadyImported = 0, unreadable = 1, categorized = 3, uncategorized = 1, ownTransfers = 1, personalTransfers = 1), first)
        val again = ImportSummary.of(run(), unreadable = parsed.skippedCount)
        assertEquals(0, again.imported)
        assertEquals(4, again.alreadyImported)
    }

    @Test
    fun `stats keep personal and own transfers apart from income and expenses`() {
        val stats = CalculateFinancialStatsUseCase().execute(
            listOf(
                tx("salary", 500_000_00),
                tx("food", -20_000_00, categoryId = "food"),
                tx("own_out", -100_000_00, direction = TransactionDirection.TRANSFER),
                tx("own_in", 100_000_00, direction = TransactionDirection.TRANSFER),
                tx("sent", -5_000_00, direction = TransactionDirection.PERSONAL_TRANSFER),
                tx("got", 3_000_00, direction = TransactionDirection.PERSONAL_TRANSFER)
            )
        )
        assertEquals(500_000_00L, stats.incomeMinor)
        assertEquals(20_000_00L, stats.expenseMinor)
        assertEquals(100_000_00L, stats.transfersMinor)
        assertEquals(2, stats.transferCount)
        assertEquals(5_000_00L, stats.personalSentMinor)
        assertEquals(3_000_00L, stats.personalReceivedMinor)
        assertEquals(20_000_00L, stats.avgExpenseMinor)
    }

}
