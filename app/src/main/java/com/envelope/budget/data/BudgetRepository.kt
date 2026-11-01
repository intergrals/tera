package com.envelope.budget.data

import androidx.room.withTransaction
import com.envelope.budget.data.db.AllocationEntity
import com.envelope.budget.data.db.AppDatabase
import com.envelope.budget.data.db.AccountEntity
import com.envelope.budget.data.db.CategoryEntity
import com.envelope.budget.data.db.CategoryGroupEntity
import com.envelope.budget.data.db.ScheduledEntity
import com.envelope.budget.data.db.TargetSnoozeEntity
import com.envelope.budget.data.db.TransactionEntity
import com.envelope.budget.domain.Account
import com.envelope.budget.domain.AccountType
import com.envelope.budget.domain.BudgetCalculator
import com.envelope.budget.domain.BudgetHistory
import com.envelope.budget.domain.BudgetInput
import com.envelope.budget.domain.Category
import com.envelope.budget.domain.CategoryGroup
import com.envelope.budget.domain.ClearanceState
import com.envelope.budget.domain.FlagColor
import com.envelope.budget.domain.Metrics
import com.envelope.budget.domain.MonthKey
import com.envelope.budget.domain.Recurrence
import com.envelope.budget.domain.ScheduledTransaction
import com.envelope.budget.domain.SystemKind
import com.envelope.budget.domain.TargetKey
import com.envelope.budget.domain.TargetMath
import com.envelope.budget.domain.TargetType
import com.envelope.budget.domain.Transaction
import com.envelope.budget.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** User-facing failure that the UI surfaces as a snackbar instead of crashing. */
class DomainException(message: String) : Exception(message)

data class TransactionDraft(
    val id: Long? = null,
    val accountId: Long,
    val date: LocalDate,
    val payee: String,
    val memo: String,
    val amountCents: Long,
    val categoryId: Long?,
    val cleared: ClearanceState = ClearanceState.UNCLEARED,
    val flag: FlagColor = FlagColor.NONE,
    val transferAccountId: Long? = null,
)

data class ScheduledDraft(
    val id: Long? = null,
    val accountId: Long,
    val categoryId: Long?,
    val payee: String,
    val memo: String,
    val amountCents: Long,
    val recurrence: Recurrence,
    val nextDate: LocalDate,
)

enum class AutoAssignStrategy {
    UNDERFUNDED,
    ASSIGNED_LAST_MONTH,
    SPENT_LAST_MONTH,
    AVERAGE_SPENT_3,
    RESET,
}

/**
 * The single gateway to the ledger and the budget engine for the UI layer.
 *
 * Reads are exposed as Room `Flow`s combined into an immutable [BudgetInput], which is
 * then fed to the pure [BudgetCalculator]. Every write goes through here so the transfer
 * pair invariant and the system-category invariants can never be bypassed.
 */
class BudgetRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {

    private val calculator = BudgetCalculator()

    private data class Core(
        val accounts: List<AccountEntity>,
        val groups: List<CategoryGroupEntity>,
        val categories: List<CategoryEntity>,
        val allocations: List<AllocationEntity>,
        val transactions: List<TransactionEntity>,
    )

    private val coreFlow: Flow<Core> = combine(
        db.accountDao().observeAll(),
        db.categoryDao().observeGroups(),
        db.categoryDao().observeCategories(),
        db.allocationDao().observeAll(),
        db.transactionDao().observeAll(),
    ) { accounts, groups, categories, allocations, transactions ->
        Core(accounts, groups, categories, allocations, transactions)
    }

    val inputFlow: Flow<BudgetInput> = combine(
        coreFlow,
        db.targetSnoozeDao().observeAll(),
        settings.settings,
    ) { core, snoozes, appSettings ->
        toInput(core, snoozes, appSettings.overspendMode)
    }

    fun observeHistory(throughMonth: String): Flow<BudgetHistory> =
        inputFlow.map { input -> calculator.compute(input, throughMonth) }

    /** Live list of scheduled (upcoming) transactions. */
    val scheduledFlow: Flow<List<ScheduledTransaction>> =
        db.scheduledDao().observeAll().map { list -> list.map { it.toDomain() } }

