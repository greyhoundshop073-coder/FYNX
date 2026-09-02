package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class FynxMarketListing(val id: String, val owner: String, val title: String, val description: String, val price: String, val category: String, val mediaUri: String? = null)

private object FynxMarketStore {
    private const val PREFS = "fynx_market_listings"
    private const val KEY = "listings"
    fun load(context: Context): List<FynxMarketListing> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching { raw.split("\n").filter { it.isNotBlank() }.mapNotNull { row -> row.split("\t").let { p -> if (p.size < 7) null else FynxMarketListing(p[0], p[1], p[2], p[3], p[4], p[5], p[6].ifBlank { null }) } } }.getOrElse { emptyList() }
    }
    fun save(context: Context, listings: List<FynxMarketListing>) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, listings.joinToString("\n") { listOf(it.id,it.owner,it.title,it.description,it.price,it.category,it.mediaUri ?: "").joinToString("\t") }).apply() }
}

@Composable
fun FynxMarketplacePanel(currentUsername: String = "preview", onOpenProfile: (String) -> Unit = {}) {
    val context = LocalContext.current
    var listings by remember { mutableStateOf(FynxMarketStore.load(context)) }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf<FynxMarketListing?>(null) }
    var pickedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var cartCount by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) pickedMediaUri = uri }
    val categories = listOf("All", "Electronics", "Fashion", "Home", "Beauty", "Vehicles", "Services")
    val visible = listings.filter { (selectedCategory == "All" || it.category == selectedCategory) && (query.isBlank() || (it.title + " " + it.owner + " " + it.description).contains(query, true)) }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Marketplace", style = MaterialTheme.typography.headlineSmall); Text("Real products from FYNX accounts", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary) }
            BadgedBox(badge = { if (cartCount > 0) Badge { Text(cartCount.toString()) } }) { IconButton(onClick = {}) { Icon(Icons.Default.ShoppingBag, "Cart") } }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { selected = FynxMarketListing("new-${System.currentTimeMillis()}", currentUsername.removePrefix("@"), "", "", "", "Electronics") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Sell") }
            OutlinedTextField(query, { query = it }, Modifier.weight(2f), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search") }, shape = FynxDesign.ControlShape)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { categories.forEach { category -> FilterChip(selectedCategory == category, { selectedCategory = category }, label = { Text(category) }) } }
        if (visible.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.Storefront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(46.dp)); Spacer(Modifier.height(10.dp)); Text(if (listings.isEmpty()) "Marketplace is ready" else "No matching products", style = MaterialTheme.typography.titleLarge); Text(if (listings.isEmpty()) "Real seller listings will appear here when accounts publish products." else "Try another search or category.", color = FynxDesign.TextSecondary) }
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(visible, key = { it.id }) { listing -> FynxMarketPost(listing, { onOpenProfile(listing.owner) }, { selected = listing }, { cartCount++ }) } }
    }
    selected?.let { listing ->
        if (listing.id.startsWith("new-")) ProductComposer(listing.copy(mediaUri = pickedMediaUri?.toString() ?: listing.mediaUri), { picker.launch("image/*") }, { selected = null; pickedMediaUri = null }, { published -> listings = listOf(published) + listings; FynxMarketStore.save(context, listings); selected = null; pickedMediaUri = null })
        else AlertDialog(onDismissRequest = { selected = null }, title = { Text(listing.title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(listing.price, style = MaterialTheme.typography.titleLarge); Text(listing.description); Text("Seller: ${listing.owner}", color = FynxDesign.TextSecondary) } }, confirmButton = { TextButton(onClick = { cartCount++; selected = null }) { Text("Add to cart") } }, dismissButton = { TextButton(onClick = { onOpenProfile(listing.owner); selected = null }) { Text("View account") } })
    }
}

@Composable private fun FynxMarketPost(listing: FynxMarketListing, onProfile: () -> Unit, onOpen: () -> Unit, onAddCart: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) {
        Column {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onProfile, Modifier.size(44.dp)) { FynxAvatar(listing.owner, Modifier.size(40.dp)) }; Column(Modifier.weight(1f).padding(start = 4.dp)) { Text(listing.owner.removePrefix("@"), style = MaterialTheme.typography.titleSmall); Text("Marketplace", style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary) }; IconButton(onClick = onOpen) { Icon(Icons.Default.MoreHoriz, "Details") } }
            if (listing.mediaUri != null) MarketImage(listing.mediaUri) else Box(Modifier.fillMaxWidth().height(300.dp).background(FynxDesign.SurfaceRaised), Alignment.Center) { Icon(Icons.Default.ShoppingBag, "Product", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(58.dp)) }
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(listing.title.ifBlank { "Untitled product" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); Text(listing.price.ifBlank { "Price not set" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                if (listing.description.isNotBlank()) Text(listing.description, color = FynxDesign.TextSecondary, maxLines = 3)
                Text(listing.category, style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onOpen) { Icon(Icons.Default.FavoriteBorder, null, Modifier.size(19.dp)); Spacer(Modifier.width(3.dp)); Text("Like") }; TextButton(onClick = onOpen) { Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(19.dp)); Spacer(Modifier.width(3.dp)); Text("Comment") }; TextButton(onClick = onOpen) { Icon(Icons.Default.Share, null, Modifier.size(19.dp)); Spacer(Modifier.width(3.dp)); Text("Share") }; Spacer(Modifier.weight(1f)); IconButton(onClick = onAddCart) { Icon(Icons.Default.AddShoppingCart, "Add to cart") } }
            }
        }
    }
}

@Composable private fun ProductComposer(listing: FynxMarketListing, onPickMedia: () -> Unit, onCancel: () -> Unit, onPublish: (FynxMarketListing) -> Unit) {
    var title by remember(listing.id) { mutableStateOf(listing.title) }; var description by remember(listing.id) { mutableStateOf(listing.description) }; var price by remember(listing.id) { mutableStateOf(listing.price) }; var mediaUri by remember(listing.id) { mutableStateOf(listing.mediaUri) }
    AlertDialog(onDismissRequest = onCancel, title = { Text("Post product") }, text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) { OutlinedButton(onClick = onPickMedia, Modifier.fillMaxWidth()) { Icon(Icons.Default.AddAPhoto, null); Spacer(Modifier.width(6.dp)); Text(if (mediaUri == null) "Add product photo" else "Change product photo") }; OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Product name") }, singleLine = true); OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(), label = { Text("Price") }, singleLine = true); OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 2); Text("Category: ${listing.category}", style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary) } }, confirmButton = { TextButton(onClick = { if (title.isNotBlank() && price.isNotBlank()) onPublish(listing.copy(title = title.trim(), price = price.trim(), description = description.trim(), mediaUri = mediaUri)) }) { Text("Publish") } }, dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } })
}

@Composable private fun MarketImage(uriString: String) {
    val context = LocalContext.current; var bitmap by remember(uriString) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uriString) { bitmap = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(Uri.parse(uriString)).use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull() } }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Product photo", Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 520.dp), contentScale = ContentScale.Crop) else Box(Modifier.fillMaxWidth().height(300.dp).background(FynxDesign.SurfaceRaised), Alignment.Center) { CircularProgressIndicator() }
}
