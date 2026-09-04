package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

@Composable
fun FynxMarketplaceSellerManager(context: Context, onChanged: () -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    var listings by remember { mutableStateOf<List<FynxRemoteSocialClient.MarketplaceListing>>(emptyList()) }
    var selected by remember { mutableStateOf<FynxRemoteSocialClient.MarketplaceListing?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        loading = true; message = null
        scope.launch {
            FynxRemoteSocialClient.myListings(context)
                .onSuccess { listings = it }
                .onFailure { message = it.message ?: "Listings could not load." }
            loading = false
        }
    }

    OutlinedButton(onClick = { open = true; load() }) { Text("My listings") }

    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("My listings") },
        text = {
            when {
                loading -> Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) { CircularProgressIndicator() }
                listings.isEmpty() -> Text(message ?: "You have no marketplace listings.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 430.dp)) {
                    items(listings, key = { it.id }) { listing ->
                        Card(onClick = { selected = listing }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(listing.title, style = MaterialTheme.typography.titleMedium)
                                Text("${listing.currency} ${String.format(Locale.US, "%,.2f", listing.price)} • ${listing.quantity} available")
                                Text(if (listing.active) "Active" else "Inactive", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { load(); onChanged() }) { Text("Refresh") } },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Close") } }
    )

    selected?.let { listing ->
        var title by remember(listing.id) { mutableStateOf(listing.title) }
        var price by remember(listing.id) { mutableStateOf(listing.price.toString()) }
        var quantity by remember(listing.id) { mutableStateOf(listing.quantity.toString()) }
        var busy by remember(listing.id) { mutableStateOf(false) }
        var error by remember(listing.id) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!busy) selected = null },
            title = { Text("Manage listing") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Product name") }, singleLine = true)
                    OutlinedTextField(price, { price = it }, label = { Text("Price") }, singleLine = true)
                    OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, singleLine = true)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    busy = true; error = null
                    scope.launch {
                        FynxRemoteSocialClient.updateMarketplaceListing(context, listing.id, title, price.toDoubleOrNull() ?: -1.0, quantity.toIntOrNull() ?: -1)
                            .onSuccess { selected = null; load(); onChanged() }
                            .onFailure { error = it.message ?: "Listing update failed." }
                        busy = false
                    }
                }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Save") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(enabled = !busy, onClick = {
                        busy = true; error = null
                        scope.launch {
                            FynxRemoteSocialClient.deleteMarketplaceListing(context, listing.id)
                                .onSuccess { selected = null; load(); onChanged() }
                                .onFailure { error = it.message ?: "Listing could not be removed." }
                            busy = false
                        }
                    }) { Text("Remove") }
                    TextButton(enabled = !busy, onClick = { selected = null }) { Text("Close") }
                }
            }
        )
    }
}
