package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class FynxMarketListing(
    val id: String,
    val owner: String,
    val title: String,
    val description: String,
    val price: String,
    val category: String,
    val mediaUri: String? = null
)

private object FynxMarketStore {
    private const val PREFS = "fynx_market_listings"
    private const val KEY = "listings"

    fun load(context: Context): List<FynxMarketListing> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return defaultListings()
        return runCatching {
            raw.split("\n").filter { it.isNotBlank() }.mapNotNull { row ->
                val p = row.split("\t")
                if (p.size < 7) null else FynxMarketListing(p[0], p[1], p[2], p[3], p[4], p[5], p[6].ifBlank { null })
            }
        }.getOrElse { defaultListings() }
    }

    fun save(context: Context, listings: List<FynxMarketListing>) {
        val raw = listings.joinToString("\n") { listOf(it.id,it.owner,it.title,it.description,it.price,it.category,it.mediaUri ?: "").joinToString("\t") }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
    }

    private fun defaultListings() = listOf(
        FynxMarketListing("demo-1", "FYNX Tech Store", "Wireless Headphones", "Clear sound and comfortable fit.", "₦45,000", "Electronics"),
        FynxMarketListing("demo-2", "Urban FYNX", "Classic Sneakers", "Everyday sneakers in multiple sizes.", "₦32,000", "Fashion"),
        FynxMarketListing("demo-3", "HomeSpace", "Desk Lamp", "Modern lamp for a clean workspace.", "₦18,500", "Home")
    )
}

@Composable
fun FynxMarketplacePanel(
    currentUsername: String = "preview",
    onOpenProfile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var listings by remember { mutableStateOf(FynxMarketStore.load(context)) }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showCreate by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<FynxMarketListing?>(null) }
    var pickedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var cartCount by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pickedMediaUri = uri
    }

    val categories = listOf("All", "Electronics", "Fashion", "Home")
    val visible = listings.filter {
        (selectedCategory == "All" || it.category == selectedCategory) &&
            (query.isBlank() || (it.title + " " + it.owner + " " + it.description).contains(query, true))
    }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Marketplace", style = MaterialTheme.typography.headlineSmall)
                Text("Products from FYNX accounts", color = FynxDesign.TextSecondary)
            }
            BadgedBox(badge = { if (cartCount > 0) Badge { Text(cartCount.toString()) } }) {
                IconButton(onClick = {}) { Icon(Icons.Default.ShoppingBag, "Cart") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { selected = FynxMarketListing("new-" + System.currentTimeMillis(), currentUsername.removePrefix("@"), "", "", "", "Electronics") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Sell a product")
            }
            OutlinedButton(onClick = { query = "" }, modifier = Modifier.weight(1f)) { Text("Clear search") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(16.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search products or sellers…") }, shape = FynxDesign.ControlShape)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { category -> FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category }, label = { Text(category) }) }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(visible, key = { it.id }) { listing ->
                FynxMarketPost(
                    listing = listing,
                    onProfile = { onOpenProfile(listing.owner) },
                    onOpen = { selected = listing },
                    onAddCart = { cartCount++ }
                )
            }
        }
    }

    selected?.let { listing ->
        if (listing.id.startsWith("new-")) {
            ProductComposer(
                listing = listing.copy(mediaUri = pickedMediaUri?.toString() ?: listing.mediaUri),
                onPickMedia = { picker.launch("image/*") },
                onCancel = { selected = null; pickedMediaUri = null },
                onPublish = { published ->
                    listings = listOf(published) + listings
                    FynxMarketStore.save(context, listings)
                    selected = null
                    pickedMediaUri = null
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { selected = null },
                title = { Text(listing.title) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(listing.price, style = MaterialTheme.typography.titleLarge)
                        Text(listing.description)
                        Text("Seller: " + listing.owner, color = FynxDesign.TextSecondary)
                    }
                },
                confirmButton = { TextButton(onClick = { cartCount++; selected = null }) { Text("Add to cart") } },
                dismissButton = { TextButton(onClick = { onOpenProfile(listing.owner); selected = null }) { Text("View account") } }
            )
        }
    }
}

@Composable
private fun FynxMarketPost(
    listing: FynxMarketListing,
    onProfile: () -> Unit,
    onOpen: () -> Unit,
    onAddCart: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.6f))) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onProfile) { FynxAvatar(listing.owner, Modifier.size(46.dp)) }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(listing.owner.removePrefix("@"), style = MaterialTheme.typography.titleMedium)
                    Text("Marketplace listing", style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary)
                }
                IconButton(onClick = onOpen) { Icon(Icons.Default.MoreHoriz, "Product details") }
            }
            if (listing.mediaUri != null) MarketImage(listing.mediaUri)
            else Box(Modifier.fillMaxWidth().height(210.dp).background(FynxDesign.SurfaceRaised), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ShoppingBag, "Product", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(listing.title.ifBlank { "Untitled product" }, style = MaterialTheme.typography.titleLarge)
                Text(listing.description, color = FynxDesign.TextSecondary)
                Text(listing.price.ifBlank { "Price not set" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(listing.category, style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onOpen) { Icon(Icons.Default.FavoriteBorder, null); Spacer(Modifier.width(4.dp)); Text("Like") }
                    TextButton(onClick = onOpen) { Icon(Icons.Default.ChatBubbleOutline, null); Spacer(Modifier.width(4.dp)); Text("Comment") }
                    TextButton(onClick = onOpen) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(4.dp)); Text("Share") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onOpen) { Icon(Icons.Default.BookmarkBorder, "Save") }
                    IconButton(onClick = onAddCart) { Icon(Icons.Default.AddShoppingCart, "Add to cart") }
                }
            }
        }
    }
}

@Composable
private fun ProductComposer(
    listing: FynxMarketListing,
    onPickMedia: () -> Unit,
    onCancel: () -> Unit,
    onPublish: (FynxMarketListing) -> Unit
) {
    var title by remember(listing.id) { mutableStateOf(listing.title) }
    var description by remember(listing.id) { mutableStateOf(listing.description) }
    var price by remember(listing.id) { mutableStateOf(listing.price) }
    var category by remember(listing.id) { mutableStateOf(listing.category) }
    var mediaUri by remember(listing.id) { mutableStateOf(listing.mediaUri) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Post product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick = { onPickMedia() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text(if (mediaUri == null) "Add product photo" else "Change product photo") }
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Product name") }, singleLine = true)
                OutlinedTextField(price, { price = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Price") }, singleLine = true)
                OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 2)
                Text("Category: $category", style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank() && price.isNotBlank()) onPublish(listing.copy(title = title.trim(), price = price.trim(), description = description.trim(), category = category, mediaUri = mediaUri)) }) { Text("Publish") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

@Composable
private fun MarketImage(uriString: String) {
    val context = LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uriString) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(Uri.parse(uriString)).use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()
        }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Product photo", Modifier.fillMaxWidth().heightIn(min = 210.dp, max = 360.dp), contentScale = ContentScale.Crop)
    else Box(Modifier.fillMaxWidth().height(210.dp).background(FynxDesign.SurfaceRaised), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
