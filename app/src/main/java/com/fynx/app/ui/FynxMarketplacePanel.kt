package com.fynx.app.ui


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Build
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.Instant
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

// Keep a stalled Marketplace request from trapping users behind a spinner; retry remains available after timeout.
private const val MARKETPLACE_LISTING_REQUEST_TIMEOUT_MS = 15_000L

internal fun advanceMarketplaceListingsOffset(currentOffset: Int, requestedLimit: Int): Int =
    currentOffset + requestedLimit.coerceAtLeast(1)



@Composable
private fun MarketplaceExploreCategoryTiles(
    categories: List<String>,
    selectedCategory: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        categories.forEach { itemCategory ->
            val icon = when (itemCategory.lowercase(Locale.US)) {
                "electronics" -> Icons.Default.Devices
                "fashion" -> Icons.Default.ShoppingBag
                "home" -> Icons.Default.Home
                "beauty" -> Icons.Default.Face
                "vehicles" -> Icons.Default.DirectionsCar
                "services" -> Icons.Default.Build
                else -> Icons.Default.Storefront
            }
            Surface(
                onClick = { onSelect(itemCategory) },
                shape = RoundedCornerShape(16.dp),
                color = if (selectedCategory == itemCategory) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.width(94.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
                    }
                    Text(itemCategory, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun MarketplaceSmartDealsHero(
    listings: List<FynxRemoteSocialClient.MarketplaceListing>,
    onOpenListing: (FynxRemoteSocialClient.MarketplaceListing) -> Unit,
    onShopNow: () -> Unit,
    onOrders: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = listings.groupingBy { it.currency.trim().uppercase(Locale.US) }
        .eachCount().maxByOrNull { it.value }?.key
    val deals = remember(listings, currency) {
        listings.filter { it.active && it.quantity > 0 && it.price >= 0.0 &&
            (currency == null || it.currency.trim().uppercase(Locale.US) == currency) }
            .distinctBy { it.id }
            .sortedBy { it.price }
    }
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(deals.map { it.id }) {
        if (deals.size > 3) {
            while (true) {
                delay(4500)
                val next = if (rowState.firstVisibleItemIndex + 3 >= deals.size) 0 else rowState.firstVisibleItemIndex + 1
                rowState.animateScrollToItem(next)
            }
        }
    }
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Great Deals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Better Prices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Real products from FYNX sellers", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onOrders) { Icon(Icons.Default.ReceiptLong, contentDescription = "Orders", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh listings", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onShopNow) { Icon(Icons.Default.Storefront, contentDescription = "Browse all products", tint = MaterialTheme.colorScheme.primary) }
            }
            if (deals.isEmpty()) {
                Text("Products will appear here when live seller listings are available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 4.dp)) {
                    items(deals, key = { "smart-deal-${it.id}" }) { listing ->
                        Surface(onClick = { onOpenListing(listing) }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(116.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(7.dp)) {
                                if (listing.mediaIds.isNotEmpty()) {
                                    RemoteMarketMedia(LocalContext.current, listing.mediaIds.first(), Modifier.fillMaxWidth().aspectRatio(0.95f).clip(RoundedCornerShape(10.dp)), listing.mediaTypes.firstOrNull())
                                } else {
                                    Box(Modifier.fillMaxWidth().aspectRatio(0.95f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
                                        Icon(Icons.Default.ShoppingBag, contentDescription = "Product image unavailable", modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text(listing.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(listing.currency.uppercase(Locale.US) + " " + String.format(Locale.US, "%,.2f", listing.price), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onShopNow, modifier = Modifier.align(Alignment.End)) {
                Text("Shop Now")
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Default.Add, contentDescription = "Browse Marketplace", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun FynxMarketplacePanel(currentUsername: String = "preview", onOpenProfile: (String) -> Unit = {}, onOpenAi: (String) -> Unit = {}, onLiveProof: (String) -> Unit = {}, onOpenMyShop: () -> Unit = {}, initialListingId: String? = null, onOpenSavedProducts: () -> Unit = {}, onShareListingToStatus: (String, String) -> Unit = { _, _ -> }) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listings by remember { mutableStateOf<List<FynxRemoteSocialClient.MarketplaceListing>>(emptyList()) }
    var hasMoreListings by remember { mutableStateOf(false) }
    var loadingMoreListings by remember { mutableStateOf(false) }
    var nextListingsOffset by remember { mutableIntStateOf(0) }
    // Invalidates in-flight search/filter and pagination responses when the active listing query changes.
    var listingRequestGeneration by remember { mutableIntStateOf(0) }
    val productsGridState = rememberLazyGridState()
    var orders by remember { mutableStateOf<List<FynxRemoteSocialClient.MarketplaceOrder>>(emptyList()) }
    var ordersLoading by remember { mutableStateOf(false) }
    var ordersError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val recentSearchesKey = "marketplace_recent_searches_v1"
    var recentSearches by remember {
        mutableStateOf(
            runCatching {
                val saved = context.getSharedPreferences("fynx_marketplace", android.content.Context.MODE_PRIVATE)
                    .getString(recentSearchesKey, "[]").orEmpty()
                val json = JSONArray(saved)
                (0 until json.length()).mapNotNull { json.optString(it).trim().takeIf(String::isNotBlank) }
                    .distinctBy { it.lowercase(Locale.US) }.take(8)
            }.getOrDefault(emptyList())
        )
    }
    var showAllProducts by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("All") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var discoveryFallbackWarning by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<FynxRemoteSocialClient.MarketplaceListing?>(null) }
    var sellerStoreUsername by remember { mutableStateOf<String?>(null) }
    var checkoutListing by remember { mutableStateOf<FynxRemoteSocialClient.MarketplaceListing?>(null) }
    var paymentOrder by remember { mutableStateOf<FynxRemoteSocialClient.MarketplaceOrder?>(null) }
    var protectedOrder by remember { mutableStateOf<FynxRemoteSocialClient.MarketplaceOrder?>(null) }
    var showSell by remember { mutableStateOf(false) }
    var publishSuccessListingId by remember { mutableStateOf<String?>(null) }
    var showOrders by remember { mutableStateOf(false) }
    // Keep saved carts isolated when multiple accounts use the same device.
    val cartOwnerKey = currentUsername.removePrefix("@").trim().lowercase(Locale.US).ifBlank { "preview" }
    val restoredMarketplaceCart = remember(context, cartOwnerKey) { readFynxMarketplaceCart(context, cartOwnerKey) }
    var cart by remember(cartOwnerKey) { mutableStateOf(restoredMarketplaceCart.items) }
    var cartQuantities by remember(cartOwnerKey) { mutableStateOf(restoredMarketplaceCart.quantities) }
    var multiCheckoutItems by remember { mutableStateOf<List<FynxRemoteSocialClient.MarketplaceListing>?>(null) }
    var multiPayment by remember { mutableStateOf<Triple<String, Double, String>?>(null) }
    var completedCheckoutId by remember(cartOwnerKey) {
        mutableStateOf(
            context.getSharedPreferences("fynx_marketplace", android.content.Context.MODE_PRIVATE)
                .getString("last_checkout_receipt_id:$cartOwnerKey", null)
        )
    }
    var multiPaymentListingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showCart by remember { mutableStateOf(false) }
    var priceWatchListingId by remember { mutableStateOf<String?>(null) }
    var priceWatchBusy by remember { mutableStateOf(false) }
    var savedProductIds by remember(cartOwnerKey) { mutableStateOf<Set<String>>(emptySet()) }
    var savedProductKnownIds by remember(cartOwnerKey) { mutableStateOf<Set<String>>(emptySet()) }
    var savedProductStateLoaded by remember(cartOwnerKey) { mutableStateOf(false) }
    var savedProductStateGeneration by remember(cartOwnerKey) { mutableStateOf(0) }
    var savedProductBusy by remember(cartOwnerKey) { mutableStateOf(false) }
    var savedProductBusyIds by remember(cartOwnerKey) { mutableStateOf<Set<String>>(emptySet()) }
    fun knownSavedProductState(listingId: String): Boolean? = when {
        savedProductStateLoaded -> listingId in savedProductIds
        listingId in savedProductKnownIds -> listingId in savedProductIds
        else -> null
    }
    fun setSavedProductState(listingId: String, saved: Boolean) {
        savedProductIds = if (saved) savedProductIds + listingId else savedProductIds - listingId
        savedProductKnownIds = savedProductKnownIds + listingId
    }
    fun toggleSavedProductFromCard(listingId: String) {
        if (listingId in savedProductBusyIds) return
        savedProductBusyIds = savedProductBusyIds + listingId
        savedProductStateGeneration += 1
        scope.launch {
            FynxRemoteSocialClient.toggleMarketplaceProductSaved(context, listingId, knownSavedProductState(listingId))
                .onSuccess { saved -> setSavedProductState(listingId, saved) }
                .onFailure { error = marketplaceSafeError(it, "Saved Products could not be updated.") }
            savedProductBusyIds = savedProductBusyIds - listingId
        }
    }
    var nearbyMode by remember { mutableStateOf(false) }
    var nearbyLabel by remember { mutableStateOf("") }
    var nearbyLoading by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { it }) {
            scope.launch {
                nearbyLoading = true
                FynxPostLocationClient.currentPlace(context)
                    .onSuccess { place -> nearbyLabel = place; nearbyMode = true }
                    .onFailure { error = marketplaceSafeError(it, "FYNX could not identify your area.") }
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
                    .onFailure { error = marketplaceSafeError(it, "FYNX could not identify your area.") }
                nearbyLoading = false
            }
        } else {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    val categories = listOf("All", "Electronics", "Fashion", "Home", "Beauty", "Vehicles", "Services", "Food", "Property", "Agriculture", "Phones & Computers", "Furniture", "Baby & Kids", "Books", "Sports", "Tools", "Health", "Other")
    var sellerReputations by remember { mutableStateOf<Map<String, FynxMarketplaceClient.SellerReputation>>(emptyMap()) }
    var sellerPhotoIds by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }

    fun reloadOrders() {
        scope.launch {
            ordersLoading = true
            ordersError = null
            FynxRemoteSocialClient.orders(context)
                .onSuccess { orders = it }
                .onFailure { ordersError = marketplaceSafeError(it, "Orders could not load.") }
            ordersLoading = false
        }
    }

    fun reload() {
        scope.launch {
            val requestGeneration = ++listingRequestGeneration
            val requestQuery = query
            val requestCategory = category
            val requestNearbyMode = nearbyMode
            val requestNearbyLabel = nearbyLabel
            loading = true
            loadingMoreListings = false
            nextListingsOffset = 0
            hasMoreListings = false
            error = null
            discoveryFallbackWarning = null
            val result = kotlinx.coroutines.withTimeoutOrNull(MARKETPLACE_LISTING_REQUEST_TIMEOUT_MS) {
                if (requestNearbyMode && requestNearbyLabel.isNotBlank()) {
                    FynxRemoteSocialClient.nearbyMarketplaceListings(context, requestQuery, requestCategory, requestNearbyLabel)
                        .map { Triple(it, false, null as String?) }
                } else {
                    FynxRemoteSocialClient.marketplaceDiscoveryPage(context, requestQuery, requestCategory, limit = 24, offset = 0)
                        .map { page -> Triple(page.listings, page.hasMore, null as String?) }
                        .recoverCatching {
                            val fallbackListings = FynxRemoteSocialClient.listings(context, requestQuery, requestCategory).getOrThrow()
                            Triple(
                                fallbackListings,
                                false,
                                "Marketplace discovery is temporarily unavailable; showing live listings through the backup route."
                            )
                        }
                }
            }
            if (requestGeneration != listingRequestGeneration) return@launch
            if (result == null) {
                error = "Marketplace is taking longer than expected. Check your connection and try again."
            } else {
                result.onSuccess { (items, more, fallbackWarning) ->
                    listings = items.distinctBy { it.id }
                    hasMoreListings = more
                    discoveryFallbackWarning = fallbackWarning
                    nextListingsOffset = advanceMarketplaceListingsOffset(0, 24)
                }.onFailure { error = marketplaceSafeError(it, "Marketplace could not load.") }
            }
            // Listing visibility must not wait on the independent orders request.
            loading = false
            reloadOrders()
        }
    }

    fun loadMoreListings() {
        if (loading || loadingMoreListings || !hasMoreListings || nearbyMode) return
        val requestGeneration = listingRequestGeneration
        val requestQuery = query
        val requestCategory = category
        val requestOffset = nextListingsOffset
        scope.launch {
            loadingMoreListings = true
            val result = kotlinx.coroutines.withTimeoutOrNull(MARKETPLACE_LISTING_REQUEST_TIMEOUT_MS) {
                FynxRemoteSocialClient.marketplaceDiscoveryPage(context, requestQuery, requestCategory, limit = 24, offset = requestOffset)
            }
            if (requestGeneration != listingRequestGeneration) return@launch
            if (result == null) {
                error = "More products could not load. Scroll up and retry."
            } else {
                result.onSuccess { page ->
                    listings = appendDistinctMarketplacePage(listings, page.listings) { it.id }
                    nextListingsOffset = advanceMarketplaceListingsOffset(nextListingsOffset, 24)
                    hasMoreListings = page.hasMore && page.listings.isNotEmpty()
                    error = null
                }.onFailure { error = marketplaceSafeError(it, "More products could not load.") }
            }
            loadingMoreListings = false
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

    LaunchedEffect(cartOwnerKey, cart, cartQuantities) {
        writeFynxMarketplaceCart(context, cartOwnerKey, cart, cartQuantities)
    }

    LaunchedEffect(cartOwnerKey) {
        val requestGeneration = savedProductStateGeneration
        FynxRemoteSocialClient.savedMarketplaceProducts(context)
            .onSuccess { savedProducts ->
                if (savedProductStateGeneration == requestGeneration) {
                    savedProductIds = savedProducts.map { it.id }.toSet()
                    savedProductKnownIds = emptySet()
                    savedProductStateLoaded = true
                }
            }
            .onFailure {
                if (savedProductStateGeneration == requestGeneration) savedProductStateLoaded = false
            }
    }

    LaunchedEffect(query, category, nearbyMode, nearbyLabel) {
        // Invalidate prior requests immediately, before the search debounce completes.
        listingRequestGeneration += 1
        delay(if (query.isBlank()) 0L else 350L)
        reload()
    }

    LaunchedEffect(showAllProducts, query, category, nearbyMode) {
        if (showAllProducts || query.isNotBlank() || category != "All" || nearbyMode) {
            snapshotFlow {
                val layout = productsGridState.layoutInfo
                (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) to layout.totalItemsCount
            }.collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 6) loadMoreListings()
            }
        }
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

    LaunchedEffect(query) {
        val term = query.trim()
        if (term.length >= 2) {
            delay(900)
            recentSearches = (listOf(term) + recentSearches.filterNot { it.equals(term, ignoreCase = true) }).take(8)
            val saved = JSONArray().apply { recentSearches.forEach(::put) }
            context.getSharedPreferences("fynx_marketplace", android.content.Context.MODE_PRIVATE)
                .edit().putString(recentSearchesKey, saved.toString()).apply()
        }
    }

    LaunchedEffect(initialListingId) {
        val listingId = initialListingId?.trim().orEmpty()
        if (listingId.isNotBlank()) {
            loadExactMarketplaceListing(context, listingId)
                .onSuccess { listing -> selected = listing }
                .onFailure { error = marketplaceSafeError(it, "Marketplace listing could not be opened.") }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            MarketplaceReferenceSearchBar(
                value = query,
                onValueChange = { query = it.take(80) },
                onCart = { showCart = true },
                cartCount = cartQuantities.values.sum().coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onOpenMyShop, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)) {
                    Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("My Shop", maxLines = 1)
                }
                OutlinedButton(onClick = { showOrders = true; reloadOrders() }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Orders", maxLines = 1)
                }
                OutlinedButton(onClick = onOpenSavedProducts, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Saved products", maxLines = 1)
                }
            }
            completedCheckoutId?.let { checkoutId ->
                MarketplaceCheckoutReceiptAction(
                    context = context,
                    checkoutId = checkoutId
                )
            }
            MarketplaceReferenceCategoryRow(
                categories = categories,
                selected = category,
                onSelect = { category = it },
                onNearby = { toggleNearby() },
                nearbySelected = nearbyMode,
                nearbyLabel = if (nearbyMode) "Near ${nearbyLabel.substringBefore(",").ifBlank { "me" }}" else "Near me",
                nearbyLoading = nearbyLoading,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp)
            )
            if (query.isBlank() && recentSearches.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent searches", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = {
                        recentSearches = emptyList()
                        context.getSharedPreferences("fynx_marketplace", android.content.Context.MODE_PRIVATE)
                            .edit().remove(recentSearchesKey).apply()
                    }) { Text("Clear") }
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 16.dp)
                ) {
                    items(recentSearches, key = { "recent-search-$it" }) { term ->
                        InputChip(
                            selected = false,
                            onClick = { query = term },
                            label = { Text(term, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        recentSearches = recentSearches.filterNot { it.equals(term, ignoreCase = true) }
                                        val saved = JSONArray().apply { recentSearches.forEach(::put) }
                                        context.getSharedPreferences("fynx_marketplace", android.content.Context.MODE_PRIVATE)
                                            .edit().putString(recentSearchesKey, saved.toString()).apply()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) { Icon(Icons.Default.Close, contentDescription = "Remove $term", modifier = Modifier.size(14.dp)) }
                            }
                        )
                    }
                }
            }
            if (discoveryFallbackWarning != null && listings.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(discoveryFallbackWarning.orEmpty(), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { reload() }) { Text("Retry discovery") }
                }
            }
            if (error != null && listings.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error.orEmpty(), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { reload() }) { Text("Retry") }
                }
            }
            if (loading && listings.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                loading && listings.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                listings.isEmpty() && error != null -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 132.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item(key = "marketplace-offline-hero") {
                        MarketplaceSmartDealsHero(
                            listings = listings,
                            onOpenListing = { selected = it },
                            onShopNow = { showAllProducts = true },
                            onOrders = { showOrders = true; reloadOrders() },
                            onRefresh = { reload() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item(key = "marketplace-offline-status") {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                Text("Live listings could not load. Your Marketplace is ready to retry; no sample products are shown.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { reload() }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Retry") }
                            }
                        }
                    }
                    item(key = "marketplace-explore-title") { MarketplaceReferenceSectionTitle("Explore categories") }
                    item(key = "marketplace-explore-categories") {
                        MarketplaceExploreCategoryTiles(categories.filter { it != "All" }, category) { itemCategory ->
                            category = itemCategory
                            showAllProducts = true
                        }
                    }
                    item(key = "marketplace-offline-sell") {
                        OutlinedButton(onClick = { showSell = true }, modifier = Modifier.fillMaxWidth()) { Text("Sell something") }
                    }
                }
                listings.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    val filtered = query.isNotBlank() || category != "All" || nearbyMode
                    if (filtered) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Storefront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("No matching products", style = MaterialTheme.typography.titleLarge)
                            Text("Try another search or clear the current filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { query = ""; category = "All"; if (nearbyMode) toggleNearby() }) { Text("Clear filters") }
                        }
                    } else {
                        MarketplaceReferenceEmptyState(
                            title = "No products yet",
                            message = "Be the first seller on FYNX",
                            onSell = { showSell = true }
                        )
                    }
                }
                else -> if (showAllProducts || query.isNotBlank() || category != "All" || nearbyMode) {
                    LazyVerticalGrid(
                        state = productsGridState,
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 132.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (visibleSellerListings.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Top Sellers (Highest Sales)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Highest successful sales from sellers currently represented here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(visibleSellerListings, key = { it.sellerUsername.removePrefix("@").trim().lowercase() }) { seller ->
                                        val key = seller.sellerUsername.removePrefix("@").trim().lowercase()
                                        val reputation = sellerReputations[key]
                                        if (reputation != null) MarketplaceSellerCard(seller, reputation, sellerPhotoIds[key]) { sellerStoreUsername = seller.sellerUsername }
                                    }
                                }
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text("Products", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        gridItems(listings, key = { it.id }) { listing ->
                            val sellerKey = listing.sellerUsername.removePrefix("@").trim().lowercase()
                            MarketplaceReferenceProductCard(
                                listing = listing,
                                sellerRating = sellerReputations[sellerKey],
                                sellerPhotoId = sellerPhotoIds[sellerKey],
                                onOpen = { selected = listing },
                                onProfile = { sellerStoreUsername = listing.sellerUsername },
                                onContact = { contactSeller(listing.sellerUsername, listing.id) },
                                isFavorite = listing.id in savedProductIds,
                                isFavoriteBusy = listing.id in savedProductBusyIds,
                                onFavorite = { toggleSavedProductFromCard(listing.id) }
                            )
                        }
                        if (loadingMoreListings) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                } else LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 132.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item(key = "marketplace-hero") {
                        MarketplaceSmartDealsHero(
                            listings = listings,
                            onOpenListing = { selected = it },
                            onShopNow = { showAllProducts = true },
                            onOrders = { showOrders = true; reloadOrders() },
                            onRefresh = { reload() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (visibleSellerListings.isNotEmpty()) {
                        item(key = "marketplace-top-sellers-title") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Top Sellers (Highest Sales)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Based on successful sales, not invented rankings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        item(key = "marketplace-top-sellers") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(visibleSellerListings, key = { it.sellerUsername.removePrefix("@").trim().lowercase() }) { seller ->
                                    val key = seller.sellerUsername.removePrefix("@").trim().lowercase()
                                    val reputation = sellerReputations[key]
                                    if (reputation != null) MarketplaceSellerCard(seller, reputation, sellerPhotoIds[key]) { sellerStoreUsername = seller.sellerUsername }
                                }
                            }
                        }
                    }
                    if (listings.isNotEmpty()) {
                        item(key = "marketplace-recommended-title") {
                            MarketplaceReferenceSectionTitle("Recommended for you", action = "See all", onAction = { showAllProducts = true })
                        }
                        item(key = "marketplace-recommended-listings") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
                                items(listings.take(8), key = { "recommended-${it.id}" }) { listing ->
                                    Box(Modifier.width(208.dp)) {
                                        MarketplaceReferenceProductCard(
                                            listing = listing,
                                            sellerRating = sellerReputations[listing.sellerUsername.removePrefix("@").trim().lowercase()],
                                            sellerPhotoId = sellerPhotoIds[listing.sellerUsername.removePrefix("@").trim().lowercase()],
                                            onOpen = { selected = listing },
                                            onProfile = { sellerStoreUsername = listing.sellerUsername },
                                            onContact = { contactSeller(listing.sellerUsername, listing.id) },
                                isFavorite = listing.id in savedProductIds,
                                isFavoriteBusy = listing.id in savedProductBusyIds,
                                onFavorite = { toggleSavedProductFromCard(listing.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (nearbyMode && listings.isNotEmpty()) {
                        item(key = "marketplace-nearby-title") { MarketplaceReferenceSectionTitle("Listings near you", action = "See all", onAction = { showAllProducts = true }) }
                        item(key = "marketplace-nearby-listings") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
                                items(listings.take(8), key = { "nearby-${it.id}" }) { listing ->
                                    Box(Modifier.width(208.dp)) {
                                        MarketplaceReferenceProductCard(
                                        listing = listing,
                                        sellerRating = sellerReputations[listing.sellerUsername.removePrefix("@").trim().lowercase()],
                                        sellerPhotoId = sellerPhotoIds[listing.sellerUsername.removePrefix("@").trim().lowercase()],
                                        onOpen = { selected = listing },
                                        onProfile = { sellerStoreUsername = listing.sellerUsername },
                                        onContact = { contactSeller(listing.sellerUsername, listing.id) },
                                isFavorite = listing.id in savedProductIds,
                                isFavoriteBusy = listing.id in savedProductBusyIds,
                                onFavorite = { toggleSavedProductFromCard(listing.id) }
                                    )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "marketplace-explore-title") { MarketplaceReferenceSectionTitle("Explore categories") }
                    item(key = "marketplace-explore-categories") {
                        MarketplaceExploreCategoryTiles(categories.filter { it != "All" }, category) { itemCategory ->
                            category = itemCategory
                            showAllProducts = true
                        }
                    }
                    if (listings.size > 8) {
                        item(key = "marketplace-new-title") {
                            MarketplaceReferenceSectionTitle("More to explore", action = "See all", onAction = { showAllProducts = true })
                        }
                        item(key = "marketplace-new-listings") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
                                items(listings.drop(8).take(8), key = { "more-${it.id}" }) { listing ->
                                    Box(Modifier.width(208.dp)) {
                                        MarketplaceReferenceProductCard(
                                            listing = listing,
                                            sellerRating = sellerReputations[listing.sellerUsername.removePrefix("@").trim().lowercase()],
                                            sellerPhotoId = sellerPhotoIds[listing.sellerUsername.removePrefix("@").trim().lowercase()],
                                            onOpen = { selected = listing },
                                            onProfile = { sellerStoreUsername = listing.sellerUsername },
                                            onContact = { contactSeller(listing.sellerUsername, listing.id) },
                                isFavorite = listing.id in savedProductIds,
                                isFavoriteBusy = listing.id in savedProductBusyIds,
                                onFavorite = { toggleSavedProductFromCard(listing.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (listings.isNotEmpty()) FloatingActionButton(onClick = { showSell = true }, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().imePadding().padding(end = 18.dp, bottom = 18.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Sell", modifier = Modifier.padding(end = 14.dp)) }
    }

    if (showSell) MarketplaceSellDialog(context, onPublished = { listingId -> showSell = false; publishSuccessListingId = listingId; reload() }, onCancel = { showSell = false })
    publishSuccessListingId?.let { listingId ->
        AlertDialog(
            onDismissRequest = { publishSuccessListingId = null },
            title = { Text("Product published") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("FYNX accepted your listing request.")
                    Text("Product ID: $listingId", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Marketplace is refreshing. If the product does not appear, open My Shop and check the listing before trying to publish it again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { publishSuccessListingId = null; onOpenMyShop() }) {
                    Text("Open My Shop")
                }
            },
            dismissButton = {
                TextButton(onClick = { publishSuccessListingId = null; reload() }) {
                    Text("Refresh Marketplace")
                }
            }
        )
    }
    LaunchedEffect(selected?.id) {
        priceWatchListingId = null
        val requestGeneration = savedProductStateGeneration
        selected?.id?.let { listingId ->
            FynxRemoteSocialClient.marketplacePriceWatchState(context, listingId)
                .onSuccess { watched -> priceWatchListingId = if (watched) listingId else null }
            FynxRemoteSocialClient.marketplaceProductSaved(context, listingId)
                .onSuccess { saved ->
                    if (savedProductStateGeneration == requestGeneration) setSavedProductState(listingId, saved)
                }
        }
    }

    selected?.let { listing ->
        val sellerKey = listing.sellerUsername.removePrefix("@").trim().lowercase()
        val viewerUsername = currentUsername.removePrefix("@").trim()
        val isOwnListing = viewerUsername.isNotBlank() && listing.sellerUsername.removePrefix("@").trim().equals(viewerUsername, ignoreCase = true)
        MarketplaceDetails(
            l = listing,
            canPurchase = !isOwnListing,
            sellerReputation = sellerReputations[sellerKey],
            onProfile = { sellerStoreUsername = listing.sellerUsername; selected = null },
            onContact = { contactSeller(listing.sellerUsername, listing.id) },
            onBuyNow = { selected = null; checkoutListing = listing },
            onAddToCart = { if (cart.none { it.id == listing.id }) { if (cart.size >= 20) error = "Your FYNX cart can contain at most 20 different products." else { cart = cart + listing; cartQuantities = cartQuantities + (listing.id to 1) } }; selected = null },
            onBuyTogether = { FynxShareActions.share(context, FynxShareActions.marketplacePayload(listing.id, listing.title), onShareToStatus = onShareListingToStatus) },
            onShareToStatus = onShareListingToStatus,
            onLiveProof = { onLiveProof(listing.sellerUsername) },
            watchedPrice = priceWatchListingId == listing.id,
            priceWatchBusy = priceWatchBusy,
            productSaved = listing.id in savedProductIds,
            savedProductBusy = savedProductBusy,
            onToggleSavedProduct = { listingId ->
                if (!savedProductBusy) {
                    savedProductBusy = true
                    savedProductStateGeneration += 1
                    scope.launch {
                        FynxRemoteSocialClient.toggleMarketplaceProductSaved(context, listingId, knownSavedProductState(listingId))
                            .onSuccess { saved -> setSavedProductState(listingId, saved) }
                            .onFailure { error = marketplaceSafeError(it, "Saved Products could not be updated.") }
                        savedProductBusy = false
                    }
                }
            },
            onWatchPrice = { listingId ->
                if (!priceWatchBusy) {
                    priceWatchBusy = true
                    scope.launch {
                        val result = if (priceWatchListingId == listingId) {
                            FynxRemoteSocialClient.unwatchMarketplacePrice(context, listingId)
                        } else {
                            FynxRemoteSocialClient.watchMarketplacePrice(context, listingId)
                        }
                        result.onSuccess { watched -> priceWatchListingId = if (watched) listingId else null }
                            .onFailure { error = marketplaceSafeError(it, "Price Watch could not be updated.") }
                        priceWatchBusy = false
                    }
                }
            },
            onOpenAssistant = { listingId -> onOpenAi(listingId) },
            onClose = { selected = null }
        )
    }
    sellerStoreUsername?.let { username ->
        FynxMarketplaceSellerStoreDialog(
            username = username,
            viewerUsername = currentUsername,
            listings = listings.filter { it.sellerUsername.removePrefix("@").trim().equals(username.removePrefix("@").trim(), ignoreCase = true) },
            reputation = sellerReputations[username.removePrefix("@").trim().lowercase()],
            photoId = sellerPhotoIds[username.removePrefix("@").trim().lowercase()],
            onOpenListing = { selected = it; sellerStoreUsername = null },
            onContact = { listing -> contactSeller(username, listing.id) },
            onClose = { sellerStoreUsername = null }
        )
    }
    checkoutListing?.let { listing -> FynxMarketplaceCheckoutDialog(context = context, listing = listing, onProtectedOrder = { order -> checkoutListing = null; orders = listOf(order) + orders.filterNot { it.id == order.id }; paymentOrder = order }, onClose = { checkoutListing = null }) }
    paymentOrder?.let { order -> MarketplacePaymentDialog(context = context, order = order, onPaid = { clearMarketplaceCheckoutOrderIdForOrder(context, order.id); paymentOrder = null; cart = cart.filterNot { it.id == order.listingId }; cartQuantities = cartQuantities.filterKeys { it != order.listingId }; protectedOrder = order; reload() }, onClose = { paymentOrder = null }) }
    protectedOrder?.let { order -> MarketplaceProtectedOrderDialog(order = order, onViewOrder = { protectedOrder = null; showOrders = true }, onContinue = { protectedOrder = null }) }
    if (showCart) MarketplaceCartDialog(items = cart, quantities = cartQuantities, onQuantityChange = { id, value -> cartQuantities = cartQuantities + (id to value) }, onRemove = { item -> cart = cart.filterNot { it.id == item.id }; cartQuantities = cartQuantities - item.id }, onCheckout = { selectedItems -> showCart = false; multiCheckoutItems = selectedItems }, onClose = { showCart = false })
    multiCheckoutItems?.let { selectedItems -> FynxMarketplaceMultiCheckoutDialog(context, selectedItems, cartQuantities, onPaymentReady = { id, total, currency -> multiPaymentListingIds = selectedItems.map { it.id }.toSet(); multiCheckoutItems = null; multiPayment = Triple(id, total, currency) }, onClose = { multiCheckoutItems = null }) }
    multiPayment?.let { p -> MarketplaceMultiProductPaymentDialog(context, p.first, p.second, p.third, onPaid = {
        val paidIds = multiPaymentListingIds
        context.getSharedPreferences("fynx_marketplace", android.content.Context.MODE_PRIVATE)
            .edit().putString("last_checkout_receipt_id:$cartOwnerKey", p.first).apply()
        completedCheckoutId = p.first
        cart = cart.filterNot { it.id in paidIds }
        cartQuantities = cartQuantities.filterKeys { it !in paidIds }
        multiPaymentListingIds = emptySet()
        multiPayment = null
        reload()
    }, onClose = { multiPaymentListingIds = emptySet(); multiPayment = null }) }
    if (showOrders) MarketplaceOrders(context, orders, ordersLoading, ordersError, onRefresh = { reloadOrders() }, onClose = { showOrders = false })
}

internal data class FynxSavedMarketplaceCart(
    val items: List<FynxRemoteSocialClient.MarketplaceListing>,
    val quantities: Map<String, Int>
)

private const val FYNX_MARKETPLACE_CART_KEY = "marketplace_cart_snapshot_v1"

private fun marketplaceCartStorageKey(ownerKey: String): String = "$FYNX_MARKETPLACE_CART_KEY:${ownerKey.trim().lowercase(Locale.US)}"

internal fun readFynxMarketplaceCart(context: android.content.Context, ownerKey: String): FynxSavedMarketplaceCart {
    return runCatching {
        val raw = context.getSharedPreferences("fynx_marketplace", android.content.Context.MODE_PRIVATE)
            .getString(marketplaceCartStorageKey(ownerKey), null)
            ?: return FynxSavedMarketplaceCart(emptyList(), emptyMap())
        val array = JSONArray(raw)
        val items = mutableListOf<FynxRemoteSocialClient.MarketplaceListing>()
        val quantities = linkedMapOf<String, Int>()
        for (index in 0 until minOf(array.length(), 20)) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val cartQuantity = item.optInt("cartQuantity", 1)
            if (id.isBlank() || cartQuantity !in 1..999 || items.any { it.id == id }) continue
            val media = item.optJSONArray("mediaIds") ?: JSONArray()
            val mediaIds = (0 until media.length()).mapNotNull { media.optString(it).trim().takeIf(String::isNotBlank) }
            val listing = FynxRemoteSocialClient.MarketplaceListing(
                id = id,
                sellerId = item.optString("sellerId"),
                sellerUsername = item.optString("sellerUsername"),
                sellerDisplayName = item.optString("sellerDisplayName"),
                storeName = item.optString("storeName"),
                title = item.optString("title"),
                description = item.optString("description"),
                price = item.optDouble("price", Double.NaN),
                currency = item.optString("currency"),
                category = item.optString("category"),
                condition = item.optString("condition"),
                quantity = item.optInt("quantity", 0),
                location = item.optString("location"),
                deliveryAvailable = item.optBoolean("deliveryAvailable"),
                pickupAvailable = item.optBoolean("pickupAvailable"),
                deliveryFee = if (item.isNull("deliveryFee")) null else item.optDouble("deliveryFee"),
                mediaIds = mediaIds,
                active = item.optBoolean("active", false)
            )
            if (!listing.price.isFinite() || listing.price < 0.0 || listing.currency.isBlank()) continue
            items += listing
            quantities[id] = cartQuantity
        }
        FynxSavedMarketplaceCart(items, quantities)
    }.getOrElse { FynxSavedMarketplaceCart(emptyList(), emptyMap()) }
}

internal fun writeFynxMarketplaceCart(
    context: android.content.Context,
    ownerKey: String,
    items: List<FynxRemoteSocialClient.MarketplaceListing>,
    quantities: Map<String, Int>
) {
    runCatching {
        val array = JSONArray()
        items.distinctBy { it.id }.take(20).forEach { listing ->
            val cartQuantity = quantities[listing.id] ?: return@forEach
            if (cartQuantity !in 1..999) return@forEach
            array.put(JSONObject().apply {
                put("id", listing.id)
                put("sellerId", listing.sellerId)
                put("sellerUsername", listing.sellerUsername)
                put("sellerDisplayName", listing.sellerDisplayName)
                put("storeName", listing.storeName)
                put("title", listing.title)
                put("description", listing.description)
                put("price", listing.price)
                put("currency", listing.currency)
                put("category", listing.category)
                put("condition", listing.condition)
                put("quantity", listing.quantity)
                put("location", listing.location)
                put("deliveryAvailable", listing.deliveryAvailable)
                put("pickupAvailable", listing.pickupAvailable)
                if (listing.deliveryFee == null) put("deliveryFee", JSONObject.NULL) else put("deliveryFee", listing.deliveryFee)
                put("mediaIds", JSONArray().apply { listing.mediaIds.forEach { put(it) } })
                put("active", listing.active)
                put("cartQuantity", cartQuantity)
            })
        }
        context.getSharedPreferences("fynx_marketplace", android.content.Context.MODE_PRIVATE)
            .edit().putString(marketplaceCartStorageKey(ownerKey), array.toString()).apply()
    }
}

@Composable
private fun MarketplaceProtectedOrderDialog(order: FynxRemoteSocialClient.MarketplaceOrder, onViewOrder: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(onDismissRequest = onContinue, icon = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) }, title = { Text("Order protected") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Payment received", style = MaterialTheme.typography.titleMedium); Text(order.productTitle.ifBlank { "FYNX order" }); Text("${order.currency} ${String.format(Locale.US, "%,.2f", order.totalAmount)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary); Text("Your payment is protected by FYNX. The seller will fulfill the order, and funds remain protected until the order reaches the appropriate completion state."); Text("Order #${order.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, confirmButton = { Button(onClick = onViewOrder) { Text("View order") } }, dismissButton = { TextButton(onClick = onContinue) { Text("Continue shopping") } })
}

@Composable
private fun MarketplaceCard(l: FynxRemoteSocialClient.MarketplaceListing, onProfile: () -> Unit, onContact: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    val username = l.sellerUsername.removePrefix("@").trim()
    var photoId by remember(username) { mutableStateOf<String?>(null) }
    LaunchedEffect(username) { if (username.isNotBlank()) FynxProfileRemoteClient.get(context, username).onSuccess { photoId = it.profilePhotoMediaId } }
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onProfile, modifier = Modifier.size(34.dp)) { FynxRemoteProfileAvatar(photoId, l.sellerDisplayName.ifBlank { l.sellerUsername }, Modifier.size(30.dp).clip(RoundedCornerShape(50)), ownerUsername = l.sellerUsername) }
                Column(Modifier.weight(1f).padding(start = 2.dp)) {
                    Text(l.sellerDisplayName.ifBlank { l.sellerUsername.removePrefix("@") }, fontWeight = FontWeight.SemiBold, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                    Text(l.storeName.ifBlank { "FYNX seller" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            if (l.mediaIds.isNotEmpty()) RemoteMarketMedia(context, l.mediaIds.first(), Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)), l.mediaTypes.firstOrNull())
            else Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) { Icon(Icons.Default.ShoppingBag, "Product", Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary) }
            Text(l.title, fontWeight = FontWeight.Bold, maxLines = 2, minLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(l.currency.uppercase() + " " + String.format(Locale.US, "%,.2f", l.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            if (l.location.isNotBlank() || l.quantity in 1..5) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (l.location.isNotBlank()) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            l.location,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (l.quantity in 1..5) {
                        Text(
                            "Only ${l.quantity} left",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1
                        )
                    }
                }
            }
            TextButton(onClick = onContact, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 0.dp)) { Icon(Icons.Default.Phone, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Contact") }
        }
    }
}

@Composable
private fun MarketplaceSellerCard(listing: FynxRemoteSocialClient.MarketplaceListing, reputation: FynxMarketplaceClient.SellerReputation, photoId: String?, onProfile: () -> Unit) {
    Surface(Modifier.width(190.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FynxRemoteProfileAvatar(photoId, listing.sellerDisplayName.ifBlank { listing.sellerUsername }, Modifier.size(42.dp).clip(RoundedCornerShape(50)), ownerUsername = listing.sellerUsername)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(listing.sellerDisplayName.ifBlank { listing.sellerUsername.removePrefix("@") }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                Text(listing.category + " • " + reputation.successfulSales + " sales", maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (reputation.reviewCount > 0) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Star, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary); Text(" " + String.format(Locale.US, "%.1f", reputation.averageRating), style = MaterialTheme.typography.labelSmall) } }
                TextButton(onClick = onProfile, contentPadding = PaddingValues(0.dp)) { Text("View Store") }
            }
        }
    }
}
@Composable
private fun RemoteMarketMedia(context: android.content.Context, mediaId: String, modifier: Modifier, mimeType: String? = null) { val mediaUrl = remember(mediaId) { FynxMarketplaceClient.mediaUrl(context, mediaId) }; FynxRemoteMedia(mediaUrl, FynxMarketplaceClient.mediaDisplayType(mimeType), modifier) }

@Composable
private fun MarketplaceDetails(
    l: FynxRemoteSocialClient.MarketplaceListing,
    canPurchase: Boolean = true,
    sellerReputation: FynxMarketplaceClient.SellerReputation?,
    onProfile: () -> Unit,
    onContact: () -> Unit,
    onBuyNow: () -> Unit,
    onAddToCart: () -> Unit,
    onBuyTogether: () -> Unit,
    onShareToStatus: (String, String) -> Unit,
    onLiveProof: () -> Unit,
    watchedPrice: Boolean,
    priceWatchBusy: Boolean,
    productSaved: Boolean,
    savedProductBusy: Boolean,
    onToggleSavedProduct: (String) -> Unit,
    onWatchPrice: (String) -> Unit,
    onOpenAssistant: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onClose, title = { Text("Product details", maxLines = 1) }, text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (l.mediaIds.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(l.mediaIds.take(12).mapIndexed { index, mediaId -> mediaId to l.mediaTypes.getOrNull(index) }) { (mediaId, mimeType) -> RemoteMarketMedia(LocalContext.current, mediaId, Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)), mimeType) } }
        MarketplaceReferenceProductSummary(
            listing = l,
            onMessageSeller = onContact,
            onBuyNow = onBuyNow,
            onShare = { FynxShareActions.share(context, FynxShareActions.marketplacePayload(l.id, l.title), onShareToStatus = onShareListingToStatus) },
            canPurchase = canPurchase
        )
        if (!canPurchase) {
            Text("This is your listing. Buyers can see it in Marketplace, but you cannot buy your own product.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        // Put seller and fulfillment facts immediately after the price summary,
        // before the long description, matching the product-detail hierarchy.
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Seller:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(l.sellerDisplayName.ifBlank { l.sellerUsername }, fontWeight = FontWeight.SemiBold)
                        if (l.location.isNotBlank()) {
                            Text("Location: ${l.location}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = onProfile) { Text("View shop") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (l.quantity > 0) "${l.quantity} available" else "Out of stock",
                        color = if (l.quantity > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(l.condition, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (l.deliveryAvailable || l.pickupAvailable) {
                    Text(
                        listOfNotNull(
                            if (l.deliveryAvailable) "Delivery available${l.deliveryFee?.let { " • ${l.currency} ${String.format(Locale.US, "%,.2f", it)} fee" } ?: ""}" else null,
                            if (l.pickupAvailable) "Pickup available" else null
                        ).joinToString("  •  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onContact) { Text("Contact seller") }
            }
        }
        MarketplaceReferenceDescriptionCard(listing = l)
        Text("🛡 FYNX protected payment", fontWeight = FontWeight.SemiBold)
        Text("Payment stays protected through the existing FYNX order lifecycle until the appropriate completion state.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        sellerReputation?.let { reputation ->
            FynxMarketplaceTrustPassport(
                sellerUsername = l.sellerUsername,
                reputation = reputation,
                onOpenProfile = { _ -> onProfile() }
            )
        }
        FynxMarketplaceBuyTogether(
            listing = l,
            onStart = { _ -> onBuyTogether() }
        )
        FynxMarketplaceLiveProof(
            listing = l,
            onRequestProof = { _, _ -> onLiveProof() }
        )
        FynxMarketplacePriceWatch(
            listing = l,
            watched = watchedPrice,
            busy = priceWatchBusy,
            onWatchPrice = { onWatchPrice(it) }
        )
        OutlinedButton(
            onClick = { onToggleSavedProduct(l.id) },
            enabled = !savedProductBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (savedProductBusy) "Updating saved products..." else if (productSaved) "Remove from Saved Products" else "Save product")
        }
        FynxMarketplaceBuyingAssistant(
            listing = l,
            onOpenAssistant = { listingId -> onOpenAssistant(listingId) }
        )
    } }, confirmButton = {
        Button(onClick = onAddToCart, enabled = canPurchase && l.quantity > 0, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ShoppingCart, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (!canPurchase) "Your listing" else if (l.quantity > 0) "Add to cart" else "Out of stock")
        }
    }, dismissButton = { TextButton(onClick = onProfile) { Text("View seller") } })
}

@Composable
private fun MarketplacePaymentDialog(context: android.content.Context, order: FynxRemoteSocialClient.MarketplaceOrder, onPaid: () -> Unit, onClose: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf<FynxMarketplacePayment?>(null) }
    var busy by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy && !verifying) onClose() },
        title = { Text("Secure checkout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(order.productTitle.ifBlank { "FYNX order" }, style = MaterialTheme.typography.titleMedium)
                Text("${order.currency} ${String.format(Locale.US, "%,.2f", order.totalAmount)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                if (payment == null) {
                    Text("Enter the email you want to use for payment. Your FYNX password or payment secret is never requested here.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Payment email") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                } else {
                    Text("Checkout was opened. After completing payment, return to FYNX and verify the payment.", style = MaterialTheme.typography.bodySmall)
                    Text("Reference: ${payment?.reference.orEmpty()}", style = MaterialTheme.typography.labelSmall)
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (payment == null) {
                Button(enabled = !busy, onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        initializeMarketplacePayment(context, order.id, email)
                            .onSuccess { checkout ->
                                payment = checkout
                                openMarketplaceCheckout(context, checkout.authorizationUrl).onFailure {
                                    payment = null
                                    message = marketplaceSafeError(it, "Could not open payment checkout.")
                                }
                            }
                            .onFailure { message = marketplaceSafeError(it, "Could not start payment.") }
                        busy = false
                    }
                }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Continue to payment") }
            } else {
                Button(enabled = !verifying, onClick = {
                    verifying = true
                    message = null
                    scope.launch {
                        verifyMarketplacePayment(context, payment?.reference.orEmpty()).onSuccess { onPaid() }.onFailure { message = marketplaceSafeError(it, "Payment is not verified yet.") }
                        verifying = false
                    }
                }) { if (verifying) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Verify payment") }
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy && !verifying) { Text("Close") } }
    )
}

@Composable
private fun MarketplaceSellDialog(context: android.content.Context, onPublished: (String) -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf("") }; var desc by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }; var currency by remember { mutableStateOf(FynxMarketplaceSellerFlowSupport.DEFAULT_CURRENCY) }; var quantity by remember { mutableStateOf("1") }; var category by remember { mutableStateOf("Electronics") }; var condition by remember { mutableStateOf("NEW") }; var brand by remember { mutableStateOf("") }; var model by remember { mutableStateOf("") }; var size by remember { mutableStateOf("") }; var color by remember { mutableStateOf("") }; var material by remember { mutableStateOf("") }; var dimensions by remember { mutableStateOf("") }; var warranty by remember { mutableStateOf("") }; var vehicleYear by remember { mutableStateOf("") }; var mileage by remember { mutableStateOf("") }; var beautyDetails by remember { mutableStateOf("") }; var serviceDetails by remember { mutableStateOf("") }; var generalDetails by remember { mutableStateOf("") }; var location by remember { mutableStateOf("") }; var delivery by remember { mutableStateOf(false) }; var pickup by remember { mutableStateOf(true) }; var media by remember { mutableStateOf<List<Uri>>(emptyList()) }; var showCamera by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }; var locationLoading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    val sellerLocationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { it }) {
            scope.launch {
                locationLoading = true
                FynxPostLocationClient.currentPlace(context)
                    .onSuccess { place -> location = place }
                    .onFailure { error = marketplaceSafeError(it, "FYNX could not identify this location.") }
                locationLoading = false
            }
        } else error = "Location permission is needed to add your listing area."
    }
    fun useCurrentListingLocation() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            scope.launch {
                locationLoading = true
                FynxPostLocationClient.currentPlace(context)
                    .onSuccess { place -> location = place }
                    .onFailure { error = marketplaceSafeError(it, "FYNX could not identify this location.") }
                locationLoading = false
            }
        } else sellerLocationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val selectedChipColors = fynxMarketplaceSelectedChipColors()
    val listingDescription = remember(desc, category, brand, model, size, color, material, dimensions, warranty, vehicleYear, mileage, beautyDetails, serviceDetails, generalDetails) {
        val specifications = when (category) {
            "Electronics" -> listOf("Brand" to brand, "Model" to model, "Warranty" to warranty)
            "Fashion" -> listOf("Size" to size, "Colour" to color, "Material" to material)
            "Home" -> listOf("Material" to material, "Dimensions" to dimensions, "Colour" to color)
            "Vehicles" -> listOf("Make / brand" to brand, "Model" to model, "Year" to vehicleYear, "Mileage" to mileage)
            "Beauty" -> listOf("Brand" to brand, "Size / volume" to size, "Product details" to beautyDetails)
            "Services" -> listOf("Service details" to serviceDetails)
            else -> listOf("Brand / maker" to brand, "Colour" to color, "Additional details" to generalDetails)
        }.filter { it.second.isNotBlank() }
        buildString {
            append(desc.trim())
            if (specifications.isNotEmpty()) {
                append("\n\nProduct specifications\n")
                specifications.forEach { (label, value) -> append(label).append(": ").append(value.trim()).append('\n') }
            }
        }.trim().take(FynxMarketplaceSellerFlowSupport.MAX_DESCRIPTION_LENGTH)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(FynxMarketplaceSellerFlowSupport.MAX_PRODUCT_MEDIA)) { uris -> if (uris.isNotEmpty()) media = FynxMarketplaceSellerFlowSupport.normalizedMedia(context, media + uris) }
    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, title = { Text("Sell on FYNX") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Add product media, then enter the key details.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Product media (${media.size}/${FynxMarketplaceSellerFlowSupport.MAX_PRODUCT_MEDIA})", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, enabled = !busy && media.size < FynxMarketplaceSellerFlowSupport.MAX_PRODUCT_MEDIA, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Collections, null); Spacer(Modifier.width(5.dp)); Text("Choose media") }; OutlinedButton(onClick = { showCamera = true }, enabled = !busy && media.size < FynxMarketplaceSellerFlowSupport.MAX_PRODUCT_MEDIA, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(5.dp)); Text("Camera") } }
        if (media.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(vertical = 2.dp)) { items(media, key = { it.toString() }) { uri -> Box(Modifier.width(78.dp).height(78.dp)) { val isVideo = contextIsVideo(LocalContext.current, uri); if (isVideo) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) { Icon(Icons.Default.Videocam, "Video") } else AndroidView(factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } }, update = { imageView -> imageView.setImageURI(uri) }, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))); IconButton(onClick = { media = media.filterNot { it == uri } }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) { Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error) } } } }
        Text("1. Product basics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(title, { title = it.take(FynxMarketplaceSellerFlowSupport.MAX_TITLE_LENGTH) }, label = { Text("Product name") }, supportingText = { Text("Use a clear name buyers can search for.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Category", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Electronics", "Fashion", "Home", "Beauty", "Vehicles", "Services", "Food", "Property", "Agriculture", "Phones & Computers", "Furniture", "Baby & Kids", "Books", "Sports", "Tools", "Health", "Other").forEach { item -> FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) }, colors = selectedChipColors) } }
        Text("Condition", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("NEW" to "New", "USED" to "Used", "REFURBISHED" to "Refurbished").forEach { (value, label) -> FilterChip(selected = condition == value, onClick = { condition = value }, label = { Text(label) }, colors = selectedChipColors) } }
        Text("2. Price and stock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Choose one currency for this listing. Buyers see the price exactly in the selected currency.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FynxMarketplaceSellerFlowSupport.SUPPORTED_CURRENCIES.forEach { code -> FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code) }, colors = selectedChipColors) } }
        OutlinedTextField(price, { price = it.filter { ch -> ch.isDigit() || ch == '.' }.take(14) }, label = { Text("Price ($currency)") }, supportingText = { Text("Enter a positive amount.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(9) }, label = { Text("Available quantity") }, supportingText = { Text("How many units can you supply?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("3. Details buyers care about", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("These optional fields are added to your product description so buyers can compare listings more confidently.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (category) {
            "Electronics" -> {
                OutlinedTextField(brand, { brand = it.take(100) }, label = { Text("Brand") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it.take(100) }, label = { Text("Model / model number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(warranty, { warranty = it.take(160) }, label = { Text("Warranty or included accessories") }, modifier = Modifier.fillMaxWidth())
            }
            "Fashion" -> {
                OutlinedTextField(size, { size = it.take(80) }, label = { Text("Size / fit") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(color, { color = it.take(80) }, label = { Text("Colour") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(material, { material = it.take(120) }, label = { Text("Material / fabric") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            "Home" -> {
                OutlinedTextField(material, { material = it.take(120) }, label = { Text("Material") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dimensions, { dimensions = it.take(120) }, label = { Text("Dimensions (e.g. 120 × 60 × 75 cm)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(color, { color = it.take(80) }, label = { Text("Colour / finish") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            "Vehicles" -> {
                OutlinedTextField(brand, { brand = it.take(100) }, label = { Text("Make / brand") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it.take(100) }, label = { Text("Model / trim") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(vehicleYear, { vehicleYear = it.filter(Char::isDigit).take(4) }, label = { Text("Year") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(mileage, { mileage = it.take(80) }, label = { Text("Mileage / usage") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            "Beauty" -> {
                OutlinedTextField(brand, { brand = it.take(100) }, label = { Text("Brand") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(size, { size = it.take(80) }, label = { Text("Size / volume") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(beautyDetails, { beautyDetails = it.take(500) }, label = { Text("Ingredients, skin/hair type, or expiry details") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
            "Services" -> OutlinedTextField(serviceDetails, { serviceDetails = it.take(500) }, label = { Text("What is included, duration, and service area") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            else -> {
                OutlinedTextField(brand, { brand = it.take(100) }, label = { Text("Brand / maker (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(color, { color = it.take(80) }, label = { Text("Colour (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(generalDetails, { generalDetails = it.take(500) }, label = { Text("Other useful product details") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        }
        OutlinedTextField(desc, { desc = it.take(4000) }, label = { Text("Product description") }, supportingText = { Text("Describe condition, included items, defects, and anything buyers should know.") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Text("4. Location and delivery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(location, { location = it.take(160) }, label = { Text("Listing area") }, supportingText = { Text("Use a town, city, or state buyers recognise.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { useCurrentListingLocation() }, enabled = !busy && !locationLoading, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (locationLoading) "Finding your area..." else "Use my current area") }
        Text("FYNX uses a human-readable area for discovery; your exact GPS coordinates are not published with the listing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(delivery, { delivery = it }); Text("Delivery") }; Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(pickup, { pickup = it }); Text("Pickup") }; error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(enabled = !busy && location.trim().isNotBlank() && FynxMarketplaceSellerFlowSupport.validListing(title, listingDescription, price.toDoubleOrNull(), quantity.toIntOrNull(), media) && FynxMarketplaceSellerFlowSupport.isSupportedCurrency(currency), onClick = { busy = true; error = null; scope.launch { FynxRemoteSocialClient.createMarketplaceListing(context, title, listingDescription, "", price.toDouble(), currency, category, condition, quantity.toIntOrNull() ?: 1, location, delivery, pickup, null, media).onSuccess { listingId -> onPublished(listingId) }.onFailure { error = marketplaceSafeError(it, "Listing could not be published."); busy = false } } }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Publish") } }, dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") } })
    if (showCamera) Dialog(onDismissRequest = { showCamera = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().safeDrawingPadding()) { FynxCameraCapturePanel(onCaptured = { uri, _ -> media = FynxMarketplaceSellerFlowSupport.addMedia(context, media, uri); showCamera = false }, onDismiss = { showCamera = false }) } } }
}

private fun contextIsVideo(context: android.content.Context, uri: Uri): Boolean { val mime = context.contentResolver.getType(uri).orEmpty().lowercase(); return mime.startsWith("video/") || uri.toString().lowercase().let { it.endsWith(".mp4") || it.endsWith(".webm") || it.endsWith(".3gp") || it.endsWith(".mkv") } }

@Composable
private fun MarketplaceCartDialog(items: List<FynxRemoteSocialClient.MarketplaceListing>, quantities: Map<String, Int>, onQuantityChange: (String, Int) -> Unit, onRemove: (FynxRemoteSocialClient.MarketplaceListing) -> Unit, onCheckout: (List<FynxRemoteSocialClient.MarketplaceListing>) -> Unit, onClose: () -> Unit) {
    var selectedIds by remember(items) { mutableStateOf(items.map { it.id }.toSet()) }
    val selected = items.filter { it.id in selectedIds }
    val mixed = selected.map { it.currency.trim().uppercase(Locale.US) }.distinct().size > 1
    val unavailable = selected.any { it.quantity <= 0 || (quantities[it.id] ?: 1) > it.quantity }
    AlertDialog(onDismissRequest = onClose, title = { Text("Shopping cart") }, text = { if (items.isEmpty()) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ShoppingCart, null, Modifier.size(48.dp)); Spacer(Modifier.height(8.dp)); Text("Your cart is empty."); Text("Add products from the marketplace to start checkout.", style = MaterialTheme.typography.bodySmall) } } else { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(selected.size.toString()+" of "+items.size+" selected • maximum 20 products per checkout", style = MaterialTheme.typography.bodySmall); LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 300.dp)) { items(items, key = { it.id }) { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(item.id in selectedIds, { checked -> selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id }); Column(Modifier.weight(1f)) { Text(item.title, style = MaterialTheme.typography.titleMedium); Text(item.currency+" "+String.format(Locale.US, "%,.2f", item.price), style = MaterialTheme.typography.bodySmall) }; TextButton(onClick = { onRemove(item) }) { Text("Remove") } }; Row(verticalAlignment = Alignment.CenterVertically) { Text("Qty"); TextButton(onClick = { onQuantityChange(item.id, ((quantities[item.id] ?: 1)-1).coerceAtLeast(1)) }, enabled = (quantities[item.id] ?: 1) > 1) { Text("−") }; Text((quantities[item.id] ?: 1).toString()); TextButton(onClick = { onQuantityChange(item.id, ((quantities[item.id] ?: 1)+1).coerceAtMost(item.quantity)) }, enabled = (quantities[item.id] ?: 1) < item.quantity) { Text("+") } } } } } }; if (mixed) Text("A single checkout cannot mix NGN and USD. Select one currency.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall); if (unavailable) Text("Some selected products no longer have enough stock. Remove them or reduce their quantities before checkout.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } } }, confirmButton = { Button(enabled = selected.isNotEmpty() && !mixed && !unavailable, onClick = { onCheckout(selected) }) { Text("Checkout "+selected.size.toString()+" product"+if (selected.size == 1) "" else "s") } }, dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
}

@Composable
private fun MarketplaceOrders(context: android.content.Context, orders: List<FynxRemoteSocialClient.MarketplaceOrder>, loading: Boolean, error: String?, onRefresh: () -> Unit, onClose: () -> Unit) {
    var selected by remember { mutableStateOf<FynxRemoteSocialClient.MarketplaceOrder?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("My orders") },
        text = {
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Text("Loading your orders…")
                }
                error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    Text("Check your connection and try again.", style = MaterialTheme.typography.bodySmall)
                }
                orders.isEmpty() -> Text("No orders yet.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(orders, key = { it.id }) { order ->
                        Card(onClick = { selected = order }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(order.productTitle.ifBlank { "FYNX order" }, style = MaterialTheme.typography.titleMedium)
                                Text("${order.currency} ${String.format(Locale.US, "%,.2f", order.totalAmount)} • ${order.status}", color = MaterialTheme.colorScheme.primary)
                                order.trackingReference?.let { Text("Tracking: $it", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onRefresh, enabled = !loading) { Text(if (loading) "Refreshing…" else if (error != null) "Try again" else "Refresh") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
    selected?.let { order -> OrderActions(context, order, onChanged = { selected = null; onRefresh() }, onClose = { selected = null }) }
}

@Composable
private fun OrderActions(context: android.content.Context, order: FynxRemoteSocialClient.MarketplaceOrder, onChanged: () -> Unit, onClose: () -> Unit) {
    var dispute by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf("") }
    var rating by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }
    var showLifecycle by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Order ${order.status}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(order.productTitle)
                Text("Total: ${order.currency} ${String.format(Locale.US, "%,.2f", order.totalAmount)}")
                Text("Protected order. Complete payment through an approved payment provider before shipment.", style = MaterialTheme.typography.bodySmall)
                if (order.status in setOf("PAID", "SHIPPED", "DELIVERED", "INSPECTION", "COMPLETED", "DISPUTED", "REFUNDED")) {
                    MarketplaceReceiptAction(context, order.id)
                }
                if (order.status == "PAID" || order.status == "SHIPPED" || order.status == "INSPECTION") {
                    Text("Next step", style = MaterialTheme.typography.labelLarge)
                    Text(when (order.status) { "PAID" -> "Choose delivery or pickup so the seller can fulfill the order."; "SHIPPED" -> "Confirm the order when you receive it."; else -> "Inspect the order and complete it when everything is correct." }, style = MaterialTheme.typography.bodySmall)
                }
                when {
                    dispute -> OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text("What happened?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    order.status == "PAYMENT_PENDING" -> Text("You can cancel this unpaid order.")
                    order.status == "COMPLETED" -> {
                        Text("Rate seller")
                        Row(verticalAlignment = Alignment.CenterVertically) { (1..5).forEach { star -> TextButton(onClick = { rating = star }) { Text(if (star <= rating) "★" else "☆") } } }
                        OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Review") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            when {
                dispute -> Button(onClick = { scope.launch { FynxRemoteSocialClient.disputeMarketplaceOrder(context, order.id, "OTHER", details).onSuccess { onChanged() } } }) { Text("Open dispute") }
                order.status == "PAYMENT_PENDING" -> Button(onClick = { scope.launch { FynxRemoteSocialClient.cancelMarketplaceOrder(context, order.id).onSuccess { onChanged() } } }) { Text("Cancel order") }
                order.status == "PAID" || order.status == "SHIPPED" || order.status == "INSPECTION" -> Button(onClick = { showLifecycle = true }) { Text(when (order.status) { "PAID" -> "Choose fulfillment"; "SHIPPED" -> "Confirm received"; else -> "Complete order" }) }
                order.status == "COMPLETED" -> Button(onClick = { scope.launch { FynxRemoteSocialClient.reviewMarketplaceOrder(context, order.id, rating, comment).onSuccess { onChanged() } } }) { Text("Submit review") }
                else -> Spacer(Modifier.size(1.dp))
            }
        },
        dismissButton = { TextButton(onClick = { if (!dispute && order.status != "COMPLETED") dispute = true else onClose() }) { Text(if (!dispute && order.status != "COMPLETED") "Report problem" else "Close") } }
    )

    if (showLifecycle) FynxMarketplaceOrderLifecycle(context, order, onChanged = { showLifecycle = false; onChanged() }, onClose = { showLifecycle = false })
}
private data class FynxMarketplaceMultiLine(val listing: FynxRemoteSocialClient.MarketplaceListing, val quantity: Int, val method: String, val address: FynxMarketplaceCheckoutAddress?)
internal data class FynxMarketplaceExpectedCheckoutLine(
    val listingId: String,
    val quantity: Int,
    val currency: String,
    val fulfillmentMethod: String
)
internal data class FynxMarketplaceMultiQuoteItem(
    val listingId: String,
    val sellerUsername: String,
    val productTitle: String,
    val quantity: Int,
    val unitPrice: Double,
    val currency: String,
    val fulfillmentMethod: String,
    val shippingAddress: JSONObject?,
    val subtotal: Double,
    val deliveryFee: Double,
    val marketplaceFee: Double,
    val marketplaceFeeBuyer: Double,
    val marketplaceFeeSeller: Double,
    val total: Double
)
internal data class FynxMarketplaceMultiQuote(
    val id: String,
    val expiresAt: String,
    val currency: String,
    val items: List<FynxMarketplaceMultiQuoteItem>,
    val subtotal: Double,
    val deliveryFee: Double,
    val marketplaceFeeTotal: Double,
    val fee: Double,
    val total: Double
)

internal fun parseMarketplaceMultiCheckoutQuoteResponse(
    raw: String,
    expectedLines: List<FynxMarketplaceExpectedCheckoutLine>,
    now: Instant = Instant.now()
): FynxMarketplaceMultiQuote {
    require(expectedLines.isNotEmpty() && expectedLines.size <= 20) {
        "Select between 1 and 20 products."
    }
    require(expectedLines.map { it.listingId }.distinct().size == expectedLines.size) {
        "A cart may contain each product only once."
    }
    val expectedCurrency = expectedLines.first().currency.trim().uppercase(Locale.US)
    require(expectedCurrency in setOf("NGN", "USD") &&
        expectedLines.all { it.currency.trim().uppercase(Locale.US) == expectedCurrency }) {
        "A single checkout cannot mix NGN and USD."
    }
    require(expectedLines.all {
        it.listingId.isNotBlank() && it.quantity > 0 &&
            it.fulfillmentMethod.trim().uppercase(Locale.US) in setOf("DELIVERY", "PICKUP")
    }) { "The cart contains an invalid product, quantity or fulfillment method." }

    val quote = JSONObject(raw).optJSONObject("quote")
        ?: throw IllegalStateException("The server returned an incomplete checkout quote.")

    fun textValue(obj: JSONObject, key: String): String =
        obj.optString(key).trim().takeIf { it.isNotBlank() && it != "null" }.orEmpty()
    fun requiredText(obj: JSONObject, key: String): String =
        textValue(obj, key).takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("The checkout quote is missing $key.")
    fun amount(obj: JSONObject, key: String): Double = obj.optDouble(key, Double.NaN)

    val quoteId = requiredText(quote, "id")
    if (runCatching { UUID.fromString(quoteId) }.isFailure) {
        throw IllegalStateException("The server returned an invalid checkout quote ID.")
    }
    val expiresAt = requiredText(quote, "expiresAt")
    if (marketplaceQuoteExpired(expiresAt, now)) {
        throw IllegalStateException("This checkout quote has expired. Review the exact total again.")
    }
    val currency = requiredText(quote, "currency").uppercase(Locale.US)
    if (currency != expectedCurrency) {
        throw IllegalStateException("The checkout currency changed. Review the cart before continuing.")
    }

    val expectedById = expectedLines.associateBy { it.listingId.trim() }
    val quotedItems = quote.optJSONArray("items")
        ?: throw IllegalStateException("The server returned an incomplete checkout cart.")
    if (quotedItems.length() != expectedLines.size) {
        throw IllegalStateException("The server returned an incomplete checkout cart.")
    }

    val seenIds = mutableSetOf<String>()
    val items = buildList {
        for (index in 0 until quotedItems.length()) {
            val item = quotedItems.optJSONObject(index)
                ?: throw IllegalStateException("The checkout quote contains an invalid product.")
            val listingId = requiredText(item, "listingId")
            if (!seenIds.add(listingId)) {
                throw IllegalStateException("The checkout quote contains a duplicate product.")
            }
            val expected = expectedById[listingId]
                ?: throw IllegalStateException("The checkout quote contains a product that was not in your cart.")
            val quantity = item.optInt("quantity", -1)
            val itemCurrency = requiredText(item, "currency").uppercase(Locale.US)
            val method = requiredText(item, "fulfillmentMethod").uppercase(Locale.US)
            if (quantity != expected.quantity || quantity <= 0) {
                throw IllegalStateException("The quantity for a cart product changed. Review checkout again.")
            }
            if (itemCurrency != currency || itemCurrency != expected.currency.trim().uppercase(Locale.US)) {
                throw IllegalStateException("A cart product uses a different currency. Review checkout again.")
            }
            if (method != expected.fulfillmentMethod.trim().uppercase(Locale.US)) {
                throw IllegalStateException("The fulfillment method for a cart product changed. Review checkout again.")
            }

            val sellerUsername = requiredText(item, "sellerUsername")
            val productTitle = requiredText(item, "productTitle")
            val unitPrice = amount(item, "unitPrice")
            val subtotal = amount(item, "subtotal")
            val deliveryFee = amount(item, "deliveryFee")
            val marketplaceFee = amount(item, "marketplaceFee")
            val buyerFee = amount(item, "marketplaceFeeBuyer")
            val sellerFee = amount(item, "marketplaceFeeSeller")
            val total = amount(item, "total")
            val amounts = listOf(unitPrice, subtotal, deliveryFee, marketplaceFee, buyerFee, sellerFee, total)
            if (amounts.any { !it.isFinite() }) {
                throw IllegalStateException("The checkout quote contains an invalid price or fee.")
            }
            if (unitPrice <= 0.0 || subtotal <= 0.0 || deliveryFee < 0.0 ||
                marketplaceFee < 0.0 || buyerFee < 0.0 || sellerFee < 0.0 || total <= 0.0 ||
                buyerFee > marketplaceFee + 0.02 ||
                kotlin.math.abs(marketplaceFee - buyerFee - sellerFee) > 0.02 ||
                (method == "PICKUP" && deliveryFee > 0.02)
            ) {
                throw IllegalStateException("A cart product has inconsistent price or fee values.")
            }
            val expectedSubtotal = Math.round(unitPrice * quantity * 100.0) / 100.0
            if (kotlin.math.abs(subtotal - expectedSubtotal) > 0.02) {
                throw IllegalStateException("A cart product subtotal does not match its quantity.")
            }
            val expectedTotal = Math.round((subtotal + deliveryFee + buyerFee) * 100.0) / 100.0
            if (kotlin.math.abs(total - expectedTotal) > 0.02) {
                throw IllegalStateException("A cart product total does not match its fee breakdown.")
            }
            add(
                FynxMarketplaceMultiQuoteItem(
                    listingId = listingId,
                    sellerUsername = sellerUsername,
                    productTitle = productTitle,
                    quantity = quantity,
                    unitPrice = unitPrice,
                    currency = itemCurrency,
                    fulfillmentMethod = method,
                    shippingAddress = item.optJSONObject("shippingAddress"),
                    subtotal = subtotal,
                    deliveryFee = deliveryFee,
                    marketplaceFee = marketplaceFee,
                    marketplaceFeeBuyer = buyerFee,
                    marketplaceFeeSeller = sellerFee,
                    total = total
                )
            )
        }
    }
    if (seenIds != expectedById.keys) {
        throw IllegalStateException("The server quote does not contain every selected cart product.")
    }

    val subtotal = amount(quote, "subtotal")
    val deliveryFee = amount(quote, "deliveryFee")
    val marketplaceFeeTotal = amount(quote, "marketplaceFee")
    val buyerFee = amount(quote, "marketplaceFeeBuyer")
    val total = amount(quote, "total")
    val aggregateAmounts = listOf(subtotal, deliveryFee, marketplaceFeeTotal, buyerFee, total)
    if (aggregateAmounts.any { !it.isFinite() } || subtotal <= 0.0 || deliveryFee < 0.0 ||
        marketplaceFeeTotal < 0.0 || buyerFee < 0.0 || total <= 0.0
    ) {
        throw IllegalStateException("The checkout quote contains invalid aggregate totals.")
    }
    fun sumsTo(actual: Double, expected: Double): Boolean = kotlin.math.abs(actual - expected) <= 0.02
    if (!sumsTo(subtotal, items.sumOf { it.subtotal }) ||
        !sumsTo(deliveryFee, items.sumOf { it.deliveryFee }) ||
        !sumsTo(marketplaceFeeTotal, items.sumOf { it.marketplaceFee }) ||
        !sumsTo(buyerFee, items.sumOf { it.marketplaceFeeBuyer }) ||
        !sumsTo(total, items.sumOf { it.total }) ||
        !sumsTo(total, subtotal + deliveryFee + buyerFee)
    ) {
        throw IllegalStateException("The checkout total does not reconcile with all products, delivery and FYNX fees.")
    }
    return FynxMarketplaceMultiQuote(
        id = quoteId,
        expiresAt = expiresAt,
        currency = currency,
        items = items,
        subtotal = subtotal,
        deliveryFee = deliveryFee,
        marketplaceFeeTotal = marketplaceFeeTotal,
        fee = buyerFee,
        total = total
    )
}

internal data class FynxMarketplaceMultiCheckoutResult(
    val checkoutId: String,
    val currency: String,
    val total: Double,
    val orderIds: List<String>
)

internal fun parseMarketplaceMultiCheckoutOrderResponse(
    raw: String,
    expectedQuote: FynxMarketplaceMultiQuote
): FynxMarketplaceMultiCheckoutResult {
    fun requiredText(obj: JSONObject, key: String): String =
        obj.optString(key).trim().takeIf { it.isNotBlank() && it != "null" }
            ?: throw IllegalStateException("Protected cart checkout is missing $key.")
    fun amount(obj: JSONObject, key: String): Double = obj.optDouble(key, Double.NaN)
    fun close(actual: Double, expected: Double): Boolean =
        actual.isFinite() && kotlin.math.abs(actual - expected) <= 0.02

    val root = JSONObject(raw)
    val checkout = root.optJSONObject("checkout")
        ?: throw IllegalStateException("Protected multi-product checkout was not created.")
    val checkoutId = requiredText(checkout, "id")
    if (checkoutId != expectedQuote.id || runCatching { UUID.fromString(checkoutId) }.isFailure) {
        throw IllegalStateException("The protected checkout ID does not match the reviewed cart quote.")
    }
    val currency = requiredText(checkout, "currency").uppercase(Locale.US)
    if (currency != expectedQuote.currency) {
        throw IllegalStateException("The cart currency changed before payment. Review checkout again.")
    }
    val status = requiredText(checkout, "status").uppercase(Locale.US)
    if (status != "PAYMENT_PENDING") {
        throw IllegalStateException("The protected checkout is not in a payable pending state.")
    }

    val subtotal = amount(checkout, "subtotal")
    val deliveryFee = amount(checkout, "delivery_fee")
    val marketplaceFee = amount(checkout, "marketplace_fee")
    val total = amount(checkout, "buyer_total")
    if (!close(subtotal, expectedQuote.subtotal) ||
        !close(deliveryFee, expectedQuote.deliveryFee) ||
        !close(marketplaceFee, expectedQuote.fee) ||
        !close(total, expectedQuote.total) ||
        !close(total, subtotal + deliveryFee + expectedQuote.fee)
    ) {
        throw IllegalStateException("The final cart total changed after review. No payment was started; review the exact total again.")
    }

    val rawOrders = root.optJSONArray("orders")
        ?: throw IllegalStateException("The protected checkout did not return its product orders.")
    if (rawOrders.length() != expectedQuote.items.size) {
        throw IllegalStateException("The protected checkout returned an incomplete set of product orders.")
    }
    val expectedById = expectedQuote.items.associateBy { it.listingId }
    val seenListingIds = mutableSetOf<String>()
    val seenOrderIds = mutableSetOf<String>()
    val validatedOrders = buildList {
        for (index in 0 until rawOrders.length()) {
            val rawOrder = rawOrders.optJSONObject(index)
                ?: throw IllegalStateException("The protected checkout contains an invalid product order.")
            val listingId = requiredText(rawOrder, "listingId")
            if (!seenListingIds.add(listingId)) {
                throw IllegalStateException("The protected checkout contains a duplicate product order.")
            }
            val expected = expectedById[listingId]
                ?: throw IllegalStateException("The protected checkout contains a product that was not in your reviewed cart.")
            val order = parseProtectedMarketplaceCheckoutOrderResponse(
                raw = JSONObject().put("order", rawOrder).toString(),
                expectedListingId = expected.listingId,
                expectedCurrency = expected.currency,
                expectedQuantity = expected.quantity,
                expectedFulfillmentMethod = expected.fulfillmentMethod
            )
            if (order.status.uppercase(Locale.US) != "PAYMENT_PENDING") {
                throw IllegalStateException("A product order is not in a payable pending state.")
            }
            if (!seenOrderIds.add(order.id)) {
                throw IllegalStateException("The protected checkout returned a duplicate order ID.")
            }
            if (!close(order.unitPrice, expected.unitPrice) ||
                !close(order.deliveryFee, expected.deliveryFee) ||
                !close(order.totalAmount, expected.total)
            ) {
                throw IllegalStateException("A product price changed after review. No payment was started; review the exact total again.")
            }
            if (!close(amount(rawOrder, "productSubtotal"), expected.subtotal) ||
                !close(amount(rawOrder, "marketplaceFee"), expected.marketplaceFee) ||
                !close(amount(rawOrder, "marketplaceFeeBuyer"), expected.marketplaceFeeBuyer) ||
                !close(amount(rawOrder, "marketplaceFeeSeller"), expected.marketplaceFeeSeller)
            ) {
                throw IllegalStateException("A product fee breakdown changed after review. No payment was started; review the exact total again.")
            }
            add(order)
        }
    }
    if (seenListingIds != expectedById.keys) {
        throw IllegalStateException("The protected checkout omitted one or more selected products.")
    }
    if (!close(validatedOrders.sumOf { it.totalAmount }, expectedQuote.total)) {
        throw IllegalStateException("The product orders do not reconcile with the reviewed cart total.")
    }
    return FynxMarketplaceMultiCheckoutResult(
        checkoutId = checkoutId,
        currency = currency,
        total = total,
        orderIds = validatedOrders.map { it.id }
    )
}

private suspend fun requestMultiQuote(context: android.content.Context, lines: List<FynxMarketplaceMultiLine>): Result<FynxMarketplaceMultiQuote> {
    if (lines.isEmpty() || lines.size > 20) return Result.failure(IllegalArgumentException("Select between 1 and 20 products."))
    val expectedLines = lines.map { line ->
        FynxMarketplaceExpectedCheckoutLine(line.listing.id, line.quantity, line.listing.currency, line.method)
    }
    val array = JSONArray()
    lines.forEach { line ->
        array.put(JSONObject().apply {
            put("listingId", line.listing.id.toLongOrNull() ?: throw IllegalArgumentException("Invalid product in cart."))
            put("quantity", line.quantity)
            put("fulfillmentMethod", line.method)
            line.address?.let { a ->
                put("shippingAddress", JSONObject().apply {
                    put("name", a.name.trim())
                    put("phone", a.phone.trim())
                    put("address", a.address.trim())
                    put("city", a.city.trim())
                    put("state", a.state.trim())
                    put("country", a.country.trim())
                })
            }
        })
    }
    return FynxBackendClient.postJson(context, "/api/marketplace/checkout/multi-quote", JSONObject().put("items", array).toString()).mapCatching { raw ->
        parseMarketplaceMultiCheckoutQuoteResponse(raw, expectedLines)
    }
}

private suspend fun createMultiOrder(
    context: android.content.Context,
    lines: List<FynxMarketplaceMultiLine>,
    quote: FynxMarketplaceMultiQuote
): Result<FynxMarketplaceMultiCheckoutResult> {
    if (marketplaceQuoteExpired(quote.expiresAt)) {
        return Result.failure(IllegalStateException("This checkout quote has expired. Review the exact total again."))
    }
    val expectedLines = lines.map {
        FynxMarketplaceExpectedCheckoutLine(it.listing.id, it.quantity, it.listing.currency, it.method)
    }
    if (expectedLines.size != quote.items.size || expectedLines.any { line ->
            quote.items.none { it.listingId == line.listingId && it.quantity == line.quantity &&
                it.currency == line.currency.trim().uppercase(Locale.US) &&
                it.fulfillmentMethod == line.fulfillmentMethod.trim().uppercase(Locale.US) }
        }) {
        return Result.failure(IllegalStateException("The cart changed after its quote. Review the exact total again."))
    }
    val array = JSONArray()
    lines.forEach { line ->
        val item = JSONObject().apply {
            put("listingId", line.listing.id.toLongOrNull() ?: throw IllegalArgumentException("Invalid product in cart."))
            put("quantity", line.quantity)
            put("fulfillmentMethod", line.method)
            if (line.method == "DELIVERY") {
                val address = line.address ?: throw IllegalArgumentException("Delivery address is required.")
                require(address.name.isNotBlank() && address.phone.isNotBlank() && address.address.isNotBlank()) {
                    "Delivery address is required."
                }
                put("name", address.name)
                put("phone", address.phone)
                put("address", address.address)
                put("city", address.city)
                put("state", address.state)
                put("country", address.country)
            }
        }
        array.put(item)
    }
    return FynxBackendClient.postJson(
        context,
        "/api/marketplace/checkout/multi-order",
        JSONObject().put("checkoutId", quote.id).put("items", array).toString()
    ).mapCatching { raw ->
        parseMarketplaceMultiCheckoutOrderResponse(raw, quote)
    }
}

@Composable
private fun FynxMarketplaceMultiCheckoutDialog(context: android.content.Context, items: List<FynxRemoteSocialClient.MarketplaceListing>, quantities: Map<String,Int>, onPaymentReady: (String,Double,String)->Unit, onClose: ()->Unit) {
    val scope=rememberCoroutineScope(); var methods by remember { mutableStateOf(items.associate { it.id to if(it.pickupAvailable)"PICKUP" else "DELIVERY" }) }; var name by remember{mutableStateOf("")};var phone by remember{mutableStateOf("")};var address by remember{mutableStateOf("")};var city by remember{mutableStateOf("")};var state by remember{mutableStateOf("")};var country by remember{mutableStateOf("Nigeria")};var quote by remember{mutableStateOf<FynxMarketplaceMultiQuote?>(null)};var quoteRequestGeneration by remember{mutableStateOf(0)};var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf<String?>(null)}
    fun lines()=items.map{val m=methods[it.id]?:"DELIVERY";FynxMarketplaceMultiLine(it,quantities[it.id]?:1,m,if(m=="DELIVERY")FynxMarketplaceCheckoutAddress(name,phone,address,city,state,country)else null)}
    fun invalidateQuote(){quoteRequestGeneration+=1;quote=null;message=null};fun review(){quoteRequestGeneration+=1;val requestGeneration=quoteRequestGeneration;quote=null;if(items.map{it.currency.trim().uppercase(Locale.US)}.distinct().size!=1){message="A single checkout cannot mix NGN and USD.";return};if(methods.values.any{it=="DELIVERY"}&&(name.isBlank()||phone.isBlank()||address.isBlank())){message="Name, phone and delivery address are required.";return};busy=true;scope.launch{val result=requestMultiQuote(context,lines());if(quoteRequestGeneration==requestGeneration){result.onSuccess{quote=it}.onFailure{message=marketplaceSafeError(it, "Checkout quote could not be prepared.")}};busy=false}}
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text("Checkout "+items.size+" products")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){items.forEach{item->val m=methods[item.id]?:"DELIVERY";Card(Modifier.fillMaxWidth()){Column(Modifier.padding(8.dp)){Text(item.title,style=MaterialTheme.typography.titleMedium);Text(item.currency+" × "+(quantities[item.id]?:1));Row{if(item.deliveryAvailable)FilterChip(m=="DELIVERY",{methods=methods+(item.id to "DELIVERY");invalidateQuote()},label={Text("Delivery")},enabled=!busy,colors=fynxMarketplaceSelectedChipColors());if(item.pickupAvailable)FilterChip(m=="PICKUP",{methods=methods+(item.id to "PICKUP");invalidateQuote()},label={Text("Pickup")},enabled=!busy,colors=fynxMarketplaceSelectedChipColors())}}}};if(methods.values.any{it=="DELIVERY"}){OutlinedTextField(name,{name=it;invalidateQuote()},label={Text("Full name")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth());OutlinedTextField(phone,{phone=it;invalidateQuote()},label={Text("Phone")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth());OutlinedTextField(address,{address=it;invalidateQuote()},label={Text("Delivery address")},enabled=!busy,modifier=Modifier.fillMaxWidth());Row{OutlinedTextField(city,{city=it;invalidateQuote()},label={Text("City")},singleLine=true,enabled=!busy,modifier=Modifier.weight(1f));OutlinedTextField(state,{state=it;invalidateQuote()},label={Text("State")},singleLine=true,enabled=!busy,modifier=Modifier.weight(1f))}};Button(onClick={review()},enabled=!busy,modifier=Modifier.fillMaxWidth()){if(busy)CircularProgressIndicator(Modifier.height(18.dp))else Text("Review exact total")};quote?.let{q->
    Text("Review your exact Marketplace total",style=MaterialTheme.typography.titleSmall)
    Text("Quote valid for 5 minutes. The protected checkout is recalculated and confirmed by FYNX before payment.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Quote expires: "+runCatching { Instant.parse(q.expiresAt).toString().replace("T", " ").removeSuffix("Z") }.getOrElse { q.expiresAt }, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
    q.items.forEach { quoted ->
        val listing = items.firstOrNull { it.id == quoted.listingId }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                if (listing?.mediaIds?.isNotEmpty() == true) {
                    RemoteMarketMedia(context, listing.mediaIds.first(), Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)), listing.mediaTypes.firstOrNull())
                } else {
                    Box(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
                        Icon(Icons.Default.ShoppingBag, "Product", Modifier.size(36.dp), tint=MaterialTheme.colorScheme.primary)
                    }
                }
                Text(quoted.productTitle.ifBlank { listing?.title.orEmpty() },style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.SemiBold)
                Text("Seller: "+quoted.sellerUsername.removePrefix("@"))
                Text("Quantity: "+quoted.quantity+" • "+quoted.fulfillmentMethod.lowercase().replaceFirstChar { it.uppercase() })
                Text("Unit price: "+quoted.currency+" "+String.format(Locale.US,"%,.2f",quoted.unitPrice))
                Text("Line subtotal: "+quoted.currency+" "+String.format(Locale.US,"%,.2f",quoted.subtotal))
                Text("Delivery: "+quoted.currency+" "+String.format(Locale.US,"%,.2f",quoted.deliveryFee))
                Text("FYNX fee: "+quoted.currency+" "+String.format(Locale.US,"%,.2f",quoted.marketplaceFeeBuyer))
                if (quoted.fulfillmentMethod == "DELIVERY") {
                    quoted.shippingAddress?.let { a ->
                        Text("Deliver to: "+a.optString("name")+" • "+a.optString("phone"))
                        Text(a.optString("address")+", "+a.optString("city")+", "+a.optString("state"))
                    }
                }
                Text("Line total: "+quoted.currency+" "+String.format(Locale.US,"%,.2f",quoted.total),style=MaterialTheme.typography.titleSmall)
            }
        }
    }
    Text("Subtotal: "+q.currency+" "+String.format(Locale.US,"%,.2f",q.subtotal))
    Text("Delivery: "+q.currency+" "+String.format(Locale.US,"%,.2f",q.deliveryFee))
    Text("FYNX fee: "+q.currency+" "+String.format(Locale.US,"%,.2f",q.fee))
    Text("Total: "+q.currency+" "+String.format(Locale.US,"%,.2f",q.total),style=MaterialTheme.typography.titleLarge)
    Text("The amount above is the server-authoritative quote. FYNX will revalidate inventory and totals before creating the protected checkout.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
};message?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={Button(enabled=quote!=null&&!busy,onClick={val q=quote?:return@Button;if(marketplaceQuoteExpired(q.expiresAt)){quote=null;message="This quote has expired. Review the exact total again.";return@Button};busy=true;scope.launch{createMultiOrder(context,lines(),q).onSuccess{created->onPaymentReady(created.checkoutId,created.total,created.currency);busy=false}.onFailure{message=marketplacePaymentFailureMessage(it);busy=false}}}){Text("Place order & pay")}},dismissButton={TextButton(onClick=onClose,enabled=!busy){Text("Cancel")}})
}
@Composable
private fun MarketplaceMultiProductPaymentDialog(
    context: android.content.Context,
    checkoutId: String,
    total: Double,
    currency: String,
    onPaid: () -> Unit,
    onClose: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf<FynxMarketplacePayment?>(null) }
    var busy by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy && !verifying) onClose() },
        title = { Text(if (payment == null) "Secure Marketplace checkout" else "Verify your payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Multi-product checkout", style = MaterialTheme.typography.titleMedium)
                Text(
                    "One protected payment covers the products in this cart, including the reviewed delivery and FYNX fee amounts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("TOTAL TO PAY", style = MaterialTheme.typography.labelMedium)
                Text(
                    currency + " " + String.format(Locale.US, "%,.2f", total),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (payment == null) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim().take(160) },
                        label = { Text("Paystack payment email") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Review the amount before continuing. FYNX will confirm payment with the backend before showing your combined receipt.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("Complete payment in Paystack, then return here.")
                    Text("Reference: " + payment?.reference.orEmpty(), style = MaterialTheme.typography.labelSmall)
                    Text(
                        "If confirmation is delayed, verify this same payment again rather than paying twice.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            if (payment == null) {
                Button(enabled = !busy && email.contains("@"), onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        initializeMarketplaceCheckoutGroupPayment(context, checkoutId, email).onSuccess { p ->
                            if (!p.currency.equals(currency, ignoreCase = true) || kotlin.math.abs(p.amount - total) >= 0.005) {
                                message = "The payment amount or currency does not match the reviewed total. Payment was not opened. Close checkout and review the cart again."
                            } else {
                                payment = p
                                openMarketplaceCheckout(context, p.authorizationUrl).onFailure {
                                    payment = null
                                    message = marketplaceSafeError(it, "Could not open Paystack checkout.")
                                }
                            }
                        }.onFailure { message = marketplacePaymentFailureMessage(it) }
                        busy = false
                    }
                }) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Continue to Paystack")
                }
            } else {
                Button(enabled = !verifying, onClick = {
                    verifying = true
                    message = null
                    scope.launch {
                        verifyMarketplaceCheckoutGroupPayment(context, payment?.reference.orEmpty())
                            .onSuccess { onPaid() }
                            .onFailure { message = marketplaceSafeError(it, "Payment is not verified yet. You can retry verification.") }
                        verifying = false
                    }
                }) {
                    if (verifying) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Verify payment")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !busy && !verifying) { Text("Close") }
        }
    )
}

/**
 * Appends a page while keeping the first occurrence of each stable item key.
 */
internal fun <T, K> appendDistinctMarketplacePage(
    existing: List<T>,
    incoming: List<T>,
    keySelector: (T) -> K
): List<T> {
    val knownKeys = existing.asSequence().map(keySelector).toMutableSet()
    return existing + incoming.filter { knownKeys.add(keySelector(it)) }
}