    /** Synchronous compute for ViewModels that already hold a [BudgetInput]. */
    fun computeHistory(input: BudgetInput, throughMonth: String): BudgetHistory =
        calculator.compute(input, throughMonth)

    // ---------------------------------------------------------------- reads

    suspend fun inputNow(): BudgetInput = withContext(Dispatchers.IO) {
        val core = Core(
            accounts = db.accountDao().getAll(),
            groups = db.categoryDao().getGroups(),
            categories = db.categoryDao().getCategories(),
            allocations = db.allocationDao().getAll(),
            transactions = db.transactionDao().getAllOrdered(),
        )
        toInput(core, db.targetSnoozeDao().getAll(), settings.current().overspendMode)
    }

    suspend fun historyNow(throughMonth: String): BudgetHistory = withContext(Dispatchers.Default) {
        calculator.compute(inputNow(), throughMonth)
    }

    suspend fun accounts(): List<Account> = withContext(Dispatchers.IO) {
        db.accountDao().getAll().map { it.toDomain() }
    }

    suspend fun account(id: Long): Account? = withContext(Dispatchers.IO) {
        db.accountDao().getById(id)?.toDomain()
    }

    suspend fun observeAccount(id: Long): Flow<AccountEntity?> = db.accountDao().observeById(id)

    suspend fun categories(): List<Category> = withContext(Dispatchers.IO) {
        db.categoryDao().getCategories().map { it.toDomain() }
    }

    suspend fun groups(): List<CategoryGroup> = withContext(Dispatchers.IO) {
        db.categoryDao().getGroups().map { it.toDomain() }
    }

    suspend fun transactionsForAccount(accountId: Long): List<Transaction> =
        withContext(Dispatchers.IO) {
            db.transactionDao().getForAccount(accountId).map { it.toDomain() }
        }

    suspend fun observeTransactionsForAccount(accountId: Long): Flow<List<Transaction>> =
        db.transactionDao().observeForAccount(accountId).map { list -> list.map { it.toDomain() } }

    suspend fun transaction(id: Long): Transaction? = withContext(Dispatchers.IO) {
        db.transactionDao().getById(id)?.toDomain()
    }

    suspend fun scheduled(): List<ScheduledTransaction> = withContext(Dispatchers.IO) {
        db.scheduledDao().getAll().map { it.toDomain() }
    }

    suspend fun scheduled(id: Long): ScheduledTransaction? = withContext(Dispatchers.IO) {
        db.scheduledDao().getById(id)?.toDomain()
    }

    suspend fun payees(): List<String> = withContext(Dispatchers.IO) {
        db.transactionDao().distinctPayees()
    }

    suspend fun searchTransactions(query: String, accountId: Long?): List<Transaction> =
        withContext(Dispatchers.IO) {
            db.transactionDao().search(query.trim(), accountId ?: -1L).map { it.toDomain() }
        }

    suspend fun currentBalance(accountId: Long): Long = withContext(Dispatchers.Default) {
        Metrics.currentBalances(inputNow())[accountId] ?: 0L
    }

    // ------------------------------------------------------------ onboarding

    suspend fun isOnboardingDone(): Boolean = settings.current().onboardingDone

    suspend fun skipOnboarding() = withContext(Dispatchers.IO) {
        ensureSystemCategories()
        settings.setOnboardingDone(true)
    }

