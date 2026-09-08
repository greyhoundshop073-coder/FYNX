package com.fynx.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun FynxAdvertisingCampaignPanel(currentUsername: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
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
    var campaigns by remember { mutableStateOf(listOf<JSONObject>()) }
    var refreshing by remember { mutableStateOf(true) }

    fun refresh() {
        scope.launch {
            refreshing = true
            FynxAdvertisingClient.campaigns(context).onSuccess { campaigns = it }
                .onFailure { message = it.message ?: "Unable to load campaigns." }
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Create an advert", style = MaterialTheme.typography.headlineSmall)
        Text("Create, submit and pay for campaigns through the authenticated FYNX advertising service.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(name, { name = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Campaign name") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("product", "post", "business").forEach { type ->
                FilterChip(selected = creativeType == type, onClick = { creativeType = type }, label = { Text(type.replaceFirstChar { it.uppercase() }) }, leadingIcon = { Icon(if (type == "business") Icons.Default.Storefront else Icons.Default.Campaign, null) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(headline, { headline = it.take(180) }, Modifier.fillMaxWidth(), label = { Text("Headline") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(body, { body = it.take(5000) }, Modifier.fillMaxWidth(), label = { Text("Advert text") }, minLines = 3)
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
            val payload = JSONObject().apply {
                put("name", name.trim()); put("creativeType", creativeType); put("headline", headline.trim()); put("body", body.trim())
                put("targeting", targeting); put("dailyBudgetKobo", daily); put("totalBudgetKobo", total)
                put("idempotencyKey", "android-" + System.currentTimeMillis())
            }
            scope.launch {
                FynxAdvertisingClient.createCampaign(context, payload)
                    .onSuccess { created ->
                        campaigns = listOf(created) + campaigns
                        name = ""; headline = ""; body = ""
                        message = "Advert created as draft. Submit it for review below."
                    }.onFailure { message = it.message ?: "Unable to create advert." }
                loading = false
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "Saving…" else "Create advert") }
        message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(16.dp))
        Text("Your campaigns", style = MaterialTheme.typography.titleMedium)
        if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(campaigns, key = { it.optLong("id") }) { campaign ->
                val id = campaign.optLong("id")
                val status = campaign.optString("status")
                val payment = campaign.optString("payment_status", "unpaid")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(campaign.optString("name"), style = MaterialTheme.typography.titleMedium)
                        Text("Status: $status")
                        Text("Budget: ₦" + campaign.optLong("total_budget_kobo") / 100.0 + "  •  Payment: $payment")
                        if (status == "draft" || status == "rejected") {
                            Button(onClick = {
                                scope.launch { FynxAdvertisingClient.submitReview(context, id).onSuccess { updated -> campaigns = campaigns.map { if (it.optLong("id") == id) updated else it }; message = "Campaign submitted for review." }.onFailure { message = it.message ?: "Unable to submit for review." } }
                            }) { Text("Submit for review") }
                        }
                        if (status == "paused" && payment != "paid") {
                            Button(onClick = {
                                scope.launch {
                                    FynxAdvertisingClient.initializePayment(context, id).onSuccess { paymentInit ->
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paymentInit.authorizationUrl)))
                                        message = "Payment page opened. After payment, return to FYNX and verify the campaign from this card."
                                    }.onFailure { message = it.message ?: "Unable to initialize payment." }
                                }
                            }) { Text("Pay campaign budget") }
                        }
                        if (payment == "pending") {
                            OutlinedButton(onClick = {
                                val reference = campaign.optString("payment_reference")
                                scope.launch { FynxAdvertisingClient.verifyPayment(context, id, reference).onSuccess { updated -> campaigns = campaigns.map { if (it.optLong("id") == id) updated else it }; message = "Payment verification completed." }.onFailure { message = it.message ?: "Payment is not verified yet." } }
                            }) { Text("Verify payment") }
                        }
                    }
                }
            }
        }
    }
}
