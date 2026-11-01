package com.tera.app.domain

/** Account kinds. Credit accounts are budget accounts that also own a Payment category. */
enum class AccountType {
    CHECKING,
    SAVINGS,
    CASH,
    CREDIT;

    val isCredit: Boolean get() = this == CREDIT

    companion object {
        fun fromStorage(value: String): AccountType =
            entries.firstOrNull { it.name == value } ?: CHECKING
    }
}

/** System-owned categories cannot be deleted or picked as a transaction category. */
enum class SystemKind {
    ADJUSTMENT,
    CARD_PAYMENT;

    companion object {
        fun fromStorage(value: String?): SystemKind? =
            value?.let { raw -> entries.firstOrNull { it.name == raw } }
    }
}

enum class TargetType {
    NONE,
    NEEDED_FOR_SPENDING,
    SAVINGS_BALANCE,
    MONTHLY_SAVINGS_BUILDER;

    companion object {
        fun fromStorage(value: String): TargetType =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}

/** How overspending is resolved when the month rolls over. */
enum class OverspendMode {
    /** YNAB behaviour: cash shortfall hits next month's Ready to Assign, card shortfall rides as debt. */
    YNAB,

    /** Negative Available carries into next month as red; Ready to Assign is untouched. */
    CARRY_NEGATIVE;

    companion object {
        fun fromStorage(value: String?): OverspendMode =
            entries.firstOrNull { it.name == value } ?: YNAB
    }
}

enum class ClearanceState {
    UNCLEARED,
    CLEARED,
    RECONCILED;

    companion object {
        fun fromOrdinal(value: Int): ClearanceState = entries.getOrElse(value) { UNCLEARED }
    }
}

/** Transaction flags, mirroring YNAB's colour flags. Ordinal is the stored value. */
enum class FlagColor {
    NONE,
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    BLUE,
    PURPLE;

    companion object {
        fun fromOrdinal(value: Int): FlagColor = entries.getOrElse(value) { NONE }
    }
}

enum class Recurrence {
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY;

    companion object {
        fun fromStorage(value: String): Recurrence =
            entries.firstOrNull { it.name == value } ?: MONTHLY
    }
}

/** Three-state health of a credit card Payment category. */
enum class PaymentState {
    /** Available covers the full card balance. */
    GREEN,

    /** Owes more than is reserved (starting balance, overspending, transfers, interest). */
    YELLOW,

    /** Paid more than was reserved; assigning to the Payment category fixes it. */
    RED,
}

/** How a transfer is treated by the budget, decided by its two endpoints. */
enum class TransferKind {
    BUDGET_TO_BUDGET,
    BUDGET_TO_CARD,
    CARD_TO_BUDGET,
    BUDGET_TO_TRACKING,
    TRACKING_TO_BUDGET,
    CARD_AND_TRACKING,
    CARD_TO_CARD,
    TRACKING_TO_TRACKING,
}
