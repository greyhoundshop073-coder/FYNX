package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun FynxAdvertisingCampaignPanel(currentUsername: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by remember { mutableStateOf("") }
    var headline by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var creativeType by remember { mutableStateOf("product") }
    var location by remember { mutableStateOf("") }
    var interests by remember { mutableStateOf("") }
    var daily by remember { mutableStateOf(1000L) }
    var total by remember { mutableStateOf(5000L) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Create an advert", style = MaterialTheme.typography.headlineSmall)
        Text("Simple setup. You stay in control of who you reach and how much you spend.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Campaign name") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("product", "post", "business").forEach { type ->
                FilterChip(selected = creativeType == type, onClick = { creativeType = type },
                    label = { Text(type.replaceFirstChar { it.uppercase() }) },
                    leadingIcon = { Icon(if (type == "business") Icons.Default.Storefront else Icons.Default.Campaign, null) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(headline, { headline = it }, Modifier.fillMaxWidth(), label = { Text("Headline") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), label = { Text("Advert text") }, minLines = 3)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("Target locations (comma separated)") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(interests, { interests = it }, Modifier.fillMaxWidth(), label = { Text("Target interests (comma separated)") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        Text("Daily budget: ₦" + (daily / 100.0))
        Slider(value = daily.toFloat(), onValueChange = { daily = it.toLong().coerceAtLeast(100) }, valueRange = 100f..100000f)
        Text("Total budget: ₦" + (total / 100.0))
        Slider(value = total.toFloat(), onValueChange = { total = it.toLong().coerceAtLeast(daily) }, valueRange = 1000f..1000000f)
        Spacer(Modifier.height(8.dp))
        Button(enabled = !loading && name.isNotBlank() && headline.isNotBlank() && total >= daily, onClick = {
            loading = true; message = null
            val targeting = JSONObject().apply {
                put("locations", JSONArray(location.split(',').map { it.trim() }.filter { it.isNotBlank() }))
                put("interests", JSONArray(interests.split(',').map { it.trim() }.filter { it.isNotBlank() }))
            }
            val bodyJson = JSONObject().apply {
                put("name", name); put("creativeType", creativeType); put("headline", headline); put("body", body)
                put("targeting", targeting); put("dailyBudgetKobo", daily); put("totalBudgetKobo", total)
                put("idempotencyKey", "android-" + System.currentTimeMillis())
            }
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                val result = FynxBackendClient.postJson(context, "/api/advertising/campaigns", bodyJson.toString())
                loading = false
                message = result.fold({ "Advert saved for review." }, { it.message ?: "Unable to create advert." })
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "Saving…" else "Save advert for review") }
        message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
