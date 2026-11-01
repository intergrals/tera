package com.envelope.budget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MetricsTest {

    @Test
    fun ageOfMoneyIsDaysBetweenEarningAndSpending() {
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash()),
            transactions = listOf(
                Fixtures.tx(Fixtures.CASH_ID, 100_000, "2025-01-01"),
                Fixtures.tx(Fixtures.CASH_ID, -100_000, "2025-01-11", Fixtures.GROCERIES_ID),
            ),
        )
        assertEquals(10, Metrics.ageOfMoney(input))
    }

    @Test
    fun ageOfMoneyConsumesInflowLotsOldestFirst() {
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash()),
            transactions = listOf(
                Fixtures.tx(Fixtures.CASH_ID, 100_000, "2025-01-01"),
                Fixtures.tx(Fixtures.CASH_ID, 100_000, "2025-01-11"),
                Fixtures.tx(Fixtures.CASH_ID, -200_000, "2025-01-21", Fixtures.GROCERIES_ID),
            ),
        )
        // (100.00 x 20 days + 100.00 x 10 days) / 200.00 = 15 days
        assertEquals(15, Metrics.ageOfMoney(input))
    }

    @Test
    fun cardPurchaseDoesNotAgeMoneyUntilTheCardIsPaid() {
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash(), Fixtures.card()),
            transactions = buildList {
                add(Fixtures.tx(Fixtures.CASH_ID, 100_000, "2025-01-01"))
                add(Fixtures.tx(Fixtures.CARD_ID, -100_000, "2025-01-05", Fixtures.GROCERIES_ID))
                addAll(Fixtures.transferPair(Fixtures.CASH_ID, Fixtures.CARD_ID, 100_000, "2025-01-20"))
            },
            allocations = Fixtures.assigned(Fixtures.GROCERIES_ID to 100_000, month = "2025-01"),
        )
        // Money ages until the cash actually leaves (the card payment on Jan 20), not the purchase.
        assertEquals(19, Metrics.ageOfMoney(input))
    }

    @Test
    fun ageOfMoneyAveragesOnlyTheLastTenCashOutflows() {
        val transactions = buildList {
            add(Fixtures.tx(Fixtures.CASH_ID, 1_000_000, "2025-01-01"))
            for (day in 2..12) {
                add(
                    Fixtures.tx(
                        Fixtures.CASH_ID,
                        -10_000,
                        "2025-01-${day.toString().padStart(2, '0')}",
                        Fixtures.GROCERIES_ID,
                    ),
                )
            }
        }
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash()),
            transactions = transactions,
            allocations = Fixtures.assigned(Fixtures.GROCERIES_ID to 1_000_000, month = "2025-01"),
        )
        // Outflows on days 2..12 have ages 1..11; the last ten (days 3..12) average 6.5 -> 6.
        assertEquals(6, Metrics.ageOfMoney(input))
    }

    @Test
    fun ageOfMoneyIsNullWithoutSpending() {
        val input = Fixtures.input(accounts = listOf(Fixtures.cash()))
        assertNull(Metrics.ageOfMoney(input))
    }

    @Test
    fun daysOfBufferingIgnoresCardDebtAndUsesPositiveBudgetCashOnly() {
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash(), Fixtures.card(startingBalanceCents = -50_000)),
            transactions = listOf(
                Fixtures.tx(Fixtures.CASH_ID, 100_000, "2025-01-01"),
                Fixtures.tx(Fixtures.CASH_ID, -9_000, "2025-03-01", Fixtures.GROCERIES_ID),
            ),
            allocations = Fixtures.assigned(Fixtures.GROCERIES_ID to 9_000, month = "2025-03"),
        )
        assertEquals(91_000, Metrics.onBudgetCash(input))
        // 90 observed days, 9_000 spent -> 100/day average; 91_000 / 100 = 910 days.
        assertEquals(910, Metrics.daysOfBuffering(input, LocalDate.parse("2025-04-01")))
    }

    @Test
    fun daysOfBufferingIsNullWithoutSpending() {
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash()),
            transactions = listOf(Fixtures.tx(Fixtures.CASH_ID, 100_000, "2025-01-01")),
        )
        assertNull(Metrics.daysOfBuffering(input, LocalDate.parse("2025-04-01")))
    }

    @Test
    fun budgetToBudgetTransfersAreNotCashOutflows() {
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash(), Fixtures.savings(), Fixtures.card()),
            transactions = listOf(
                Fixtures.tx(Fixtures.CASH_ID, 100_000, "2025-01-01"),
                Fixtures.tx(Fixtures.CASH_ID, -40_000, "2025-01-05", Fixtures.GROCERIES_ID),
            ) + Fixtures.transferPair(Fixtures.CASH_ID, Fixtures.SAVINGS_ID, 10_000, "2025-01-06"),
            allocations = Fixtures.assigned(Fixtures.GROCERIES_ID to 40_000, month = "2025-01"),
        )
        assertEquals(1, Metrics.cashOutflows(input).size)
    }
}
