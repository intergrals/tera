package com.tera.app.ui.categories

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.tera.app.data.BudgetRepository
import com.tera.app.data.DomainException
import com.tera.app.data.SettingsRepository
import com.tera.app.domain.Category
import com.tera.app.domain.CategoryGroup
import com.tera.app.domain.EnvelopeRules
import com.tera.app.domain.TargetType
import com.tera.app.ui.LocalAppContainer
import com.tera.app.ui.components.DateField
import com.tera.app.ui.components.EmptyState
import com.tera.app.ui.components.vmFactory
import com.tera.app.domain.MonthKey
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

data class ManageGroupRow(val group: CategoryGroup, val categories: List<Category>)

data class ManageUiState(
    val groups: List<ManageGroupRow> = emptyList(),
    val currency: String = "$",
    val loading: Boolean = true,
)

class ManageEnvelopesViewModel(
    private val repo: BudgetRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val state: StateFlow<ManageUiState> = combine(repo.inputFlow, settings.settings) { input, prefs ->
        val rows = input.groups
            .filter { !it.isSystem }
            .sortedBy { it.sortOrder }
            .map { group ->
                ManageGroupRow(
                    group = group,
                    categories = input.categories
                        .filter { it.groupId == group.id && !it.isSystem }
                        .sortedBy { it.sortOrder },
                )
            }
        ManageUiState(groups = rows, currency = prefs.currencySymbol, loading = false)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageUiState())

    fun addGroup(name: String) = run("Group added") { repo.createGroup(name) }

    fun renameGroup(id: Long, name: String) = run("Group renamed") { repo.renameGroup(id, name) }

    fun deleteGroup(id: Long) = run("Group deleted") { repo.deleteGroup(id) }

    fun addCategory(groupId: Long, type: TargetType, valid: EnvelopeRules.Result.Valid, hidden: Boolean) =
        run("Envelope added") {
            val id = repo.createCategory(
                groupId = groupId,
                name = valid.name,
                targetType = type,
                targetAmountCents = valid.amountCents,
                targetDate = valid.date,
                note = valid.note,
            )
            if (hidden) repo.setCategoryHidden(id, true)
        }

    fun updateCategory(
        id: Long,
        valid: EnvelopeRules.Result.Valid,
        type: TargetType,
        hidden: Boolean,
    ) = run("Envelope saved") {
        repo.updateCategory(
            categoryId = id,
            name = valid.name,
            note = valid.note,
            targetType = type,
            targetAmountCents = valid.amountCents,
            targetDate = valid.date,
        )
        repo.setCategoryHidden(id, hidden)
    }

    fun deleteCategory(id: Long) = run("Envelope deleted") { repo.deleteCategory(id) }

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageEnvelopesScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: ManageEnvelopesViewModel = viewModel(
        factory = vmFactory { ManageEnvelopesViewModel(container.budget, container.settings) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var groupDialogOpen by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<CategoryGroup?>(null) }
    var categoryEditor by remember { mutableStateOf<CategoryEditorRequest?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var deletingGroup by remember { mutableStateOf<CategoryGroup?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Envelopes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingGroup = null
                        groupDialogOpen = true
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add group")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.groups.isEmpty()) {
                EmptyState(
                    title = "No envelope groups yet",
                    body = "Groups hold related envelopes. Add a group with the + button, then add Groceries, " +
                        "Rent and the rest inside it. To start from a template instead, erase all data in " +
                        "Settings and run setup again.",
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    state.groups.forEach { row ->
                        item(key = "group-${row.group.id}") {
                            GroupHeaderRow(
                                group = row.group,
                                onRename = { editingGroup = row.group },
                                onDelete = { deletingGroup = row.group },
                            )
                        }
                        items(row.categories, key = { "cat-${it.id}" }) { category ->
                            CategoryRow(
                                category = category,
                                currency = state.currency,
                                onEdit = {
                                    categoryEditor = CategoryEditorRequest(row.group.id, category)
                                },
                                onDelete = { deletingCategory = category },
                            )
                        }
                        item(key = "add-${row.group.id}") {
                            TextButton(
                                onClick = { categoryEditor = CategoryEditorRequest(row.group.id, null) },
                                modifier = Modifier.padding(start = 12.dp),
                            ) { Text("+ Add envelope") }
                        }
                        item(key = "sep-${row.group.id}") { HorizontalDivider() }
                    }
                }
            }
        }
    }

    if (groupDialogOpen) {
        NameDialog(
            title = if (editingGroup == null) "New group" else "Rename group",
            initial = editingGroup?.name ?: "",
            onDismiss = { groupDialogOpen = false },
            onSave = { name ->
                val group = editingGroup
                if (group == null) viewModel.addGroup(name) else viewModel.renameGroup(group.id, name)
                groupDialogOpen = false
            },
        )
    }

    categoryEditor?.let { request ->
        CategoryDialog(
            existing = request.category,
            currency = state.currency,
            onDismiss = { categoryEditor = null },
            onSave = { type, valid, hidden ->
                val existing = request.category
                if (existing == null) {
                    viewModel.addCategory(request.groupId, type, valid, hidden)
                } else {
                    viewModel.updateCategory(existing.id, valid, type, hidden)
                }
                categoryEditor = null
            },
        )
    }

    deletingCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(category.id)
                    deletingCategory = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) { Text("Cancel") }
            },
            title = { Text("Delete ${category.name}?") },
            text = {
                Text(
                    "Envelopes with history or money assigned cannot be deleted. If this one is refused, " +
                        "mark it hidden instead so it disappears from the budget but keeps its history.",
                )
            },
        )
    }

    deletingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { deletingGroup = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(group.id)
                    deletingGroup = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingGroup = null }) { Text("Cancel") }
            },
            title = { Text("Delete ${group.name}?") },
            text = {
                Text(
                    "Every envelope in the group must be empty and unused first; otherwise the delete is " +
                        "refused. Nothing is deleted silently.",
                )
            },
        )
    }
}