    suspend fun seedStarterData() = withContext(Dispatchers.IO) {
        db.withTransaction {
            ensureSystemCategoriesInternal()
            if (db.categoryDao().getGroups().none { !it.isSystem }) {
                starterTemplate().forEachIndexed { groupIndex, (groupName, categoryNames) ->
                    val groupId = db.categoryDao().insertGroup(
                        CategoryGroupEntity(name = groupName, sortOrder = groupIndex),
                    )
                    categoryNames.forEachIndexed { categoryIndex, categoryName ->
                        db.categoryDao().insert(
                            CategoryEntity(
                                groupId = groupId,
                                name = categoryName,
                                sortOrder = categoryIndex,
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Ensures the system group/categories exist. Safe to call repeatedly. */
    suspend fun ensureSystemCategories() = withContext(Dispatchers.IO) {
        db.withTransaction { ensureSystemCategoriesInternal() }
        Unit
    }

    private suspend fun ensureSystemCategoriesInternal(): SystemCategories {
        val groups = db.categoryDao().getGroups()
        val systemGroup = groups.firstOrNull { it.isSystem && it.name == SYSTEM_GROUP_NAME }
            ?: db.categoryDao().getGroups().firstOrNull { it.isSystem }
            ?: CategoryGroupEntity(
                name = SYSTEM_GROUP_NAME,
                sortOrder = 10_000,
                isHidden = true,
                isSystem = true,
            ).let { it.copy(id = db.categoryDao().insertGroup(it)) }

        val adjustment = db.categoryDao().getSystemCategory(SystemKind.ADJUSTMENT.name)
            ?: CategoryEntity(
                groupId = systemGroup.id,
                name = "Adjustment",
                sortOrder = 0,
                isHidden = true,
                isSystem = true,
                systemKind = SystemKind.ADJUSTMENT.name,
            ).let { it.copy(id = db.categoryDao().insert(it)) }

        val paymentsGroup = groups.firstOrNull { it.isSystem && it.name == PAYMENTS_GROUP_NAME }
            ?: CategoryGroupEntity(
                name = PAYMENTS_GROUP_NAME,
                sortOrder = 10_001,
                isSystem = true,
            ).let { it.copy(id = db.categoryDao().insertGroup(it)) }

        return SystemCategories(systemGroup.id, adjustment.id, paymentsGroup.id)
    }

    // -------------------------------------------------------------- accounts

    suspend fun createAccount(
        name: String,
        type: AccountType,
        startingBalanceCents: Long,
        startingDate: LocalDate,
        isTracking: Boolean = false,
    ): Long = withContext(Dispatchers.IO) {
        if (name.isBlank()) throw DomainException("Account name cannot be empty")
        db.withTransaction {
            val accountId = db.accountDao().insert(
                AccountEntity(
                    name = name.trim(),
                    type = type.name,
                    startingBalanceCents = startingBalanceCents,
                    startingDate = Dates.toIso(startingDate),
                    isTracking = isTracking,
                    sortOrder = db.accountDao().getAll().size,
                ),
            )
            if (type == AccountType.CREDIT && !isTracking) {
                val system = ensureSystemCategoriesInternal()
                db.categoryDao().insert(
                    CategoryEntity(
                        groupId = system.paymentsGroupId,
                        name = name.trim(),
                        sortOrder = (db.categoryDao().getCategories().count { it.linkedAccountId != null }),
                        isSystem = true,
                        systemKind = SystemKind.CARD_PAYMENT.name,
                        linkedAccountId = accountId,
                    ),
                )
            }
            accountId
        }
    }

    suspend fun renameAccount(accountId: Long, name: String) = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isEmpty()) throw DomainException("Account name cannot be empty")
        db.withTransaction {
            val account = db.accountDao().getById(accountId)
                ?: throw DomainException("Account not found")
            db.accountDao().update(account.copy(name = clean))
            db.categoryDao().getPaymentCategoryFor(accountId)?.let { payment ->
                db.categoryDao().update(payment.copy(name = clean))
            }
        }
    }

    suspend fun setAccountClosed(accountId: Long, closed: Boolean) = withContext(Dispatchers.IO) {
        db.accountDao().setClosed(accountId, closed)
    }

    suspend fun deleteAccount(accountId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            if (db.accountDao().transactionCount(accountId) > 0) {
                throw DomainException("This account has transactions. Close it instead of deleting.")
            }
            db.categoryDao().getPaymentCategoryFor(accountId)?.let { db.categoryDao().delete(it) }
            db.accountDao().getById(accountId)?.let { db.accountDao().delete(it) }
        }
    }

    suspend fun adjustStartingBalance(accountId: Long, balanceCents: Long, date: LocalDate) =
        withContext(Dispatchers.IO) {
            val account = db.accountDao().getById(accountId)
                ?: throw DomainException("Account not found")
            db.accountDao().update(
                account.copy(
                    startingBalanceCents = balanceCents,
                    startingDate = Dates.toIso(date),
                ),
            )
        }

    // ------------------------------------------------------------ categories

    suspend fun createGroup(name: String): Long = withContext(Dispatchers.IO) {
        if (name.isBlank()) throw DomainException("Group name cannot be empty")
        db.categoryDao().insertGroup(
            CategoryGroupEntity(
                name = name.trim(),
                sortOrder = db.categoryDao().getGroups().size,
            ),
        )
    }

    suspend fun renameGroup(groupId: Long, name: String) = withContext(Dispatchers.IO) {
        val group = db.categoryDao().getGroups().firstOrNull { it.id == groupId }
            ?: throw DomainException("Group not found")
        if (group.isSystem) throw DomainException("System groups cannot be renamed")
        db.categoryDao().updateGroup(group.copy(name = name.trim()))
    }

    suspend fun deleteGroup(groupId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val group = db.categoryDao().getGroups().firstOrNull { it.id == groupId }
                ?: throw DomainException("Group not found")
            if (group.isSystem) throw DomainException("System groups cannot be deleted")
            val categories = db.categoryDao().getCategories().filter { it.groupId == groupId }
            categories.forEach { category ->
                if (db.categoryDao().transactionCount(category.id) > 0) {
                    throw DomainException(
                        "Move the transactions in \u201C${category.name}\u201D before deleting this group.",
                    )
                }
            }
            db.categoryDao().deleteGroup(group)
        }
    }

    suspend fun createCategory(
        groupId: Long,
        name: String,
        targetType: TargetType = TargetType.NONE,
        targetAmountCents: Long? = null,
        targetDate: LocalDate? = null,
        note: String = "",
    ): Long = withContext(Dispatchers.IO) {
        if (name.isBlank()) throw DomainException("Category name cannot be empty")
        db.categoryDao().insert(
            CategoryEntity(
                groupId = groupId,
                name = name.trim(),
                note = note,
                sortOrder = db.categoryDao().getCategories().count { it.groupId == groupId },
                targetType = targetType.name,
                targetAmountCents = targetAmountCents,
                targetDate = Dates.toIsoOrNull(targetDate),
            ),
        )
    }

    suspend fun updateCategory(
        categoryId: Long,
        name: String,
        note: String,
        targetType: TargetType,
        targetAmountCents: Long?,
        targetDate: LocalDate?,
    ) = withContext(Dispatchers.IO) {
        val category = db.categoryDao().getById(categoryId)
            ?: throw DomainException("Category not found")
        if (category.isSystem) throw DomainException("System categories cannot be edited")
        db.categoryDao().update(
            category.copy(
                name = name.trim(),
                note = note,
                targetType = targetType.name,
                targetAmountCents = if (targetType == TargetType.NONE) null else targetAmountCents,
                targetDate = if (targetType == TargetType.NEEDED_FOR_SPENDING) {
                    Dates.toIsoOrNull(targetDate)
                } else {
                    null
                },
            ),
        )
    }

    suspend fun setCategoryHidden(categoryId: Long, hidden: Boolean) = withContext(Dispatchers.IO) {
        val category = db.categoryDao().getById(categoryId) ?: return@withContext
        if (category.isSystem) throw DomainException("System categories cannot be hidden")
        db.categoryDao().update(category.copy(isHidden = hidden))
    }

    suspend fun deleteCategory(categoryId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val category = db.categoryDao().getById(categoryId)
                ?: throw DomainException("Category not found")
            if (category.isSystem) throw DomainException("System categories cannot be deleted")
            if (db.categoryDao().transactionCount(categoryId) > 0) {
                throw DomainException("This category has transactions. Hide it instead.")
            }
            if (db.categoryDao().nonZeroAllocationCount(categoryId) > 0) {
                throw DomainException("This category has money assigned. Unassign it first.")
            }
            db.categoryDao().delete(category)
        }
    }

    // ---------------------------------------------------------- transactions

    /**
     * Creates or updates a transaction. Transfers are stored as a linked pair with a
     * shared date; editing replaces the pair atomically so the two legs can never drift.
     */
    suspend fun saveTransaction(draft: TransactionDraft): Long = withContext(Dispatchers.IO) {
        if (draft.amountCents == 0L) throw DomainException("Enter an amount")
        if (draft.transferAccountId == draft.accountId) {
            throw DomainException("Pick a different account for the transfer")
        }
        db.withTransaction {
            draft.id?.let { existingId ->
                val existing = db.transactionDao().getById(existingId)
                if (existing?.transferPeerId != null) {
                    deleteTransactionInternal(existingId)
                } else if (existing != null) {
                    db.transactionDao().deleteById(existingId)
                }
            }

            if (draft.transferAccountId != null) {
                val outflowAmount = -kotlin.math.abs(draft.amountCents)
                val sourceId = db.transactionDao().insert(
                    TransactionEntity(
                        accountId = draft.accountId,
                        categoryId = null,
                        date = Dates.toIso(draft.date),
                        payee = draft.payee,
                        memo = draft.memo,
                        amountCents = outflowAmount,
                        cleared = draft.cleared.ordinal,
                        flag = draft.flag.ordinal,
                    ),
                )
                val targetId = db.transactionDao().insert(
                    TransactionEntity(
                        accountId = draft.transferAccountId,
                        categoryId = null,
                        date = Dates.toIso(draft.date),
                        payee = draft.payee.ifBlank { "Transfer" },
                        memo = draft.memo,
                        amountCents = -outflowAmount,
                        cleared = draft.cleared.ordinal,
                        flag = draft.flag.ordinal,
                    ),
                )
                linkPair(sourceId, targetId)
                sourceId
            } else {
                db.transactionDao().insert(
                    TransactionEntity(
                        accountId = draft.accountId,
                        categoryId = draft.categoryId,
                        date = Dates.toIso(draft.date),
                        payee = draft.payee.trim(),
                        memo = draft.memo.trim(),
                        amountCents = draft.amountCents,
                        cleared = draft.cleared.ordinal,
                        flag = draft.flag.ordinal,
                    ),
                )
            }
        }
    }

    private suspend fun linkPair(firstId: Long, secondId: Long) {
        val first = db.transactionDao().getById(firstId) ?: return
        val second = db.transactionDao().getById(secondId) ?: return
        db.transactionDao().update(first.copy(transferPeerId = secondId))
        db.transactionDao().update(second.copy(transferPeerId = firstId))
    }

    suspend fun deleteTransaction(id: Long) = withContext(Dispatchers.IO) {
        db.withTransaction { deleteTransactionInternal(id) }
    }

    private suspend fun deleteTransactionInternal(id: Long) {
        val existing = db.transactionDao().getById(id) ?: return
        existing.transferPeerId?.let { peerId ->
            db.transactionDao().deleteById(peerId)
        }
        db.transactionDao().deleteById(id)
    }

    suspend fun setCleared(id: Long, state: ClearanceState) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val tx = db.transactionDao().getById(id) ?: return@withTransaction
            db.transactionDao().update(tx.copy(cleared = state.ordinal))
            tx.transferPeerId?.let { peerId ->
                db.transactionDao().getById(peerId)?.let { peer ->
                    db.transactionDao().update(peer.copy(cleared = state.ordinal))
                }
            }
        }
    }

