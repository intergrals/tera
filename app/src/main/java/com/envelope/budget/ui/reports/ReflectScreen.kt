package com.envelope.budget.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.envelope.budget.data.BudgetRepository
import com.envelope.budget.data.SettingsRepository
import com.envelope.budget.domain.Category
import com.envelope.budget.domain.Metrics
import com.envelope.budget.domain.MonthKey
import com.envelope.budget.domain.NetWorthPoint
import com.envelope.budget.domain.TargetMath
import com.envelope.budget.ui.LocalAppContainer
import com.envelope.budget.ui.components.EmptyState
import com.envelope.budget.ui.components.MoneyText
import com.envelope.budget.ui.components.SectionHeader
import com.envelope.budget.ui.components.vmFactory
import com.envelope.budget.ui.theme.LocalEnvelopeColors
import com.envelope.budget.ui.theme.MoneyTextStyle
import com.envelope.budget.util.MoneyFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class SpendingSlice(val name: String, val cents: Long)

data class IncomeExpensePoint(val month: String, val incomeCents: Long, val expenseCents: Long)

data class TargetRow(
    val name: String,
    val availableCents: Long,
    val targetCents: Long,
    val progress: Float,
    val met: Boolean,
)

data class ReflectUiState(
    val currency: String = "$",
    val month: String = MonthKey.current(),
    val spending: List<SpendingSlice> = emptyList(),
    val spendingTotal: Long = 0,
    val netWorth: List<NetWorthPoint> = emptyList(),
    val incomeExpense: List<IncomeExpensePoint> = emptyList(),
    val ageOfMoney: Int? = null,
    val daysOfBuffering: Int? = null,
    val targets: List<TargetRow> = emptyList(),
    val loading: Boolean = true,
)

