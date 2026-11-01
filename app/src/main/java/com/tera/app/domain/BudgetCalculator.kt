package com.tera.app.domain

/**
 * Pure budget engine.
 *
 * The engine replays every transaction once, in (date, id) order, across consecutive
 * months, and records per-month results. Nothing is cached or stored: the whole month
 * history is recomputed from the input, so the ledger and the budget can never diverge.
 *
 * Credit card reserve rule (funding-capped):
 *   outflows: reserveDelta = min(debtIncrease, fundedFromCategory)
 *   inflows:  reserveDelta = debtDelta (negative, releasing reserve)
 * An unfunded card purchase leaves debt without reserve, so the card's Payment category
 * goes underfunded (YELLOW) instead of silently manufacturing payable cash.
 */
class BudgetCalculator {

    fun compute(input: BudgetInput, throughMonth: String): BudgetHistory {
        val transactions = input.transactions.sortedWith(compareBy({ it.date }, { it.id }))
        val byMonth = transactions.groupBy { MonthKey.of(it.date) }

        val firstDataMonth = minOf(
            byMonth.keys.minOrNull() ?: throughMonth,
            input.allocations.keys.minOrNull() ?: throughMonth,
        )
        val months = if (firstDataMonth > throughMonth) {
            listOf(throughMonth)
        } else {
            MonthKey.range(firstDataMonth, throughMonth)
        }

        val state = SimState(input)
        val results = LinkedHashMap<String, MonthResult>()
        val netWorth = ArrayList<NetWorthPoint>()

        for (month in months) {
            state.beginMonth(month, results[MonthKey.previous(month)])
            byMonth[month]?.forEach { state.apply(it) }
            results[month] = state.endMonth(month)
            netWorth.add(state.netWorthPoint(month))
        }

        return BudgetHistory(
            months = results,
            netWorth = netWorth,
            currentBalances = state.balances.toMap(),
        )
    }

    /** Mutable simulation state, one instance per [compute] call. */
    private class SimState(private val input: BudgetInput) {

        private val categories = input.categories
        private val categoryIds = categories.map { it.id }
        private val paymentCategoryByAccount = categories
            .filter { it.isPaymentCategory && it.linkedAccountId != null }
            .associate { it.linkedAccountId!! to it.id }

        val balances = HashMap<Long, Long>()
        private val startingApplied = HashSet<Long>()

        private val runningAvailable = HashMap<Long, Long>()
        private val assignedThisMonth = HashMap<Long, Long>()
        private val activityThisMonth = HashMap<Long, Long>()
        private val rolloverThisMonth = HashMap<Long, Long>()
        private val cashShortfall = HashMap<Long, Long>()
        private val cardShortfall = HashMap<Long, Long>()

        /** Reserve moves for the current month, keyed by card account id. */
        private val reserveThisMonth = HashMap<Long, Long>()
        private val paymentRollover = HashMap<Long, Long>()

        private var rtaCarriedIn = 0L
        private var rtaOverspentCarried = 0L
        private var rtaIncome = 0L
        private var trackingNet = 0L
        private var assignedTotal = 0L
        private var incomeThisMonth = 0L
        private var expenseThisMonth = 0L
        private val spendingByCategory = HashMap<Long, Long>()

