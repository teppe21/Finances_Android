package com.teppe21.finances

import com.teppe21.finances.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyInputTest {
    @Test fun twentyDigitsIsInvalidNotAnException() {
        assertNull(Money.parseUserInput("99999999999999999999"))
        assertNull(Money.parseUserInput("99999999999999999999,99"))
    }

    @Test fun capIsValidAndAboveIsNot() {
        assertEquals(Money.MAX_INPUT_MINOR, Money.parseUserInput("100000000"))
        assertNull(Money.parseUserInput("100000000,01"))
        assertNull(Money.parseUserInput("100000001"))
    }

    @Test fun normalValuesUnchanged() {
        assertEquals(1999L, Money.parseUserInput("19,99"))
        assertEquals(125000L, Money.parseUserInput("1 250"))
        assertEquals(0L, Money.parseUserInput("0"))
    }
}
