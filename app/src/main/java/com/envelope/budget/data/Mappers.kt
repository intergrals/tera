package com.envelope.budget.data

import com.envelope.budget.data.db.AccountEntity
import com.envelope.budget.data.db.CategoryEntity
import com.envelope.budget.data.db.CategoryGroupEntity
import com.envelope.budget.data.db.ScheduledEntity
import com.envelope.budget.data.db.TransactionEntity
import com.envelope.budget.domain.Account
import com.envelope.budget.domain.AccountType
import com.envelope.budget.domain.Category
import com.envelope.budget.domain.CategoryGroup
import com.envelope.budget.domain.ClearanceState
import com.envelope.budget.domain.FlagColor
import com.envelope.budget.domain.Recurrence
import com.envelope.budget.domain.ScheduledTransaction
import com.envelope.budget.domain.SystemKind
import com.envelope.budget.domain.TargetType
import com.envelope.budget.domain.Transaction
import com.envelope.budget.util.Dates

/** Entity -> domain mapping. Enums are stored as names and parsed defensively. */

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    type = AccountType.fromStorage(type),
    startingBalanceCents = startingBalanceCents,
    startingDate = Dates.parseOrToday(startingDate),
    isTracking = isTracking,
    isClosed = isClosed,
    sortOrder = sortOrder,
)

fun CategoryGroupEntity.toDomain(): CategoryGroup = CategoryGroup(
    id = id,
    name = name,
    sortOrder = sortOrder,
    isHidden = isHidden,
    isSystem = isSystem,
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    groupId = groupId,
    name = name,
    note = note,
    sortOrder = sortOrder,
    isHidden = isHidden,
    isSystem = isSystem,
    systemKind = SystemKind.fromStorage(systemKind),
    linkedAccountId = linkedAccountId,
    targetType = TargetType.fromStorage(targetType),
    targetAmountCents = targetAmountCents,
    targetDate = Dates.parseOrNull(targetDate),
)

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    accountId = accountId,
    categoryId = categoryId,
    date = Dates.parseOrToday(date),
    payee = payee,
    memo = memo,
    amountCents = amountCents,
    cleared = ClearanceState.fromOrdinal(cleared),
    flag = FlagColor.fromOrdinal(flag),
    transferPeerId = transferPeerId,
)

fun ScheduledEntity.toDomain(): ScheduledTransaction = ScheduledTransaction(
    id = id,
    accountId = accountId,
    categoryId = categoryId,
    payee = payee,
    memo = memo,
    amountCents = amountCents,
    recurrence = Recurrence.fromStorage(frequency),
    nextDate = Dates.parseOrToday(nextDate),
    lastPostedDate = Dates.parseOrNull(lastPostedDate),
    isActive = isActive,
    isTransfer = isTransfer,
    transferAccountId = transferAccountId,
)