        fun beginMonth(monthKey: String, previous: MonthResult?) {
            assignedThisMonth.clear()
            activityThisMonth.clear()
            rolloverThisMonth.clear()
            cashShortfall.clear()
            cardShortfall.clear()
            reserveThisMonth.clear()
            rtaIncome = 0L
            trackingNet = 0L
            assignedTotal = 0L
            incomeThisMonth = 0L
            expenseThisMonth = 0L
            spendingByCategory.clear()

            // Starting balances enter the ledger in the first month that contains them.
            val monthEnd = MonthKey.parse(monthKey).atEndOfMonth()
            input.accounts.forEach { account ->
                if (account.id !in startingApplied && !account.startingDate.isAfter(monthEnd)) {
                    balances[account.id] = (balances[account.id] ?: 0L) + account.startingBalanceCents
                    startingApplied.add(account.id)
                }
            }

            if (previous == null) {
                rtaCarriedIn = 0L
                rtaOverspentCarried = 0L
            } else {
                rtaCarriedIn = previous.readyToAssignCents
                rtaOverspentCarried = if (input.mode == OverspendMode.DEDUCT_FROM_NEXT_MONTH) {
                    previous.categories.values.sumOf { it.cashShortfallCents }
                } else {
                    0L
                }
            }

            val monthAllocations = input.allocations[monthKey].orEmpty()
            val previousCategories = previous?.categories.orEmpty()
            val previousPayments = previous?.payments.orEmpty()

            categoryIds.forEach { categoryId ->
                val carried = previousCategories[categoryId]
                val rollover = when {
                    carried == null -> 0L
                    input.mode == OverspendMode.CARRY_NEGATIVE -> carried.availableCents
                    else -> maxOf(0L, carried.availableCents)
                }
                val assigned = monthAllocations[categoryId] ?: 0L

                rolloverThisMonth[categoryId] = rollover
                // Available = rollover + assigned + activity, accumulated during the month.
                runningAvailable[categoryId] = rollover + assigned
                assignedThisMonth[categoryId] = assigned
                assignedTotal += assigned
                activityThisMonth[categoryId] = 0L
            }

            // Payment categories always carry their balance: a negative reserve (you paid the
            // card more than you had assigned) is a real deficit that must be made up, so it
            // stays RED across months until the user assigns money to it.
            paymentCategoryByAccount.forEach { (accountId, _) ->
                val carried = previousPayments[accountId]
                paymentRollover[accountId] = carried?.availableCents ?: 0L
                reserveThisMonth[accountId] = 0L
            }
        }

        fun apply(tx: Transaction) {
            val account = input.accountsById[tx.accountId] ?: return
            val before = balances[account.id] ?: 0L
            val after = before + tx.amountCents
            balances[account.id] = after

            if (tx.isTransfer) {
                if (tx.id < tx.transferPeerId!!) applyTransfer(tx)
                return
            }

            val categoryId = tx.categoryId
            if (categoryId != null) {
                applyCategorized(tx, account, before, after, categoryId)
            } else if (tx.amountCents > 0) {
                rtaIncome += tx.amountCents
                incomeThisMonth += tx.amountCents
            } else {
                rtaIncome += tx.amountCents
                expenseThisMonth += -tx.amountCents
            }
        }

        private fun applyCategorized(
            tx: Transaction,
            account: Account,
            balanceBefore: Long,
            balanceAfter: Long,
            categoryId: Long,
        ) {
            val category = input.categoriesById[categoryId] ?: return
            val amount = tx.amountCents
            val before = runningAvailable[categoryId] ?: 0L
            val after = before + amount
            runningAvailable[categoryId] = after
            activityThisMonth[categoryId] = (activityThisMonth[categoryId] ?: 0L) + amount

            if (category.isPaymentCategory) {
                // Payment categories are card-managed: never categorized to directly.
                return
            }

            if (amount < 0) {
                val overflow = maxOf(0L, -after) - maxOf(0L, -before)
                if (overflow > 0) {
                    if (account.isBudgetCard) {
                        cardShortfall[categoryId] = (cardShortfall[categoryId] ?: 0L) + overflow
                    } else if (account.isBudgetCash) {
                        cashShortfall[categoryId] = (cashShortfall[categoryId] ?: 0L) + overflow
                    }
                }
                if (account.isBudgetCard) {
                    val funded = -amount - maxOf(0L, overflow)
                    val debtIncrease = maxOf(0L, -balanceAfter) - maxOf(0L, -balanceBefore)
                    val reserve = minOf(debtIncrease, funded)
                    if (reserve > 0) {
                        reserveThisMonth[account.id] = (reserveThisMonth[account.id] ?: 0L) + reserve
                    }
                }
                expenseThisMonth += -amount
            } else {
                if (account.isBudgetCard) {
                    val debtDelta = maxOf(0L, -balanceAfter) - maxOf(0L, -balanceBefore)
                    if (debtDelta < 0) {
                        reserveThisMonth[account.id] =
                            (reserveThisMonth[account.id] ?: 0L) + debtDelta
                    }
                }
                // A refund reduces the month's net expenses.
                expenseThisMonth -= amount
            }

            if (!category.isAdjustment) {
                // Net spend: outflows positive, refunds negative. Report layers clamp for charts.
                spendingByCategory[categoryId] = (spendingByCategory[categoryId] ?: 0L) - amount
            }
        }

