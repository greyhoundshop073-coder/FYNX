package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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

/** Seller management surface. Uses the authenticated Marketplace API; no fake inventory. */
@Composable
fun FynxMarketplaceSellerCenterPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listings by remember { mutableStateOf(emptyList<FynxMarketplaceClient.Listing>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            FynxMarketplaceClient.myListings(context)
                .onSuccess { listings = it; error = null }
                .onFailure { error = it.message ?: "Could not load your listings." }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Seller Center", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Manage your real FYNX Marketplace listings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (listings.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("You have no active listings yet", style = MaterialTheme.typography.titleMedium)
                    Text("Create a product from Marketplace to manage it here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
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
    }
}

private fun formatSellerMoney(price: Double, currency: String): String =
    "${currency.uppercase()} ${String.format(java.util.Locale.US, "%,.2f", price)}"
