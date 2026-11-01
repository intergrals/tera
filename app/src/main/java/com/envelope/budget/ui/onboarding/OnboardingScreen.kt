package com.envelope.budget.ui.onboarding

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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.envelope.budget.data.BudgetRepository
import com.envelope.budget.data.DomainException
import com.envelope.budget.data.SettingsRepository
import com.envelope.budget.domain.AccountType
import com.envelope.budget.ui.LocalAppContainer
import com.envelope.budget.ui.components.vmFactory
import com.envelope.budget.util.MoneyFormat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class OnboardingViewModel(
    private val repo: BudgetRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun finish(
        currency: String,
        accountName: String,
        type: AccountType,
        startingBalance: String,
        seedTemplate: Boolean,
    ) = viewModelScope.launch {
        try {
            if (currency.isNotBlank()) settings.setCurrencySymbol(currency)
            repo.ensureSystemCategories()
            if (seedTemplate) repo.seedStarterData()
            if (accountName.isNotBlank()) {
                repo.createAccount(
                    name = accountName,
                    type = type,
                    startingBalanceCents = MoneyFormat.parse(startingBalance) ?: 0L,
                    startingDate = LocalDate.now(),
                )
            }
            settings.setOnboardingDone(true)
        } catch (e: DomainException) {
            _messages.emit(e.message ?: "Could not finish setup")
        }
    }

    fun skip() = viewModelScope.launch {
        repo.ensureSystemCategories()
        settings.setOnboardingDone(true)
    }
}

@Composable
fun OnboardingScreen() {
    val container = LocalAppContainer.current
    val viewModel: OnboardingViewModel = viewModel(
        factory = vmFactory { OnboardingViewModel(container.budget, container.settings) },
    )
    val snackbar = remember { SnackbarHostState() }
    var currency by remember { mutableStateOf(container.settings.current().currencySymbol) }
    var accountName by remember { mutableStateOf("Checking") }
    var startingBalance by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.CHECKING) }
    var seedTemplate by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(Modifier.height(32.dp))
            Text("Envelope", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Give every dollar a job. Your data stays on this device \u2014 no accounts, no sync, " +
                    "no network.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            Text("Currency symbol", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it.take(3) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            Text("First account", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = accountName,
                onValueChange = { accountName = it },
                label = { Text("Name") },
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
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = startingBalance,
                onValueChange = { startingBalance = it },
                label = { Text("Starting balance ($currency)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "For a credit card, enter the amount you currently owe as a positive number.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = seedTemplate, onCheckedChange = { seedTemplate = it })
                Text("Add starter envelopes (rent, groceries, true expenses\u2026)")
            }
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val balance = if (type == AccountType.CREDIT) {
                        (MoneyFormat.parse(startingBalance) ?: 0L).let { -kotlin.math.abs(it) }
                    } else {
                        MoneyFormat.parse(startingBalance) ?: 0L
                    }
                    viewModel.finish(
                        currency = currency,
                        accountName = accountName,
                        type = type,
                        startingBalance = MoneyFormat.format(balance, "", false),
                        seedTemplate = seedTemplate,
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Start budgeting") }

            TextButton(onClick = viewModel::skip, modifier = Modifier.fillMaxWidth()) {
                Text("Skip setup")
            }
        }
    }
}
