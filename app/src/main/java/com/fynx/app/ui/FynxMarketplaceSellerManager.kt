package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FynxMarketplaceSellerManager(context: Context, onChanged: () -> Unit) {
    var listings by remember { mutableStateOf<List<FynxRemoteSocialClient.MarketplaceListing>>(emptyList()) }
    var selected by remember { mutableStateOf<FynxRemoteSocialClient.MarketplaceListing?>(null) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshInFlight by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun load() {
        if (refreshInFlight) return
        refreshInFlight = true
        loading = true
        error = null
        scope.launch {
            try {
                FynxRemoteSocialClient.myListings(context)
                    .onSuccess { listings = it }
                    .onFailure { error = it.message ?: "Unable to load your listings." }
            } finally {
                loading = false
                refreshInFlight = false
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My listings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(enabled = !refreshInFlight, onClick = { load() }) { Text("Refresh") }
        }
        LaunchedEffect(Unit) { load() }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listings, key = { it.id }) { listing ->
                Card(onClick = { selected = listing }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(listing.title, style = MaterialTheme.typography.titleSmall)
                            Text("${listing.currency} ${listing.price} • Qty ${listing.quantity}")
                        }
                        Text("Manage")
                    }
                }
            }
        }
    }

    selected?.let { listing ->
        var title by remember(listing.id) { mutableStateOf(listing.title) }
        var price by remember(listing.id) { mutableStateOf(listing.price.toString()) }
        var quantity by remember(listing.id) { mutableStateOf(listing.quantity.toString()) }
        var busy by remember(listing.id) { mutableStateOf(false) }
        var dialogError by remember(listing.id) { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!busy) selected = null },
            title = { Text("Manage listing") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Product name") }, singleLine = true)
                    OutlinedTextField(price, { price = it }, label = { Text("Price") }, singleLine = true)
                    OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, singleLine = true)
                    dialogError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    busy = true
                    dialogError = null
                    scope.launch {
                        updateMarketplaceListing(context, listing.id, title, price.toDoubleOrNull() ?: -1.0, quantity.toIntOrNull() ?: -1)
                            .onSuccess { selected = null; load(); onChanged() }
                            .onFailure { dialogError = it.message ?: "Listing update failed." }
                        busy = false
                    }
                }) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Save")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(enabled = !busy, onClick = { selected = null }) { Text("Cancel") }
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        dialogError = null
                        scope.launch {
                            FynxRemoteSocialClient.deleteMarketplaceListing(context, listing.id)
                                .onSuccess { selected = null; load(); onChanged() }
                                .onFailure { dialogError = it.message ?: "Listing removal failed." }
                            busy = false
                        }
                    }) { Text("Remove") }
                }
            }
        )
    }
}
