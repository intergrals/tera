package com.tera.app.domain

/**
 * Decides how a transfer pair is treated by the budget, from its two endpoints.
 *
 * Transfers are **not** uniformly ignored. Money crossing the budget boundary is a real
 * budget flow: sending money to a tracking account is spending, and receiving money from
 * one is income. Transfers between two budget accounts are invisible to Ready to Assign,
 * except that paying a credit card releases its Payment reserve.
 */
object TransferClassifier {

    fun classify(from: Account, to: Account): TransferKind {
        val fromCard = from.isBudgetCard
        val toCard = to.isBudgetCard
        val fromTracking = from.isTracking
        val toTracking = to.isTracking

        return when {
            fromTracking && toTracking -> TransferKind.TRACKING_TO_TRACKING
            fromCard && toCard -> TransferKind.CARD_TO_CARD
            fromCard && toTracking -> TransferKind.CARD_AND_TRACKING
            fromTracking && toCard -> TransferKind.CARD_AND_TRACKING
            fromCard -> TransferKind.CARD_TO_BUDGET
            toCard -> TransferKind.BUDGET_TO_CARD
            fromTracking -> TransferKind.TRACKING_TO_BUDGET
            toTracking -> TransferKind.BUDGET_TO_TRACKING
            else -> TransferKind.BUDGET_TO_BUDGET
        }
    }

    /** True when the transfer moves cash out of the budget (RTA spending). */
    fun isBudgetOutflow(kind: TransferKind): Boolean = kind == TransferKind.BUDGET_TO_TRACKING

    /** True when the transfer brings cash into the budget (RTA income). */
    fun isBudgetInflow(kind: TransferKind): Boolean = kind == TransferKind.TRACKING_TO_BUDGET
}

fun AccountType.isCashLike(): Boolean =
    this == AccountType.CHECKING || this == AccountType.SAVINGS || this == AccountType.CASH

fun AccountType.isLiability(): Boolean = this == AccountType.CREDIT
