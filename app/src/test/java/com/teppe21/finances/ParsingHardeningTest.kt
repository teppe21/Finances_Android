package com.teppe21.finances

import com.teppe21.finances.core.money.AmountParser
import com.teppe21.finances.core.money.SeparatorStyle
import com.teppe21.finances.domain.model.PaymentMethod
import com.teppe21.finances.native.csv.CsvParser
import com.teppe21.finances.native.csv.CsvSkipReason
import com.teppe21.finances.native.csv.DateOrder
import com.teppe21.finances.native.csv.DateReading
import com.teppe21.finances.native.receipt.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate

class AmountParserStrictnessTest {

    private fun bd(s: String) = BigDecimal(s)

    @Test
    fun `auto style accepts only regular grouping and decimals`() {
        val valid = mapOf(
            "0" to "0",
            "7" to "7",
            "1249" to "1249",
            "1 249" to "1249",
            "1.249" to "1249",
            "1,249" to "1249",
            "1'249" to "1249",
            "12.50" to "12.50",
            "12,5" to "12.5",
            "0.50" to "0.50",
            "1.249,50" to "1249.50",
            "1,249.50" to "1249.50",
            "1 249 567,89" to "1249567.89",
            "-1 249,50" to "-1249.50",
            "1 249,50-" to "-1249.50",
            "+12.00" to "12.00"
        )
        for ((input, expected) in valid) {
            assertEquals("input '$input'", bd(expected), AmountParser.parse(input))
        }
    }

    @Test
    fun `irregular grouping and malformed numbers are rejected not repaired`() {
        val invalid = listOf(
            "12,34.56",     // group of two before the decimal point
            "1.234.56",     // looks like two kinds of separator
            "1234,567",     // four digits before a single separator that would then need to be a group
            "0,500",        // a group cannot start with zero
            "0.500",
            "1 2345",
            "12.3456",
            "1,2,3",
            "1..5",
            "1.",
            ".5",
            "- 5 -",
            "1 234.567.89",
            "1 2 3",
            "abc",
            "",
            "   ",
            "1e5",
            "12 €"
        )
        for (input in invalid) {
            assertNull("input '$input' must not parse", AmountParser.parse(input))
        }
    }

    @Test
    fun `a lone three digit group is grouping because two decimal currencies cannot have three decimals`() {
        assertEquals(bd("1500"), AmountParser.parse("1.500"))
        assertEquals(bd("1500"), AmountParser.parse("1,500"))
    }

    @Test
    fun `explicit comma-decimal style reads dot as grouping and rejects a three digit fraction`() {
        assertEquals(bd("1500"), AmountParser.parse("1.500", SeparatorStyle.COMMA_DECIMAL))
        assertEquals(bd("1234.56"), AmountParser.parse("1.234,56", SeparatorStyle.COMMA_DECIMAL))
        assertNull(AmountParser.parse("1,500", SeparatorStyle.COMMA_DECIMAL))
        assertNull("dot cannot be decimal here", AmountParser.parse("1.234,56.7", SeparatorStyle.COMMA_DECIMAL))
        assertEquals(bd("1234"), AmountParser.parse("1 234", SeparatorStyle.COMMA_DECIMAL))
    }

    @Test
    fun `explicit dot-decimal style reads comma as grouping and rejects a three digit fraction`() {
        assertEquals(bd("1500"), AmountParser.parse("1,500", SeparatorStyle.DOT_DECIMAL))
        assertEquals(bd("1234.56"), AmountParser.parse("1,234.56", SeparatorStyle.DOT_DECIMAL))
        assertNull("1.500 would be a three digit fraction", AmountParser.parse("1.500", SeparatorStyle.DOT_DECIMAL))
        assertNull(AmountParser.parse("1,234,56", SeparatorStyle.DOT_DECIMAL))
    }

    @Test
    fun `values beyond a Long never wrap`() {
        assertEquals(bd("99999999999999999999.99"), AmountParser.parse("99999999999999999999.99"))
        try {
            AmountParser.parseMinor("99999999999999999999.99")
            org.junit.Assert.fail("expected overflow to be reported")
        } catch (_: ArithmeticException) {
        }
    }

