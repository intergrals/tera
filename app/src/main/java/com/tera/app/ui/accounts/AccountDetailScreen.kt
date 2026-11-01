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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tera.app.data.BudgetRepository
import com.tera.app.data.DomainException
import com.tera.app.data.SettingsRepository
import com.tera.app.domain.Account
import com.tera.app.domain.ClearanceState
import com.tera.app.domain.MonthKey
import com.tera.app.domain.Transaction
import com.tera.app.ui.LocalAppContainer
import com.tera.app.ui.components.EmptyState
import com.tera.app.ui.components.FlagDot
import com.tera.app.ui.components.MoneyText
import com.tera.app.ui.components.vmFactory
import com.tera.app.ui.theme.LocalTeraColors
import com.tera.app.ui.theme.MoneyTextStyle
import com.tera.app.util.MoneyFormat
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

data class RegisterRow(
    val tx: Transaction,
    val runningBalanceCents: Long,
    val title: String,
    val subtitle: String,
)

data class AccountDetailUiState(
    val account: Account? = null,
    val rows: List<RegisterRow> = emptyList(),
    val balanceCents: Long = 0,
    val currency: String = "$",
    val query: String = "",
)

class AccountDetailViewModel(
    private val repo: BudgetRepository,
    settings: SettingsRepository,
    val accountId: Long,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val state: StateFlow<AccountDetailUiState> = combine(
        repo.inputFlow,
        settings.settings,
        query,
    ) { input, prefs, search ->
        val account = input.accountsById[accountId]
        val transactions = input.transactions
            .filter { it.accountId == accountId }
            .sortedWith(compareBy({ it.date }, { it.id }))

        var running = account?.startingBalanceCents ?: 0L
        val rows = transactions.map { tx ->
            running += tx.amountCents
            RegisterRow(
                tx = tx,
                runningBalanceCents = running,
                title = tx.payee.ifBlank { if (tx.amountCents > 0) "Inflow" else "Outflow" },
                subtitle = subtitleFor(tx, input),
            )
        }

        val filtered = if (search.isBlank()) {
            rows
        } else {
            rows.filter {
                it.tx.payee.contains(search, ignoreCase = true) ||
                    it.tx.memo.contains(search, ignoreCase = true) ||
                    it.subtitle.contains(search, ignoreCase = true)
            }
        }

        AccountDetailUiState(
            account = account,
            rows = filtered.reversed(),
            balanceCents = if (transactions.isEmpty()) account?.startingBalanceCents ?: 0L else running,
            currency = prefs.currencySymbol,
            query = search,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailUiState())

    private fun subtitleFor(tx: Transaction, input: com.tera.app.domain.BudgetInput): String {
        val parts = ArrayList<String>(2)
        if (tx.isTransfer) {
            val peer = tx.transferPeerId?.let { input.transactionsById[it] }
            val otherAccount = peer?.let { input.accountsById[it.accountId] }
            parts.add("Transfer: ${otherAccount?.name ?: "account"}")
        } else {
            val categoryName = tx.categoryId?.let { input.categoriesById[it]?.name }
            parts.add(categoryName ?: "Ready to Assign")
        }
        if (tx.memo.isNotBlank()) parts.add(tx.memo)
        return parts.joinToString(" \u00B7 ")
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleCleared(row: RegisterRow) = launch {
        val next = if (row.tx.cleared == ClearanceState.UNCLEARED) {
            ClearanceState.CLEARED
        } else {
            ClearanceState.UNCLEARED
        }
        repo.setCleared(row.tx.id, next)
    }

    fun delete(row: RegisterRow) = launch {
        repo.deleteTransaction(row.tx.id)
        _messages.emit("Transaction deleted")
    }

    fun reconcile(actualBalance: String) = launch {
        val cents = MoneyFormat.parse(actualBalance)
            ?: throw DomainException("Enter the real account balance")
        repo.reconcile(accountId, cents)
        _messages.emit("Account reconciled")
    }

    fun closeAccount() = launch {
        val account = repo.account(accountId) ?: return@launch
        repo.setAccountClosed(accountId, !account.isClosed)
        _messages.emit(if (account.isClosed) "Account reopened" else "Account closed")
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
fun AccountDetailScreen(
    accountId: Long,
    onBack: () -> Unit,
    onAddTransaction: (Long) -> Unit,
    onEditTransaction: (Long, Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: AccountDetailViewModel = viewModel(
        key = "account-$accountId",
        factory = vmFactory {
            AccountDetailViewModel(container.budget, container.settings, accountId)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var reconcileOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<RegisterRow?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.account?.name ?: "Account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { reconcileOpen = true }) { Text("Reconcile") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddTransaction(accountId) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Transaction") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Balance", style = MaterialTheme.typography.labelSmall)
                MoneyText(
                    state.balanceCents,
                    state.currency,
                    style = com.tera.app.ui.theme.MoneyTextStyleLarge,
                    color = if (state.balanceCents < 0) {
                        LocalTeraColors.current.negative
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text("Search payee, memo, category") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (state.rows.isEmpty()) {
                EmptyState(
                    title = "No transactions yet",
                    body = "Tap the button below to record your first transaction on this account.",
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.tx.id }) { row ->
                        RegisterRowItem(
                            row = row,
                            currency = state.currency,
                            onToggleCleared = { viewModel.toggleCleared(row) },
                            onClick = { onEditTransaction(row.tx.id, accountId) },
                            onDelete = { deleteTarget = row },
                        )
                    }
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }
    }

    if (reconcileOpen) {
        var actual by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reconcileOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reconcile(actual)
                    reconcileOpen = false
                }) { Text("Reconcile") }
            },
            dismissButton = { TextButton(onClick = { reconcileOpen = false }) { Text("Cancel") } },
            title = { Text("Reconcile ${state.account?.name.orEmpty()}") },
            text = {
                Column {
                    Text(
                        "Statement balance is ${MoneyFormat.format(state.balanceCents, state.currency)}. " +
                            "Enter the real balance; the difference is booked to Adjustment.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = actual,
                        onValueChange = { actual = it },
                        label = { Text("Actual balance") },
                        singleLine = true,
                    )
                }
            },
        )
    }

    deleteTarget?.let { row ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(row)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
            title = { Text("Delete transaction?") },
            text = {
                Text(
                    if (row.tx.isTransfer) {
                        "This is one half of a transfer; both sides are removed."
                    } else {
                        "This cannot be undone."
                    },
                )
            },
        )
    }
}

@Composable
private fun RegisterRowItem(
    row: RegisterRow,
    currency: String,
    onToggleCleared: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleCleared) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = if (row.tx.cleared == ClearanceState.UNCLEARED) {
                    "Mark cleared"
                } else {
                    "Mark uncleared"
                },
                tint = if (row.tx.cleared == ClearanceState.UNCLEARED) {
                    MaterialTheme.colorScheme.outlineVariant
                } else {
                    LocalTeraColors.current.positive
                },
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlagDot(row.tx.flag)
                Text(
                    row.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${MonthKey.formatDay(row.tx.date)} \u00B7 ${row.subtitle}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(
                row.tx.amountCents,
                currency,
                color = if (row.tx.amountCents > 0) {
                    LocalTeraColors.current.positive
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            MoneyText(
                row.runningBalanceCents,
                currency,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete transaction", tint = MaterialTheme.colorScheme.outline)
        }
    }
    HorizontalDivider()
}
