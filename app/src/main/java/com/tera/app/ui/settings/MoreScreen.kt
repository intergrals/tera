package com.tera.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenUpcoming: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDisclosure: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("More") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text("Upcoming transactions") },
                supportingContent = { Text("Recurring bills and income") },
                leadingContent = { Icon(Icons.Filled.Event, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenUpcoming),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Settings") },
                supportingContent = { Text("Currency, overspending, backup, export") },
                leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenSettings),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("How TERA calculates things") },
                supportingContent = { Text("Definitions and known limits") },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onOpenDisclosure),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "TERA is an offline envelope-budgeting app. Every dollar you own lives in an " +
                        "envelope (a category), and credit cards reserve cash in a Payment envelope as " +
                        "you spend.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Version 1.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
