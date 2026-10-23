package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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

/** Seller management surface connected to authenticated Marketplace settlement APIs. */
@Composable
fun FynxMarketplaceSellerCenterPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listings by remember { mutableStateOf(emptyList<FynxMarketplaceClient.Listing>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var editingListing by remember { mutableStateOf<FynxMarketplaceClient.Listing?>(null) }
    var editTitle by rememberSaveable { mutableStateOf("") }
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
            FynxBackendClient.get(context, "/api/marketplace/seller/orders")
                .onSuccess { raw ->
                    val orders = JSONObject(raw).optJSONArray("orders")
                    val records = mutableListOf<SellerMoneyRecord>()
                    var settlementFailures = 0
                    if (orders != null) {
                        for (i in 0 until orders.length()) {
                            val order = orders.optJSONObject(i) ?: continue
                            val orderId = order.optString("id")
                            if (orderId.isBlank()) continue
                            FynxBackendClient.get(context, "/api/marketplace/settlement/order/$orderId")
                                .onSuccess { settlementRaw ->
                                    val settlement = JSONObject(settlementRaw)
                                    val accounting = settlement.optJSONObject("accounting") ?: run {
                                        settlementFailures += 1
                                        return@onSuccess
                                    }
                                    val protection = settlement.optJSONObject("protection")
                                    val escrow = settlement.optJSONObject("escrow")
                                    val funds = protection?.optString("funds").orEmpty().ifBlank { escrow?.optString("status").orEmpty() }.ifBlank { "UNKNOWN" }
                                    val operations = settlement.optJSONArray("operations")
                                    var payoutStatus = funds
                                    var updatedAt = order.optString("updatedAt").ifBlank { order.optString("createdAt") }
                                    if (operations != null) {
                                        for (j in 0 until operations.length()) {
                                            val op = operations.optJSONObject(j) ?: continue
                                            if (op.optString("operation_type") == "PAYOUT_RELEASE") {
                                                payoutStatus = op.optString("status").ifBlank { funds }
                                                updatedAt = op.optString("updated_at").ifBlank { updatedAt }
                                                break
                                            }
                                        }
                                    }
                                    records += SellerMoneyRecord(
                                        orderId = orderId,
                                        title = order.optJSONObject("product")?.optString("title").orEmpty().ifBlank { "FYNX order" },
                                        currency = accounting.optString("currency", order.optString("currency", "NGN")).uppercase(),
                                        sellerNet = accounting.optDouble("sellerNetAmount", 0.0),
                                        marketplaceFee = accounting.optDouble("marketplaceFee", 0.0),
                                        funds = funds,
                                        payoutStatus = payoutStatus,
                                        updatedAt = updatedAt
                                    )
                                }
                                .onFailure { settlementFailures += 1 }
                        }
                    }
                    moneyRecords = records.sortedByDescending { it.updatedAt }
                    if (settlementFailures > 0) {
                        moneyError = "Some settlement records could not load. Refresh My Shop to retry."
                    }
                }
                .onFailure { moneyError = it.message ?: "Seller earnings could not load." }
            moneyLoading = false
        }
    }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            FynxMarketplaceClient.myListings(context)
                .onSuccess { listings = it.filter { listing -> listing.active } }
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

    val currencies = moneyRecords.map { it.currency }.distinct()

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
                        Text("${listings.size} listing${if (listings.size == 1) "" else "s"} • ${moneyRecords.size} order settlement record${if (moneyRecords.size == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        val protected = rows.filter { it.funds in setOf("HELD", "DISPUTED", "REFUND_PENDING") }.sumOf { it.sellerNet }
                        val available = rows.filter { it.funds == "RELEASE_ELIGIBLE" }.sumOf { it.sellerNet }
                        val pending = rows.filter { it.funds == "RELEASE_PENDING" || it.payoutStatus == "PENDING" }.sumOf { it.sellerNet }
                        val paid = rows.filter { it.funds == "RELEASED" || it.payoutStatus == "SUCCEEDED" }.sumOf { it.sellerNet }
                        Text(currency, fontWeight = FontWeight.Bold)
                        MoneySummaryRow("Protected", protected, currency)
                        MoneySummaryRow("Available for payout", available, currency)
                        MoneySummaryRow("Payout pending", pending, currency)
                        MoneySummaryRow("Paid out", paid, currency)
                    }
                }
            }
        }

        if (activeSection == "Earnings & payouts") item {
            Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Earnings history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
                        Text("You have no active listings yet", style = MaterialTheme.typography.titleMedium)
                        Text("Create a product from Marketplace to manage it here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    type = "auto",
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
                                Text("${listing.quantity} in stock • ${listing.category} • ${listing.condition}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (deletingId == listing.id) CircularProgressIndicator(Modifier.size(24.dp))
                            else Row {
                                IconButton(onClick = {
                                    editingListing = listing
                                    editTitle = listing.title
                                    editPrice = listing.price.toString()
                                    editQuantity = listing.quantity.toString()
                                    editError = null
                                }) { Icon(Icons.Default.Edit, "Edit listing") }
                                IconButton(onClick = {
                                    deletingId = listing.id
                                    scope.launch {
                                        FynxMarketplaceClient.deleteListing(context, listing.id)
                                            .onSuccess { listings = listings.filterNot { it.id == listing.id } }
                                            .onFailure { error = it.message ?: "Listing could not be removed." }
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
    }

    if (editingListing != null) {
        val selectedListing = editingListing!!
        AlertDialog(
            onDismissRequest = { if (!editSaving) editingListing = null },
            title = { Text("Edit listing") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Update the product name, price and available stock. Existing orders and delivery settings are preserved.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it.take(160) },
                        label = { Text("Product name") },
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
                        label = { Text("Available stock (0 = out of stock)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !editSaving
                    )
                    editError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !editSaving &&
                        editTitle.trim().length >= 2 &&
                        editPrice.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true &&
                        editQuantity.toIntOrNull()?.let { it >= 0 } == true,
                    onClick = {
                        val price = editPrice.toDoubleOrNull() ?: return@Button
                        val quantity = editQuantity.toIntOrNull() ?: return@Button
                        editSaving = true
                        editError = null
                        scope.launch {
                            FynxMarketplaceClient.updateListing(context, selectedListing.id, editTitle, price, quantity)
                                .onSuccess {
                                    listings = listings.map { current ->
                                        if (current.id == selectedListing.id) current.copy(
                                            title = editTitle.trim(),
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