    @Test
    fun `style proof only comes from self-evident amounts`() {
        assertEquals(SeparatorStyle.COMMA_DECIMAL, AmountParser.styleProvedBy("1.234,56"))
        assertEquals(SeparatorStyle.COMMA_DECIMAL, AmountParser.styleProvedBy("12,5"))
        assertEquals(SeparatorStyle.DOT_DECIMAL, AmountParser.styleProvedBy("1,234.56"))
        assertEquals(SeparatorStyle.DOT_DECIMAL, AmountParser.styleProvedBy("-804.00"))
        assertNull(AmountParser.styleProvedBy("1.500"))
        assertNull(AmountParser.styleProvedBy("1500"))
        assertNull(AmountParser.styleProvedBy(null))
    }
}

class CsvDateAndAmountInferenceTest {

    @Test
    fun `year-first and dotted day-first dates need no context`() {
        assertEquals(LocalDate.of(2026, 3, 28), CsvParser.parseDate("2026-03-28"))
        assertEquals(LocalDate.of(2026, 3, 28), CsvParser.parseDate("2026.03.28."))
        assertEquals(LocalDate.of(2026, 3, 28), CsvParser.parseDate("2026/03/28"))
        assertEquals(LocalDate.of(2026, 4, 3), CsvParser.parseDate("03.04.2026"))
    }

    @Test
    fun `slash and dash dates that are valid one way only read themselves`() {
        assertEquals(LocalDate.of(2026, 3, 28), CsvParser.parseDate("28/03/2026"))
        assertEquals(LocalDate.of(2026, 3, 28), CsvParser.parseDate("28-03-2026"))
        assertEquals(LocalDate.of(2026, 3, 28), CsvParser.parseDate("03/28/2026"))
        assertEquals(LocalDate.of(2026, 5, 5), CsvParser.parseDate("05/05/2026"))
    }

    @Test
    fun `ambiguous slash dates need a file-level order`() {
        assertEquals(DateReading.Ambiguous, CsvParser.readDate("03/04/2026"))
        assertEquals(LocalDate.of(2026, 4, 3), CsvParser.parseDate("03/04/2026", DateOrder.DAY_FIRST))
        assertEquals(LocalDate.of(2026, 3, 4), CsvParser.parseDate("03/04/2026", DateOrder.MONTH_FIRST))
        assertEquals(DateReading.Invalid, CsvParser.readDate("32/13/2026"))
        assertEquals(DateReading.Invalid, CsvParser.readDate("2026-02-30"))
        assertEquals(DateReading.Invalid, CsvParser.readDate("31/04/2026"))
    }

    @Test
    fun `order is inferred only from cells that prove one order`() {
        assertEquals(DateOrder.DAY_FIRST, CsvParser.inferDateOrder(listOf("03/04/2026", "28/03/2026", "garbage")))
        assertEquals(DateOrder.MONTH_FIRST, CsvParser.inferDateOrder(listOf("03/04/2026", "03/28/2026")))
        assertNull("no proof", CsvParser.inferDateOrder(listOf("03/04/2026", "05/06/2026")))
        assertNull("conflicting proof", CsvParser.inferDateOrder(listOf("28/03/2026", "03/28/2026")))
        assertNull("invalid dates prove nothing", CsvParser.inferDateOrder(listOf("31/02/2026", "40/13/2026")))
        assertNull(CsvParser.inferDateOrder(listOf("2026-03-28", "28.03.2026")))
    }

    @Test
    fun `day-first evidence resolves ambiguous rows in the same file`() {
        val csv = """
            Date;Description;Amount
            03/04/2026;Ambiguous but resolved;-500
            28/03/2026;Proves day first;-700
        """.trimIndent()
        val result = CsvParser.parseText(csv)
        assertEquals(0, result.skippedCount)
        assertEquals(LocalDate.of(2026, 4, 3), result.rows[0].date)
        assertEquals(LocalDate.of(2026, 3, 28), result.rows[1].date)
    }

    @Test
    fun `month-first evidence resolves them the other way`() {
        val csv = """
            Date,Description,Amount
            03/04/2026,Ambiguous but resolved,-5.00
            03/28/2026,Proves month first,-7.00
        """.trimIndent()
        val result = CsvParser.parseText(csv)
        assertEquals(LocalDate.of(2026, 3, 4), result.rows[0].date)
        assertEquals(LocalDate.of(2026, 3, 28), result.rows[1].date)
    }