        private fun applyTransfer(tx: Transaction) {
            val peer = input.transactionsById[tx.transferPeerId!!] ?: return
            val outflowLeg = if (tx.amountCents < 0) tx else peer
            val inflowLeg = if (tx.amountCents < 0) peer else tx
            val from = input.accountsById[outflowLeg.accountId] ?: return
            val to = input.accountsById[inflowLeg.accountId] ?: return
            val amount = -outflowLeg.amountCents

            when (TransferClassifier.classify(from, to)) {
                TransferKind.BUDGET_TO_BUDGET -> Unit
                TransferKind.BUDGET_TO_CARD -> subtractReserve(to.id, amount)
                TransferKind.CARD_TO_BUDGET -> Unit
                TransferKind.BUDGET_TO_TRACKING -> {
                    trackingNet -= amount
                    expenseThisMonth += amount
                }
                TransferKind.TRACKING_TO_BUDGET -> {
                    trackingNet += amount
                    incomeThisMonth += amount
                }
                TransferKind.CARD_AND_TRACKING -> {
                    val card = if (from.isBudgetCard) from else to
                    subtractReserve(card.id, amount)
                }
                TransferKind.CARD_TO_CARD -> Unit
                TransferKind.TRACKING_TO_TRACKING -> Unit
            }
        }

        private fun subtractReserve(accountId: Long, amount: Long) {
            reserveThisMonth[accountId] = (reserveThisMonth[accountId] ?: 0L) - amount
        }

        fun endMonth(monthKey: String): MonthResult {
            val categoryResults = LinkedHashMap<Long, CategoryMonth>()
            categoryIds.forEach { categoryId ->
                val rollover = rolloverThisMonth[categoryId] ?: 0L
                categoryResults[categoryId] = CategoryMonth(
                    categoryId = categoryId,
                    assignedCents = assignedThisMonth[categoryId] ?: 0L,
                    activityCents = activityThisMonth[categoryId] ?: 0L,
                    rolloverInCents = rollover,
                    availableCents = runningAvailable[categoryId] ?: rollover,
                    cashShortfallCents = cashShortfall[categoryId] ?: 0L,
                    cardShortfallCents = cardShortfall[categoryId] ?: 0L,
                )
            }

            val paymentResults = LinkedHashMap<Long, PaymentMonth>()
            paymentCategoryByAccount.forEach { (accountId, paymentCategoryId) ->
                val activity = reserveThisMonth[accountId] ?: 0L
                val rollover = paymentRollover[accountId] ?: 0L
                val assigned = assignedThisMonth[paymentCategoryId] ?: 0L
                val available = rollover + assigned + activity
                val debt = maxOf(0L, -(balances[accountId] ?: 0L))
                val state = when {
                    available < 0 -> PaymentState.RED
                    debt == 0L -> PaymentState.GREEN
                    available < debt -> PaymentState.YELLOW
                    else -> PaymentState.GREEN
                }
                paymentResults[accountId] = PaymentMonth(
                    accountId = accountId,
                    paymentCategoryId = paymentCategoryId,
                    assignedCents = assigned,
                    activityCents = activity,
                    rolloverInCents = rollover,
                    availableCents = available,
                    debtCents = debt,
                    state = state,
                )
            }

            val rta = rtaCarriedIn + rtaIncome + trackingNet - assignedTotal - rtaOverspentCarried

            return MonthResult(
                month = monthKey,
                readyToAssignCents = rta,
                breakdown = RtaBreakdown(
                    carriedInCents = rtaCarriedIn,
                    incomeCents = rtaIncome,
                    assignedCents = assignedTotal,
                    overspentLastMonthCents = rtaOverspentCarried,
                    trackingNetCents = trackingNet,
                ),
                categories = categoryResults,
                payments = paymentResults,
                accountBalances = balances.toMap(),
                spendingByCategory = spendingByCategory.toMap(),
                incomeCents = incomeThisMonth,
                expenseCents = expenseThisMonth,
            )
        }

        fun netWorthPoint(monthKey: String): NetWorthPoint {
            var assets = 0L
            var liabilities = 0L
            input.accounts.forEach { account ->
                val balance = balances[account.id] ?: 0L
                if (balance >= 0) assets += balance else liabilities += -balance
            }
            return NetWorthPoint(monthKey, assets, liabilities)
        }
    }
}
