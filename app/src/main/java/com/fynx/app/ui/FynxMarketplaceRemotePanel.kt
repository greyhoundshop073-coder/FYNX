package com.fynx.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun FynxMarketplaceRemotePanel(currentUsername: String = "preview", onOpenProfile: (String) -> Unit = {}, onOpenChat: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories = listOf("All", "Electronics", "Fashion", "Home", "Beauty", "Vehicles", "Services")
    var listings by remember { mutableStateOf(emptyList<FynxMarketplaceClient.Listing>()) }
    var mine by remember { mutableStateOf(emptyList<FynxMarketplaceClient.Listing>()) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var loading by remember { mutableStateOf(true) }
    var showSell by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<FynxMarketplaceClient.Listing?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            val result = FynxMarketplaceClient.listings(context, query, category)
            listings = result.getOrElse { emptyList() }
            mine = FynxMarketplaceClient.myListings(context).getOrElse { mine }
            message = result.exceptionOrNull()?.message
            loading = false
        }
    }

    LaunchedEffect(query, category) { refresh() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Marketplace", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Discover real listings from FYNX accounts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { showSell = true }, shape = FynxDesign.ControlShape) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sell")
            }
        }
        OutlinedTextField(query, { query = it.take(80) }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search products or sellers") }, shape = FynxDesign.ControlShape)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            categories.forEach { item -> FilterChip(category == item, { category = it }, label = { Text(item) }) }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp)) }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (listings.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Storefront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(10.dp))
                Text("No listings yet", style = MaterialTheme.typography.titleLarge)
                Text("Only real seller listings are shown here. Publish a product to make it discoverable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { showSell = true }) { Text("List a product") }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(listings, key = { it.id }) { listing ->
                    RemoteMarketCard(listing, context, onOpenProfile = { onOpenProfile(listing.sellerUsername) }, onOpen = { selected = listing }, onContact = { onOpenChat(listing.sellerUsername) })
                }
                if (mine.isNotEmpty()) item {
                    Text("Your active listings: ${mine.count { it.active }}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showSell) {
        FynxMarketplaceSellerDialog(currentUsername, onDismiss = { showSell = false }) { title, description, store, price, currency, cat, condition, quantity, location, delivery, pickup, fee, mediaUri ->
            scope.launch {
                val mime = context.contentResolver.getType(mediaUri).orEmpty().lowercase()
                if (!mime.startsWith("image/") && !mime.startsWith("video/")) {
                    message = "Please choose a product photo or video."
                    return@launch
                }
                val media = FynxProductionMessaging.uploadMedia(context, mediaUri, mime).getOrElse { error ->
                    message = error.message ?: "Media upload failed."
                    return@launch
                }
                FynxMarketplaceClient.createListing(context, title, description, store, price, currency, cat, condition, quantity, location, delivery, pickup, fee, listOf(media.id))
                    .onSuccess {
                        showSell = false
                        message = "Product published to Marketplace."
                        refresh()
                    }
                    .onFailure { message = it.message ?: "Listing could not be published." }
            }
        }
    }

    selected?.let { listing ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(listing.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatMoney(listing.price, listing.currency), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    if (listing.description.isNotBlank()) Text(listing.description)
                    Text("Seller: ${listing.sellerDisplayName.ifBlank { listing.sellerUsername }}")
                    if (listing.storeName.isNotBlank()) Text("Store: ${listing.storeName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (listing.location.isNotBlank()) Text("Location: ${listing.location}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (listing.deliveryAvailable) "Delivery available" else if (listing.pickupAvailable) "Pickup available" else "Contact seller for fulfillment", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { onOpenChat(listing.sellerUsername); selected = null }) {
                    Icon(Icons.Default.ChatBubbleOutline, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Contact seller")
                }
            },
            dismissButton = {
                TextButton(onClick = { onOpenProfile(listing.sellerUsername); selected = null }) { Text("View seller") }
            }
        )
    }
}

@Composable
private fun RemoteMarketCard(
    listing: FynxMarketplaceClient.Listing,
    context: android.content.Context,
    onOpenProfile: () -> Unit,
    onOpen: () -> Unit,
    onContact: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = FynxDesign.LargeCardShape,
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenProfile, Modifier.size(46.dp)) {
                    FynxAvatar(listing.sellerDisplayName.ifBlank { listing.sellerUsername }, Modifier.size(40.dp).clip(RoundedCornerShape(50)))
                }
                Column(Modifier.weight(1f)) {
                    Text(listing.sellerDisplayName.ifBlank { listing.sellerUsername.removePrefix("@") }, fontWeight = FontWeight.SemiBold)
                    Text("@${listing.sellerUsername.removePrefix("@")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onOpen) { Icon(Icons.Default.MoreHoriz, "Details") }
            }
            val mediaId = listing.mediaIds.firstOrNull()
            if (mediaId != null) {
                RemoteMarketMedia(context, mediaId)
            } else {
                Box(Modifier.fillMaxWidth().height(250.dp).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
                    Icon(Icons.Default.ShoppingBag, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
                }
            }
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(listing.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(formatMoney(listing.price, listing.currency), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (listing.description.isNotBlank()) Text(listing.description, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = onOpen, label = { Text(listing.category) })
                    Text("${listing.quantity} available", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = onContact) { Icon(Icons.Default.ChatBubbleOutline, null); Spacer(Modifier.width(3.dp)); Text("Contact") }
                    TextButton(onClick = onOpenProfile) { Icon(Icons.Default.Person, null); Spacer(Modifier.width(3.dp)); Text("Seller") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onOpen) { Icon(Icons.Default.ArrowForward, "Open") }
                }
            }
        }
    }
}