    @Test
    fun `without evidence ambiguous rows are skipped with a reason and valid ones kept`() {
        val csv = """
            Date;Description;Amount
            03/04/2026;Ambiguous;-500
            2026-04-05;Clear;-600
            31/02/2026;Impossible;-700
        """.trimIndent()
        val result = CsvParser.parseText(csv)
        assertEquals(listOf("Clear"), result.rows.map { it.description })
        assertEquals(1, result.skipped[CsvSkipReason.AMBIGUOUS_DATE])
        assertEquals(1, result.skipped[CsvSkipReason.INVALID_DATE])
    }

    @Test
    fun `a file with conflicting evidence keeps provable rows and skips ambiguous ones`() {
        val csv = """
            Date;Description;Amount
            28/03/2026;Day first;-100
            03/28/2026;Month first;-200
            03/04/2026;Ambiguous;-300
        """.trimIndent()
        val result = CsvParser.parseText(csv)
        assertEquals(listOf("Day first", "Month first"), result.rows.map { it.description })
        assertEquals(LocalDate.of(2026, 3, 28), result.rows[0].date)
        assertEquals(LocalDate.of(2026, 3, 28), result.rows[1].date)
        assertEquals(1, result.skipped[CsvSkipReason.AMBIGUOUS_DATE])
    }

    @Test
    fun `invalid dates do not feed the inference`() {
        val csv = """
            Date;Description;Amount
            31/02/2026;Impossible;-100
            03/04/2026;Ambiguous;-200
        """.trimIndent()
        val result = CsvParser.parseText(csv)
        assertEquals(0, result.rows.size)
        assertEquals(1, result.skipped[CsvSkipReason.INVALID_DATE])
        assertEquals(1, result.skipped[CsvSkipReason.AMBIGUOUS_DATE])
    }

    @Test
    fun `a dot-decimal file rejects a lone 1,5 style value it cannot read exactly`() {
        val csv = """
            Date,Description,Amount
            2026-04-01,Plain decimal,"12.50"
            2026-04-02,Dot value with three digits,"1.500"
            2026-04-03,Comma grouped,"1,500.00"
        """.trimIndent()
        val result = CsvParser.parseText(csv, defaultCurrency = "EUR")
        assertEquals(listOf("Plain decimal", "Comma grouped"), result.rows.map { it.description })
        assertEquals(1250L, result.rows[0].amountMinor)
        assertEquals(150000L, result.rows[1].amountMinor)
        assertEquals(1, result.skipped[CsvSkipReason.INVALID_AMOUNT])
    }

    @Test
    fun `a comma-decimal file reads dots as grouping`() {
        val csv = """
            Dátum;Leírás;Összeg
            2026-04-01;Kávé;-1.500
            2026-04-02;Bevásárlás;-12.345,60
            2026-04-03;Apró;-12,5
        """.trimIndent()
        val result = CsvParser.parseText(csv)
        assertEquals(listOf(-150000L, -1234560L, -1250L), result.rows.map { it.amountMinor })
    }

    @Test
    fun `utf-8 and windows-1250 files give the same rows`() {
        val text = "Dátum;Leírás;Összeg\n28.03.2026;Árvíztűrő tükörfúrógép;-1 249,50\n29/03/2026;Őszi vásár;-2 000\n"
        val utf8 = CsvParser.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        val cp1250 = CsvParser.parse(ByteArrayInputStream(text.toByteArray(charset("windows-1250"))))
        assertEquals("UTF-8", utf8.charset)
        assertEquals("windows-1250", cp1250.charset)
        assertEquals(utf8.rows.map { it.description }, cp1250.rows.map { it.description })
        assertEquals(listOf("Árvíztűrő tükörfúrógép", "Őszi vásár"), cp1250.rows.map { it.description })
        assertEquals(listOf(-124950L, -200000L), cp1250.rows.map { it.amountMinor })
        assertEquals(LocalDate.of(2026, 3, 29), cp1250.rows[1].date)
    }
}

class ReceiptTotalExtractionTest {

    private fun total(vararg lines: String) = ReceiptParser.parse(lines.joinToString("\n")).totalMinor

    @Test
    fun `payable label wins over larger numbers elsewhere`() {
        val scan = ReceiptParser.parse(
            "EXAMPLE MARKET\n2026.03.28 10:15\nITEM A 399 Ft\nITEM B 850 Ft\nBARCODE 5998877665544\nFIZETENDO: 1 249 Ft\nKESZPENZ: 5 000 Ft\nVISSZAJARO: 3 751 Ft"
        )
        assertEquals(124900L, scan.totalMinor)
        assertEquals(PaymentMethod.CASH, scan.paymentMethod)
    }

