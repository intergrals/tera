package com.tera.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EnvelopeRulesTest {

    private fun valid(r: EnvelopeRules.Result): EnvelopeRules.Result.Valid {
        assertTrue("expected valid but was $r", r is EnvelopeRules.Result.Valid)
        return r as EnvelopeRules.Result.Valid
    }

    @Test
    fun blankNameIsRejected() {
        val r = EnvelopeRules.validate("   ", "", TargetType.NONE, "", null)
        assertTrue(r is EnvelopeRules.Result.Invalid)
    }

    @Test
    fun nameIsTrimmed() {
        val r = valid(EnvelopeRules.validate("  Groceries  ", "", TargetType.NONE, "", null))
        assertEquals("Groceries", r.name)
    }

    @Test
    fun overlongNameIsRejected() {
        val r = EnvelopeRules.validate("x".repeat(EnvelopeRules.MAX_NAME_LENGTH + 1), "", TargetType.NONE, "", null)
        assertTrue(r is EnvelopeRules.Result.Invalid)
    }

    @Test
    fun noTargetIgnoresAmountAndDate() {
        val r = valid(
            EnvelopeRules.validate(
                "Fun",
                "  guilt free  ",
                TargetType.NONE,
                "not money",
                LocalDate.parse("2026-01-01"),
            ),
        )
        assertNull(r.amountCents)
        assertNull(r.date)
        assertEquals("guilt free", r.note)
    }

    @Test
    fun targetRequiresAnAmount() {
        assertTrue(EnvelopeRules.validate("Car", "", TargetType.SAVINGS_BALANCE, "", null) is EnvelopeRules.Result.Invalid)
        assertTrue(EnvelopeRules.validate("Car", "", TargetType.SAVINGS_BALANCE, "abc", null) is EnvelopeRules.Result.Invalid)
    }

    @Test
    fun targetMustBePositive() {
        assertTrue(EnvelopeRules.validate("Car", "", TargetType.SAVINGS_BALANCE, "0", null) is EnvelopeRules.Result.Invalid)
        assertTrue(EnvelopeRules.validate("Car", "", TargetType.SAVINGS_BALANCE, "-20", null) is EnvelopeRules.Result.Invalid)
    }

    @Test
    fun savingsBalanceKeepsTheAmountAndDropsTheDate() {
        val r = valid(
            EnvelopeRules.validate(
                "Car repair",
                "",
                TargetType.SAVINGS_BALANCE,
                "1,200",
                LocalDate.parse("2026-06-30"),
            ),
        )
        assertEquals(120_000L, r.amountCents)
        assertNull(r.date)
    }

    @Test
    fun neededForSpendingKeepsItsDate() {
        val date = LocalDate.parse("2026-06-30")
        val r = valid(EnvelopeRules.validate("Vacation", "", TargetType.NEEDED_FOR_SPENDING, "900", date))
        assertEquals(90_000L, r.amountCents)
        assertEquals(date, r.date)
    }

    @Test
    fun monthlyBuilderDropsTheDate() {
        val r = valid(
            EnvelopeRules.validate(
                "Subscriptions",
                "",
                TargetType.MONTHLY_SAVINGS_BUILDER,
                "15.50",
                LocalDate.parse("2026-06-30"),
            ),
        )
        assertEquals(1_550L, r.amountCents)
        assertNull(r.date)
    }
}
