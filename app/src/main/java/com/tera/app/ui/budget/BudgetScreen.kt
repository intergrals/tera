package com.tera.app.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tera.app.data.AutoAssignStrategy
import com.tera.app.domain.PaymentState
import com.tera.app.ui.LocalAppContainer
import com.tera.app.ui.components.AmountKeypad
import com.tera.app.ui.components.ColumnLabels
import com.tera.app.ui.components.EmptyState
import com.tera.app.ui.components.KeypadDisplay
import com.tera.app.ui.components.MoneyText
import com.tera.app.ui.components.MonthNavigator
import com.tera.app.ui.components.Pill
import com.tera.app.ui.components.SectionHeader
import com.tera.app.ui.components.availableColor
import com.tera.app.ui.components.vmFactory
import com.tera.app.ui.theme.LocalTeraColors
import com.tera.app.ui.theme.MoneyTextStyle
import com.tera.app.util.MoneyFormat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onAddTransaction: () -> Unit,
    onOpenUpcoming: () -> Unit,
    onOpenManageEnvelopes: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: BudgetViewModel = viewModel(
        factory = vmFactory { BudgetViewModel(container.budget, container.settings) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var menuOpen by remember { mutableStateOf(false) }
    var allocateTarget by remember { mutableStateOf<CategoryView?>(null) }
    var breakdownOpen by remember { mutableStateOf(false) }
    var collapsedGroups by remember { mutableStateOf(emptySet<Long>()) }

    val paymentCategoryViews = state.payments.map { payment ->
        CategoryView(
            id = payment.paymentCategoryId,
            name = "${payment.name} Payment",
            note = "Reserved for the card bill",
            assignedCents = payment.assignedCents,
            activityCents = payment.activityCents,
            availableCents = payment.availableCents,
            hasTarget = false,
            targetSuggestedCents = 0,
            progress = null,
            targetMet = false,
            snoozed = false,
        )
    }


    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    MonthNavigator(
                        month = state.month,
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                        onToday = viewModel::currentMonth,
                        isCurrent = state.isCurrentMonth,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenUpcoming) {
                        Icon(Icons.Outlined.Event, contentDescription = "Upcoming transactions")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Budget actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Manage envelopes") },
                            onClick = {
                                menuOpen = false
                                onOpenManageEnvelopes()
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Auto-assign: underfunded") },
                            onClick = {
                                menuOpen = false
                                viewModel.autoAssign(AutoAssignStrategy.UNDERFUNDED)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Auto-assign: last month's assigned") },
                            onClick = {
                                menuOpen = false
                                viewModel.autoAssign(AutoAssignStrategy.ASSIGNED_LAST_MONTH)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Auto-assign: last month's spending") },
                            onClick = {
                                menuOpen = false
                                viewModel.autoAssign(AutoAssignStrategy.SPENT_LAST_MONTH)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Auto-assign: 3-month average") },
                            onClick = {
                                menuOpen = false
                                viewModel.autoAssign(AutoAssignStrategy.AVERAGE_SPENT_3)
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Reset this month's assignments") },
                            onClick = {
                                menuOpen = false
                                viewModel.autoAssign(AutoAssignStrategy.RESET)
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTransaction,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Transaction") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        ) {
            item {
                ReadyToAssignCard(
                    amountCents = state.readyToAssignCents,
                    currency = state.currency,
                    onClick = { breakdownOpen = true },
                )
            }
            item {
                ColumnLabels(
                    "Category" to 2.1f,
                    "Assigned" to 1f,
                    "Activity" to 1f,
                    "Available" to 1.1f,
                )
            }
            if (state.groups.isEmpty() && state.payments.isEmpty()) {
                item {
                    EmptyState(
                        title = "No envelopes yet",
                        body = "Envelopes are where you give dollars a job. Create a group such as " +
                            "Living, add envelopes inside it, then assign money to them.",
                    )
                    TextButton(
                        onClick = onOpenManageEnvelopes,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) { Text("Set up envelopes") }
                }
            }
            state.groups.forEach { group ->
                val expanded = group.id !in collapsedGroups
                item(key = "group-${group.id}") {
                    GroupHeader(
                        group = group,
                        currency = state.currency,
                        expanded = expanded,
                        onToggle = {
                            collapsedGroups = if (expanded) {
                                collapsedGroups + group.id
                            } else {
                                collapsedGroups - group.id
                            }
                        },
                    )
                }
                if (expanded) {
                    items(group.categories, key = { "cat-${it.id}" }) { category ->
                        CategoryRow(
                            category = category,
                            currency = state.currency,
                            onClick = { allocateTarget = category },
                        )
                    }
                }
            }
            if (state.payments.isNotEmpty()) {
                item(key = "payments-header") { SectionHeader("Credit Card Payments") }
                items(state.payments, key = { "card-${it.accountId}" }) { payment ->
                    PaymentRow(
                        payment = payment,
                        currency = state.currency,
                        onClick = {
                            allocateTarget = paymentCategoryViews.firstOrNull {
                                it.id == payment.paymentCategoryId
                            }
                        },
                    )
                }
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Total",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(2.1f),
                    )
                    MoneyText(
                        state.assignedTotalCents,
                        state.currency,
                        modifier = Modifier.weight(1f),
                        style = MoneyTextStyle,
                    )
                    MoneyText(
                        state.activityTotalCents,
                        state.currency,
                        modifier = Modifier.weight(1f),
                        style = MoneyTextStyle,
                    )
                    MoneyText(
                        state.availableTotalCents,
                        state.currency,
                        modifier = Modifier.weight(1.1f),
                        style = MoneyTextStyle,
                        color = availableColor(state.availableTotalCents),
                    )
                }
            }
        }
    }

    allocateTarget?.let { category ->
        AllocateSheet(
            category = category,
            currency = state.currency,
            onDismiss = { allocateTarget = null },
            onAssign = { cents ->
                viewModel.assign(category.id, cents)
                allocateTarget = null
            },
            onFundTarget = { cents ->
                viewModel.fundTarget(category.id, cents)
                allocateTarget = null
            },
            onSnooze = { snoozed -> viewModel.snoozeTarget(category.id, snoozed) },
            onMoveMoney = { cents, targetCategoryId ->
                viewModel.moveMoney(category.id, targetCategoryId, cents)
                allocateTarget = null
            },
            destinations = state.groups.flatMap { it.categories } + paymentCategoryViews,
        )
    }

    if (breakdownOpen) {
        val breakdown = state.breakdown
        AlertDialog(
            onDismissRequest = { breakdownOpen = false },
            confirmButton = {
                TextButton(onClick = { breakdownOpen = false }) { Text("Close") }
            },
            title = { Text("Ready to Assign") },
            text = {
                Column {
                    BreakdownRow("Carried in from last month", breakdown?.carriedInCents ?: 0L, state.currency)
                    BreakdownRow("Income this month", breakdown?.incomeCents ?: 0L, state.currency)
                    BreakdownRow("Moved from tracking accounts", breakdown?.trackingNetCents ?: 0L, state.currency)
                    BreakdownRow("Assigned this month", -(breakdown?.assignedCents ?: 0L), state.currency)
                    BreakdownRow("Overspent last month", -(breakdown?.overspentLastMonthCents ?: 0L), state.currency)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    BreakdownRow("Ready to Assign", state.readyToAssignCents, state.currency, emphasise = true)
                    if (state.mode == com.tera.app.domain.OverspendMode.CARRY_NEGATIVE) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Overspending carries forward as red envelopes in this mode.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
    }
    // Keep the snackbar usable from sheet callbacks.
    scope.launch { /* no-op keeps scope referenced for future use */ }
}

@Composable
private fun ReadyToAssignCard(amountCents: Long, currency: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (amountCents < 0) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Ready to Assign",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(4.dp))
            MoneyText(
                cents = amountCents,
                currency = currency,
                style = com.tera.app.ui.theme.MoneyTextStyleLarge,
                color = if (amountCents < 0) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
            )
            if (amountCents > 0) {
                Text(
                    "Give every dollar a job \u2014 tap for the breakdown",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            } else if (amountCents < 0) {
                Text(
                    "You have assigned more than you have",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(
    group: GroupView,
    currency: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(2.1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MoneyText(group.assignedCents, currency, modifier = Modifier.weight(1f), style = MoneyTextStyle)
        MoneyText(group.activityCents, currency, modifier = Modifier.weight(1f), style = MoneyTextStyle)
        MoneyText(
            group.availableCents,
            currency,
            modifier = Modifier.weight(1.1f),
            style = MoneyTextStyle,
            color = availableColor(group.availableCents),
        )
    }
}

@Composable
private fun CategoryRow(category: CategoryView, currency: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(2.1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (category.hasTarget) {
                val progress = category.progress ?: 0f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.height(3.dp).width(64.dp).clip(RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            category.targetMet -> "Target met"
                            category.snoozed -> "Snoozed"
                            else -> "Needs ${MoneyFormat.format(category.targetSuggestedCents, currency)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (category.note.isNotBlank()) {
                Text(
                    category.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        MoneyText(category.assignedCents, currency, modifier = Modifier.weight(1f), style = MoneyTextStyle)
        MoneyText(category.activityCents, currency, modifier = Modifier.weight(1f), style = MoneyTextStyle)
        MoneyText(
            category.availableCents,
            currency,
            modifier = Modifier.weight(1.1f),
            style = MoneyTextStyle,
            color = availableColor(category.availableCents),
        )
    }
}

@Composable
private fun PaymentRow(payment: PaymentView, currency: String, onClick: () -> Unit) {
    val colors = LocalTeraColors.current
    val (stateColor, stateLabel) = when (payment.state) {
        PaymentState.GREEN -> colors.positive to "Paid in full"
        PaymentState.YELLOW -> colors.warning to "Underfunded"
        PaymentState.RED -> colors.negative to "Overpaid"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(2.1f)) {
            Text(payment.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(stateLabel, stateColor)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Owe ${MoneyFormat.format(payment.debtCents, currency)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MoneyText(payment.assignedCents, currency, modifier = Modifier.weight(1f), style = MoneyTextStyle)
        MoneyText(payment.activityCents, currency, modifier = Modifier.weight(1f), style = MoneyTextStyle)
        MoneyText(
            payment.availableCents,
            currency,
            modifier = Modifier.weight(1.1f),
            style = MoneyTextStyle,
            color = when (payment.state) {
                PaymentState.RED -> colors.negative
                PaymentState.YELLOW -> colors.warning
                PaymentState.GREEN -> colors.positive
            },
        )
    }
}

@Composable
private fun BreakdownRow(label: String, cents: Long, currency: String, emphasise: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = if (emphasise) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        MoneyText(
            cents,
            currency,
            style = if (emphasise) MaterialTheme.typography.titleMedium else MoneyTextStyle,
            color = if (cents < 0) LocalTeraColors.current.negative else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Assign / fund / snooze / move money for one category. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllocateSheet(
    category: CategoryView,
    currency: String,
    destinations: List<CategoryView>,
    onDismiss: () -> Unit,
    onAssign: (Long) -> Unit,
    onFundTarget: (Long) -> Unit,
    onSnooze: (Boolean) -> Unit,
    onMoveMoney: (Long, Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var digits by remember { mutableStateOf(MoneyFormat.toKeypadDigits(category.assignedCents)) }
    var moveMode by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(category.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "Available ${MoneyFormat.format(category.availableCents, currency)} \u00B7 " +
                    "Assigned ${MoneyFormat.format(category.assignedCents, currency)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (!moveMode) {
                KeypadDisplay(digits, currency)
                Spacer(Modifier.height(8.dp))
                if (category.hasTarget) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (category.targetSuggestedCents > 0) {
                            FilledTonalButton(onClick = { onFundTarget(category.targetSuggestedCents) }) {
                                Text("Fund ${MoneyFormat.format(category.targetSuggestedCents, currency)}")
                            }
                        }
                        TextButton(onClick = { onSnooze(!category.snoozed) }) {
                            Text(if (category.snoozed) "Un-snooze" else "Snooze target")
                        }
                    }
                }
                AmountKeypad(
                    digits = digits,
                    onDigit = { digit -> digits = (digits + digit).take(11) },
                    onBackspace = { digits = digits.dropLast(1) },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { onAssign(MoneyFormat.fromKeypadDigits(digits)) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Assign") }
                    TextButton(onClick = { digits = "" }, modifier = Modifier.weight(1f)) {
                        Text("Clear")
                    }
                    TextButton(onClick = { moveMode = true }, modifier = Modifier.weight(1f)) {
                        Text("Move")
                    }
                }
            } else {
                Text(
                    "Move money from ${category.name} to:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                KeypadDisplay(digits, currency)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(destinations.filter { it.id != category.id && it.id != category.id }, key = { it.id }) { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMoveMoney(MoneyFormat.fromKeypadDigits(digits), target.id)
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(target.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            MoneyText(target.availableCents, currency, color = availableColor(target.availableCents))
                        }
                    }
                }
                AmountKeypad(
                    digits = digits,
                    onDigit = { digit -> digits = (digits + digit).take(11) },
                    onBackspace = { digits = digits.dropLast(1) },
                )
                TextButton(onClick = { moveMode = false }) { Text("Back") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
