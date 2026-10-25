package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

private data class SellerMoneyRecord(
    val orderId: String,
    val title: String,
    val currency: String,
    val sellerNet: Double,
    val marketplaceFee: Double,
    val funds: String,
    val payoutStatus: String,
    val updatedAt: String
)

private data class SellerMoneyTotals(
    val protectedFunds: Double,
    val availableForPayout: Double,
    val payoutPending: Double,
    val paidOut: Double
)

private data class SellerMoneySnapshot(
    val records: List<SellerMoneyRecord>,
    val totals: Map<String, SellerMoneyTotals>?
)

/** Seller management surface connected to authenticated Marketplace settlement APIs. */
@Composable
fun FynxMarketplaceSellerCenterPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listings by remember { mutableStateOf(emptyList<FynxMarketplaceClient.Listing>()) }
    var archivedListings by remember { mutableStateOf(emptyList<FynxMarketplaceClient.Listing>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var republishingId by remember { mutableStateOf<String?>(null) }
    var listingActionError by remember { mutableStateOf<String?>(null) }
    var listingActionMessage by remember { mutableStateOf<String?>(null) }
    var editingListing by remember { mutableStateOf<FynxMarketplaceClient.Listing?>(null) }
    var editTitle by rememberSaveable { mutableStateOf("") }
    var editDescription by rememberSaveable { mutableStateOf("") }
    var editCategory by rememberSaveable { mutableStateOf("") }
    var editCondition by rememberSaveable { mutableStateOf("NEW") }
    var editLocation by rememberSaveable { mutableStateOf("") }
    var editPrice by rememberSaveable { mutableStateOf("") }
    var editQuantity by rememberSaveable { mutableStateOf("") }
    var editSaving by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }
    var payoutAccount by remember { mutableStateOf<JSONObject?>(null) }
    var payoutAccounts by remember { mutableStateOf(emptyList<JSONObject>()) }
    var payoutCurrency by rememberSaveable { mutableStateOf("NGN") }
    var accountLoading by remember { mutableStateOf(true) }
    var accountSaving by remember { mutableStateOf(false) }
    var accountMessage by remember { mutableStateOf<String?>(null) }
    var showPayoutAccount by remember { mutableStateOf(false) }
    var bankCode by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var moneyLoading by remember { mutableStateOf(true) }
    var moneyError by remember { mutableStateOf<String?>(null) }
    var moneyRecords by remember { mutableStateOf(emptyList<SellerMoneyRecord>()) }
    var moneyTotals by remember { mutableStateOf(emptyMap<String, SellerMoneyTotals>()) }
    var moneyTotalsFromBackend by remember { mutableStateOf(false) }
    var showDriverAssignments by remember { mutableStateOf(false) }
    var activeSection by rememberSaveable { mutableStateOf("Overview") }
    var shopName by rememberSaveable { mutableStateOf("") }
    var shopDescription by rememberSaveable { mutableStateOf("") }
    var shopLocation by rememberSaveable { mutableStateOf("") }
    var shopContactPhone by rememberSaveable { mutableStateOf("") }
    var shopLoading by remember { mutableStateOf(true) }
    var shopSaving by remember { mutableStateOf(false) }
    var shopError by remember { mutableStateOf<String?>(null) }
    var shopMessage by remember { mutableStateOf<String?>(null) }

    fun loadMoney() {
        scope.launch {
            moneyLoading = true
            moneyError = null
            val result = FynxBackendClient.get(context, "/api/marketplace/settlement/seller/summary")
                .mapCatching { raw ->
                    val root = JSONObject(raw)
                    val settlements = root.optJSONArray("settlements")
                        ?: throw IllegalStateException("Seller earnings response was incomplete.")
                    val totalsArray = root.optJSONArray("totals")
                    val totals = mutableMapOf<String, SellerMoneyTotals>()
                    if (totalsArray != null) {
                        for (i in 0 until totalsArray.length()) {
                            val row = totalsArray.optJSONObject(i) ?: continue
                            val currency = row.optString("currency", "NGN").uppercase()
                            totals[currency] = SellerMoneyTotals(
                                protectedFunds = row.optDouble("protectedFunds", 0.0),
                                availableForPayout = row.optDouble("availableForPayout", 0.0),
                                payoutPending = row.optDouble("payoutPending", 0.0),
                                paidOut = row.optDouble("paidOut", 0.0)
                            )
                        }
                    }
                    val records = mutableListOf<SellerMoneyRecord>()
                    for (i in 0 until settlements.length()) {
                        val row = settlements.optJSONObject(i) ?: continue
                        val orderId = row.optString("orderId")
                        if (orderId.isBlank()) continue
                        val funds = row.optString("funds").ifBlank { "UNKNOWN" }
                        val payoutStatus = row.optString("payoutStatus").ifBlank { funds }
                        records += SellerMoneyRecord(
                            orderId = orderId,
                            title = row.optString("title").ifBlank { "FYNX order" },
                            currency = row.optString("currency", "NGN").uppercase(),
                            sellerNet = row.optDouble("sellerNetAmount", 0.0),
                            marketplaceFee = row.optDouble("marketplaceFee", 0.0),
                            funds = funds,
                            payoutStatus = payoutStatus,
                            updatedAt = row.optString("updatedAt")
                        )
                    }
                    SellerMoneySnapshot(records, if (totalsArray != null) totals else null)
                }
            result.onSuccess { snapshot ->
                moneyRecords = snapshot.records.sortedByDescending { it.updatedAt }
                moneyTotals = snapshot.totals.orEmpty()
                moneyTotalsFromBackend = snapshot.totals != null
            }.onFailure { failure ->
                moneyError = failure.message ?: "Seller earnings could not load."
            }
            moneyLoading = false
        }
    }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            FynxMarketplaceClient.myListings(context)
                .onSuccess { inventory ->
                    listings = inventory.filter { listing -> listing.active }
                    archivedListings = inventory.filterNot { listing -> listing.active }
                    listingActionError = null
                    listingActionMessage = null
                }
                .onFailure { error = it.message ?: "Could not load your listings." }
            shopLoading = true
            shopError = null
            FynxBackendClient.get(context, "/api/marketplace/my-shop")
                .onSuccess { raw ->
                    val profile = JSONObject(raw).optJSONObject("shop") ?: JSONObject()
                    shopName = profile.optString("shopName")
                    shopDescription = profile.optString("description")
                    shopLocation = profile.optString("location")
                    shopContactPhone = profile.optString("contactPhone")
                }
                .onFailure { shopError = it.message ?: "Shop profile could not load." }
            shopLoading = false
            accountLoading = true
            FynxBackendClient.get(context, "/api/marketplace/settlement/payout-account")
                .onSuccess { raw ->
                    val payload = JSONObject(raw)
                    val parsed = mutableListOf<JSONObject>()
                    val accounts = payload.optJSONArray("payoutAccounts")
                    if (accounts != null) {
                        for (i in 0 until accounts.length()) accounts.optJSONObject(i)?.let { parsed += it }
                    }
                    if (parsed.isEmpty()) payload.optJSONObject("payoutAccount")?.let { parsed += it }
                    payoutAccounts = parsed
                    payoutAccount = parsed.firstOrNull { it.optString("currency").trim().uppercase() == payoutCurrency } ?: parsed.firstOrNull()
                    accountMessage = null
                }
                .onFailure { accountMessage = it.message ?: "Payout account status could not load." }
            accountLoading = false
            loading = false
            loadMoney()
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // Include currencies from all-history totals even if recent detail rows omit them.
    val currencies = (moneyRecords.map { it.currency } + moneyTotals.keys).distinct().sorted()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("My Shop", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Manage your storefront, listings, orders, deliveries and earnings from one place.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, "Refresh My Shop") }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Overview", "Shop profile", "Listings", "Orders", "Delivery & drivers", "Earnings & payouts").forEach { section ->
                    FilterChip(selected = activeSection == section, onClick = { activeSection = section }, label = { Text(section, maxLines = 1) })
                }
            }
        }
        if (activeSection == "Overview") {
            item {
                Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Your shop at a glance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(if (shopName.isBlank()) "Set up your shop identity so buyers can recognize your business." else shopName, style = MaterialTheme.typography.titleMedium)
                        Text("${listings.size} listing${if (listings.size == 1) "" else "s"} • ${moneyRecords.size} recent settlement record${if (moneyRecords.size == 1) "" else "s"} (latest 100)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { activeSection = "Shop profile" }, modifier = Modifier.weight(1f)) { Text("Shop profile") }
                            OutlinedButton(onClick = { activeSection = "Listings" }, modifier = Modifier.weight(1f)) { Text("Manage listings") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { activeSection = "Orders" }, modifier = Modifier.weight(1f)) { Text("Orders") }
                            OutlinedButton(onClick = { activeSection = "Delivery & drivers" }, modifier = Modifier.weight(1f)) { Text("Delivery & drivers") }
                        }
                        OutlinedButton(onClick = { activeSection = "Earnings & payouts" }, modifier = Modifier.fillMaxWidth()) { Text("Earnings & payouts") }
                        OutlinedButton(onClick = { showDriverAssignments = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Manage driver deliveries")
                        }
                        if (loading || shopLoading || moneyLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(Modifier.size(16.dp))
                                Text("Refreshing your shop information…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (activeSection == "Orders") {
            item {
                Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Orders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Review buyer orders, fulfillment progress, protected payment status, receipts and available next actions.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FynxMarketplaceSellerOrders(context = context, onChanged = { refresh() })
                    }
                }
            }
        }
        if (activeSection == "Delivery & drivers") {
            item {
                Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Delivery & drivers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Manage driver assignments and delivery coordination separately from order review and payout settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { showDriverAssignments = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Manage driver deliveries")
                        }
                    }
                }
            }
        }
        if (activeSection == "Shop profile") item {
            Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Shop identity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (shopLoading) CircularProgressIndicator(Modifier.size(18.dp))
                    }
                    Text("Set a shop name and details that stay with your seller account. A Business Account is not required.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    shopError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(value = shopName, onValueChange = { shopName = it.take(120); shopMessage = null; shopError = null }, label = { Text("Shop name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !shopSaving)
                    OutlinedTextField(value = shopDescription, onValueChange = { shopDescription = it.take(800); shopMessage = null; shopError = null }, label = { Text("About your shop (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, enabled = !shopSaving)
                    OutlinedTextField(value = shopLocation, onValueChange = { shopLocation = it.take(160); shopMessage = null; shopError = null }, label = { Text("Shop location (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !shopSaving)
                    OutlinedTextField(value = shopContactPhone, onValueChange = { shopContactPhone = it.take(40); shopMessage = null; shopError = null }, label = { Text("Public shop contact (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !shopSaving)
                    shopMessage?.let { Text(it, color = if (it == "Shop profile saved.") androidx.compose.ui.graphics.Color(0xFF16A34A) else MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = {
                            shopSaving = true
                            shopMessage = null
                            shopError = null
                            scope.launch {
                                val payload = JSONObject()
                                    .put("shopName", shopName.trim())
                                    .put("description", shopDescription.trim())
                                    .put("location", shopLocation.trim())
                                    .put("contactPhone", shopContactPhone.trim())
                                FynxBackendClient.patchJson(context, "/api/marketplace/my-shop", payload.toString())
                                    .onSuccess { raw ->
                                        val profile = JSONObject(raw).optJSONObject("shop") ?: JSONObject()
                                        shopName = profile.optString("shopName")
                                        shopDescription = profile.optString("description")
                                        shopLocation = profile.optString("location")
                                        shopContactPhone = profile.optString("contactPhone")
                                        shopMessage = "Shop profile saved."
                                        shopError = null
                                    }
                                    .onFailure { shopError = it.message ?: "Shop profile could not be saved." }
                                shopSaving = false
                            }
                        },
                        enabled = !shopLoading && !shopSaving && shopName.trim().length >= 2,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (shopSaving) CircularProgressIndicator(Modifier.size(18.dp))
                        else Text("Save shop details")
                    }
                }
            }
        }

        if (activeSection == "Earnings & payouts") item {
            Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Marketplace earnings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (moneyLoading) CircularProgressIndicator(Modifier.size(20.dp))
                    }
                    Text("FYNX keeps buyer payments protected until the order is completed. Only settlement records marked eligible can be released.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    moneyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!moneyLoading && moneyRecords.isEmpty() && moneyError == null) {
                        Text("No marketplace earnings yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    currencies.forEach { currency ->
                        val rows = moneyRecords.filter { it.currency == currency }
                        val serverTotals = moneyTotals[currency]
                        val protected = if (moneyTotalsFromBackend) serverTotals?.protectedFunds ?: 0.0
                            else rows.filter { it.funds in setOf("HELD", "DISPUTED", "REFUND_PENDING") }.sumOf { it.sellerNet }
                        val available = if (moneyTotalsFromBackend) serverTotals?.availableForPayout ?: 0.0
                            else rows.filter { it.funds == "RELEASE_ELIGIBLE" }.sumOf { it.sellerNet }
                        val pending = if (moneyTotalsFromBackend) serverTotals?.payoutPending ?: 0.0
                            else rows.filter { it.funds == "RELEASE_PENDING" || it.payoutStatus == "PENDING" }.sumOf { it.sellerNet }
                        val paid = if (moneyTotalsFromBackend) serverTotals?.paidOut ?: 0.0
                            else rows.filter { it.funds == "RELEASED" || it.payoutStatus == "SUCCEEDED" }.sumOf { it.sellerNet }
                        Text(currency, fontWeight = FontWeight.Bold)
                        MoneySummaryRow("Protected", protected, currency)
                        MoneySummaryRow("Available for payout", available, currency)
                        MoneySummaryRow("Payout pending", pending, currency)
                        MoneySummaryRow("Paid out", paid, currency)
                    }
                    if (!moneyLoading && moneyError == null && !moneyTotalsFromBackend) {
                        Text(
                            "This backend only provides the latest 100 settlements. Totals below may not include older orders.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (activeSection == "Earnings & payouts") item {
            Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recent earnings history (latest 100; showing 20)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    moneyError?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { loadMoney() }, enabled = !moneyLoading) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry earnings")
                        }
                    }
                    if (moneyLoading) CircularProgressIndicator(Modifier.size(20.dp))
                    else if (moneyRecords.isEmpty() && moneyError == null) Text("Completed and protected Marketplace earnings will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else moneyRecords.take(20).forEach { record ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(record.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text(formatSellerMoney(record.sellerNet, record.currency), color = MaterialTheme.colorScheme.primary)
                            }
                            Text("${record.funds} • payout ${record.payoutStatus} • fee ${formatSellerMoney(record.marketplaceFee, record.currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (record.updatedAt.isNotBlank()) Text(record.updatedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(Modifier.padding(vertical = 5.dp))
                        }
                    }
                }
            }
        }

        if (activeSection == "Earnings & payouts") item {
            Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalance, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Payout account", fontWeight = FontWeight.SemiBold)
                            if (accountLoading) Text("Checking verification…", style = MaterialTheme.typography.bodySmall)
                            else if (payoutAccount == null) Text("No verified payout account connected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            else Text(
                                "${payoutAccount?.optString("bankName").orEmpty().ifBlank { "Bank" }} •••• ${payoutAccount?.optString("accountLast4").orEmpty()} • ${if (payoutAccount?.optBoolean("verified") == true) "Verified" else "Not verified"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { showPayoutAccount = true }) { Text(if (payoutAccount == null) "Set up" else "Manage") }
                    }
                    Text("Seller payouts remain blocked until FYNX confirms buyer completion and the protected settlement is eligible.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (accountMessage != null && activeSection == "Earnings & payouts") item {
            val isAccountSuccess = accountMessage == "Payout account verified and connected."
            Text(accountMessage!!, color = if (isAccountSuccess) androidx.compose.ui.graphics.Color(0xFF16A34A) else MaterialTheme.colorScheme.error)
        }
        if (error != null && activeSection == "Listings") item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        if (activeSection == "Listings") {
        if (loading) {
            item { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (listings.isEmpty() && error != null) {
            item {
                OutlinedButton(onClick = { refresh() }, modifier = Modifier.fillMaxWidth(), enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Retry loading listings")
                }
            }
        } else if (listings.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(if (archivedListings.isEmpty()) "You have no active listings yet" else "No active listings right now", style = MaterialTheme.typography.titleMedium)
                        Text(if (archivedListings.isEmpty()) "Create a product from Marketplace to manage it here." else "Republish an archived listing below or create a new product.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            item { Text("Your listings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            items(listings, key = { it.id }) { listing ->
                Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (listing.mediaIds.isNotEmpty()) {
                                FynxRemoteMedia(
                                    mediaUrl = FynxMarketplaceClient.mediaUrl(context, listing.mediaIds.first()),
                                    type = FynxMarketplaceClient.mediaDisplayType(listing.mediaTypes.firstOrNull()),
                                    modifier = Modifier.size(88.dp)
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(88.dp),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Inventory2, contentDescription = "Product image unavailable", modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(listing.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(formatSellerMoney(listing.price, listing.currency), color = MaterialTheme.colorScheme.primary)
                                Text("${listing.quantity} total stock • ${listing.category} • ${listing.condition}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (deletingId == listing.id) CircularProgressIndicator(Modifier.size(24.dp))
                            else Row {
                                IconButton(onClick = {
                                    editingListing = listing
                                    editTitle = listing.title
                                    editDescription = listing.description
                                    editCategory = listing.category
                                    editCondition = listing.condition.uppercase(Locale.US)
                                    editLocation = listing.location
                                    editPrice = listing.price.toString()
                                    editQuantity = listing.quantity.toString()
                                    editError = null
                                }) { Icon(Icons.Default.Edit, "Edit listing") }
                                IconButton(onClick = {
                                    deletingId = listing.id
                                    scope.launch {
                                        FynxMarketplaceClient.deleteListing(context, listing.id)
                                            .onSuccess {
                                                val archived = listings.firstOrNull { it.id == listing.id }?.copy(active = false)
                                                listings = listings.filterNot { it.id == listing.id }
                                                if (archived != null) archivedListings = listOf(archived) + archivedListings.filterNot { it.id == listing.id }
                                                listingActionMessage = "${listing.title} was archived and is no longer visible to buyers."
                                                listingActionError = null
                                                error = null
                                            }
                                            .onFailure { listingActionError = it.message ?: "Listing could not be removed." }
                                        deletingId = null
                                    }
                                }) { Icon(Icons.Default.Delete, "Remove listing") }
                            }
                        }
                        FynxMarketplaceShippingSettingsCard(
                            context = context,
                            listingId = listing.id,
                            deliveryAvailable = listing.deliveryAvailable,
                            pickupAvailable = listing.pickupAvailable
                        )
                    }
                }
            }
        }
        }

        if (activeSection == "Listings" && !loading && archivedListings.isNotEmpty()) {
            item { Text("Archived listings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            items(archivedListings, key = { "archived-${it.id}" }) { listing ->
                Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (listing.mediaIds.isNotEmpty()) {
                                FynxRemoteMedia(
                                    mediaUrl = FynxMarketplaceClient.mediaUrl(context, listing.mediaIds.first()),
                                    type = FynxMarketplaceClient.mediaDisplayType(listing.mediaTypes.firstOrNull()),
                                    modifier = Modifier.size(72.dp)
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(72.dp),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Inventory2, contentDescription = "Product image unavailable", modifier = Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(listing.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(formatSellerMoney(listing.price, listing.currency), color = MaterialTheme.colorScheme.primary)
                                Text("${listing.quantity} total stock • ${listing.category} • ${listing.condition}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Archived · hidden from buyers", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                editingListing = listing
                                editTitle = listing.title
                                editDescription = listing.description
                                editCategory = listing.category
                                editCondition = listing.condition.uppercase(Locale.US)
                                editLocation = listing.location
                                editPrice = listing.price.toString()
                                editQuantity = listing.quantity.toString()
                                editError = null
                            }) { Icon(Icons.Default.Edit, "Edit archived listing") }
                        }
                        Text(
                            if (listing.quantity > 0) "This product stays hidden until you republish it." else "Add stock with Edit before republishing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                republishingId = listing.id
                                listingActionError = null
                                listingActionMessage = null
                                scope.launch {
                                    FynxMarketplaceClient.reactivateListing(context, listing.id)
                                        .onSuccess {
                                            archivedListings = archivedListings.filterNot { it.id == listing.id }
                                            listings = listOf(listing.copy(active = true)) + listings.filterNot { it.id == listing.id }
                                            listingActionMessage = "${listing.title} is live in Marketplace again."
                                        }
                                        .onFailure { listingActionError = it.message ?: "Listing could not be republished." }
                                    republishingId = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = listing.quantity > 0 && republishingId == null
                        ) {
                            if (republishingId == listing.id) CircularProgressIndicator(Modifier.size(18.dp))
                            else Text("Republish listing")
                        }
                    }
                }
            }
        }
        if (activeSection == "Listings" && listingActionMessage != null) item {
            Text(listingActionMessage!!, color = androidx.compose.ui.graphics.Color(0xFF16A34A))
        }
        if (activeSection == "Listings" && listingActionError != null) item {
            Text(listingActionError!!, color = MaterialTheme.colorScheme.error)
        }
    }

    if (editingListing != null) {
        val selectedListing = editingListing!!
        AlertDialog(
            onDismissRequest = { if (!editSaving) editingListing = null },
            title = { Text("Edit listing") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Update the product details, price and available stock. Existing orders, photos and delivery settings are preserved.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it.take(160) },
                        label = { Text("Product name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !editSaving
                    )
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it.take(4000) },
                        label = { Text("Product description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        enabled = !editSaving
                    )
                    OutlinedTextField(
                        value = editCategory,
                        onValueChange = { editCategory = it.take(40) },
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !editSaving
                    )
                    Text("Condition", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("NEW", "USED", "REFURBISHED").forEach { condition ->
                            FilterChip(
                                selected = editCondition == condition,
                                onClick = { editCondition = condition },
                                enabled = !editSaving,
                                label = { Text(condition.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }, maxLines = 1)
                                }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = editLocation,
                        onValueChange = { editLocation = it.take(160) },
                        label = { Text("Product location") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !editSaving
                    )
                    OutlinedTextField(
                        value = editPrice,
                        onValueChange = { editPrice = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Price (${selectedListing.currency})") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !editSaving
                    )
                    OutlinedTextField(
                        value = editQuantity,
                        onValueChange = { editQuantity = it.filter(Char::isDigit).take(9) },
                        label = { Text("Total stock (0 = out of stock)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !editSaving
                    )
                    Text("Stock cannot be reduced below items already reserved by active orders.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    editError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !editSaving &&
                        editTitle.trim().length >= 2 &&
                        editDescription.trim().length >= 5 &&
                        editCategory.trim().isNotBlank() &&
                        editCondition.trim().uppercase(Locale.US) in setOf("NEW", "USED", "REFURBISHED") &&
                        editLocation.trim().isNotBlank() &&
                        editPrice.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true &&
                        editQuantity.toIntOrNull()?.let { it >= 0 } == true,
                    onClick = {
                        val price = editPrice.toDoubleOrNull() ?: return@Button
                        val quantity = editQuantity.toIntOrNull() ?: return@Button
                        editSaving = true
                        editError = null
                        scope.launch {
                            FynxMarketplaceClient.updateListing(context, selectedListing.id, editTitle, editDescription, editCategory, editCondition, editLocation, price, quantity)
                                .onSuccess {
                                    listings = listings.map { current ->
                                        if (current.id == selectedListing.id) current.copy(
                                            title = editTitle.trim(),
                                            description = editDescription.trim(),
                                            category = editCategory.trim(),
                                            condition = editCondition.trim().uppercase(Locale.US),
                                            location = editLocation.trim(),
                                            price = price,
                                            quantity = quantity
                                        ) else current
                                    }
                                    archivedListings = archivedListings.map { current ->
                                        if (current.id == selectedListing.id) current.copy(
                                            title = editTitle.trim(),
                                            description = editDescription.trim(),
                                            category = editCategory.trim(),
                                            condition = editCondition.trim().uppercase(Locale.US),
                                            location = editLocation.trim(),
                                            price = price,
                                            quantity = quantity
                                        ) else current
                                    }
                                    editingListing = null
                                    error = null
                                }
                                .onFailure { editError = it.message ?: "Listing could not be updated." }
                            editSaving = false
                        }
                    }
                ) {
                    if (editSaving) CircularProgressIndicator(Modifier.size(18.dp))
                    else Text("Save changes")
                }
            },
            dismissButton = {
                TextButton(enabled = !editSaving, onClick = { editingListing = null }) { Text("Cancel") }
            }
        )
    }

    if (showPayoutAccount) {
        AlertDialog(
            onDismissRequest = { if (!accountSaving) showPayoutAccount = false },
            title = { Text("Payout account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("FYNX verifies the bank details with the payout provider before the account can receive protected Marketplace payouts.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("NGN", "USD").forEach { currency ->
                            FilterChip(selected = payoutCurrency == currency, onClick = { payoutCurrency = currency }, label = { Text(currency) })
                        }
                    }
                    OutlinedTextField(bankCode, { bankCode = it.filter(Char::isDigit) }, label = { Text("Bank code") }, singleLine = true)
                    OutlinedTextField(accountNumber, { accountNumber = it.filter(Char::isDigit) }, label = { Text("Account number") }, singleLine = true)
                    OutlinedTextField(accountName, { accountName = it }, label = { Text("Account name") }, singleLine = true)
                    accountMessage?.let { Text(it, color = if (it == "Payout account verified and connected.") androidx.compose.ui.graphics.Color(0xFF16A34A) else MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(enabled = !accountSaving, onClick = {
                    accountSaving = true
                    accountMessage = null
                    scope.launch {
                        val bodyJson = JSONObject()
                            .put("bankCode", bankCode.trim())
                            .put("accountNumber", accountNumber.trim())
                            .put("accountName", accountName.trim())
                            .put("currency", payoutCurrency)
                            .put("bankName", payoutAccount?.optString("bankName").orEmpty())
                        val body = bodyJson.toString()
                        FynxBackendClient.postJson(context, "/api/marketplace/settlement/payout-account", body)
                            .onSuccess { raw ->
                                val saved = JSONObject(raw).optJSONObject("payoutAccount")
                                if (saved != null) {
                                    val savedCurrency = saved.optString("currency").trim().uppercase()
                                    payoutAccounts = payoutAccounts.filterNot { it.optString("currency").trim().uppercase() == savedCurrency } + saved
                                    payoutAccount = saved
                                    payoutCurrency = savedCurrency
                                }
                                accountMessage = "Payout account verified and connected."
                                bankCode = ""; accountNumber = ""; accountName = ""
                            }
                            .onFailure { accountMessage = it.message ?: "Payout account could not be verified." }
                        accountSaving = false
                    }
                }) { if (accountSaving) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Verify & save") }
            },
            dismissButton = { TextButton(enabled = !accountSaving, onClick = { showPayoutAccount = false }) { Text("Close") } }
        )
    }
    if (showDriverAssignments) {
        FynxMarketplaceDriverAssignmentsDialog(context = context, onDismiss = { showDriverAssignments = false })
    }
}

@Composable
private fun MoneySummaryRow(label: String, amount: Double, currency: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(formatSellerMoney(amount, currency), fontWeight = FontWeight.SemiBold)
    }
}

private fun formatSellerMoney(price: Double, currency: String): String =
    "${currency.uppercase()} ${String.format(Locale.US, "%,.2f", price)}"