@Composable
private fun RemoteMarketMedia(context: android.content.Context, mediaId: String) {
    var bitmap by remember(mediaId) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(mediaId) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(FynxMarketplaceClient.mediaUrl(context, mediaId)).openConnection() as HttpURLConnection)
                connection.setRequestProperty("Authorization", "Bearer ${FynxBackendClient.accessToken(context).orEmpty()}")
                connection.connectTimeout = 10_000
                connection.readTimeout = 20_000
                try {
                    if (connection.responseCode in 200..299) connection.inputStream.use { BitmapFactory.decodeStream(it) } else null
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), "Product photo", Modifier.fillMaxWidth().heightIn(min = 250.dp, max = 480.dp), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.fillMaxWidth().height(250.dp).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
private fun FynxMarketplaceSellerDialog(
    currentUsername: String,
    onDismiss: () -> Unit,
    onPublish: (String, String, String, Double, String, String, String, Int, String, Boolean, Boolean, Double?, Uri) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var store by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Electronics") }
    var condition by remember { mutableStateOf("NEW") }
    var quantity by remember { mutableStateOf("1") }
    var location by remember { mutableStateOf("") }
    var delivery by remember { mutableStateOf(false) }
    var pickup by remember { mutableStateOf(true) }
    var fee by remember { mutableStateOf("") }
    var media by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> media = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sell on FYNX") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Seller: @${currentUsername.removePrefix("@")}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AddAPhoto, null)
                    Spacer(Modifier.width(5.dp))
                    Text(if (media == null) "Add product photo" else "Change product photo")
                }
                OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Product name") }, singleLine = true)
                OutlinedTextField(price, { price = it.take(20) }, Modifier.fillMaxWidth(), label = { Text("Price") }, singleLine = true)
                OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text("Quantity") }, singleLine = true)
                OutlinedTextField(description, { description = it.take(4000) }, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 2)
                OutlinedTextField(store, { store = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Store name (optional)") }, singleLine = true)
                OutlinedTextField(location, { location = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("Location (optional)") }, singleLine = true)
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Electronics", "Fashion", "Home", "Beauty", "Vehicles", "Services").forEach { item -> FilterChip(category == item, { category = item }, label = { Text(item) }) }
                }
                Text("Condition", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("NEW", "USED", "REFURBISHED").forEach { item -> FilterChip(condition == item, { condition = item }, label = { Text(item) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(delivery, { delivery = it }); Text("Delivery")
                    Spacer(Modifier.width(8.dp))
                    Checkbox(pickup, { pickup = it }); Text("Pickup")
                }
                if (delivery) OutlinedTextField(fee, { fee = it.take(20) }, Modifier.fillMaxWidth(), label = { Text("Delivery fee (NGN, optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val p = price.toDoubleOrNull()
                val q = quantity.toIntOrNull()
                val f = fee.toDoubleOrNull()
                if (p != null && p > 0 && q != null && q >= 0 && title.trim().length >= 2 && description.trim().length >= 5 && media != null) {
                    onPublish(title.trim(), description.trim(), store.trim(), p, "NGN", category, condition, q, location.trim(), delivery, pickup, f, media!!)
                }
            }) { Text("Publish") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatMoney(price: Double, currency: String): String = "${currency.uppercase()} ${String.format(java.util.Locale.US, "%,.2f", price)}"
