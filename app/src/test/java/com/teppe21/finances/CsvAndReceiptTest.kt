package com.teppe21.finances

import com.teppe21.finances.domain.model.PaymentMethod
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.native.csv.CsvParser
import com.teppe21.finances.native.csv.CsvSkipReason
import com.teppe21.finances.native.receipt.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate

class CsvAndReceiptTest {

    @Test
    fun `hungarian semicolon csv with quotes and european numbers`() {
        val csv = listOf(
            "Dátum;Leírás;Összeg;Pénznem",
            "2026.03.28.;\"SPAR; Budapest\";-1 249,50;HUF",
            "2026-03-29;Fizetés;450 000;HUF",
            "28.03.2026;\"Online \"\"Shop\"\"\";-19,99;EUR"
        ).joinToString("\n")
        val result = CsvParser.parseText(csv)
        assertEquals(3, result.rows.size)
        assertEquals(0, result.skippedCount)

        val spar = result.rows[0]
        assertEquals(LocalDate.of(2026, 3, 28), spar.date)
        assertEquals("SPAR; Budapest", spar.description)
        assertEquals(-124950L, spar.amountMinor)
        assertEquals(TransactionDirection.EXPENSE, spar.direction)

        assertEquals(45_000_000L, result.rows[1].amountMinor)
        assertEquals(TransactionDirection.INCOME, result.rows[1].direction)

        assertEquals(-1999L, result.rows[2].amountMinor)
        assertEquals("EUR", result.rows[2].currency)
        assertEquals("Online \"Shop\"", result.rows[2].description)
    }

    @Test
    fun `us formatted numbers and separate debit credit columns`() {
        val csv = """
            Date,Description,Debit,Credit
            2026-04-01,Rent,"1,234.56",
            2026-04-02,Salary,,"3,000.00"
        """.trimIndent()
        val result = CsvParser.parseText(csv, defaultCurrency = "EUR")
        assertEquals(-123456L, result.rows[0].amountMinor)
        assertEquals(300000L, result.rows[1].amountMinor)
        assertEquals("EUR", result.rows[0].currency)
    }

    @Test
    fun `malformed rows are skipped and counted, never dated today`() {
        val csv = """
            Date;Description;Amount;Currency
            yesterday;Coffee;-500;HUF
            03/04/2026;Ambiguous;-500;HUF
            2026-02-30;Impossible;-500;HUF
            2026-04-01;Unreadable;abc;HUF
            2026-04-01;Zero;0;HUF
            2026-04-01;Crypto;-1;BTC
            2026-04-01;Valid;-500;HUF
        """.trimIndent()
        val result = CsvParser.parseText(csv)
        assertEquals(listOf("Valid"), result.rows.map { it.description })
        assertEquals(2, result.skipped[CsvSkipReason.INVALID_DATE])
        // 03/04/2026 reads as 3 April or 4 March and nothing else in the file decides which.
        assertEquals(1, result.skipped[CsvSkipReason.AMBIGUOUS_DATE])
        assertEquals(1, result.skipped[CsvSkipReason.INVALID_AMOUNT])
        assertEquals(1, result.skipped[CsvSkipReason.ZERO_AMOUNT])
        assertEquals(1, result.skipped[CsvSkipReason.UNSUPPORTED_CURRENCY])
    }

    @Test
    fun `windows-1250 encoded export is decoded`() {
        val text = "Dátum;Leírás;Összeg\n2026-05-01;Élelmiszer bolt;-2 500\n"
        val bytes = text.toByteArray(charset("windows-1250"))
        val result = CsvParser.parse(ByteArrayInputStream(bytes))
        assertEquals("windows-1250", result.charset)
        assertEquals("Élelmiszer bolt", result.rows.single().description)
        assertEquals(-250000L, result.rows.single().amountMinor)
    }

    @Test
    fun `utf8 bom and quoted newlines`() {
        val csv = "﻿Date,Description,Amount\n2026-05-02,\"Line one\nLine two\",-10.00\n2026-05-03,Next,-1.00\n"
        val result = CsvParser.parse(ByteArrayInputStream(csv.toByteArray()))
        assertEquals("UTF-8", result.charset)
        assertEquals(2, result.rows.size)
        assertEquals("Line one\nLine two", result.rows[0].description)
        assertEquals(4, result.rows[1].line)
    }

    @Test
    fun `date parser`() {
        assertEquals(LocalDate.of(2026, 3, 28), CsvParser.parseDate("2026.03.28."))
        assertEquals(LocalDate.of(2026, 3, 28), CsvParser.parseDate("28.03.2026"))
        assertEquals(LocalDate.of(2026, 3, 28), CsvParser.parseDate("2026-03-28T10:15:00"))
        // Only month-first gives a real date, so the cell itself proves the order.
        assertEquals(LocalDate.of(2026, 3, 28), CsvParser.parseDate("03/28/2026"))
        assertNull(CsvParser.parseDate("03/04/2026"))
        assertNull(CsvParser.parseDate(""))
    }

    @Test
    fun `hungarian receipt total, vat and payment method`() {
        val scan = ReceiptParser.parse(
            "SPAR MAGYARORSZAG KFT.\n2026.03.28 10:15\nTEJ 399 Ft\nKENYER 850 Ft\nFIZETENDO: 1 249 Ft\nKESZPENZ: 2 000 Ft\nVISSZAJARO: 751 Ft\nAFA TARTALOM: 60 Ft"
        )
        assertEquals(124900L, scan.totalMinor)
        assertEquals("HUF", scan.currency)
        assertEquals(PaymentMethod.CASH, scan.paymentMethod)
        assertEquals(6000L, scan.taxMinor)
    }

    @Test
    fun `receipt decimal amounts are not inflated`() {
        val scan = ReceiptParser.parse("CAFE EXAMPLE\nTOTAL 12.50 EUR\nCARD")
        assertEquals(1250L, scan.totalMinor)
        assertEquals("EUR", scan.currency)
        assertTrue(scan.items.none { it.totalPriceMinor == 125000L })
    }
}
