package com.envelope.budget.ui.scheduled

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.envelope.budget.data.BudgetRepository
import com.envelope.budget.data.DomainException
import com.envelope.budget.data.ScheduledDraft
import com.envelope.budget.data.SettingsRepository
import com.envelope.budget.domain.Account
import com.envelope.budget.domain.Category
import com.envelope.budget.domain.CategoryGroup
import com.envelope.budget.domain.MonthKey
import com.envelope.budget.domain.Recurrence
import com.envelope.budget.domain.ScheduledTransaction
import com.envelope.budget.ui.LocalAppContainer
import com.envelope.budget.ui.components.AccountPickerSheet
import com.envelope.budget.ui.components.CategoryPickerSheet
import com.envelope.budget.ui.components.DateField
import com.envelope.budget.ui.components.EmptyState
import com.envelope.budget.ui.components.MoneyText
import com.envelope.budget.ui.components.Pill
import com.envelope.budget.ui.components.vmFactory
import com.envelope.budget.ui.theme.LocalEnvelopeColors
import com.envelope.budget.ui.theme.MoneyTextStyle
import com.envelope.budget.util.MoneyFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class UpcomingRow(
    val scheduled: ScheduledTransaction,
    val accountName: String,
    val categoryName: String,
    val overdue: Boolean,
)

data class UpcomingUiState(
    val rows: List<UpcomingRow> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val groups: List<CategoryGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currency: String = "$",
)

class UpcomingViewModel(
    private val repo: BudgetRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val state: StateFlow<UpcomingUiState> = combine(
        repo.inputFlow,
        repo.scheduledFlow,
        settings.settings,
    ) { input, scheduled, prefs ->
        val today = LocalDate.now()
        val rows = scheduled
            .sortedBy { it.nextDate }
            .map { item ->
                UpcomingRow(
                    scheduled = item,
                    accountName = input.accountsById[item.accountId]?.name ?: "Account",
                    categoryName = item.categoryId?.let { input.categoriesById[it]?.name }
                        ?: "Ready to Assign",
                    overdue = item.nextDate.isBefore(today),
                )
            }
        UpcomingUiState(
            rows = rows,
            accounts = input.accounts.filter { !it.isClosed },
            groups = input.groups,
            categories = input.categories,
            currency = prefs.currencySymbol,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpcomingUiState())

    fun postNow(id: Long) = launch {
        repo.postScheduledNow(id, LocalDate.now())
        _messages.emit("Transaction entered")
    }

    fun delete(id: Long) = launch {
        repo.deleteScheduled(id)
        _messages.emit("Upcoming transaction deleted")
    }

    fun save(
        id: Long?,
        payee: String,
        amount: String,
        accountId: Long,
        categoryId: Long?,
        date: LocalDate,
        recurrence: Recurrence,
    ) = launch {
        if (payee.isBlank()) throw DomainException("Enter a payee")
        val cents = MoneyFormat.parse(amount) ?: throw DomainException("Enter an amount")
        repo.saveScheduled(
            ScheduledDraft(
                id = id,
                accountId = accountId,
                categoryId = categoryId,
                payee = payee,
                memo = "",
                amountCents = -kotlin.math.abs(cents),
                recurrence = recurrence,
                nextDate = date,
            ),
        )
        _messages.emit("Upcoming transaction saved")
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: DomainException) {
                _messages.emit(e.message ?: "Something went wrong")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: UpcomingViewModel = viewModel(
        factory = vmFactory { UpcomingViewModel(container.budget, container.settings) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<UpcomingRow?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Upcoming") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Upcoming") },
            )
        },
    ) { padding ->
        if (state.rows.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "Nothing scheduled",
                    body = "Add rent, subscriptions and other recurring bills so they are ready to enter on time.",
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.rows, key = { it.scheduled.id }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editing = row }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    row.scheduled.payee,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                )
                                if (row.overdue) {
                                    Spacer(Modifier.padding(horizontal = 4.dp))
                                    Pill("Overdue", LocalEnvelopeColors.current.negative)
                                }
                            }
                            Text(
                                "${MonthKey.formatDay(row.scheduled.nextDate)} \u00B7 " +
                                    "${row.scheduled.recurrence.name.lowercase()} \u00B7 ${row.accountName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MoneyText(row.scheduled.amountCents, state.currency)
                        TextButton(onClick = { viewModel.postNow(row.scheduled.id) }) {
                            Text("Enter")
                        }
                    }
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    if (creating || editing != null) {
        val row = editing
        UpcomingEditorDialog(
            row = row,
            accounts = state.accounts,
            groups = state.groups,
            categories = state.categories,
            currency = state.currency,
            onDismiss = {
                creating = false
                editing = null
            },
            onDelete = {
                row?.let { viewModel.delete(it.scheduled.id) }
                creating = false
                editing = null
            },
            onSave = { payee, amount, accountId, categoryId, date, recurrence ->
                viewModel.save(row?.scheduled?.id, payee, amount, accountId, categoryId, date, recurrence)
                creating = false
                editing = null
            },
        )
    }
}

@Composable
private fun UpcomingEditorDialog(
    row: UpcomingRow?,
    accounts: List<Account>,
    groups: List<CategoryGroup>,
    categories: List<Category>,
    currency: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (String, String, Long, Long?, LocalDate, Recurrence) -> Unit,
) {
    var payee by remember { mutableStateOf(row?.scheduled?.payee ?: "") }
    var amount by remember {
        mutableStateOf(
            row?.scheduled?.amountCents?.let { MoneyFormat.format(it, "", false) } ?: "",
        )
    }
    var accountId by remember {
        mutableStateOf(row?.scheduled?.accountId ?: accounts.firstOrNull()?.id ?: 0L)
    }
    var categoryId by remember { mutableStateOf(row?.scheduled?.categoryId) }
    var date by remember { mutableStateOf(row?.scheduled?.nextDate ?: LocalDate.now()) }
    var recurrence by remember {
        mutableStateOf(row?.scheduled?.recurrence ?: Recurrence.MONTHLY)
    }
    var accountPicker by remember { mutableStateOf(false) }
    var categoryPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = payee.isNotBlank() && accountId != 0L,
                onClick = { onSave(payee, amount, accountId, categoryId, date, recurrence) },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (row != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        title = { Text(if (row == null) "New upcoming transaction" else "Edit upcoming") },
        text = {
            Column {
                OutlinedTextField(
                    value = payee,
                    onValueChange = { payee = it },
                    label = { Text("Payee") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount ($currency)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { accountPicker = true }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Account")
                    Text(
                        accounts.firstOrNull { it.id == accountId }?.name ?: "Pick",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { categoryPicker = true }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Category")
                    Text(
                        categories.firstOrNull { it.id == categoryId }?.name ?: "Ready to Assign",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                DateField(label = "Next date", date = date, onDateChange = { date = it })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Recurrence.entries.forEach { option ->
                        FilterChip(
                            selected = recurrence == option,
                            onClick = { recurrence = option },
                            label = { Text(option.name.lowercase().take(5)) },
                        )
                    }
                }
            }
        },
    )

    if (accountPicker) {
        AccountPickerSheet(
            accounts = accounts,
            selectedId = accountId,
            onDismiss = { accountPicker = false },
            onPick = {
                accountId = it.id
                accountPicker = false
            },
        )
    }
    if (categoryPicker) {
        CategoryPickerSheet(
            groups = groups,
            categories = categories,
            onDismiss = { categoryPicker = false },
            onPick = {
                categoryId = it
                categoryPicker = false
            },
        )
    }
}
