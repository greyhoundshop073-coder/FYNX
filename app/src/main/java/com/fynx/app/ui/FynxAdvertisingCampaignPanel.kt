package com.fynx.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
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
    var selectedListingId by remember { mutableStateOf<String?>(null) }
    var listingOptions by remember { mutableStateOf<List<FynxMarketplaceClient.Listing>>(emptyList()) }
    var listingsLoading by remember { mutableStateOf(true) }
    var listingMenuExpanded by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf("") }
    var interests by remember { mutableStateOf("") }
    var daily by remember { mutableStateOf(1000L) }
    var total by remember { mutableStateOf(5000L) }
    var costPerClick by remember { mutableStateOf(500L) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var campaigns by remember { mutableStateOf(listOf<JSONObject>()) }
    var refreshing by remember { mutableStateOf(true) }
    val selectedListing = listingOptions.firstOrNull { it.id == selectedListingId }
    val eligibleListings = listingOptions.filter { it.active && it.quantity > 0 }

    fun refresh() {
        scope.launch {
            refreshing = true
            FynxAdvertisingClient.campaigns(context).onSuccess { campaigns = it }
                .onFailure { message = it.message ?: "Unable to load campaigns." }
            refreshing = false
        }
    }

    fun refreshListings() {
        scope.launch {
            listingsLoading = true
            FynxMarketplaceClient.myListings(context)
                .onSuccess { loaded ->
                    listingOptions = loaded
                    if (selectedListingId !in loaded.filter { it.active && it.quantity > 0 }.map { it.id }) {
                        selectedListingId = null
                    }
                    message = if (loaded.isEmpty()) "Create a Marketplace listing before advertising a product." else null
                }
                .onFailure { message = it.message ?: "Your Marketplace products could not be loaded." }
            listingsLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        refreshListings()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Create a Marketplace advert", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Choose one of your own active Marketplace products. FYNX keeps the advert linked to that exact listing, so buyers reach the correct product.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(name, { name = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Campaign name") }, singleLine = true)

        Spacer(Modifier.height(8.dp))
        Text("Advertised product", style = MaterialTheme.typography.titleSmall)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { listingMenuExpanded = true },
                enabled = !listingsLoading && eligibleListings.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selectedListing?.let { "${it.title} · ${it.currency} ${"%,.2f".format(java.util.Locale.US, it.price)}" }
                    ?: if (listingsLoading) "Loading your products…" else "Choose one of your products")
            }
            DropdownMenu(expanded = listingMenuExpanded, onDismissRequest = { listingMenuExpanded = false }) {
                eligibleListings.forEach { listing ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(listing.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${listing.currency} ${"%,.2f".format(java.util.Locale.US, listing.price)} · ${listing.quantity} in stock",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = { selectedListingId = listing.id; listingMenuExpanded = false; message = null }
                    )
                }
            }
        }
        if (listingsLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (eligibleListings.isEmpty()) {
            Text(
                "No active, in-stock products are available to advertise. Add or restock a product in My Shop, then refresh.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = ::refreshListings) { Text("Refresh products") }
        } else {
            TextButton(onClick = ::refreshListings, enabled = !loading) { Text("Refresh product list") }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(headline, { headline = it.take(180) }, Modifier.fillMaxWidth(), label = { Text("Headline") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(body, { body = it.take(5000) }, Modifier.fillMaxWidth(), label = { Text("Advert text") }, minLines = 3)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(location, { location = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text("Target locations (comma separated)") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(interests, { interests = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text("Target interests (comma separated)") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        Text("Cost per verified click: ₦" + (costPerClick / 100.0))
        Text("Only one payable click per buyer, per campaign, each Lagos day. Impressions are free.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = costPerClick.toFloat(),
            onValueChange = {
                costPerClick = it.toLong().coerceAtLeast(100)
                if (daily < costPerClick) daily = costPerClick
                if (total < daily) total = daily
            },
            valueRange = 100f..5000f
        )
        Text("Daily budget: ₦" + (daily / 100.0))
        Slider(value = daily.toFloat(), onValueChange = { daily = it.toLong().coerceAtLeast(costPerClick); if (total < daily) total = daily }, valueRange = 100f..100000f)
        Text("Total prepaid campaign budget: ₦" + (total / 100.0))
        Slider(value = total.toFloat(), onValueChange = { total = it.toLong().coerceAtLeast(maxOf(daily, costPerClick)) }, valueRange = 1000f..1000000f)
        Text("Campaign delivery stops at either budget limit. Payment is required only after campaign review and approval.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = !loading && !listingsLoading && selectedListing != null && name.isNotBlank() && headline.isNotBlank() &&
                costPerClick > 0 && daily >= costPerClick && total >= daily,
            onClick = {
                val creative = selectedListing ?: return@Button
                loading = true
                message = null
                val targeting = JSONObject().apply {
                    put("locations", JSONArray(location.split(',').map { it.trim() }.filter { it.isNotBlank() }))
                    put("interests", JSONArray(interests.split(',').map { it.trim() }.filter { it.isNotBlank() }))
                }
                val payload = JSONObject().apply {
                    put("name", name.trim())
                    put("creativeType", "product")
                    put("creativeRef", creative.id)
                    put("headline", headline.trim())
                    put("body", body.trim())
                    put("targeting", targeting)
                    put("dailyBudgetKobo", daily)
                    put("totalBudgetKobo", total)
                    put("costPerClickKobo", costPerClick)
                    put("idempotencyKey", "android-" + java.util.UUID.randomUUID().toString())
                }
                scope.launch {
                    FynxAdvertisingClient.createCampaign(context, payload)
                        .onSuccess { created ->
                            campaigns = listOf(created) + campaigns
                            name = ""
                            headline = ""
                            body = ""
                            selectedListingId = null
                            message = "Product advert saved as a draft. Submit it for review below."
                        }
                        .onFailure { message = it.message ?: "Unable to create advert." }
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Saving…" else "Create advert") }
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
                        Text("CPC: ₦" + campaign.optLong("cost_per_click_kobo", 500L) / 100.0 + " per verified click")
                        Text("Spent: ₦" + campaign.optLong("spent_kobo") / 100.0 + "  •  Today: ₦" + campaign.optLong("daily_spent_kobo") / 100.0 + " / ₦" + campaign.optLong("daily_budget_kobo") / 100.0)
                        Text("Prepaid budget: ₦" + campaign.optLong("total_budget_kobo") / 100.0 + "  •  Payment: $payment")
                        if (status == "draft" || status == "rejected") {
                            Button(onClick = {
                                scope.launch { FynxAdvertisingClient.submitReview(context, id).onSuccess { updated -> campaigns = campaigns.map { if (it.optLong("id") == id) updated else it }; message = "Campaign submitted for review." }.onFailure { message = it.message ?: "Unable to submit for review." } }
                            }) { Text("Submit for review") }
                        }
                        if (status == "paused" && payment != "paid") {
                            Button(onClick = {
                                scope.launch {
                                    FynxAdvertisingClient.initializePayment(context, id).onSuccess { paymentInit ->
                                        val url = paymentInit.authorizationUrl
                                        if (url.startsWith("https://", ignoreCase = true)) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            message = "Payment page opened. After payment, return to FYNX and verify the campaign from this card."
                                        } else {
                                            message = "The payment provider returned an invalid checkout link."
                                        }
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