    // ---------------------------------------------------------------- budget

    suspend fun assign(month: String, categoryId: Long, cents: Long) = withContext(Dispatchers.IO) {
        if (cents == 0L) {
            db.allocationDao().clear(month, categoryId)
        } else {
            db.allocationDao().upsert(AllocationEntity(month, categoryId, cents))
        }
    }

    suspend fun adjustAssign(month: String, categoryId: Long, delta: Long) =
        withContext(Dispatchers.IO) {
            if (delta == 0L) return@withContext
            db.withTransaction {
                val current = db.allocationDao().get(month, categoryId)?.assignedCents ?: 0L
                val next = current + delta
                if (next == 0L) {
                    db.allocationDao().clear(month, categoryId)
                } else {
                    db.allocationDao().upsert(AllocationEntity(month, categoryId, next))
                }
            }
        }

    /** Money Moves: a null id means Ready to Assign. */
    suspend fun moveMoney(month: String, fromCategoryId: Long?, toCategoryId: Long?, cents: Long) =
        withContext(Dispatchers.IO) {
            if (cents <= 0L) throw DomainException("Enter an amount to move")
            if (fromCategoryId == toCategoryId) throw DomainException("Pick two different envelopes")
            db.withTransaction {
                fromCategoryId?.let { writeAssignment(month, it, -cents) }
                toCategoryId?.let { writeAssignment(month, it, cents) }
            }
        }

