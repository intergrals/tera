package com.envelope.budget.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyFormatTest {

    @Test
    fun formatsCentsWithGroupingAndTwoDecimals() {
        assertEquals("$1,234.56", MoneyFormat.format(123_456))
        assertEquals("$0.00", MoneyFormat.format(0))
        assertEquals("$0.05", MoneyFormat.format(5))
        assertEquals("$1,000,000.00", MoneyFormat.format(100_000_000))
    }

    @Test
    fun formatsNegativesAndCustomSymbols() {
        assertEquals("-$1,234.56", MoneyFormat.format(-123_456))
        assertEquals("EUR10.00", MoneyFormat.format(1_000, "EUR"))
    }

    @Test
    fun formatSignedAddsPlusForIncome() {
        assertEquals("+$5.00", MoneyFormat.formatSigned(500))
        assertEquals("-$5.00", MoneyFormat.formatSigned(-500))
    }

    @Test
    fun formatWholeDropsCentsForChartLabels() {
        assertEquals("$1,234", MoneyFormat.formatWhole(123_456))
        assertEquals("-$12", MoneyFormat.formatWhole(-1_200))
    }

    @Test
    fun parsesCommonUserInputShapes() {
        assertEquals(123_456L, MoneyFormat.parse("1,234.56"))
        assertEquals(123_456L, MoneyFormat.parse("1.234,56"))
        assertEquals(125_000L, MoneyFormat.parse("1250"))
        assertEquals(1_250L, MoneyFormat.parse("$12.5"))
        assertEquals(-1_250L, MoneyFormat.parse("-12.50"))
        // A three-digit group after the separator is thousands, not a decimal.
        assertEquals(123_400L, MoneyFormat.parse("1,234"))
    }

    @Test
    fun rejectsUnreadableInput() {
        assertNull(MoneyFormat.parse(""))
        assertNull(MoneyFormat.parse("abc"))
        assertNull(MoneyFormat.parse("   "))
    }

    @Test
    fun keypadDigitsTreatLastTwoDigitsAsCents() {
        assertEquals(1_234L, MoneyFormat.fromKeypadDigits("1234"))
        assertEquals(0L, MoneyFormat.fromKeypadDigits(""))
        assertEquals("1234", MoneyFormat.toKeypadDigits(1_234))
        assertEquals(MoneyFormat.MAX_ABS_CENTS, MoneyFormat.fromKeypadDigits("99999999999999"))
    }
}
