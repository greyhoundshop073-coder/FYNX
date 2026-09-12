package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Seller management surface connected to the authenticated Marketplace settlement APIs. */
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
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text("Seller Center", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Your Marketplace sales, protected funds and payout controls use your real FYNX account data.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        val body = JSONObject().put("bankCode", bankCode.trim()).put("accountNumber", accountNumber.trim()).put("accountName", accountName.trim()).put("bankName", payoutAccount?.optString("bankName").orEmpty()).toString()
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

private fun formatSellerMoney(price: Double, currency: String): String =
    "${currency.uppercase()} ${String.format(java.util.Locale.US, "%,.2f", price)}"
