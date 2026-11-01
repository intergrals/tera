package com.envelope.budget.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.envelope.budget.data.AutoAssignStrategy
import com.envelope.budget.data.BudgetRepository
import com.envelope.budget.data.DomainException
import com.envelope.budget.data.SettingsRepository
import com.envelope.budget.domain.BudgetInput
import com.envelope.budget.domain.Category
import com.envelope.budget.domain.CategoryGroup
import com.envelope.budget.domain.CategoryMonth
import com.envelope.budget.domain.MonthKey
import com.envelope.budget.domain.OverspendMode
import com.envelope.budget.domain.PaymentMonth
import com.envelope.budget.domain.PaymentState
import com.envelope.budget.domain.RtaBreakdown
import com.envelope.budget.domain.TargetKey
import com.envelope.budget.domain.TargetMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryView(
    val id: Long,
    val name: String,
    val note: String,
    val assignedCents: Long,
    val activityCents: Long,
    val availableCents: Long,
    val hasTarget: Boolean,
    val targetSuggestedCents: Long,
    val progress: Float?,
    val targetMet: Boolean,
    val snoozed: Boolean,
)

data class GroupView(
    val id: Long,
    val name: String,
    val categories: List<CategoryView>,
    val assignedCents: Long,
    val activityCents: Long,
    val availableCents: Long,
)

data class PaymentView(
    val accountId: Long,
    val paymentCategoryId: Long,
    val name: String,
    val assignedCents: Long,
    val activityCents: Long,
    val availableCents: Long,
    val debtCents: Long,
    val state: PaymentState,
)

data class BudgetUiState(
    val month: String = MonthKey.current(),
    val currentMonth: String = MonthKey.current(),
    val currency: String = "$",
    val mode: OverspendMode = OverspendMode.YNAB,
    val loading: Boolean = true,
    val readyToAssignCents: Long = 0,
    val breakdown: RtaBreakdown? = null,
    val groups: List<GroupView> = emptyList(),
    val payments: List<PaymentView> = emptyList(),
    val assignedTotalCents: Long = 0,
    val activityTotalCents: Long = 0,
    val availableTotalCents: Long = 0,
    val categoriesById: Map<Long, Category> = emptyMap(),
    val groupsById: Map<Long, CategoryGroup> = emptyMap(),
) {
    val isCurrentMonth: Boolean get() = month == currentMonth
}

class BudgetViewModel(
    private val repo: BudgetRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(MonthKey.current())
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val state: StateFlow<BudgetUiState> = combine(
        repo.inputFlow,
        selectedMonth,
        settings.settings,
    ) { input, month, appSettings ->
        build(input, month, appSettings.currencySymbol, appSettings.overspendMode)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    fun previousMonth() {
        selectedMonth.value = MonthKey.previous(selectedMonth.value)
    }

    fun nextMonth() {
        selectedMonth.value = MonthKey.next(selectedMonth.value)
    }

    fun currentMonth() {
        selectedMonth.value = MonthKey.current()
    }

    fun assign(categoryId: Long, cents: Long) = run("Assignment saved") {
        repo.assign(selectedMonth.value, categoryId, cents)
    }

    fun fundTarget(categoryId: Long, suggestedCents: Long) = run("Target funded") {
        repo.adjustAssign(selectedMonth.value, categoryId, suggestedCents)
    }

    fun snoozeTarget(categoryId: Long, snoozed: Boolean) = run(null) {
        repo.setTargetSnooze(categoryId, selectedMonth.value, snoozed)
    }

    fun autoAssign(strategy: AutoAssignStrategy) = run("Auto-assign applied") {
        repo.autoAssign(selectedMonth.value, strategy, groupId = null)
    }

    fun moveMoney(fromCategoryId: Long?, toCategoryId: Long?, cents: Long) =
        run("Money moved") {
            repo.moveMoney(selectedMonth.value, fromCategoryId, toCategoryId, cents)
        }

    private fun run(successMessage: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                successMessage?.let { _messages.emit(it) }
            } catch (e: DomainException) {
                _messages.emit(e.message ?: "Something went wrong")
            }
        }
    }

    private fun build(
        input: BudgetInput,
        month: String,
        currency: String,
        mode: OverspendMode,
    ): BudgetUiState {
        val history = repo.computeHistory(input, month)
        val result = history.month(month)
        val visibleCategories = input.categories.filter { !it.isSystem && !it.isHidden }

        val groups = input.groups
            .filter { !it.isSystem && !it.isHidden }
            .sortedBy { it.sortOrder }
            .mapNotNull { group ->
                val categories = visibleCategories
                    .filter { it.groupId == group.id }
                    .sortedBy { it.sortOrder }
                    .map { category -> categoryView(input, category, result?.categories?.get(category.id), month) }
                if (categories.isEmpty()) return@mapNotNull null
                GroupView(
                    id = group.id,
                    name = group.name,
                    categories = categories,
                    assignedCents = categories.sumOf { it.assignedCents },
                    activityCents = categories.sumOf { it.activityCents },
                    availableCents = categories.sumOf { it.availableCents },
                )
            }

        val payments = result?.payments?.values.orEmpty()
            .mapNotNull { payment -> paymentView(input, payment) }
            .sortedBy { it.name }

        val mainCategories = groups.flatMap { it.categories }
        return BudgetUiState(
            month = month,
            currentMonth = MonthKey.current(),
            currency = currency,
            mode = mode,
            loading = false,
            readyToAssignCents = result?.readyToAssignCents ?: 0L,
            breakdown = result?.breakdown,
            groups = groups,
            payments = payments,
            assignedTotalCents = mainCategories.sumOf { it.assignedCents } + payments.sumOf { it.assignedCents },
            activityTotalCents = mainCategories.sumOf { it.activityCents } + payments.sumOf { it.activityCents },
            availableTotalCents = mainCategories.sumOf { it.availableCents } + payments.sumOf { it.availableCents },
            categoriesById = input.categoriesById,
            groupsById = input.groups.associateBy { it.id },
        )
    }

    private fun categoryView(
        input: BudgetInput,
        category: Category,
        state: CategoryMonth?,
        month: String,
    ): CategoryView {
        val snoozed = input.snoozedTargets.contains(TargetKey(category.id, month))
        val available = state?.availableCents ?: 0L
        val suggested = if (category.hasTarget) {
            TargetMath.suggestedFunding(month, category, available, snoozed)
        } else {
            0L
        }
        return CategoryView(
            id = category.id,
            name = category.name,
            note = category.note,
            assignedCents = state?.assignedCents ?: 0L,
            activityCents = state?.activityCents ?: 0L,
            availableCents = available,
            hasTarget = category.hasTarget,
            targetSuggestedCents = suggested,
            progress = TargetMath.progress(category, available),
            targetMet = TargetMath.isMet(category, available),
            snoozed = snoozed,
        )
    }

    private fun paymentView(
        input: BudgetInput,
        payment: PaymentMonth,
    ): PaymentView? {
        val account = input.accountsById[payment.accountId] ?: return null
        return PaymentView(
            accountId = payment.accountId,
            paymentCategoryId = payment.paymentCategoryId,
            name = account.name,
            assignedCents = payment.assignedCents,
            activityCents = payment.activityCents,
            availableCents = payment.availableCents,
            debtCents = payment.debtCents,
            state = payment.state,
        )
    }
}
