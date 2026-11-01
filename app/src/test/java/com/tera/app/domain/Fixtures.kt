package com.tera.app.domain

import java.time.LocalDate

/**
 * Shared fixture builders for the engine tests. Kept deliberately small and explicit:
 * every test states the ledger it needs instead of hiding it behind helpers.
 */
object Fixtures {

    const val CASH_ID = 1L
    const val CARD_ID = 2L
    const val SAVINGS_ID = 3L
    const val TRACKING_ID = 4L
    const val CARD_TWO_ID = 5L

    const val LIVING_GROUP = 10L
    const val PAYMENTS_GROUP = 20L

    const val GROCERIES_ID = 11L
    const val INTEREST_ID = 12L
    const val ADJUSTMENT_ID = 13L
    const val CARD_PAYMENT_ID = 100L
    const val CARD_TWO_PAYMENT_ID = 105L

    fun cash(): Account = Account(
        id = CASH_ID,
        name = "Checking",
        type = AccountType.CHECKING,
        startingBalanceCents = 0,
        startingDate = LocalDate.parse("2025-01-01"),
        isTracking = false,
        isClosed = false,
        sortOrder = 0,
    )

    fun savings(): Account = cash().copy(id = SAVINGS_ID, name = "Savings", type = AccountType.SAVINGS)

    fun tracking(): Account = cash().copy(
        id = TRACKING_ID,
        name = "Brokerage",
        type = AccountType.CHECKING,
        isTracking = true,
    )

    fun card(
        id: Long = CARD_ID,
        startingBalanceCents: Long = 0,
        name: String = "Visa",
    ): Account = Account(
        id = id,
        name = name,
        type = AccountType.CREDIT,
        startingBalanceCents = startingBalanceCents,
        startingDate = LocalDate.parse("2025-01-01"),
        isTracking = false,
        isClosed = false,
        sortOrder = id.toInt(),
    )

    fun groups(): List<CategoryGroup> = listOf(
        CategoryGroup(LIVING_GROUP, "Living", 0, false, false),
        CategoryGroup(PAYMENTS_GROUP, "Credit Card Payments", 1, false, true),
    )

    fun paymentCategory(accountId: Long = CARD_ID, id: Long = CARD_PAYMENT_ID): Category = Category(
        id = id,
        groupId = PAYMENTS_GROUP,
        name = "Visa",
        note = "",
        sortOrder = 0,
        isHidden = false,
        isSystem = true,
        systemKind = SystemKind.CARD_PAYMENT,
        linkedAccountId = accountId,
        targetType = TargetType.NONE,
        targetAmountCents = null,
        targetDate = null,
    )

    fun categories(): List<Category> = listOf(
        Category(
            id = GROCERIES_ID,
            groupId = LIVING_GROUP,
            name = "Groceries",
            note = "",
            sortOrder = 0,
            isHidden = false,
            isSystem = false,
            systemKind = null,
            linkedAccountId = null,
            targetType = TargetType.NONE,
            targetAmountCents = null,
            targetDate = null,
        ),
        Category(
            id = INTEREST_ID,
            groupId = LIVING_GROUP,
            name = "Interest",
            note = "",
            sortOrder = 1,
            isHidden = false,
            isSystem = false,
            systemKind = null,
            linkedAccountId = null,
            targetType = TargetType.NONE,
            targetAmountCents = null,
            targetDate = null,
        ),
        Category(
            id = ADJUSTMENT_ID,
            groupId = LIVING_GROUP,
            name = "Adjustment",
            note = "",
            sortOrder = 2,
            isHidden = true,
            isSystem = true,
            systemKind = SystemKind.ADJUSTMENT,
            linkedAccountId = null,
            targetType = TargetType.NONE,
            targetAmountCents = null,
            targetDate = null,
        ),
        paymentCategory(),
    )

    private var nextId = 1000L

    fun tx(
        accountId: Long,
        amountCents: Long,
        date: String,
        categoryId: Long? = null,
        payee: String = "",
        id: Long = nextId++,
        transferPeerId: Long? = null,
    ): Transaction = Transaction(
        id = id,
        accountId = accountId,
        categoryId = categoryId,
        date = LocalDate.parse(date),
        payee = payee,
        memo = "",
        amountCents = amountCents,
        cleared = ClearanceState.UNCLEARED,
        flag = FlagColor.NONE,
        transferPeerId = transferPeerId,
    )

    /** Builds a matched transfer pair (two rows) between two accounts. */
    fun transferPair(
        fromAccountId: Long,
        toAccountId: Long,
        amountCents: Long,
        date: String,
        id: Long = nextId++,
        peerId: Long = nextId++,
    ): List<Transaction> = listOf(
        tx(
            accountId = fromAccountId,
            amountCents = -amountCents,
            date = date,
            id = id,
            transferPeerId = peerId,
        ),
        tx(
            accountId = toAccountId,
            amountCents = amountCents,
            date = date,
            id = peerId,
            transferPeerId = id,
        ),
    )

    fun input(
        accounts: List<Account> = listOf(cash(), card()),
        categories: List<Category> = categories(),
        transactions: List<Transaction> = emptyList(),
        allocations: Map<String, Map<Long, Long>> = emptyMap(),
        mode: OverspendMode = OverspendMode.DEDUCT_FROM_NEXT_MONTH,
        snoozed: Set<TargetKey> = emptySet(),
    ): BudgetInput = BudgetInput(
        accounts = accounts,
        groups = groups(),
        categories = categories,
        transactions = transactions,
        allocations = allocations,
        snoozedTargets = snoozed,
        mode = mode,
    )

    fun assigned(vararg pairs: Pair<Long, Long>, month: String): Map<String, Map<Long, Long>> =
        mapOf(month to pairs.toMap())
}