class ReflectViewModel(
    private val repo: BudgetRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<ReflectUiState> = combine(repo.inputFlow, settings.settings) { input, prefs ->
        val today = LocalDate.now()
        val month = MonthKey.of(today)
        val history = repo.computeHistory(input, month)
        val current = history.month(month)

        val spending = (current?.spendingByCategory ?: emptyMap())
            .mapNotNull { (categoryId, cents) ->
                val category = input.categoriesById[categoryId] ?: return@mapNotNull null
                if (category.isSystem) return@mapNotNull null
                SpendingSlice(category.name, cents)
            }
            .filter { it.cents > 0 }
            .sortedByDescending { it.cents }

        val trailing = MonthKey.trailingMonths(month, 12)
        val incomeExpense = trailing.map { key ->
            val result = history.month(key)
            IncomeExpensePoint(
                month = key,
                incomeCents = result?.incomeCents ?: 0L,
                expenseCents = result?.expenseCents ?: 0L,
            )
        }

        val netWorth = trailing.mapNotNull { key ->
            history.netWorth.lastOrNull { it.month == key }
        }.ifEmpty { history.netWorth.takeLast(1) }

        val targets = input.categories
            .filter { it.hasTarget && !it.isSystem && !it.isHidden }
            .map { category: Category ->
                val available = current?.categories?.get(category.id)?.availableCents ?: 0L
                TargetRow(
                    name = category.name,
                    availableCents = available,
                    targetCents = category.targetAmountCents ?: 0L,
                    progress = TargetMath.progress(category, available) ?: 0f,
                    met = TargetMath.isMet(category, available),
                )
            }
            .sortedBy { it.met }

        ReflectUiState(
            currency = prefs.currencySymbol,
            month = month,
            spending = spending,
            spendingTotal = spending.sumOf { it.cents },
            netWorth = netWorth,
            incomeExpense = incomeExpense,
            ageOfMoney = Metrics.ageOfMoney(input),
            daysOfBuffering = Metrics.daysOfBuffering(input, today),
            targets = targets,
            loading = false,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReflectUiState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflectScreen() {
    val container = LocalAppContainer.current
    val viewModel: ReflectViewModel = viewModel(
        factory = vmFactory { ReflectViewModel(container.budget, container.settings) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Spending", "Net worth", "Income", "Age of money", "Targets")

    Scaffold(topBar = { TopAppBar(title = { Text("Reflect") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEachIndexed { index, label ->
                    FilterChip(
                        selected = tab == index,
                        onClick = { tab = index },
                        label = { Text(label) },
                    )
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> {
                        if (state.spending.isEmpty()) {
                            item {
                                EmptyState(
                                    "No spending this month",
                                    "Categorized outflows in ${MonthKey.label(state.month)} will appear here.",
                                )
                            }
                        } else {
                            item { SectionHeader("${MonthKey.label(state.month)} \u00B7 total ${MoneyFormat.format(state.spendingTotal, state.currency)}") }
                            items(state.spending) { slice ->
                                val fraction = if (state.spendingTotal > 0) {
                                    slice.cents.toFloat() / state.spendingTotal.toFloat()
                                } else {
                                    0f
                                }
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(slice.name, style = MaterialTheme.typography.bodyMedium)
                                        MoneyText(slice.cents, state.currency)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction.coerceIn(0.01f, 1f))
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        item { SectionHeader("Net worth \u2014 last 12 months") }
                        item {
                            val points = state.netWorth
                            val max = points.maxOfOrNull { maxOf(it.assetsCents, it.liabilitiesCents) } ?: 0L
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(120.dp),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    points.forEach { point ->
                                        val netFraction = if (max > 0) {
                                            (point.netCents.toFloat() / max.toFloat()).coerceIn(-1f, 1f)
                                        } else {
                                            0f
                                        }
                                        val height = (kotlin.math.abs(netFraction) * 110f).coerceAtLeast(2f)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(height.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(
                                                    if (point.netCents >= 0) {
                                                        LocalEnvelopeColors.current.positive
                                                    } else {
                                                        LocalEnvelopeColors.current.negative
                                                    },
                                                ),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                points.lastOrNull()?.let { last ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Assets ${MoneyFormat.format(last.assetsCents, state.currency)}")
                                        Text("Debts ${MoneyFormat.format(last.liabilitiesCents, state.currency)}")
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Net worth", style = MaterialTheme.typography.titleMedium)
                                        MoneyText(
                                            last.netCents,
                                            state.currency,
                                            color = if (last.netCents < 0) {
                                                LocalEnvelopeColors.current.negative
                                            } else {
                                                LocalEnvelopeColors.current.positive
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        item { SectionHeader("Income vs expense \u2014 last 12 months") }
                        item {
                            val max = state.incomeExpense.maxOfOrNull {
                                maxOf(it.incomeCents, it.expenseCents)
                            } ?: 0L
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(120.dp),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    state.incomeExpense.forEach { point ->
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Bottom,
                                        ) {
                                            val income = if (max > 0) {
                                                (point.incomeCents.toFloat() / max * 55f)
                                            } else {
                                                0f
                                            }
                                            val expense = if (max > 0) {
                                                (point.expenseCents.toFloat() / max * 55f)
                                            } else {
                                                0f
                                            }
                                            Box(
                                                Modifier.fillMaxWidth().height(income.coerceAtLeast(1f).dp)
                                                    .background(LocalEnvelopeColors.current.positive),
                                            )
                                            Box(
                                                Modifier.fillMaxWidth().height(expense.coerceAtLeast(1f).dp)
                                                    .background(LocalEnvelopeColors.current.negative),
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                state.incomeExpense.takeLast(3).reversed().forEach { point ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(MonthKey.shortLabel(point.month))
                                        Text(
                                            "in ${MoneyFormat.format(point.incomeCents, state.currency)} \u00B7 " +
                                                "out ${MoneyFormat.format(point.expenseCents, state.currency)}",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        item { SectionHeader("How long your money lasts") }
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                ),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Age of Money", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        state.ageOfMoney?.let { "$it days" } ?: "Not enough history yet",
                                        style = com.envelope.budget.ui.theme.MoneyTextStyleLarge,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text("Days of Buffering", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        state.daysOfBuffering?.let { "$it days" } ?: "No recent spending",
                                        style = com.envelope.budget.ui.theme.MoneyTextStyleLarge,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "Age of Money averages the last 10 cash outflows by how long the money " +
                                            "had been in the budget. Card purchases age money in the Payment " +
                                            "reserve until the card is paid. Days of Buffering divides positive " +
                                            "budget balances by the last 90 days of average spending.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        if (state.targets.isEmpty()) {
                            item {
                                EmptyState(
                                    "No targets yet",
                                    "Set a target on any category to track progress here.",
                                )
                            }
                        } else {
                            items(state.targets) { target ->
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(target.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${MoneyFormat.format(target.availableCents, state.currency)} / " +
                                                MoneyFormat.format(target.targetCents, state.currency),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { target.progress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}
