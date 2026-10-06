package com.fynx.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun FynxMarketplacePanel(currentUsername: String = "preview", onOpenProfile: (String) -> Unit = {}, initialListingId: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listings by remember { mutableStateOf<List<FynxRemoteSocialClient.MarketplaceListing>>(emptyList()) }
    var orders by remember { mutableStateOf<List<FynxRemoteSocialClient.MarketplaceOrder>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<FynxRemoteSocialClient.MarketplaceListing?>(null) }
    var checkoutListing by remember { mutableStateOf<FynxRemoteSocialClient.MarketplaceListing?>(null) }
    var paymentOrder by remember { mutableStateOf<FynxRemoteSocialClient.MarketplaceOrder?>(null) }
    var protectedOrder by remember { mutableStateOf<FynxRemoteSocialClient.MarketplaceOrder?>(null) }
    var showSell by remember { mutableStateOf(false) }
    var showOrders by remember { mutableStateOf(false) }
    var cart by remember { mutableStateOf<List<FynxRemoteSocialClient.MarketplaceListing>>(emptyList()) }
    var showCart by remember { mutableStateOf(false) }
    var nearbyMode by remember { mutableStateOf(false) }
    var nearbyLabel by remember { mutableStateOf("") }
    var nearbyLoading by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { it }) {
            scope.launch {
                nearbyLoading = true
                FynxPostLocationClient.currentPlace(context)
                    .onSuccess { place -> nearbyLabel = place; nearbyMode = true }
                    .onFailure { error = it.message ?: "FYNX could not identify your area." }
                nearbyLoading = false
            }
        } else {
            error = "Location permission is needed to find Marketplace products near you."
        }
    }
    fun toggleNearby() {
        if (nearbyMode) {
            nearbyMode = false
            nearbyLabel = ""
            return
        }
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            scope.launch {
                nearbyLoading = true
                FynxPostLocationClient.currentPlace(context)
                    .onSuccess { place -> nearbyLabel = place; nearbyMode = true }
                    .onFailure { error = it.message ?: "FYNX could not identify your area." }
                nearbyLoading = false
            }
        } else {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    val categories = listOf("All", "Electronics", "Fashion", "Home", "Beauty", "Vehicles", "Services")
    var sellerReputations by remember { mutableStateOf<Map<String, FynxMarketplaceClient.SellerReputation>>(emptyMap()) }
    var sellerPhotoIds by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            val result = if (nearbyMode && nearbyLabel.isNotBlank()) {
                FynxRemoteSocialClient.nearbyMarketplaceListings(context, query, category, nearbyLabel)
            } else {
                FynxRemoteSocialClient.listings(context, query, category)
            }
            result.onSuccess { listings = it }
                .onFailure { error = it.message ?: "Marketplace could not load." }
            FynxRemoteSocialClient.orders(context).onSuccess { orders = it }
            loading = false
        }
    }

    fun contactSeller(username: String, listingId: String) {
        val normalized = username.removePrefix("@").trim()
        if (normalized.isNotBlank()) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FynxDeepLinkParser.chatAppLink(normalized, listingId))))
            }.onFailure {
                error = "FYNX could not open the seller chat. Please try again."
            }
        }
    }

    LaunchedEffect(query, category, nearbyMode, nearbyLabel) {
        delay(if (query.isBlank()) 0L else 350L)
        reload()
    }

    LaunchedEffect(listings) {
        val sellers = listings.distinctBy { it.sellerUsername.removePrefix("@").trim().lowercase() }.take(12)
        val reputationMap = linkedMapOf<String, FynxMarketplaceClient.SellerReputation>()
        val photoMap = linkedMapOf<String, String?>()
        sellers.forEach { listing ->
            val username = listing.sellerUsername.removePrefix("@").trim()
            if (username.isBlank()) return@forEach
            FynxMarketplaceClient.sellerReputation(context, username).onSuccess { reputationMap[username.lowercase()] = it }
            FynxProfileRemoteClient.get(context, username).onSuccess { photoMap[username.lowercase()] = it.profilePhotoMediaId }
        }
        sellerReputations = reputationMap
        sellerPhotoIds = photoMap
    }

    val visibleSellerListings = remember(listings, sellerReputations) {
        listings.distinctBy { it.sellerUsername.removePrefix("@").trim().lowercase() }
            .filter { sellerReputations.containsKey(it.sellerUsername.removePrefix("@").trim().lowercase()) }
            .sortedByDescending { sellerReputations[it.sellerUsername.removePrefix("@").trim().lowercase()]?.successfulSales ?: 0 }
            .take(8)
    }

    LaunchedEffect(initialListingId) {
        val listingId = initialListingId?.trim().orEmpty()
        if (listingId.isNotBlank()) {
            loadExactMarketplaceListing(context, listingId)
                .onSuccess { listing -> selected = listing }
                .onFailure { error = it.message ?: "Marketplace listing could not be opened." }
        }
    }

    Box(Modifier.fillMaxSize().background(FynxMarketplaceReferenceStyle.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Marketplace", color = FynxMarketplaceReferenceStyle.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(if (nearbyMode && nearbyLabel.isNotBlank()) "Showing products near $nearbyLabel" else "Discover products on FYNX", color = FynxMarketplaceReferenceStyle.textMuted, style = MaterialTheme.typography.bodySmall)
                }
                BadgedBox(badge = { if (cart.isNotEmpty()) Badge { Text(cart.size.toString()) } }) { IconButton(onClick = { showCart = true }) { Icon(Icons.Default.ShoppingCart, "Cart", tint = FynxMarketplaceReferenceStyle.text) } }
                IconButton(onClick = { showOrders = true }) { Icon(Icons.Default.ReceiptLong, "Orders", tint = FynxMarketplaceReferenceStyle.text) }
                IconButton(onClick = { reload() }) { Icon(Icons.Default.Refresh, "Refresh", tint = FynxMarketplaceReferenceStyle.text) }
            }
            OutlinedTextField(value = query, onValueChange = { query = it.take(80) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null, tint = FynxMarketplaceReferenceStyle.textMuted) }, placeholder = { Text("Search products or sellers", color = FynxMarketplaceReferenceStyle.textMuted) }, shape = FynxMarketplaceReferenceStyle.radius, colors = TextFieldDefaults.colors(focusedTextColor = FynxMarketplaceReferenceStyle.text, unfocusedTextColor = FynxMarketplaceReferenceStyle.text, focusedContainerColor = FynxMarketplaceReferenceStyle.surface, unfocusedContainerColor = FynxMarketplaceReferenceStyle.surface, focusedIndicatorColor = FynxMarketplaceReferenceStyle.primarySoft, unfocusedIndicatorColor = FynxMarketplaceReferenceStyle.outline, cursorColor = FynxMarketplaceReferenceStyle.primarySoft))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = nearbyMode, onClick = { toggleNearby() }, label = {
                    if (nearbyLoading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) else Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (nearbyMode) "Near ${nearbyLabel.substringBefore(",").ifBlank { "me" }}" else "Near me")
                })
                categories.forEach { item -> FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) }) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
            if (loading && listings.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth(), color = FynxMarketplaceReferenceStyle.primarySoft)
            when {
                loading && listings.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = FynxMarketplaceReferenceStyle.primarySoft) }
                listings.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    val filtered = query.isNotBlank() || category != "All" || nearbyMode
                    if (filtered) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Search, null, tint = FynxMarketplaceReferenceStyle.primarySoft, modifier = Modifier.size(48.dp))
                            Text("No matching products", color = FynxMarketplaceReferenceStyle.text, style = MaterialTheme.typography.titleLarge)
                            Text("Try another search or clear the current filters.", color = FynxMarketplaceReferenceStyle.textMuted)
                            TextButton(onClick = { query = ""; category = "All"; if (nearbyMode) toggleNearby() }) { Text("Clear filters", color = FynxMarketplaceReferenceStyle.primarySoft) }
                        }
                    } else {
                        FynxMarketplaceReferenceEmptyMarketplace(onSell = { showSell = true })
                    }
                }
                else -> LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().background(FynxMarketplaceReferenceStyle.background), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 132.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (visibleSellerListings.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Top Sellers (Highest Sales)", color = FynxMarketplaceReferenceStyle.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Highest successful sales from sellers currently represented here", color = FynxMarketplaceReferenceStyle.textMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 16.dp)) {
                                items(visibleSellerListings, key = { it.sellerUsername.removePrefix("@").trim().lowercase() }) { seller ->
                                    MarketplaceSellerCard(seller, sellerReputations[seller.sellerUsername.removePrefix("@").trim().lowercase()]!!, sellerPhotoIds[seller.sellerUsername.removePrefix("@").trim().lowercase()]) { onOpenProfile(seller.sellerUsername) }
                                }
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) { Text("Products", color = FynxMarketplaceReferenceStyle.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp)) }
                    }
                    gridItems(listings, key = { it.id }) { listing ->
                        MarketplaceReferenceProductCard(listing = listing, sellerRating = sellerReputations[listing.sellerUsername.removePrefix("@").trim().lowercase()], sellerPhotoId = sellerPhotoIds[listing.sellerUsername.removePrefix("@").trim().lowercase()], onOpen = { selected = listing }, onProfile = { onOpenProfile(listing.sellerUsername) }, onSeller = { onOpenProfile(listing.sellerUsername) }, onContact = { contactSeller(listing.sellerUsername, listing.id) })
                    }
                }
            }
        }
        FloatingActionButton(onClick = { showSell = true }, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().imePadding().padding(end = 18.dp, bottom = 18.dp), shape = RoundedCornerShape(18.dp), containerColor = FynxMarketplaceReferenceStyle.primary) {
            Icon(Icons.Default.Add, contentDescription = null, tint = FynxMarketplaceReferenceStyle.text)
            Spacer(Modifier.width(6.dp))
            Text("Sell", color = FynxMarketplaceReferenceStyle.text, modifier = Modifier.padding(end = 14.dp))
        }
    }
    if (showSell) MarketplaceSellDialog(context, onPublished = { showSell = false; reload() }, onCancel = { showSell = false })
    selected?.let { listing -> MarketplaceDetails(l = listing, onProfile = { onOpenProfile(listing.sellerUsername); selected = null }, onContact = { contactSeller(listing.sellerUsername, listing.id) }, onBuyNow = { selected = null; checkoutListing = listing }, onAddToCart = { if (cart.none { it.id == listing.id }) cart = cart + listing; selected = null }, onClose = { selected = null }) }
    checkoutListing?.let { listing -> FynxMarketplaceCheckoutDialog(context = context, listing = listing, onProtectedOrder = { order -> checkoutListing = null; orders = listOf(order) + orders.filterNot { it.id == order.id }; paymentOrder = order }, onClose = { checkoutListing = null }) }
    paymentOrder?.let { order -> MarketplacePaymentDialog(context = context, order = order, onPaid = { paymentOrder = null; protectedOrder = order; reload() }, onClose = { paymentOrder = null }) }
    protectedOrder?.let { order -> MarketplaceProtectedOrderDialog(order = order, onViewOrder = { protectedOrder = null; showOrders = true }, onContinue = { protectedOrder = null }) }
    if (showCart) MarketplaceCartDialog(items = cart, onRemove = { item -> cart = cart.filterNot { it.id == item.id } }, onCheckout = { listing -> showCart = false; checkoutListing = listing }, onClose = { showCart = false })
    if (showOrders) MarketplaceOrders(context, orders, onRefresh = { reload() }, onClose = { showOrders = false })
}
