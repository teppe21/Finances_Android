package com.teppe21.finances

import com.teppe21.finances.core.money.AmountParser
import com.teppe21.finances.core.money.Money
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.native.notification.parsing.MoneyMentions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MoneyParsingTest {

    @Test
    fun `amount parser handles common separator styles exactly`() {
        assertEquals(BigDecimal("804.00"), AmountParser.parse("804.00"))
        assertEquals(BigDecimal("22500"), AmountParser.parse("22,500"))
        assertEquals(BigDecimal("14500"), AmountParser.parse("14 500"))
        assertEquals(BigDecimal("4500"), AmountParser.parse("4.500"))
        assertEquals(BigDecimal("12.99"), AmountParser.parse("12,99"))
        assertEquals(BigDecimal("1234.56"), AmountParser.parse("1.234,56"))
        assertEquals(BigDecimal("1234.56"), AmountParser.parse("1,234.56"))
        assertEquals(BigDecimal("1234567.89"), AmountParser.parse("1,234,567.89"))
        assertEquals(BigDecimal("1234.5"), AmountParser.parse("1 234,5"))
        assertEquals(BigDecimal("-4500"), AmountParser.parse("-4.500"))
    }

    @Test
    fun `amount parser rejects malformed input`() {
        assertNull(AmountParser.parse(""))
        assertNull(AmountParser.parse("abc"))
        assertNull(AmountParser.parse("12.345.6"))
        assertNull(AmountParser.parse("1,2,3"))
        assertNull(AmountParser.parse("12.3456"))
    }

    @Test
    fun `minor units never drift through floating point`() {
        // 19.99 * 100 in double arithmetic is 1998.9999...; the old CSV code truncated it to 1998.
        assertEquals(1999L, AmountParser.parseMinor("19.99"))
        assertEquals(1999L, Money.parseUserInput("19,99"))
        assertEquals(125000L, Money.parseUserInput("1250"))
        assertNull(Money.parseUserInput("12.345"))
        assertNull(Money.parseUserInput("-5"))
        // Large values stay exact.
        assertEquals(9_000_000_000_000_001L, AmountParser.parseMinor("90000000000000.01"))
    }

    @Test
    fun `money mentions ignore balance lines and word fragments`() {
        val mentions = MoneyMentions.find("You spent HUF804\nHUF balance: HUF42,285.97")
        assertEquals(1, mentions.size)
        assertEquals(80400L, mentions[0].amountMinor)
        assertEquals("HUF", mentions[0].currency)

        // "gift 500" must not be read as "ft 500".
        assertTrue(MoneyMentions.find("Send a gift 500 times").isEmpty())
        assertEquals(1450000L, MoneyMentions.find("Elköltöttél 14 500 Ft-ot itt: LIDL.").single().amountMinor)
        assertEquals("EUR", MoneyMentions.find("12.50 EUR with Erste Visa").single().currency)
        assertEquals(-1, MoneyMentions.find("Összeg: -4.500 Ft.").single().explicitSign)
        assertEquals(2500L, MoneyMentions.find("Paid €25.00").single().amountMinor)
    }

    @Test
    fun `formatter uses exact decimals and currency precision`() {
        assertEquals("14,500 Ft", CurrencyFormatter.format(1450000L, "HUF"))
        assertEquals("1,001 Ft", CurrencyFormatter.format(100050L, "HUF")) // 1000.50 rounds half up
        assertEquals("€12.50", CurrencyFormatter.format(1250L, "EUR"))
        assertEquals("-$25.00", CurrencyFormatter.formatSigned(-2500L, "USD"))
        assertEquals("£0.00", CurrencyFormatter.format(0L, "GBP"))
        assertEquals("CHF 90,071,992,547,409.93", CurrencyFormatter.format(9_007_199_254_740_993L, "CHF"))
    }
}
