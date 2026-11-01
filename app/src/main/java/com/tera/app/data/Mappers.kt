package com.tera.app.data

import com.tera.app.data.db.AccountEntity
import com.tera.app.data.db.CategoryEntity
import com.tera.app.data.db.CategoryGroupEntity
import com.tera.app.data.db.ScheduledEntity
import com.tera.app.data.db.TransactionEntity
import com.tera.app.domain.Account
import com.tera.app.domain.AccountType
import com.tera.app.domain.Category
import com.tera.app.domain.CategoryGroup
import com.tera.app.domain.ClearanceState
import com.tera.app.domain.FlagColor
import com.tera.app.domain.Recurrence
import com.tera.app.domain.ScheduledTransaction
import com.tera.app.domain.SystemKind
import com.tera.app.domain.TargetType
import com.tera.app.domain.Transaction
import com.tera.app.util.Dates

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
