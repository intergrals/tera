package com.tera.app.domain

import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Target math. All three target types are supported:
 *
 * - NEEDED_FOR_SPENDING: top up to the amount, spread over the months remaining when a
 *   date is set (rounding up to the cent so you never fall short).
 * - SAVINGS_BALANCE: refill up to the amount (add whatever is missing right now).
 * - MONTHLY_SAVINGS_BUILDER: a flat amount every month regardless of balance.
 */
object TargetMath {

    /** Amount that should be assigned this month to stay on target; 0 when snoozed. */
    fun suggestedFunding(
        month: String,
        category: Category,
        availableCents: Long,
        snoozed: Boolean,
    ): Long {
        if (!category.hasTarget) return 0L
        if (snoozed) return 0L
        val amount = category.targetAmountCents ?: return 0L

        return when (category.targetType) {
            TargetType.NONE -> 0L
            TargetType.MONTHLY_SAVINGS_BUILDER -> amount
            TargetType.SAVINGS_BALANCE -> maxOf(0L, amount - availableCents)
            TargetType.NEEDED_FOR_SPENDING -> {
                val remaining = amount - availableCents
                if (remaining <= 0) return 0L
                val targetDate = category.targetDate ?: return remaining
                val monthsLeft = monthsRemaining(month, targetDate)
                ceilDiv(remaining, monthsLeft)
            }
        }
    }

    /** 0f..1f progress toward the target amount, or null when there is no target. */
    fun progress(category: Category, availableCents: Long): Float? {
        val amount = category.targetAmountCents ?: return null
        if (category.targetType == TargetType.NONE || amount <= 0L) return null
        return (availableCents.toFloat() / amount.toFloat()).coerceIn(0f, 1f)
    }

    fun isMet(category: Category, availableCents: Long): Boolean {
        val amount = category.targetAmountCents ?: return false
        return category.hasTarget && availableCents >= amount
    }

    /** Inclusive count of months from [month] through the target month, minimum 1. */
    fun monthsRemaining(month: String, targetDate: java.time.LocalDate): Long {
        val current = YearMonth.from(MonthKey.parse(month))
        val target = YearMonth.from(targetDate)
        val months = ChronoUnit.MONTHS.between(current, target) + 1
        return maxOf(1L, months)
    }

    private fun ceilDiv(value: Long, divisor: Long): Long {
        if (divisor <= 0) return value
        val quotient = value / divisor
        return if (value % divisor == 0L) quotient else quotient + 1
    }
}