private data class CategoryEditorRequest(val groupId: Long, val category: Category?)

@Composable
private fun GroupHeaderRow(
    group: CategoryGroup,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            group.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRename) {
            Icon(Icons.Filled.Edit, contentDescription = "Rename ${group.name}")
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete ${group.name}",
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 28.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name + if (category.isHidden) "  (hidden)" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                targetSummary(category, currency),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete ${category.name}",
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun targetSummary(category: Category, currency: String): String {
    val amount = category.targetAmountCents ?: return "No target"
    val money = MoneyFormat.format(amount, currency)
    return when (category.targetType) {
        TargetType.NONE -> "No target"
        TargetType.MONTHLY_SAVINGS_BUILDER -> "$money a month"
        TargetType.SAVINGS_BALANCE -> "Refill up to $money"
        TargetType.NEEDED_FOR_SPENDING -> category.targetDate?.let {
            "Need $money by ${MonthKey.formatDayYear(it)}"
        } ?: "Need $money this month"
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun CategoryDialog(
    existing: Category?,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (TargetType, EnvelopeRules.Result.Valid, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var type by remember { mutableStateOf(existing?.targetType ?: TargetType.NONE) }
    var amount by remember {
        mutableStateOf(
            existing?.targetAmountCents?.let { MoneyFormat.format(it, "", false) } ?: "",
        )
    }
    var date by remember { mutableStateOf(existing?.targetDate ?: LocalDate.now()) }
    var byDate by remember { mutableStateOf(existing?.targetDate != null) }
    var hidden by remember { mutableStateOf(existing?.isHidden ?: false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val result = EnvelopeRules.validate(
                    name = name,
                    note = note,
                    type = type,
                    amountText = amount,
                    date = if (byDate) date else null,
                )
                when (result) {
                    is EnvelopeRules.Result.Invalid -> error = result.message
                    is EnvelopeRules.Result.Valid -> onSave(type, result, hidden)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(if (existing == null) "New envelope" else "Edit ${existing.name}") },
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
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Target", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TargetTypeChip("None", type == TargetType.NONE) { type = TargetType.NONE }
                    TargetTypeChip("Monthly", type == TargetType.MONTHLY_SAVINGS_BUILDER) {
                        type = TargetType.MONTHLY_SAVINGS_BUILDER
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TargetTypeChip("Refill to", type == TargetType.SAVINGS_BALANCE) {
                        type = TargetType.SAVINGS_BALANCE
                    }
                    TargetTypeChip("By date", type == TargetType.NEEDED_FOR_SPENDING) {
                        type = TargetType.NEEDED_FOR_SPENDING
                    }
                }
                if (type != TargetType.NONE) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = {
                            Text(
                                if (type == TargetType.MONTHLY_SAVINGS_BUILDER) {
                                    "Amount per month ($currency)"
                                } else {
                                    "Target amount ($currency)"
                                },
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (type == TargetType.NEEDED_FOR_SPENDING) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = byDate, onCheckedChange = { byDate = it })
                        Text("Spread over months until a date", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (byDate) {
                        DateField(label = "Target date", date = date, onDateChange = { date = it })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hidden, onCheckedChange = { hidden = it })
                    Text("Hide from the budget", style = MaterialTheme.typography.bodyMedium)
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
    )
}

@Composable
private fun TargetTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
