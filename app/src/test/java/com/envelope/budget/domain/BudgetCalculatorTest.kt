package com.envelope.budget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BudgetCalculatorTest {

    private val calculator = BudgetCalculator()

    private fun month(
        input: BudgetInput,
        key: String,
        through: String = key,
    ): MonthResult = calculator.compute(input, through).let { history ->
        assertNotNull("month $key should be computed", history.month(key))
        history.month(key)!!
    }

    // ------------------------------------------------------------ Ready to Assign

    @Test
    fun incomeBecomesReadyToAssignAndAssignmentReducesIt() {
        val input = Fixtures.input(
            transactions = listOf(Fixtures.tx(Fixtures.CASH_ID, 100_000, "2025-11-01")),
            allocations = Fixtures.assigned(Fixtures.GROCERIES_ID to 60_000, month = "2025-11"),
        )
        val november = month(input, "2025-11")
        assertEquals(100_000, november.breakdown.incomeCents)
        assertEquals(60_000, november.breakdown.assignedCents)
        assertEquals(40_000, november.readyToAssignCents)
        assertEquals(60_000, november.categories[Fixtures.GROCERIES_ID]!!.availableCents)
    }

    @Test
    fun unassignedReadyToAssignCarriesIntoNextMonth() {
        val input = Fixtures.input(
            transactions = listOf(Fixtures.tx(Fixtures.CASH_ID, 100_000, "2025-11-01")),
        )
        val december = month(input, "2025-12", through = "2025-12")
        assertEquals(100_000, december.breakdown.carriedInCents)
        assertEquals(100_000, december.readyToAssignCents)
    }

    @Test
    fun negativeReadyToAssignIsReportedAsNegative() {
        val input = Fixtures.input(
            allocations = Fixtures.assigned(Fixtures.GROCERIES_ID to 50_000, month = "2025-11"),
        )
        assertEquals(-50_000, month(input, "2025-11").readyToAssignCents)
    }

    // ------------------------------------------------------------- rollover

    @Test
    fun positiveAvailableRollsForward() {
        val input = Fixtures.input(
            transactions = listOf(
                Fixtures.tx(Fixtures.CASH_ID, -40_000, "2025-11-05", Fixtures.GROCERIES_ID),
            ),
            allocations = Fixtures.assigned(Fixtures.GROCERIES_ID to 100_000, month = "2025-11"),
        )
        val december = month(input, "2025-12", through = "2025-12")
        assertEquals(60_000, december.categories[Fixtures.GROCERIES_ID]!!.rolloverInCents)
        assertEquals(60_000, december.categories[Fixtures.GROCERIES_ID]!!.availableCents)
    }

    // --------------------------------------------------- overspending: cash

    @Test
    fun cashOverspendIsDeductedFromNextMonthReadyToAssign() {
        val input = Fixtures.input(
            transactions = listOf(
                Fixtures.tx(Fixtures.CASH_ID, 100_000, "2025-11-01"),
                Fixtures.tx(Fixtures.CASH_ID, -120_000, "2025-11-05", Fixtures.GROCERIES_ID),
            ),
            allocations = Fixtures.assigned(Fixtures.GROCERIES_ID to 100_000, month = "2025-11"),
        )
        val november = month(input, "2025-11")
        assertEquals(-20_000, november.categories[Fixtures.GROCERIES_ID]!!.availableCents)
        assertEquals(20_000, november.categories[Fixtures.GROCERIES_ID]!!.cashShortfallCents)
        assertEquals(0, november.readyToAssignCents)

        val december = calculator.compute(input, "2025-12").month("2025-12")!!
        assertEquals(20_000, december.breakdown.overspentLastMonthCents)
        assertEquals(-20_000, december.readyToAssignCents)
        assertEquals("category resets to zero", 0, december.categories[Fixtures.GROCERIES_ID]!!.availableCents)
        assertEquals(0, december.categories[Fixtures.GROCERIES_ID]!!.rolloverInCents)
    }

    @Test
    fun carryNegativeModeCarriesRedInsteadOfTouchingReadyToAssign() {
        val input = Fixtures.input(
            transactions = listOf(
                Fixtures.tx(Fixtures.CASH_ID, 100_000, "2025-11-01"),
                Fixtures.tx(Fixtures.CASH_ID, -120_000, "2025-11-05", Fixtures.GROCERIES_ID),
            ),
            allocations = Fixtures.assigned(Fixtures.GROCERIES_ID to 100_000, month = "2025-11"),
            mode = OverspendMode.CARRY_NEGATIVE,
        )
        val december = calculator.compute(input, "2025-12").month("2025-12")!!
        assertEquals(-20_000, december.categories[Fixtures.GROCERIES_ID]!!.rolloverInCents)
        assertEquals(-20_000, december.categories[Fixtures.GROCERIES_ID]!!.availableCents)
        assertEquals(0, december.breakdown.overspentLastMonthCents)
        assertEquals(0, december.readyToAssignCents)
    }

    // ------------------------------------------------------- credit cards

    @Test
    fun cardPurchaseMovesReserveAndLeavesCardGreen() {
        val input = Fixtures.input(
            transactions = listOf(
                Fixtures.tx(Fixtures.CARD_ID, -8_000, "2025-11-05", Fixtures.GROCERIES_ID),
            ),
            allocations = Fixtures.assigned(Fixtures.GROCERIES_ID to 10_000, month = "2025-11"),
        )
        val november = month(input, "2025-11")
        assertEquals(2_000, november.categories[Fixtures.GROCERIES_ID]!!.availableCents)
        val payment = november.payments[Fixtures.CARD_ID]!!
        assertEquals(8_000, payment.activityCents)
        assertEquals(8_000, payment.availableCents)
        assertEquals(8_000, payment.debtCents)
        assertEquals(PaymentState.GREEN, payment.state)
    }

    @Test
    fun payingTheCardReleasesReserveAndReducesDebt() {
        val transfers = Fixtures.transferPair(Fixtures.CASH_ID, Fixtures.CARD_ID, 8_000, "2025-11-06")
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash(), Fixtures.card()),
            transactions = listOf(
                Fixtures.tx(Fixtures.CARD_ID, -8_000, "2025-11-05", Fixtures.GROCERIES_ID),
            ) + transfers,
            allocations = Fixtures.assigned(Fixtures.GROCERIES_ID to 10_000, month = "2025-11"),
        )
        val payment = month(input, "2025-11").payments[Fixtures.CARD_ID]!!
        assertEquals(0, payment.activityCents)
        assertEquals(0, payment.availableCents)
        assertEquals(0, payment.debtCents)
        assertEquals(PaymentState.GREEN, payment.state)
    }

    @Test
    fun payingMoreThanReservedTurnsThePaymentCategoryRed() {
        val transfers = Fixtures.transferPair(Fixtures.CASH_ID, Fixtures.CARD_ID, 5_000, "2025-11-06")
        val input = Fixtures.input(
            transactions = transfers,
        )
        val payment = month(input, "2025-11").payments[Fixtures.CARD_ID]!!
        assertEquals(-5_000, payment.availableCents)
        assertEquals(PaymentState.RED, payment.state)
    }

    @Test
    fun startingCardBalanceStaysUnderfundedUntilAssigned() {
        val input = Fixtures.input(accounts = listOf(Fixtures.cash(), Fixtures.card(startingBalanceCents = -50_000)))
        val before = month(input, "2025-11").payments[Fixtures.CARD_ID]!!
        assertEquals(50_000, before.debtCents)
        assertEquals(0, before.availableCents)
        assertEquals(PaymentState.YELLOW, before.state)

        val funded = Fixtures.input(
            accounts = listOf(Fixtures.cash(), Fixtures.card(startingBalanceCents = -50_000)),
            allocations = Fixtures.assigned(Fixtures.CARD_PAYMENT_ID to 50_000, month = "2025-11"),
        )
        val after = month(funded, "2025-11").payments[Fixtures.CARD_ID]!!
        assertEquals(PaymentState.GREEN, after.state)
    }

    @Test
    fun cardOverspendRidesAsDebtAndNeverTouchesReadyToAssign() {
        val input = Fixtures.input(
            transactions = listOf(
                Fixtures.tx(Fixtures.CARD_ID, -5_000, "2025-11-05", Fixtures.GROCERIES_ID),
            ),
        )
        val november = month(input, "2025-11")
        assertEquals(0, november.readyToAssignCents)
        assertEquals(5_000, november.categories[Fixtures.GROCERIES_ID]!!.cardShortfallCents)
        assertEquals(0, november.categories[Fixtures.GROCERIES_ID]!!.cashShortfallCents)
        val payment = november.payments[Fixtures.CARD_ID]!!
        assertEquals(0, payment.availableCents)
        assertEquals(5_000, payment.debtCents)
        assertEquals(PaymentState.YELLOW, payment.state)

        val december = calculator.compute(input, "2025-12").month("2025-12")!!
        assertEquals(0, december.breakdown.overspentLastMonthCents)
        assertEquals(0, december.readyToAssignCents)
    }

    @Test
    fun purchasesWhileTheCardHasCreditDoNotReserveCash() {
        // Starting balance +20.00 means the issuer owes us: spending 15.00 creates no debt.
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash(), Fixtures.card(startingBalanceCents = 2_000)),
            transactions = listOf(
                Fixtures.tx(Fixtures.CARD_ID, -1_500, "2025-11-05", Fixtures.GROCERIES_ID),
            ),
            allocations = Fixtures.assigned(Fixtures.GROCERIES_ID to 1_500, month = "2025-11"),
        )
        val payment = month(input, "2025-11").payments[Fixtures.CARD_ID]!!
        assertEquals(0, payment.activityCents)
        assertEquals(0, payment.debtCents)
        assertEquals(PaymentState.GREEN, payment.state)
    }

    @Test
    fun reserveResumesExactlyWhenDebtAppears() {
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash(), Fixtures.card(startingBalanceCents = 2_000)),
            transactions = listOf(
                Fixtures.tx(Fixtures.CARD_ID, -5_000, "2025-11-05", Fixtures.GROCERIES_ID),
            ),
            allocations = Fixtures.assigned(Fixtures.GROCERIES_ID to 5_000, month = "2025-11"),
        )
        val payment = month(input, "2025-11").payments[Fixtures.CARD_ID]!!
        // 20.00 of credit absorbed the purchase; only the remaining 30.00 became debt.
        assertEquals(3_000, payment.debtCents)
        assertEquals(3_000, payment.activityCents)
        assertEquals(PaymentState.GREEN, payment.state)
    }

    @Test
    fun refundReleasesReserveBackToTheCategory() {
        val input = Fixtures.input(
            transactions = listOf(
                Fixtures.tx(Fixtures.CARD_ID, -10_000, "2025-11-05", Fixtures.GROCERIES_ID),
                Fixtures.tx(Fixtures.CARD_ID, 4_000, "2025-11-08", Fixtures.GROCERIES_ID),
            ),
            allocations = Fixtures.assigned(Fixtures.GROCERIES_ID to 10_000, month = "2025-11"),
        )
        val november = month(input, "2025-11")
        val payment = november.payments[Fixtures.CARD_ID]!!
        assertEquals(6_000, payment.activityCents)
        assertEquals(6_000, payment.debtCents)
        assertEquals(4_000, november.categories[Fixtures.GROCERIES_ID]!!.availableCents)
        assertEquals(PaymentState.GREEN, payment.state)
    }

    @Test
    fun cashbackInflowGoesToReadyToAssignAndLeavesReserveAlone() {
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash(), Fixtures.card(startingBalanceCents = -10_000)),
            transactions = listOf(
                Fixtures.tx(Fixtures.CARD_ID, 2_000, "2025-11-05"),
            ),
        )
        val november = month(input, "2025-11")
        assertEquals(2_000, november.readyToAssignCents)
        val payment = november.payments[Fixtures.CARD_ID]!!
        assertEquals(0, payment.activityCents)
        assertEquals(8_000, payment.debtCents)
    }

    @Test
    fun interestFollowsTheFundingCap() {
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash(), Fixtures.card(startingBalanceCents = -10_000)),
            transactions = listOf(
                Fixtures.tx(Fixtures.CARD_ID, -2_000, "2025-11-15", Fixtures.INTEREST_ID),
            ),
            allocations = Fixtures.assigned(Fixtures.INTEREST_ID to 1_500, month = "2025-11"),
        )
        val november = month(input, "2025-11")
        val payment = november.payments[Fixtures.CARD_ID]!!
        assertEquals("only the funded part reserves cash", 1_500, payment.activityCents)
        assertEquals(12_000, payment.debtCents)
        assertEquals(PaymentState.YELLOW, payment.state)
        assertEquals(-500, november.categories[Fixtures.INTEREST_ID]!!.availableCents)
    }

    @Test
    fun fundedInterestMovesReserveAutomatically() {
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash(), Fixtures.card(startingBalanceCents = -10_000)),
            transactions = listOf(
                Fixtures.tx(Fixtures.CARD_ID, -2_000, "2025-11-15", Fixtures.INTEREST_ID),
            ),
            allocations = Fixtures.assigned(
                Fixtures.INTEREST_ID to 2_000,
                Fixtures.CARD_PAYMENT_ID to 10_000,
                month = "2025-11",
            ),
        )
        val payment = month(input, "2025-11").payments[Fixtures.CARD_ID]!!
        assertEquals(2_000, payment.activityCents)
        assertEquals(12_000, payment.availableCents)
        assertEquals(12_000, payment.debtCents)
        assertEquals(PaymentState.GREEN, payment.state)
    }

    @Test
    fun cardToCardTransferMovesDebtWithoutReserve() {
        val cardTwo = Fixtures.card(id = Fixtures.CARD_TWO_ID, name = "Amex")
        val transfers = Fixtures.transferPair(Fixtures.CARD_TWO_ID, Fixtures.CARD_ID, 5_000, "2025-11-10")
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash(), Fixtures.card(startingBalanceCents = -5_000), cardTwo),
            categories = Fixtures.categories() + Fixtures.paymentCategory(
                accountId = Fixtures.CARD_TWO_ID,
                id = Fixtures.CARD_TWO_PAYMENT_ID,
            ),
            transactions = transfers,
        )
        val november = month(input, "2025-11")
        assertEquals(0, november.payments[Fixtures.CARD_ID]!!.debtCents)
        assertEquals(0, november.payments[Fixtures.CARD_ID]!!.activityCents)
        val destination = november.payments[Fixtures.CARD_TWO_ID]!!
        assertEquals(5_000, destination.debtCents)
        assertEquals(0, destination.availableCents)
        assertEquals(PaymentState.YELLOW, destination.state)
    }

    // --------------------------------------------------------- transfers

    @Test
    fun budgetToBudgetTransferIsInvisibleToTheBudget() {
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash(), Fixtures.savings(), Fixtures.card()),
            transactions = listOf(Fixtures.tx(Fixtures.CASH_ID, 100_000, "2025-11-01")) +
                Fixtures.transferPair(Fixtures.CASH_ID, Fixtures.SAVINGS_ID, 40_000, "2025-11-04"),
        )
        val november = month(input, "2025-11")
        assertEquals(100_000, november.readyToAssignCents)
        assertEquals(0, november.expenseCents)
        assertEquals(60_000L, november.accountBalances[Fixtures.CASH_ID])
        assertEquals(40_000L, november.accountBalances[Fixtures.SAVINGS_ID])
    }

    @Test
    fun transferToTrackingIsBudgetSpending() {
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash(), Fixtures.tracking(), Fixtures.card()),
            transactions = listOf(Fixtures.tx(Fixtures.CASH_ID, 100_000, "2025-11-01")) +
                Fixtures.transferPair(Fixtures.CASH_ID, Fixtures.TRACKING_ID, 30_000, "2025-11-04"),
        )
        val november = month(input, "2025-11")
        assertEquals(70_000, november.readyToAssignCents)
        assertEquals(-30_000, november.breakdown.trackingNetCents)
        assertEquals(30_000, november.expenseCents)
    }

    @Test
    fun transferFromTrackingIsBudgetIncome() {
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash(), Fixtures.tracking(), Fixtures.card()),
            transactions = Fixtures.transferPair(Fixtures.TRACKING_ID, Fixtures.CASH_ID, 25_000, "2025-11-04"),
        )
        val november = month(input, "2025-11")
        assertEquals(25_000, november.readyToAssignCents)
        assertEquals(25_000, november.breakdown.trackingNetCents)
        assertEquals(25_000, november.incomeCents)
    }

    @Test
    fun payingACardFromTrackingReleasesReserveWithoutTouchingReadyToAssign() {
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash(), Fixtures.tracking(), Fixtures.card(startingBalanceCents = -10_000)),
            transactions = Fixtures.transferPair(Fixtures.TRACKING_ID, Fixtures.CARD_ID, 10_000, "2025-11-10"),
        )
        val november = month(input, "2025-11")
        assertEquals(0, november.readyToAssignCents)
        val payment = november.payments[Fixtures.CARD_ID]!!
        assertEquals(-10_000, payment.availableCents)
        assertEquals(0, payment.debtCents)
        assertEquals(PaymentState.RED, payment.state)
    }

    // ------------------------------------------------------------ mixed funding

    @Test
    fun mixedCashAndCardOverspendIsAttributedBySource() {
        val input = Fixtures.input(
            transactions = listOf(
                Fixtures.tx(Fixtures.CASH_ID, -6_000, "2025-11-02", Fixtures.GROCERIES_ID),
                Fixtures.tx(Fixtures.CARD_ID, -8_000, "2025-11-03", Fixtures.GROCERIES_ID),
            ),
            allocations = Fixtures.assigned(Fixtures.GROCERIES_ID to 10_000, month = "2025-11"),
        )
        val november = month(input, "2025-11")
        val groceries = november.categories[Fixtures.GROCERIES_ID]!!
        assertEquals(-4_000, groceries.availableCents)
        assertEquals("cash was fully funded first", 0, groceries.cashShortfallCents)
        assertEquals(4_000, groceries.cardShortfallCents)
        assertEquals(4_000, november.payments[Fixtures.CARD_ID]!!.activityCents)
        assertEquals(0, november.breakdown.overspentLastMonthCents)
    }

    @Test
    fun cashShortfallPrecedesCardSpendInDateOrder() {
        val input = Fixtures.input(
            transactions = listOf(
                Fixtures.tx(Fixtures.CASH_ID, -12_000, "2025-11-02", Fixtures.GROCERIES_ID),
                Fixtures.tx(Fixtures.CARD_ID, -2_000, "2025-11-03", Fixtures.GROCERIES_ID),
            ),
            allocations = Fixtures.assigned(Fixtures.GROCERIES_ID to 10_000, month = "2025-11"),
        )
        val groceries = month(input, "2025-11").categories[Fixtures.GROCERIES_ID]!!
        assertEquals(2_000, groceries.cashShortfallCents)
        assertEquals("nothing left to fund the later card charge", 2_000, groceries.cardShortfallCents)
    }

    // ------------------------------------------------------------- net worth

    @Test
    fun netWorthSplitsAssetsAndLiabilities() {
        val input = Fixtures.input(
            accounts = listOf(Fixtures.cash(), Fixtures.card(startingBalanceCents = -50_000)),
            transactions = listOf(Fixtures.tx(Fixtures.CASH_ID, 100_000, "2025-11-01")),
        )
        val point = calculator.compute(input, "2025-11").netWorth.last()
        assertEquals(100_000, point.assetsCents)
        assertEquals(50_000, point.liabilitiesCents)
        assertEquals(50_000, point.netCents)
    }
}
