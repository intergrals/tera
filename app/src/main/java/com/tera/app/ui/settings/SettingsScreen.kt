package com.tera.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tera.app.data.BackupRepository
import com.tera.app.data.BudgetRepository
import com.tera.app.data.DomainException
import com.tera.app.data.SettingsRepository
import com.tera.app.data.AppSettings
import com.tera.app.domain.OverspendMode
import com.tera.app.ui.LocalAppContainer
import com.tera.app.ui.components.vmFactory
import com.tera.app.ui.theme.MoneyTextStyle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val context: Context,
    private val backup: BackupRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settings.settings

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun setCurrency(symbol: String) {
        settings.setCurrencySymbol(symbol)
    }

    fun setMode(mode: OverspendMode) {
        settings.setOverspendMode(mode)
    }

    fun exportJson(uri: Uri) = launch {
        val text = backup.exportJson()
        write(uri, text)
        _messages.emit("Backup written")
    }

    fun exportCsv(uri: Uri) = launch {
        val csv = backup.exportCsv()
        write(uri, BackupRepository.UTF8_BOM + csv)
        _messages.emit("Transactions exported")
    }

    fun importJson(uri: Uri) = launch {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw DomainException("Could not read that file")
        val summary = backup.importJson(text)
        _messages.emit(
            "Restored ${summary.accounts} accounts, ${summary.categories} categories, " +
                "${summary.transactions} transactions",
        )
    }

    fun resetEverything() = launch {
        backup.clearEverything()
        _messages.emit("All data erased")
    }

    private fun write(uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: throw DomainException("Could not write that file")
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: DomainException) {
                _messages.emit(e.message ?: "Something went wrong")
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Unexpected error")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenDisclosure: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = vmFactory {
            SettingsViewModel(context.applicationContext, container.backup, container.settings)
        },
    )
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var currency by remember(settings.currencySymbol) { mutableStateOf(settings.currencySymbol) }
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportJson) }
    val importJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importJson) }
    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(viewModel::exportCsv) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Currency", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it.take(3) },
                    label = { Text("Symbol") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.padding(horizontal = 6.dp))
                TextButton(onClick = { viewModel.setCurrency(currency) }) { Text("Save") }
            }

            Spacer(Modifier.height(20.dp))
            Text("Overspending", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "How a negative envelope is resolved when the month rolls over.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.overspendMode == OverspendMode.YNAB,
                    onClick = { viewModel.setMode(OverspendMode.YNAB) },
                    label = { Text("Deduct from next month") },
                )
                FilterChip(
                    selected = settings.overspendMode == OverspendMode.CARRY_NEGATIVE,
                    onClick = { viewModel.setMode(OverspendMode.CARRY_NEGATIVE) },
                    label = { Text("Carry as red") },
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "The JSON backup includes accounts, envelopes, assignments, transactions, " +
                    "upcoming transactions and your settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { exportJson.launch("tera-backup.json") }) {
                Text("Export backup (JSON)")
            }
            TextButton(onClick = { importJson.launch(arrayOf("application/json", "*/*")) }) {
                Text("Restore from backup")
            }
            TextButton(onClick = { exportCsv.launch("tera-transactions.csv") }) {
                Text("Export transactions (CSV)")
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onOpenDisclosure) {
                Text("How TERA calculates things")
            }
            TextButton(onClick = { confirmReset = true }) {
                Text("Erase all data", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "TERA 1.0 \u2014 offline budgeting. No accounts, no network, no tracking.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetEverything()
                    confirmReset = false
                }) { Text("Erase everything") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            title = { Text("Erase all data?") },
            text = {
                Text(
                    "Every account, envelope and transaction is deleted from this device. " +
                        "This cannot be undone; export a backup first if you might want the data back.",
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisclosureScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How TERA calculates things") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Section("Age of Money")
            Body(
                "Money ages from the day it arrives until it leaves the budget as cash. " +
                    "Inflow lots are consumed oldest-first and each dollar is weighted by the days " +
                    "it stayed. The number shown is the weighted average over the last 10 cash " +
                    "outflows. Card purchases do not age money: the cash keeps ageing in the card's " +
                    "Payment category until you pay the card.",
            )
            Section("Days of Buffering")
            Body(
                "Positive balances across budget accounts (tracking accounts and negative card " +
                    "balances are excluded) divided by average daily spending over the last 90 days.",
            )
            Section("Credit cards")
            Body(
                "A purchase on a card moves money from the spending envelope into the card's Payment " +
                    "envelope, but only up to what the spending envelope actually holds. Anything " +
                    "unfunded becomes debt, and the Payment envelope shows yellow (underfunded) until " +
                    "you assign money to it. You never get free payable cash.",
            )
            Section("Interest charges")
            Body(
                "Interest behaves like any other categorized card charge: the Payment reserve moves " +
                    "automatically when the interest category is funded. If the interest category is " +
                    "empty, nothing is reserved and the card shows as underfunded until you assign to " +
                    "the Payment category directly.",
            )
            Section("Transfers")
            Body(
                "Transfers between two budget accounts do not touch Ready to Assign (paying a card " +
                    "releases its Payment reserve). Moving money to a tracking account counts as " +
                    "spending, and moving money from one counts as income. Transfers between two " +
                    "cards move debt without moving Payment reserve.",
            )
            Section("Not in this version")
            Body(
                "Split transactions, bank import, device sync, shared budgets, loan payoff " +
                    "calculators, widgets and reminders are not implemented. Nothing leaves your " +
                    "device; the app has no network permission at all.",
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
}
