package com.envelope.budget.domain

import java.time.LocalDate

/** A budget account (on- or off-budget). */
data class Account(
    val id: Long,
    val name: String,
    val type: AccountType,
    val startingBalanceCents: Long,
    val startingDate: LocalDate,
    val isTracking: Boolean,
    val isClosed: Boolean,
    val sortOrder: Int,
) {
    /** On-budget cash: checking, savings, cash — spending from here is real cash leaving. */
    val isBudgetCash: Boolean get() = !isTracking && !type.isCredit

    /** On-budget credit card: a liability that owns a Payment category. */
    val isBudgetCard: Boolean get() = !isTracking && type.isCredit
}

data class CategoryGroup(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val isHidden: Boolean,
    val isSystem: Boolean,
)

data class Category(
    val id: Long,
    val groupId: Long,
    val name: String,
    val note: String,
    val sortOrder: Int,
    val isHidden: Boolean,
    val isSystem: Boolean,
    val systemKind: SystemKind?,
    val linkedAccountId: Long?,
    val targetType: TargetType,
    val targetAmountCents: Long?,
    val targetDate: LocalDate?,
) {
    val isPaymentCategory: Boolean get() = systemKind == SystemKind.CARD_PAYMENT
    val isAdjustment: Boolean get() = systemKind == SystemKind.ADJUSTMENT
    val hasTarget: Boolean get() = targetType != TargetType.NONE && targetAmountCents != null
}

data class Transaction(
    val id: Long,
    val accountId: Long,
    val categoryId: Long?,
    val date: LocalDate,
    val payee: String,
    val memo: String,
    val amountCents: Long,
    val cleared: ClearanceState,
    val flag: FlagColor,
    val transferPeerId: Long?,
) {
    val isTransfer: Boolean get() = transferPeerId != null
    val isOutflow: Boolean get() = amountCents < 0
}

data class ScheduledTransaction(
    val id: Long,
    val accountId: Long,
    val categoryId: Long?,
    val payee: String,
    val memo: String,
    val amountCents: Long,
    val recurrence: Recurrence,
    val nextDate: LocalDate,
    val lastPostedDate: LocalDate?,
    val isActive: Boolean,
    val isTransfer: Boolean,
    val transferAccountId: Long?,
)

/**
 * Everything the pure calculator needs. Built from the database by the repository, so
 * the engine never touches Android, Room, or I/O.
 */
data class BudgetInput(
    val accounts: List<Account>,
    val groups: List<CategoryGroup>,
    val categories: List<Category>,
    /** Sorted by (date, id) — the engine relies on this ordering being stable. */
    val transactions: List<Transaction>,
    val allocations: Map<String, Map<Long, Long>>,
    val snoozedTargets: Set<TargetKey>,
    val mode: OverspendMode,
) {
    val accountsById: Map<Long, Account> = accounts.associateBy { it.id }
    val categoriesById: Map<Long, Category> = categories.associateBy { it.id }
    val transactionsById: Map<Long, Transaction> = transactions.associateBy { it.id }
}

data class TargetKey(val categoryId: Long, val month: String)

/** Per-category result for one month. */
data class CategoryMonth(
    val categoryId: Long,
    val assignedCents: Long,
    val activityCents: Long,
    val rolloverInCents: Long,
    val availableCents: Long,
    /** Portion of this month's shortfall funded from cash — deducted from next month's RTA. */
    val cashShortfallCents: Long,
    /** Portion funded by card debt — never deducted from RTA, shows as underfunding. */
    val cardShortfallCents: Long,
)

/** Per-card Payment category result for one month. */
data class PaymentMonth(
    val accountId: Long,
    val paymentCategoryId: Long,
    val assignedCents: Long,
    /** Reserve moves: purchases add, refunds and payments subtract. */
    val activityCents: Long,
    val rolloverInCents: Long,
    val availableCents: Long,
    /** Outstanding balance expressed as a positive amount owed. */
    val debtCents: Long,
    val state: PaymentState,
)

data class RtaBreakdown(
    val carriedInCents: Long,
    val incomeCents: Long,
    val assignedCents: Long,
    val overspentLastMonthCents: Long,
    val trackingNetCents: Long,
)

data class MonthResult(
    val month: String,
    val readyToAssignCents: Long,
    val breakdown: RtaBreakdown,
    val categories: Map<Long, CategoryMonth>,
    /** Keyed by credit account id. */
    val payments: Map<Long, PaymentMonth>,
    /** Account id -> signed balance at the end of this month. */
    val accountBalances: Map<Long, Long>,
    /** Category id -> positive amount spent this month (excludes system categories). */
    val spendingByCategory: Map<Long, Long>,
    val incomeCents: Long,
    val expenseCents: Long,
) {
    val totalAssignedCents: Long get() = categories.values.sumOf { it.assignedCents }
    val totalActivityCents: Long get() = categories.values.sumOf { it.activityCents }
    val totalAvailableCents: Long get() = categories.values.sumOf { it.availableCents }
}

data class NetWorthPoint(
    val month: String,
    val assetsCents: Long,
    val liabilitiesCents: Long,
) {
    val netCents: Long get() = assetsCents - liabilitiesCents
}

data class BudgetHistory(
    val months: Map<String, MonthResult>,
    val netWorth: List<NetWorthPoint>,
    /** Latest known balances, for the Accounts screen. */
    val currentBalances: Map<Long, Long>,
) {
    fun month(key: String): MonthResult? = months[key]
}
