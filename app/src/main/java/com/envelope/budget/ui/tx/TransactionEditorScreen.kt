package com.envelope.budget.ui.tx

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.envelope.budget.data.SettingsRepository
import com.envelope.budget.data.TransactionDraft
import com.envelope.budget.domain.Account
import com.envelope.budget.domain.Category
import com.envelope.budget.domain.CategoryGroup
import com.envelope.budget.domain.ClearanceState
import com.envelope.budget.domain.FlagColor
import com.envelope.budget.domain.Transaction
import com.envelope.budget.ui.LocalAppContainer
import com.envelope.budget.ui.components.AccountPickerSheet
import com.envelope.budget.ui.components.AmountKeypad
import com.envelope.budget.ui.components.CategoryPickerSheet
import com.envelope.budget.ui.components.DateField
import com.envelope.budget.ui.components.KeypadDisplay
import com.envelope.budget.ui.components.TextFieldRow
import com.envelope.budget.ui.components.vmFactory
import com.envelope.budget.util.MoneyFormat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EditorUiState(
    val currency: String = "$",
    val accounts: List<Account> = emptyList(),
    val groups: List<CategoryGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val payees: List<String> = emptyList(),
    val accountId: Long = 0,
    val date: LocalDate = LocalDate.now(),
    val payee: String = "",
    val memo: String = "",
    val digits: String = "",
    val isIncome: Boolean = false,
    val categoryId: Long? = null,
    val transferAccountId: Long? = null,
    val flag: FlagColor = FlagColor.NONE,
    val cleared: ClearanceState = ClearanceState.UNCLEARED,
    val isEditing: Boolean = false,
    val loading: Boolean = true,
    val finished: Boolean = false,
)

