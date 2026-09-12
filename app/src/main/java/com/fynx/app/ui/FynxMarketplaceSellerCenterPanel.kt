package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var payoutAccount by remember { mutableStateOf<JSONObject?>(null) }
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

    fun loadMoney() {
        scope.launch {
            moneyLoading = true
            moneyError = null
            FynxBackendClient.get(context, "/api/marketplace/seller/orders")
                .onSuccess { raw ->
                    val orders = JSONObject(raw).optJSONArray("orders")
                    val records = mutableListOf<SellerMoneyRecord>()
                    if (orders != null) {
                        for (i in 0 until orders.length()) {
                            val order = orders.optJSONObject(i) ?: continue
                            val orderId = order.optString("id")
                            if (orderId.isBlank()) continue
                            FynxBackendClient.get(context, "/api/marketplace/settlement/order/$orderId")
                                .onSuccess { settlementRaw ->
                                    val settlement = JSONObject(settlementRaw)
                                    val accounting = settlement.optJSONObject("accounting") ?: return@onSuccess
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
                        }
                    }
                    moneyRecords = records.sortedByDescending { it.updatedAt }
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
                .onSuccess { listings = it }
                .onFailure { error = it.message ?: "Could not load your listings." }
            accountLoading = true
            FynxBackendClient.get(context, "/api/marketplace/settlement/payout-account")
                .onSuccess { raw ->
                    payoutAccount = JSONObject(raw).optJSONObject("payoutAccount")
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
                    Text("Seller Center", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Your Marketplace sales, protected funds and payout controls use your real FYNX account data.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, "Refresh seller center") }
            }
        }

        item {
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

        item {
            Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Earnings history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (moneyLoading) CircularProgressIndicator(Modifier.size(20.dp))
                    else if (moneyRecords.isEmpty()) Text("Completed and protected Marketplace earnings will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        item {
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
                    FynxMarketplaceSellerOrders(context = context, onChanged = { refresh() })
                }
            }
        }

        if (accountMessage != null) item { Text(accountMessage!!, color = MaterialTheme.colorScheme.error) }
        if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        if (loading) {
            item { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
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
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(listing.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(formatSellerMoney(listing.price, listing.currency), color = MaterialTheme.colorScheme.primary)
                            Text("${listing.quantity} in stock • ${listing.category} • ${listing.condition}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (deletingId == listing.id) CircularProgressIndicator(Modifier.size(24.dp))
                        else IconButton(onClick = {
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
            }
        }
    }

    if (showPayoutAccount) {
        AlertDialog(
            onDismissRequest = { if (!accountSaving) showPayoutAccount = false },
            title = { Text("Payout account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("FYNX verifies the bank details with the payout provider before the account can receive protected Marketplace payouts.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(bankCode, { bankCode = it.filter(Char::isDigit) }, label = { Text("Bank code") }, singleLine = true)
                    OutlinedTextField(accountNumber, { accountNumber = it.filter(Char::isDigit) }, label = { Text("Account number") }, singleLine = true)
                    OutlinedTextField(accountName, { accountName = it }, label = { Text("Account name") }, singleLine = true)
                    accountMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(enabled = !accountSaving, onClick = {
                    accountSaving = true
                    accountMessage = null
                    scope.launch {
                        val body = JSONObject()
                            .put("bankCode", bankCode.trim())
                            .put("accountNumber", accountNumber.trim())
                            .put("accountName", accountName.trim())
                            .put("bankName", payoutAccount?.optString("bankName").orEmpty())
                            .toString()
                        FynxBackendClient.postJson(context, "/api/marketplace/settlement/payout-account", body)
                            .onSuccess { raw ->
                                payoutAccount = JSONObject(raw).optJSONObject("payoutAccount")
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