    @Test
    fun `no total label means no total, never the largest number`() {
        val scan = ReceiptParser.parse("EXAMPLE MARKET\nITEM A 399 Ft\nITEM B 850 Ft\nTRANSACTION 123456\nAFA 27% 60 Ft\n99 999 Ft")
        assertNull(scan.totalMinor)
        assertTrue(scan.confidence < 0.6f)
    }

    @Test
    fun `labels are matched with accents, case and common OCR digit slips`() {
        assertEquals(125000L, total("SHOP", "Végösszeg: 1 250 Ft"))
        assertEquals(125000L, total("SHOP", "FIZETEND0 1250 Ft"))
        assertEquals(125000L, total("SHOP", "ÖSSZESEN 1.250 Ft"))
        assertEquals(1250L, total("SHOP", "Grand Total 12.50 EUR"))
        assertEquals(1250L, total("SHOP", "Amount due: 12,50"))
        assertEquals(1250L, total("SHOP", "TOTAL", "12.50"))
    }

    @Test
    fun `vat subtotal discount change and cash lines are never the total`() {
        assertNull(total("SHOP", "ÁFA ÖSSZESEN 270 Ft"))
        assertNull(total("SHOP", "SUBTOTAL 1 000 Ft"))
        assertNull(total("SHOP", "KEDVEZMÉNY ÖSSZESEN 100 Ft"))
        assertNull(total("SHOP", "VISSZAJÁRÓ 751 Ft"))
        assertNull(total("SHOP", "KÉSZPÉNZ 2 000 Ft"))
        assertNull(total("SHOP", "ADÓSZÁM 12345678-2-42"))
        assertEquals(1270L, total("SHOP", "SUBTOTAL 1 000", "TOTAL incl. VAT 12.70"))
    }

    @Test
    fun `payable label beats generic total label`() {
        assertEquals(124900L, total("SHOP", "ÖSSZESEN 1 300 Ft", "FIZETENDŐ 1 249 Ft"))
    }

    @Test
    fun `conflicting totals at the same level are left for the user`() {
        assertNull(total("SHOP", "ÖSSZESEN 1 300 Ft", "ÖSSZESEN 1 249 Ft"))
        assertEquals(124900L, total("SHOP", "ÖSSZESEN 1 249 Ft", "ÖSSZESEN 1 249 Ft"))
    }

    @Test
    fun `dates times percentages and long digit runs are not amounts`() {
        assertNull(total("SHOP", "TOTAL 2026.03.28"))
        assertNull(total("SHOP", "TOTAL 10:15"))
        assertNull(total("SHOP", "TOTAL 5998877665544"))
        assertNull(total("SHOP", "TOTAL 27%"))
    }

    @Test
    fun `currency follows the receipt and defaults to HUF`() {
        assertEquals("EUR", ReceiptParser.parse("SHOP\nTOTAL 12.50 EUR").currency)
        assertEquals("EUR", ReceiptParser.parse("SHOP EUR\nTOTAL 12.50").currency)
        assertEquals("USD", ReceiptParser.parse("SHOP\nTOTAL \$12.50").currency)
        assertEquals("HUF", ReceiptParser.parse("SHOP\nFIZETENDŐ 1 249 Ft").currency)
        assertEquals("HUF", ReceiptParser.parse("SHOP\nFIZETENDŐ 1 249").currency)
    }

    @Test
    fun `plain four digit totals are not truncated`() {
        assertEquals(124900L, total("SHOP", "FIZETENDŐ: 1249 Ft"))
    }

    @Test
    fun `date formats`() {
        assertEquals(LocalDate.of(2026, 3, 28), ReceiptParser.parse("SHOP\n2026.03.28 10:15\nFIZETENDŐ 100 Ft").date)
        assertEquals(LocalDate.of(2026, 3, 28), ReceiptParser.parse("SHOP\n28.03.2026 10:15\nFIZETENDŐ 100 Ft").date)
    }

    @Test
    fun `parsed receipt never carries an image reference`() {
        assertNull(ReceiptParser.parse("SHOP\nFIZETENDŐ 100 Ft").imageUri)
    }
}
