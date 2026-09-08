package com.fynx.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun FynxAdvertisingDashboardPanel() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var dashboard by remember { mutableStateOf<JSONObject?>(null) }
    var campaigns by remember { mutableStateOf(listOf<JSONObject>()) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true; error = null
            val d = FynxAdvertisingClient.dashboard(context)
            val c = FynxAdvertisingClient.campaigns(context)
            d.onSuccess { dashboard = it }
            c.onSuccess { campaigns = it }
            if (d.isFailure || c.isFailure) error = "Unable to load advertising data."
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Advertising dashboard", style = MaterialTheme.typography.headlineSmall)
        Text("Track campaigns, spending and results in one place.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        dashboard?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Overview", style = MaterialTheme.typography.titleMedium)
                    Text("Campaigns: " + it.optInt("campaigns"))
                    Text("Active: " + it.optInt("active_campaigns"))
                    Text("Budget: ₦" + it.optLong("budget_kobo") / 100.0)
                    Text("Spent: ₦" + it.optLong("spent_kobo") / 100.0)
                    Text("Impressions: " + it.optLong("impressions"))
                    Text("Clicks: " + it.optLong("clicks"))
                    Text("Engagements: " + it.optLong("engagements"))
                    Text("Conversions: " + it.optLong("conversions"))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Campaigns", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(campaigns, key = { it.optLong("id") }) { c ->
                val id = c.optLong("id")
                val status = c.optString("status")
                val payment = c.optString("payment_status", "unpaid")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(c.optString("name"), style = MaterialTheme.typography.titleMedium)
                        Text("Status: $status")
                        Text("Payment: $payment")
                        Text("Budget: ₦" + c.optLong("total_budget_kobo") / 100.0)
                        Text("Impressions: " + c.optLong("impressions") + "  •  Clicks: " + c.optLong("clicks"))
                        Text("Engagements: " + c.optLong("engagements") + "  •  Conversions: " + c.optLong("conversions"))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (status == "draft" || status == "rejected") {
                                Button(onClick = { scope.launch { FynxAdvertisingClient.submitReview(context, id).onSuccess { updated -> campaigns = campaigns.map { if (it.optLong("id") == id) updated else it }; message = "Submitted for review." }.onFailure { message = it.message ?: "Unable to submit." } } }) { Text("Review") }
                            }
                            if (status == "paused" && payment != "paid") {
                                Button(onClick = { scope.launch { FynxAdvertisingClient.initializePayment(context, id).onSuccess { p -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.authorizationUrl))); message = "Payment page opened." }.onFailure { message = it.message ?: "Unable to initialize payment." } } }) { Text("Pay") }
                            }
                            if (status == "active") {
                                OutlinedButton(onClick = { scope.launch { FynxAdvertisingClient.setStatus(context, id, "paused").onSuccess { updated -> campaigns = campaigns.map { if (it.optLong("id") == id) updated else it }; message = "Campaign paused." }.onFailure { message = it.message ?: "Unable to pause campaign." } } }) { Text("Pause") }
                            }
                            if (status == "paused" && payment == "paid") {
                                OutlinedButton(onClick = { scope.launch { FynxAdvertisingClient.setStatus(context, id, "active").onSuccess { updated -> campaigns = campaigns.map { if (it.optLong("id") == id) updated else it }; message = "Campaign activated." }.onFailure { message = it.message ?: "Unable to activate campaign." } } }) { Text("Activate") }
                            }
                        }
                    }
                }
            }
        }
    }
}
