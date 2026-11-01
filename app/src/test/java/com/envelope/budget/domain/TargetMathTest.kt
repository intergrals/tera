package com.envelope.budget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TargetMathTest {

    private fun category(
        type: TargetType,
        amount: Long?,
        date: String? = null,
    ): Category = Fixtures.categories().first().copy(
        targetType = type,
        targetAmountCents = amount,
        targetDate = date?.let { LocalDate.parse(it) },
    )

    @Test
    fun neededForSpendingSpreadsOverRemainingMonths() {
        val category = category(TargetType.NEEDED_FOR_SPENDING, 120_000, "2025-03-31")
        assertEquals(40_000, TargetMath.suggestedFunding("2025-01", category, 0, false))
        // February: 80_000 remaining over Feb + Mar.
        assertEquals(40_000, TargetMath.suggestedFunding("2025-02", category, 40_000, false))
    }

    @Test
    fun neededForSpendingRoundsUpToTheCent() {
        val category = category(TargetType.NEEDED_FOR_SPENDING, 100_000, "2025-03-31")
        assertEquals(33_334, TargetMath.suggestedFunding("2025-01", category, 0, false))
    }

    @Test
    fun neededForSpendingWithoutDateTopsUpImmediately() {
        val category = category(TargetType.NEEDED_FOR_SPENDING, 50_000)
        assertEquals(30_000, TargetMath.suggestedFunding("2025-01", category, 20_000, false))
        assertEquals(0, TargetMath.suggestedFunding("2025-01", category, 60_000, false))
    }

    @Test
    fun savingsBalanceRefillsToTheAmount() {
        val category = category(TargetType.SAVINGS_BALANCE, 50_000)
        assertEquals(30_000, TargetMath.suggestedFunding("2025-01", category, 20_000, false))
        assertEquals(0, TargetMath.suggestedFunding("2025-01", category, 50_000, false))
    }

    @Test
    fun monthlySavingsBuilderIgnoresExistingBalance() {
        val category = category(TargetType.MONTHLY_SAVINGS_BUILDER, 25_000)
        assertEquals(25_000, TargetMath.suggestedFunding("2025-01", category, 100_000, false))
    }

    @Test
    fun snoozedTargetSuggestsNothing() {
        val category = category(TargetType.MONTHLY_SAVINGS_BUILDER, 25_000)
        assertEquals(0, TargetMath.suggestedFunding("2025-01", category, 0, true))
    }

    @Test
    fun noTargetSuggestsNothingAndHasNoProgress() {
        val category = category(TargetType.NONE, null)
        assertEquals(0, TargetMath.suggestedFunding("2025-01", category, 5_000, false))
        assertNull(TargetMath.progress(category, 5_000))
    }

    @Test
    fun progressIsClampedAndMetIsReported() {
        val category = category(TargetType.SAVINGS_BALANCE, 100_000)
        assertEquals(0.5f, TargetMath.progress(category, 50_000)!!, 0.0001f)
        assertEquals(1f, TargetMath.progress(category, 150_000)!!, 0.0001f)
        assertTrue(TargetMath.isMet(category, 100_000))
        assertFalse(TargetMath.isMet(category, 99_999))
    }
}
