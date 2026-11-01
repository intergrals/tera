package com.envelope.budget.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    indices = [Index("isTracking"), Index("type")],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** [com.envelope.budget.domain.AccountType] name. */
    val type: String,
    val startingBalanceCents: Long,
    /** ISO date (yyyy-MM-dd) the starting balance applies from. */
    val startingDate: String,
    val isTracking: Boolean = false,
    val isClosed: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "category_groups",
    indices = [Index("sortOrder")],
)
data class CategoryGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val isHidden: Boolean = false,
    /** System group ("Credit Card Payments") that the app manages. */
    val isSystem: Boolean = false,
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId"), Index("linkedAccountId"), Index("sortOrder")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val name: String,
    val note: String = "",
    val sortOrder: Int = 0,
    val isHidden: Boolean = false,
    /** System categories (Adjustment, card Payment) are undeletable and unpickable. */
    val isSystem: Boolean = false,
    /** [com.envelope.budget.domain.SystemKind] name, or null for user categories. */
    val systemKind: String? = null,
    /** For card Payment categories: the credit account this category pays. */
    val linkedAccountId: Long? = null,
    /** [com.envelope.budget.domain.TargetType] name. */
    val targetType: String = "NONE",
    val targetAmountCents: Long? = null,
    /** ISO date for by-date targets. */
    val targetDate: String? = null,
)

@Entity(
    tableName = "target_snoozes",
    primaryKeys = ["categoryId", "month"],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class TargetSnoozeEntity(
    val categoryId: Long,
    val month: String,
)

@Entity(
    tableName = "budget_allocations",
    primaryKeys = ["month", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class AllocationEntity(
    /** yyyy-MM */
    val month: String,
    val categoryId: Long,
    val assignedCents: Long,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("accountId"),
        Index("date"),
        Index(value = ["categoryId", "date"]),
        Index("transferPeerId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val categoryId: Long? = null,
    /** ISO date (yyyy-MM-dd). */
    val date: String,
    val payee: String = "",
    val memo: String = "",
    /** Signed amount in cents. Negative = money out of the account. */
    val amountCents: Long,
    /** [com.envelope.budget.domain.ClearanceState] ordinal. */
    val cleared: Int = 0,
    /** [com.envelope.budget.domain.FlagColor] ordinal. */
    val flag: Int = 0,
    /** Set on both legs of a transfer; the pair is created, edited and deleted together. */
    val transferPeerId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "scheduled_transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("accountId"), Index("nextDate"), Index("categoryId")],
)
data class ScheduledEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val categoryId: Long? = null,
    val payee: String,
    val memo: String = "",
    val amountCents: Long,
    /** [com.envelope.budget.domain.Recurrence] name. */
    val frequency: String,
    /** ISO date (yyyy-MM-dd) of the next occurrence. */
    val nextDate: String,
    val lastPostedDate: String? = null,
    val isActive: Boolean = true,
    val isTransfer: Boolean = false,
    val transferAccountId: Long? = null,
)
