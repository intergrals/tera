package com.envelope.budget.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.envelope.budget.domain.Account
import com.envelope.budget.domain.Category
import com.envelope.budget.domain.CategoryGroup
import com.envelope.budget.domain.MonthKey
import java.time.LocalDate

@Composable
fun MonthNavigator(
    month: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            text = MonthKey.label(month),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable(enabled = !isCurrent, onClick = onToday).padding(horizontal = 8.dp),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

/** Category chooser. A `null` selection means "Ready to Assign" (income / unfunded spend). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    groups: List<CategoryGroup>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit,
    title: String = "Category",
    includeReadyToAssign: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val visible = categories.filter { !it.isSystem && !it.isHidden }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
            if (includeReadyToAssign) {
                item {
                    ListItem(
                        headlineContent = { Text("Ready to Assign") },
                        supportingContent = { Text("Income, or spending not tied to an envelope") },
                        modifier = Modifier.clickable { onPick(null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                item { HorizontalDivider() }
            }
            groups.filter { !it.isSystem && !it.isHidden }.forEach { group ->
                item(key = "header-${group.id}") {
                    SectionHeader(group.name)
                }
                items(visible.filter { it.groupId == group.id }, key = { it.id }) { category ->
                    ListItem(
                        headlineContent = { Text(category.name) },
                        supportingContent = if (category.note.isBlank()) null else {
                            { Text(category.note, maxLines = 1) }
                        },
                        modifier = Modifier.clickable { onPick(category.id) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerSheet(
    accounts: List<Account>,
    selectedId: Long?,
    title: String = "Account",
    excludeId: Long? = null,
    onDismiss: () -> Unit,
    onPick: (Account) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
            items(accounts.filter { it.id != excludeId }, key = { it.id }) { account ->
                ListItem(
                    headlineContent = { Text(account.name) },
                    supportingContent = {
                        Text(
                            when {
                                account.isTracking -> "Tracking"
                                account.type == com.envelope.budget.domain.AccountType.CREDIT -> "Credit card"
                                else -> "Cash"
                            },
                        )
                    },
                    trailingContent = if (account.id == selectedId) {
                        { Text("Selected", style = MaterialTheme.typography.labelSmall) }
                    } else {
                        null
                    },
                    modifier = Modifier.clickable { onPick(account) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().clickable { showPicker = true }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = MonthKey.formatDayYear(date),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDateChange(LocalDate.ofEpochDay(millis / 86_400_000L))
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun TextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
    )
}
