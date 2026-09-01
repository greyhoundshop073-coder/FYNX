package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Safe money-alert settings. These preferences do not schedule payments or move money. */
@Composable
fun MoneyAlertsPanel() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("fynx_money_alerts", Context.MODE_PRIVATE) }
    var bills by remember { mutableStateOf(prefs.getBoolean("bills", true)) }
    var subscriptions by remember { mutableStateOf(prefs.getBoolean("subscriptions", true)) }
    var savings by remember { mutableStateOf(prefs.getBoolean("savings", true)) }
    var budget by remember { mutableStateOf(prefs.getBoolean("budget", true)) }
    var spending by remember { mutableStateOf(prefs.getBoolean("spending", false)) }

    fun save(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Notifications, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text("Money Alerts", style = MaterialTheme.typography.headlineSmall)
                Text("Choose which financial reminders FYNX may show you.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AlertToggle("Bills due", bills) { bills = it; save("bills", it) }
        AlertToggle("Subscription renewals", subscriptions) { subscriptions = it; save("subscriptions", it) }
        AlertToggle("Savings goal progress", savings) { savings = it; save("savings", it) }
        AlertToggle("Budget limits", budget) { budget = it; save("budget", it) }
        AlertToggle("Spending warnings", spending) { spending = it; save("spending", it) }
        Card(Modifier.fillMaxWidth()) {
            Text("Alert preferences are saved on this device. FYNX will not make payments automatically from these settings.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlertToggle(label: String, enabled: Boolean, onChanged: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onChanged)
        }
    }
}