class TransactionEditorViewModel(
    private val repo: BudgetRepository,
    settings: SettingsRepository,
    private val transactionId: Long?,
    initialAccountId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState(currency = settings.current().currencySymbol))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val input = repo.inputNow()
            val existing = transactionId?.let { repo.transaction(it) }
            val transferAccount = existing?.transferPeerId?.let { peerId ->
                input.transactionsById[peerId]?.accountId
            }
            val account = when {
                existing != null -> existing.accountId
                initialAccountId != null -> initialAccountId
                else -> input.accounts.firstOrNull { !it.isClosed }?.id ?: 0L
            }
            val amount = existing?.amountCents ?: 0L
            _state.value = _state.value.copy(
                accounts = input.accounts.filter { !it.isClosed },
                groups = input.groups,
                categories = input.categories,
                payees = repo.payees(),
                accountId = account,
                date = existing?.date ?: LocalDate.now(),
                payee = existing?.payee ?: "",
                memo = existing?.memo ?: "",
                digits = if (amount == 0L) "" else MoneyFormat.toKeypadDigits(amount),
                isIncome = amount > 0,
                categoryId = existing?.categoryId,
                transferAccountId = transferAccount,
                flag = existing?.flag ?: FlagColor.NONE,
                cleared = existing?.cleared ?: ClearanceState.UNCLEARED,
                isEditing = existing != null,
                loading = false,
            )
        }
    }

    fun setAccount(id: Long) {
        _state.value = _state.value.copy(accountId = id, transferAccountId = null)
    }

    fun setDate(date: LocalDate) {
        _state.value = _state.value.copy(date = date)
    }

    fun setPayee(value: String) {
        _state.value = _state.value.copy(payee = value)
    }

    fun setMemo(value: String) {
        _state.value = _state.value.copy(memo = value)
    }

    fun setCategory(id: Long?) {
        _state.value = _state.value.copy(categoryId = id, transferAccountId = null)
    }

    fun setTransferAccount(id: Long?) {
        _state.value = _state.value.copy(transferAccountId = id, categoryId = null)
    }

    fun setIncome(isIncome: Boolean) {
        _state.value = _state.value.copy(isIncome = isIncome)
    }

    fun setFlag(flag: FlagColor) {
        _state.value = _state.value.copy(flag = flag)
    }

    fun toggleCleared() {
        val current = _state.value.cleared
        _state.value = _state.value.copy(
            cleared = if (current == ClearanceState.UNCLEARED) {
                ClearanceState.CLEARED
            } else {
                ClearanceState.UNCLEARED
            },
        )
    }

    fun appendDigit(digit: Char) {
        val next = (_state.value.digits + digit).take(11)
        _state.value = _state.value.copy(digits = next)
    }

    fun backspace() {
        _state.value = _state.value.copy(digits = _state.value.digits.dropLast(1))
    }

    fun save() = viewModelScope.launch {
        val current = _state.value
        try {
            val magnitude = MoneyFormat.fromKeypadDigits(current.digits)
            if (magnitude == 0L) throw DomainException("Enter an amount")
            if (current.accountId == 0L) throw DomainException("Pick an account")
            val amount = if (current.transferAccountId != null) {
                magnitude
            } else if (current.isIncome) {
                magnitude
            } else {
                -magnitude
            }
            repo.saveTransaction(
                TransactionDraft(
                    id = transactionId,
                    accountId = current.accountId,
                    date = current.date,
                    payee = current.payee,
                    memo = current.memo,
                    amountCents = amount,
                    categoryId = if (current.transferAccountId != null) null else current.categoryId,
                    cleared = current.cleared,
                    flag = current.flag,
                    transferAccountId = current.transferAccountId,
                ),
            )
            _state.value = _state.value.copy(finished = true)
        } catch (e: DomainException) {
            _messages.emit(e.message ?: "Could not save")
        }
    }

    fun delete() = viewModelScope.launch {
        val id = transactionId ?: return@launch
        repo.deleteTransaction(id)
        _state.value = _state.value.copy(finished = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorScreen(
    transactionId: Long?,
    initialAccountId: Long?,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: TransactionEditorViewModel = viewModel(
        key = "editor-${transactionId ?: 0}-${initialAccountId ?: 0}",
        factory = vmFactory {
            TransactionEditorViewModel(
                container.budget,
                container.settings,
                transactionId,
                initialAccountId,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var accountPicker by remember { mutableStateOf(false) }
    var transferPicker by remember { mutableStateOf(false) }
    var categoryPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(state.finished) { if (state.finished) onBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit transaction" else "New transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete transaction")
                        }
                    }
                    TextButton(onClick = viewModel::save) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.isIncome,
                        onClick = { viewModel.setIncome(false) },
                        label = { Text("Expense") },
                    )
                    FilterChip(
                        selected = state.isIncome,
                        onClick = { viewModel.setIncome(true) },
                        label = { Text("Income") },
                        enabled = state.transferAccountId == null,
                    )
                }
                Spacer(Modifier.height(8.dp))
                KeypadDisplay(state.digits, state.currency, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))

                FieldRow(
                    label = "Account",
                    value = state.accounts.firstOrNull { it.id == state.accountId }?.name ?: "Pick account",
                    onClick = { accountPicker = true },
                )
                HorizontalDivider()
                DateField(label = "Date", date = state.date, onDateChange = viewModel::setDate)
                HorizontalDivider()
                FieldRow(
                    label = "Category",
                    value = when {
                        state.transferAccountId != null -> {
                            "Transfer: " + (
                                state.accounts.firstOrNull { it.id == state.transferAccountId }?.name
                                    ?: ""
                                )
                        }
                        state.categoryId == null -> "Ready to Assign"
                        else -> state.categories.firstOrNull { it.id == state.categoryId }?.name
                            ?: "Ready to Assign"
                    },
                    onClick = { categoryPicker = true },
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { transferPicker = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Transfer to", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        state.transferAccountId?.let { id ->
                            state.accounts.firstOrNull { it.id == id }?.name
                        } ?: "Not a transfer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.transferAccountId != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                HorizontalDivider()
                TextFieldRow(
                    label = "Payee",
                    value = state.payee,
                    onValueChange = viewModel::setPayee,
                    placeholder = state.payees.firstOrNull().orEmpty(),
                )
                TextFieldRow(
                    label = "Memo",
                    value = state.memo,
                    onValueChange = viewModel::setMemo,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = viewModel::toggleCleared,
                        label = {
                            Text(
                                if (state.cleared == ClearanceState.UNCLEARED) {
                                    "Uncleared"
                                } else {
                                    "Cleared"
                                },
                            )
                        },
                    )
                    FlagColor.entries.forEach { flag ->
                        FilterChip(
                            selected = state.flag == flag,
                            onClick = { viewModel.setFlag(flag) },
                            label = { Text(if (flag == FlagColor.NONE) "None" else flag.name.take(3)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            AmountKeypad(
                digits = state.digits,
                onDigit = viewModel::appendDigit,
                onBackspace = viewModel::backspace,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    if (accountPicker) {
        AccountPickerSheet(
            accounts = state.accounts,
            selectedId = state.accountId,
            title = "Account",
            onDismiss = { accountPicker = false },
            onPick = {
                viewModel.setAccount(it.id)
                accountPicker = false
            },
        )
    }
    if (transferPicker) {
        AccountPickerSheet(
            accounts = state.accounts,
            selectedId = state.transferAccountId,
            title = "Transfer to (pick the same account to clear)",
            excludeId = state.accountId,
            onDismiss = { transferPicker = false },
            onPick = {
                viewModel.setTransferAccount(if (it.id == state.transferAccountId) null else it.id)
                transferPicker = false
            },
        )
    }
    if (categoryPicker) {
        CategoryPickerSheet(
            groups = state.groups,
            categories = state.categories,
            onDismiss = { categoryPicker = false },
            onPick = {
                viewModel.setCategory(it)
                categoryPicker = false
            },
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