    /** Adds [delta] to a category's assignment inside an existing transaction. */
    private suspend fun writeAssignment(month: String, categoryId: Long, delta: Long) {
        val current = db.allocationDao().get(month, categoryId)?.assignedCents ?: 0L
        val next = current + delta
        if (next == 0L) {
            db.allocationDao().clear(month, categoryId)
        } else {
            db.allocationDao().upsert(AllocationEntity(month, categoryId, next))
        }
    }

    suspend fun autoAssign(month: String, strategy: AutoAssignStrategy, groupId: Long?) =
        withContext(Dispatchers.IO) {
            val input = inputNow()
            val history = calculator.compute(input, month)
            val current = history.month(month) ?: return@withContext
            val previous1 = history.month(MonthKey.previous(month))
            val previous2 = history.month(MonthKey.previous(MonthKey.previous(month)))
            val previous3 = history.month(
                MonthKey.previous(MonthKey.previous(MonthKey.previous(month))),
            )

            val targets = input.categories.filter {
                !it.isPaymentCategory && !it.isAdjustment && !it.isHidden &&
                    (groupId == null || it.groupId == groupId)
            }

            // Compute every new value first, then write them in one transaction.
            val updates = LinkedHashMap<Long, Long>()
            targets.forEach { category ->
                val state = current.categories[category.id] ?: return@forEach
                updates[category.id] = when (strategy) {
                    AutoAssignStrategy.UNDERFUNDED -> {
                        val snoozed = input.snoozedTargets.contains(TargetKey(category.id, month))
                        state.assignedCents + TargetMath.suggestedFunding(
                            month,
                            category,
                            state.availableCents,
                            snoozed,
                        )
                    }
                    AutoAssignStrategy.ASSIGNED_LAST_MONTH ->
                        previous1?.categories?.get(category.id)?.assignedCents ?: 0L
                    AutoAssignStrategy.SPENT_LAST_MONTH ->
                        spentIn(previous1?.categories?.get(category.id)?.activityCents)
                    AutoAssignStrategy.AVERAGE_SPENT_3 -> {
                        val total = listOf(previous1, previous2, previous3)
                            .sumOf { spentIn(it?.categories?.get(category.id)?.activityCents) }
                        total / 3L
                    }
                    AutoAssignStrategy.RESET -> 0L
                }
            }

            db.withTransaction {
                updates.forEach { (categoryId, value) ->
                    if (value == 0L) {
                        db.allocationDao().clear(month, categoryId)
                    } else {
                        db.allocationDao().upsert(AllocationEntity(month, categoryId, value))
                    }
                }
            }
        }

