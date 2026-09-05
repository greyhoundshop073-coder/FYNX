package com.fynx.app.ui

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

    fun refresh() {
        scope.launch {
            loading = true; error = null
            val d = FynxBackendClient.get(context, "/api/advertising/dashboard")
            val c = FynxBackendClient.get(context, "/api/advertising/campaigns")
            d.onSuccess { dashboard = JSONObject(it).optJSONObject("dashboard") }
            c.onSuccess {
                val array = JSONObject(it).optJSONArray("campaigns")
                campaigns = (0 until (array?.length() ?: 0)).map { i -> array!!.getJSONObject(i) }
            }
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
        dashboard?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
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
            items(campaigns) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(c.optString("name"), style = MaterialTheme.typography.titleMedium)
                        Text("Status: " + c.optString("status"))
                        Text("Payment: " + c.optString("payment_status", "unpaid"))
                        Text("Budget: ₦" + c.optLong("total_budget_kobo") / 100.0)
                        Text("Spent: ₦" + c.optLong("spent_kobo") / 100.0)
                        Text("Views: " + c.optLong("impressions") + "  •  Clicks: " + c.optLong("clicks"))
                    }
                }
            }
        }
    }
}
