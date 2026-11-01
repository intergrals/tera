package com.envelope.budget.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Age of Money and Days of Buffering — both definitions are pinned here, tested in
 * [com.envelope.budget.domain.MetricsTest], and repeated verbatim in the in-app
 * disclosure screen so the model's limits are never hidden from the user.
 *
 * Age of Money: money ages until it leaves the budget as **cash**. A card purchase does
 * not age-spend money; the cash keeps aging in the card's Payment reserve until the card
 * is paid. So cash outflows are: outflows from budget cash accounts (excluding transfers
 * to other budget cash accounts), card payments, and transfers to tracking accounts.
 */
object Metrics {

    /** Age of Money in whole days, or null when there is not enough history. */
    fun ageOfMoney(input: BudgetInput): Int? {
        val accounts = input.accountsById
        val transactions = input.transactionsById

        val events = input.transactions
            .filter { accounts[it.accountId]?.isBudgetCash == true }
            .filter { tx ->
                if (tx.isTransfer) {
                    val peer = tx.transferPeerId?.let { id -> transactions[id] }
                    val other = peer?.let { accounts[it.accountId] }
                    // Budget-cash -> budget-cash is an internal move, not income or spending.
                    other != null && !other.isBudgetCash
                } else {
                    true
                }
            }
            .sortedWith(compareBy({ it.date }, { it.id }))

        val lots = ArrayDeque<Lot>()
        val matched = ArrayList<MatchedOutflow>()

        events.forEach { tx ->
            if (tx.amountCents > 0) {
                // Only uncategorized cash inflows are new money to age (not refunds).
                if (!tx.isTransfer && tx.categoryId == null) {
                    lots.addLast(Lot(tx.date, tx.amountCents))
                }
                return@forEach
            }
            var remaining = -tx.amountCents
            var weight = 0L
            var matchedAmount = 0L
            while (remaining > 0) {
                val lot = lots.firstOrNull() ?: break
                val take = minOf(lot.remaining, remaining)
                val ageDays = ChronoUnit.DAYS.between(lot.date, tx.date)
                weight += take * ageDays
                matchedAmount += take
                lot.remaining -= take
                remaining -= take
                if (lot.remaining <= 0) lots.removeFirst()
            }
            if (matchedAmount > 0) {
                matched.add(MatchedOutflow(weight, matchedAmount))
            }
        }

        val recent = matched.takeLast(10)
        val totalWeight = recent.sumOf { it.weight }
        val totalAmount = recent.sumOf { it.amount }
        if (totalAmount <= 0) return null
        return (totalWeight / totalAmount).toInt().coerceAtMost(999)
    }

    /**
     * Days of Buffering: how long the current on-budget cash would last at the recent
     * burn rate. Cash counts only positive budget-account balances, so carrying card
     * debt cannot inflate the number.
     */
    fun daysOfBuffering(input: BudgetInput, asOf: LocalDate = LocalDate.now()): Int? {
        val balances = currentBalances(input)
        val cash = input.accounts
            .filter { !it.isTracking }
            .sumOf { maxOf(0L, balances[it.id] ?: 0L) }

        val windowStart = asOf.minusDays(90)
        val spent = cashOutflows(input)
            .filter { !it.date.isBefore(windowStart) && !it.date.isAfter(asOf) }
            .sumOf { -it.amountCents }

        val firstDate = input.transactions.minOfOrNull { it.date }
        val observedDays = if (firstDate == null) {
            1L
        } else {
            (ChronoUnit.DAYS.between(firstDate, asOf) + 1).coerceIn(1, 90)
        }
        val averageDaily = spent / observedDays
        if (averageDaily <= 0L) return null
        return (cash / averageDaily).toInt()
    }

    /** Sum of positive balances across every budget account (cash and cards). */
    fun onBudgetCash(input: BudgetInput): Long {
        val balances = currentBalances(input)
        return input.accounts
            .filter { !it.isTracking }
            .sumOf { maxOf(0L, balances[it.id] ?: 0L) }
    }

    fun currentBalances(input: BudgetInput): Map<Long, Long> {
        val balances = HashMap<Long, Long>()
        input.accounts.forEach { balances[it.id] = it.startingBalanceCents }
        input.transactions.forEach { tx ->
            balances[tx.accountId] = (balances[tx.accountId] ?: 0L) + tx.amountCents
        }
        return balances
    }

    /** Cash outflows as defined for Age of Money; reused so both metrics stay consistent. */
    fun cashOutflows(input: BudgetInput): List<Transaction> {
        val accounts = input.accountsById
        val transactions = input.transactionsById
        return input.transactions
            .filter { accounts[it.accountId]?.isBudgetCash == true && it.amountCents < 0 }
            .filter { tx ->
                if (!tx.isTransfer) return@filter true
                val other = tx.transferPeerId?.let { id -> transactions[id] }
                    ?.let { accounts[it.accountId] }
                other != null && !other.isBudgetCash
            }
            .sortedWith(compareBy({ it.date }, { it.id }))
    }

    private data class Lot(val date: LocalDate, var remaining: Long)

    private data class MatchedOutflow(val weight: Long, val amount: Long)
}