    private fun spentIn(activityCents: Long?): Long =
        maxOf(0L, -(activityCents ?: 0L))

    suspend fun setTargetSnooze(categoryId: Long, month: String, snoozed: Boolean) =
        withContext(Dispatchers.IO) {
            if (snoozed) {
                db.targetSnoozeDao().insert(TargetSnoozeEntity(categoryId, month))
            } else {
                db.targetSnoozeDao().delete(categoryId, month)
            }
        }

    suspend fun clearMonth(month: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.allocationDao().getMonth(month).forEach {
                db.allocationDao().clear(month, it.categoryId)
            }
        }
    }

    // ------------------------------------------------------------- scheduled

    suspend fun saveScheduled(draft: ScheduledDraft): Long = withContext(Dispatchers.IO) {
        if (draft.amountCents == 0L) throw DomainException("Enter an amount")
        val entity = ScheduledEntity(
            id = draft.id ?: 0L,
            accountId = draft.accountId,
            categoryId = draft.categoryId,
            payee = draft.payee.trim(),
            memo = draft.memo.trim(),
            amountCents = draft.amountCents,
            frequency = draft.recurrence.name,
            nextDate = Dates.toIso(draft.nextDate),
        )
        if (draft.id == null) {
            db.scheduledDao().insert(entity)
        } else {
            db.scheduledDao().update(entity)
            draft.id
        }
    }

    suspend fun deleteScheduled(id: Long) = withContext(Dispatchers.IO) {
        db.scheduledDao().deleteById(id)
    }

    /** Books a scheduled transaction now and advances its next date. */
    suspend fun postScheduledNow(id: Long, date: LocalDate = LocalDate.now()): Long =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val scheduled = db.scheduledDao().getById(id)
                    ?: throw DomainException("Scheduled transaction not found")
                val transactionId = db.transactionDao().insert(
                    TransactionEntity(
                        accountId = scheduled.accountId,
                        categoryId = scheduled.categoryId,
                        date = Dates.toIso(date),
                        payee = scheduled.payee,
                        memo = scheduled.memo,
                        amountCents = scheduled.amountCents,
                    ),
                )
                var next = Dates.parseOrToday(scheduled.nextDate)
                val recurrence = Recurrence.fromStorage(scheduled.frequency)
                next = Dates.advance(next, recurrence)
                while (next.isBefore(date)) {
                    next = Dates.advance(next, recurrence)
                }
                db.scheduledDao().update(
                    scheduled.copy(
                        nextDate = Dates.toIso(next),
                        lastPostedDate = Dates.toIso(date),
                    ),
                )
                transactionId
            }
        }

    // ------------------------------------------------------------ reconcile

    suspend fun currentBalanceOrZero(accountId: Long): Long =
        Metrics.currentBalances(inputNow())[accountId] ?: 0L

    /**
     * Reconciles an account against a real-world balance by booking the difference to the
     * hidden Adjustment category, then marking cleared transactions as reconciled.
     */
    suspend fun reconcile(accountId: Long, actualBalanceCents: Long): Long? =
        withContext(Dispatchers.IO) {
            // Balance is read before the transaction: never re-enter the DB from inside it.
            val current = Metrics.currentBalances(inputNow())[accountId] ?: 0L
            val delta = actualBalanceCents - current
            db.withTransaction {
                val system = ensureSystemCategoriesInternal()
                val adjustmentId = if (delta != 0L) {
                    db.transactionDao().insert(
                        TransactionEntity(
                            accountId = accountId,
                            categoryId = system.adjustmentCategoryId,
                            date = Dates.todayIso(),
                            payee = "Balance Adjustment",
                            memo = "Reconciliation",
                            amountCents = delta,
                            cleared = ClearanceState.RECONCILED.ordinal,
                        ),
                    )
                } else {
                    null
                }
                db.transactionDao().markClearedAsReconciled(accountId)
                adjustmentId
            }
        }

    // ------------------------------------------------------------ internals

    private fun toInput(
        core: Core,
        snoozes: List<TargetSnoozeEntity>,
        mode: com.envelope.budget.domain.OverspendMode,
    ): BudgetInput = BudgetInput(
        accounts = core.accounts.map { it.toDomain() },
        groups = core.groups.map { it.toDomain() },
        categories = core.categories.map { it.toDomain() },
        transactions = core.transactions.map { it.toDomain() }
            .sortedWith(compareBy({ it.date }, { it.id })),
        allocations = core.allocations
            .groupBy { it.month }
            .mapValues { (_, rows) -> rows.associate { it.categoryId to it.assignedCents } },
        snoozedTargets = snoozes.map { TargetKey(it.categoryId, it.month) }.toSet(),
        mode = mode,
    )

    private data class SystemCategories(
        val systemGroupId: Long,
        val adjustmentCategoryId: Long,
        val paymentsGroupId: Long,
    )

    companion object {
        private const val SYSTEM_GROUP_NAME = "System"
        private const val PAYMENTS_GROUP_NAME = "Credit Card Payments"

        private fun starterTemplate(): List<Pair<String, List<String>>> = listOf(
            "Immediate Obligations" to listOf(
                "Rent / Mortgage",
                "Utilities",
                "Groceries",
                "Transportation",
                "Insurance",
            ),
            "True Expenses" to listOf(
                "Auto Maintenance",
                "Home Maintenance",
                "Medical",
                "Annual Subscriptions",
            ),
            "Quality of Life" to listOf(
                "Dining Out",
                "Entertainment",
                "Personal Care",
                "Gifts",
            ),
            "Savings" to listOf(
                "Emergency Fund",
                "Vacation",
            ),
        )
    }
}
