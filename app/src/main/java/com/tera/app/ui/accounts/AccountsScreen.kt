package com.tera.app.ui.accounts

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tera.app.data.BudgetRepository
import com.tera.app.data.DomainException
import com.tera.app.data.SettingsRepository
import com.tera.app.domain.Account
import com.tera.app.domain.AccountType
import com.tera.app.domain.ClearanceState
import com.tera.app.domain.Metrics
import com.tera.app.domain.MonthKey
import com.tera.app.domain.PaymentState
import com.tera.app.domain.Transaction
import com.tera.app.ui.LocalAppContainer
import com.tera.app.ui.components.MoneyText
import com.tera.app.ui.components.SectionHeader
import com.tera.app.ui.components.vmFactory
import com.tera.app.ui.theme.LocalTeraColors
import com.tera.app.ui.theme.MoneyTextStyle
import com.tera.app.util.MoneyFormat
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

data class AccountRow(
    val account: Account,
    val balanceCents: Long,
    val paymentAvailableCents: Long?,
    val paymentState: PaymentState?,
)

data class AccountsUiState(
    val currency: String = "$",
    val budgetAccounts: List<AccountRow> = emptyList(),
    val trackingAccounts: List<AccountRow> = emptyList(),
    val netWorthCents: Long = 0,
    val loading: Boolean = true,
)

class AccountsViewModel(
    private val repo: BudgetRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val state: StateFlow<AccountsUiState> = combine(repo.inputFlow, settings.settings) { input, prefs ->
        val balances = Metrics.currentBalances(input)
        val history = repo.computeHistory(input, MonthKey.current())
        val current = history.month(MonthKey.current())
        val rows = input.accounts.sortedWith(compareBy({ it.isTracking }, { it.sortOrder }, { it.name }))
            .map { account ->
                val payment = current?.payments?.get(account.id)
                AccountRow(
                    account = account,
                    balanceCents = balances[account.id] ?: 0L,
                    paymentAvailableCents = payment?.availableCents,
                    paymentState = payment?.state,
                )
            }
        AccountsUiState(
            currency = prefs.currencySymbol,
            budgetAccounts = rows.filter { !it.account.isTracking },
            trackingAccounts = rows.filter { it.account.isTracking },
            netWorthCents = rows.sumOf { it.balanceCents },
            loading = false,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun createAccount(
        name: String,
        type: AccountType,
        startingBalance: String,
        tracking: Boolean,
    ) = launch {
        val cents = MoneyFormat.parse(startingBalance) ?: 0L
        repo.createAccount(name, type, cents, LocalDate.now(), tracking)
        _messages.emit("Account added")
    }

    fun setClosed(account: Account, closed: Boolean) = launch {
        repo.setAccountClosed(account.id, closed)
    }

    fun deleteAccount(account: Account) = launch {
        repo.deleteAccount(account.id)
        _messages.emit("Account deleted")
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
fun AccountsScreen(onOpenAccount: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: AccountsViewModel = viewModel(
        factory = vmFactory { AccountsViewModel(container.budget, container.settings) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<AccountRow?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Accounts") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Account") },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Net worth", style = MaterialTheme.typography.labelSmall)
                        MoneyText(
                            state.netWorthCents,
                            state.currency,
                            style = com.tera.app.ui.theme.MoneyTextStyleLarge,
                            color = if (state.netWorthCents < 0) {
                                LocalTeraColors.current.negative
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        )
                    }
                }
            }
            item { SectionHeader("Budget accounts") }
            items(state.budgetAccounts, key = { it.account.id }) { row ->
                AccountRowItem(row, state.currency, onOpen = { onOpenAccount(row.account.id) }, onMenu = { editTarget = row })
            }
            if (state.trackingAccounts.isNotEmpty()) {
                item { SectionHeader("Tracking accounts") }
                items(state.trackingAccounts, key = { it.account.id }) { row ->
                    AccountRowItem(row, state.currency, onOpen = { onOpenAccount(row.account.id) }, onMenu = { editTarget = row })
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    if (showAdd) {
        AddAccountDialog(
            currency = state.currency,
            onDismiss = { showAdd = false },
            onConfirm = { name, type, balance, tracking ->
                viewModel.createAccount(name, type, balance, tracking)
                showAdd = false
            },
        )
    }

    editTarget?.let { row ->
        AccountActionsDialog(
            row = row,
            currency = state.currency,
            onDismiss = { editTarget = null },
            onClose = {
                viewModel.setClosed(row.account, !row.account.isClosed)
                editTarget = null
            },
            onDelete = {
                viewModel.deleteAccount(row.account)
                editTarget = null
            },
        )
    }
}

@Composable
private fun AccountRowItem(
    row: AccountRow,
    currency: String,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.account.name + if (row.account.isClosed) " (closed)" else "",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = when {
                row.account.type == AccountType.CREDIT && row.paymentAvailableCents != null ->
                    "Payment available ${MoneyFormat.format(row.paymentAvailableCents, currency)}"
                row.account.isTracking -> "Tracking"
                else -> row.account.type.name.lowercase().replaceFirstChar { it.uppercase() }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoneyText(
            row.balanceCents,
            currency,
            color = if (row.balanceCents < 0) LocalTeraColors.current.negative else MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onMenu) {
            Icon(Icons.Filled.Refresh, contentDescription = "Account actions")
        }
    }
    HorizontalDivider()
}

@Composable
private fun AddAccountDialog(
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (String, AccountType, String, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.CHECKING) }
    var tracking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name, type, balance, tracking) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("New account") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = { Text("Starting balance ($currency)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(AccountType.CHECKING, AccountType.SAVINGS, AccountType.CASH, AccountType.CREDIT)
                        .forEach { option ->
                            FilterChip(
                                selected = type == option,
                                onClick = { type = option },
                                label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            )
                        }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = tracking, onCheckedChange = { tracking = it })
                    Text("Off-budget tracking account")
                }
                if (type == AccountType.CREDIT && !tracking) {
                    Text(
                        "A Payment category is created automatically for credit cards.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun AccountActionsDialog(
    row: AccountRow,
    currency: String,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onClose) { Text(if (row.account.isClosed) "Reopen" else "Close") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        title = { Text(row.account.name) },
        text = {
            Column {
                Text(
                    "Balance ${MoneyFormat.format(row.balanceCents, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Closing keeps the history and removes the account from pickers. " +
                        "Accounts with transactions cannot be deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
