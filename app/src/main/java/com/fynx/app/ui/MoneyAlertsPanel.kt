package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Safe money-alert settings. This does not schedule alarms or move money. */
@Composable
fun MoneyAlertsPanel() {
    var bills by remember { mutableStateOf(true) }
    var subscriptions by remember { mutableStateOf(true) }
    var savings by remember { mutableStateOf(true) }
    var budget by remember { mutableStateOf(true) }
    var spending by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Money Alerts 🔔", style = MaterialTheme.typography.headlineSmall)
        Text("Choose which financial reminders FYNX may show you.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        AlertToggle("Bills due", bills) { bills = it }
        AlertToggle("Subscription renewals", subscriptions) { subscriptions = it }
        AlertToggle("Savings goal progress", savings) { savings = it }
        AlertToggle("Budget limits", budget) { budget = it }
        AlertToggle("Spending warnings", spending) { spending = it }
        Text("Alerts are informational only. FYNX will not make payments automatically from these settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AlertToggle(label: String, enabled: Boolean, onChanged: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onChanged)
        }
    }
}
