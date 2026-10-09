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
import org.json.JSONArray
import java.time.Instant
import org.json.JSONObject
import java.util.Locale

@Composable
fun FynxMarketplacePanel(currentUsername: String = "preview", onOpenProfile: (String) -> Unit = {}, onOpenAi: (String) -> Unit = {}, onLiveProof: (String) -> Unit = {}, initialListingId: String? = null) {
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
    var cartQuantities by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var multiCheckoutItems by remember { mutableStateOf<List<FynxRemoteSocialClient.MarketplaceListing>?>(null) }
    var multiPayment by remember { mutableStateOf<Triple<String, Double, String>?>(null) }
    var showCart by remember { mutableStateOf(false) }
    var priceWatchListingId by remember { mutableStateOf<String?>(null) }
    var priceWatchBusy by remember { mutableStateOf(false) }
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Marketplace", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(if (nearbyMode && nearbyLabel.isNotBlank()) "Showing products near $nearbyLabel" else "Discover products from FYNX sellers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showOrders = true }) { Icon(Icons.Default.ReceiptLong, "Orders") }
                IconButton(onClick = { reload() }) { Icon(Icons.Default.Refresh, "Refresh") }
            }
            MarketplaceReferenceSearchBar(
                value = query,
                onValueChange = { query = it.take(80) },
                onCart = { showCart = true },
                cartCount = cartQuantities.values.sum().coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            MarketplaceReferenceCategoryRow(
                categories = categories,
                selected = category,
                onSelect = { category = it },
                onNearby = { toggleNearby() },
                nearbySelected = nearbyMode,
                nearbyLabel = if (nearbyMode) "Near ${nearbyLabel.substringBefore(",").ifBlank { "me" }}" else "Near me",
                nearbyLoading = nearbyLoading,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
            if (loading && listings.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                loading && listings.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
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
                else -> LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 132.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { if (visibleSellerListings.isNotEmpty()) { item(span = { GridItemSpan(maxLineSpan) }) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { MarketplaceReferenceSectionTitle("Top Sellers (Highest Sales)"); Text("Highest successful sales from sellers currently represented here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }; item(span = { GridItemSpan(maxLineSpan) }) { LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(start = 0.dp, end = 16.dp)) { items(visibleSellerListings, key = { it.sellerUsername.removePrefix("@").trim().lowercase() }) { seller -> MarketplaceSellerCard(seller, sellerReputations[seller.sellerUsername.removePrefix("@").trim().lowercase()]!!, sellerPhotoIds[seller.sellerUsername.removePrefix("@").trim().lowercase()], { onOpenProfile(seller.sellerUsername) }) } } }; item(span = { GridItemSpan(maxLineSpan) }) { MarketplaceReferenceSectionTitle("Products", modifier = Modifier.padding(top = 2.dp)) } }; gridItems(listings, key = { it.id }) { listing -> MarketplaceCard(l = listing, onProfile = { onOpenProfile(listing.sellerUsername) }, onContact = { contactSeller(listing.sellerUsername, listing.id) }, onOpen = { selected = listing }) } }
            }
        }
        FloatingActionButton(onClick = { showSell = true }, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().imePadding().padding(end = 18.dp, bottom = 18.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Sell", modifier = Modifier.padding(end = 14.dp)) }
    }

    if (showSell) MarketplaceSellDialog(context, onPublished = { showSell = false; reload() }, onCancel = { showSell = false })
    LaunchedEffect(selected?.id) {
        priceWatchListingId = null
        selected?.id?.let { listingId ->
            FynxRemoteSocialClient.marketplacePriceWatchState(context, listingId)
                .onSuccess { watched -> priceWatchListingId = if (watched) listingId else null }
        }
    }

    selected?.let { listing ->
        val sellerKey = listing.sellerUsername.removePrefix("@").trim().lowercase()
        MarketplaceDetails(
            l = listing,
            sellerReputation = sellerReputations[sellerKey],
            onProfile = { onOpenProfile(listing.sellerUsername); selected = null },
            onContact = { contactSeller(listing.sellerUsername, listing.id) },
            onBuyNow = { selected = null; checkoutListing = listing },
            onAddToCart = { if (cart.none { it.id == listing.id }) { if (cart.size >= 20) error = "Your FYNX cart can contain at most 20 different products." else { cart = cart + listing; cartQuantities = cartQuantities + (listing.id to 1) } }; selected = null },
            onBuyTogether = { FynxShareActions.share(context, FynxShareActions.marketplacePayload(listing.id, listing.title)) },
            onLiveProof = { onLiveProof(listing.sellerUsername) },
            watchedPrice = priceWatchListingId == listing.id,
            priceWatchBusy = priceWatchBusy,
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
                            .onFailure { error = it.message ?: "Price Watch could not be updated." }
                        priceWatchBusy = false
                    }
                }
            },
            onOpenAssistant = { listingId -> onOpenAi(listingId) },
            onClose = { selected = null }
        )
    }
    checkoutListing?.let { listing -> FynxMarketplaceCheckoutDialog(context = context, listing = listing, onProtectedOrder = { order -> checkoutListing = null; orders = listOf(order) + orders.filterNot { it.id == order.id }; paymentOrder = order }, onClose = { checkoutListing = null }) }
    paymentOrder?.let { order -> MarketplacePaymentDialog(context = context, order = order, onPaid = { paymentOrder = null; protectedOrder = order; reload() }, onClose = { paymentOrder = null }) }
    protectedOrder?.let { order -> MarketplaceProtectedOrderDialog(order = order, onViewOrder = { protectedOrder = null; showOrders = true }, onContinue = { protectedOrder = null }) }
    if (showCart) MarketplaceCartDialog(items = cart, quantities = cartQuantities, onQuantityChange = { id, value -> cartQuantities = cartQuantities + (id to value) }, onRemove = { item -> cart = cart.filterNot { it.id == item.id }; cartQuantities = cartQuantities - item.id }, onCheckout = { selectedItems -> showCart = false; multiCheckoutItems = selectedItems }, onClose = { showCart = false })
    multiCheckoutItems?.let { selectedItems -> FynxMarketplaceMultiCheckoutDialog(context, selectedItems, cartQuantities, onPaymentReady = { id, total, currency -> multiCheckoutItems = null; multiPayment = Triple(id, total, currency) }, onClose = { multiCheckoutItems = null }) }
    multiPayment?.let { p -> MarketplaceMultiProductPaymentDialog(context, p.first, p.second, p.third, onPaid = { multiPayment = null; reload() }, onClose = { multiPayment = null }) }
    if (showOrders) MarketplaceOrders(context, orders, onRefresh = { reload() }, onClose = { showOrders = false })
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
            if (l.mediaIds.isNotEmpty()) RemoteMarketMedia(context, l.mediaIds.first(), Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
            else Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) { Icon(Icons.Default.ShoppingBag, "Product", Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary) }
            Text(l.title, fontWeight = FontWeight.Bold, maxLines = 2, minLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(l.currency.uppercase() + " " + String.format(Locale.US, "%,.2f", l.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onContact, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 0.dp)) { Icon(Icons.Default.Phone, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Contact") }
        }
    }
}

@Composable
private fun MarketplaceSellerCard(listing: FynxRemoteSocialClient.MarketplaceListing, reputation: FynxMarketplaceClient.SellerReputation, photoId: String?, onProfile: () -> Unit) {
    Surface(Modifier.width(190.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFF2A2A2A)) {
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
private fun RemoteMarketMedia(context: android.content.Context, mediaId: String, modifier: Modifier) { val mediaUrl = remember(mediaId) { FynxMarketplaceClient.mediaUrl(context, mediaId) }; FynxRemoteMedia(mediaUrl, "auto", modifier) }

@Composable
private fun MarketplaceDetails(
    l: FynxRemoteSocialClient.MarketplaceListing,
    sellerReputation: FynxMarketplaceClient.SellerReputation?,
    onProfile: () -> Unit,
    onContact: () -> Unit,
    onBuyNow: () -> Unit,
    onAddToCart: () -> Unit,
    onBuyTogether: () -> Unit,
    onLiveProof: () -> Unit,
    watchedPrice: Boolean,
    priceWatchBusy: Boolean,
    onWatchPrice: (String) -> Unit,
    onOpenAssistant: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onClose, title = { Text(l.title, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (l.mediaIds.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(l.mediaIds.take(12)) { mediaId -> RemoteMarketMedia(LocalContext.current, mediaId, Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp))) } }
        Text("${l.currency} ${String.format(Locale.US, "%,.2f", l.price)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        if (l.description.isNotBlank()) Text(l.description)
        Text("Seller: ${l.sellerDisplayName.ifBlank { l.sellerUsername }}")
        Text("${l.quantity} available • ${l.condition}")
        if (l.location.isNotBlank()) Text("Location: ${l.location}")
        if (l.deliveryAvailable) Text("Delivery available${l.deliveryFee?.let { " • ${l.currency} ${String.format(Locale.US, "%,.2f", it)} fee" } ?: ""}")
        if (l.pickupAvailable) Text("Pickup available")
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
        FynxMarketplaceBuyingAssistant(
            listing = l,
            onOpenAssistant = { listingId -> onOpenAssistant(listingId) }
        )
    } }, confirmButton = { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { FynxShareActions.share(context, FynxShareActions.marketplacePayload(l.id, l.title)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Share Marketplace listing") }
        OutlinedButton(onClick = onContact, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Contact seller") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAddToCart, enabled = l.quantity > 0, modifier = Modifier.weight(1f)) { Icon(Icons.Default.ShoppingCart, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Add to cart") }
            Button(onClick = onBuyNow, enabled = l.quantity > 0, modifier = Modifier.weight(1f)) { Text("Buy now") }
        }
    } }, dismissButton = { TextButton(onClick = onProfile) { Text("View seller") } })
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
                                    message = it.message ?: "Could not open payment checkout."
                                }
                            }
                            .onFailure { message = it.message ?: "Could not start payment." }
                        busy = false
                    }
                }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Continue to payment") }
            } else {
                Button(enabled = !verifying, onClick = {
                    verifying = true
                    message = null
                    scope.launch {
                        verifyMarketplacePayment(context, payment?.reference.orEmpty()).onSuccess { onPaid() }.onFailure { message = it.message ?: "Payment is not verified yet." }
                        verifying = false
                    }
                }) { if (verifying) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Verify payment") }
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy && !verifying) { Text("Close") } }
    )
}

@Composable
private fun MarketplaceSellDialog(context: android.content.Context, onPublished: () -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf("") }; var desc by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }; var currency by remember { mutableStateOf(FynxMarketplaceSellerFlowSupport.DEFAULT_CURRENCY) }; var quantity by remember { mutableStateOf("1") }; var category by remember { mutableStateOf("Electronics") }; var location by remember { mutableStateOf("") }; var delivery by remember { mutableStateOf(false) }; var pickup by remember { mutableStateOf(true) }; var media by remember { mutableStateOf<List<Uri>>(emptyList()) }; var showCamera by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }; var locationLoading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    val sellerLocationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { it }) {
            scope.launch {
                locationLoading = true
                FynxPostLocationClient.currentPlace(context)
                    .onSuccess { place -> location = place }
                    .onFailure { error = it.message ?: "FYNX could not identify this location." }
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
                    .onFailure { error = it.message ?: "FYNX could not identify this location." }
                locationLoading = false
            }
        } else sellerLocationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(FynxMarketplaceSellerFlowSupport.MAX_PRODUCT_MEDIA)) { uris -> if (uris.isNotEmpty()) media = FynxMarketplaceSellerFlowSupport.normalizedMedia(context, media + uris) }
    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, title = { Text("Sell on FYNX") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Add product media, then enter the key details.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Product media (${media.size}/${FynxMarketplaceSellerFlowSupport.MAX_PRODUCT_MEDIA})", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, enabled = !busy && media.size < FynxMarketplaceSellerFlowSupport.MAX_PRODUCT_MEDIA, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Collections, null); Spacer(Modifier.width(5.dp)); Text("Choose media") }; OutlinedButton(onClick = { showCamera = true }, enabled = !busy && media.size < FynxMarketplaceSellerFlowSupport.MAX_PRODUCT_MEDIA, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(5.dp)); Text("Camera") } }
        if (media.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(vertical = 2.dp)) { items(media, key = { it.toString() }) { uri -> Box(Modifier.width(78.dp).height(78.dp)) { val isVideo = contextIsVideo(LocalContext.current, uri); if (isVideo) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) { Icon(Icons.Default.Videocam, "Video") } else AndroidView(factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } }, update = { imageView -> imageView.setImageURI(uri) }, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))); IconButton(onClick = { media = media.filterNot { it == uri } }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) { Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error) } } } }
        OutlinedTextField(title, { title = it }, label = { Text("Product name") }, singleLine = true, modifier = Modifier.fillMaxWidth());
        Text("Currency", style = MaterialTheme.typography.labelLarge); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FynxMarketplaceSellerFlowSupport.SUPPORTED_CURRENCIES.forEach { code -> FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code) }) } }
        OutlinedTextField(price, { price = it }, label = { Text("Price ($currency)") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, minLines = 3, modifier = Modifier.fillMaxWidth()); OutlinedTextField(location, { location = it }, label = { Text("Listing area") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { useCurrentListingLocation() }, enabled = !busy && !locationLoading, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (locationLoading) "Finding your area..." else "Use my current area") }
        Text("FYNX uses a human-readable area for discovery; your exact GPS coordinates are not published with the listing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("Electronics", "Fashion", "Home", "Beauty", "Vehicles", "Services").forEach { item -> FilterChip(category == item, { category = item }, label = { Text(item) }) } }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(delivery, { delivery = it }); Text("Delivery") }; Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(pickup, { pickup = it }); Text("Pickup") }; error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(enabled = !busy && location.trim().isNotBlank() && FynxMarketplaceSellerFlowSupport.validListing(title, desc, price.toDoubleOrNull(), quantity.toIntOrNull(), media) && FynxMarketplaceSellerFlowSupport.isSupportedCurrency(currency), onClick = { busy = true; error = null; scope.launch { FynxRemoteSocialClient.createMarketplaceListing(context, title, desc, "", price.toDouble(), currency, category, "NEW", quantity.toIntOrNull() ?: 1, location, delivery, pickup, null, media).onSuccess { onPublished() }.onFailure { error = it.message ?: "Listing could not be published."; busy = false } } }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Publish") } }, dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") } })
    if (showCamera) Dialog(onDismissRequest = { showCamera = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().safeDrawingPadding()) { FynxCameraCapturePanel(onCaptured = { uri, _ -> media = FynxMarketplaceSellerFlowSupport.addMedia(context, media, uri); showCamera = false }, onDismiss = { showCamera = false }) } } }
}

private fun contextIsVideo(context: android.content.Context, uri: Uri): Boolean { val mime = context.contentResolver.getType(uri).orEmpty().lowercase(); return mime.startsWith("video/") || uri.toString().lowercase().let { it.endsWith(".mp4") || it.endsWith(".webm") || it.endsWith(".3gp") || it.endsWith(".mkv") } }

@Composable
private fun MarketplaceCartDialog(items: List<FynxRemoteSocialClient.MarketplaceListing>, quantities: Map<String, Int>, onQuantityChange: (String, Int) -> Unit, onRemove: (FynxRemoteSocialClient.MarketplaceListing) -> Unit, onCheckout: (List<FynxRemoteSocialClient.MarketplaceListing>) -> Unit, onClose: () -> Unit) {
    var selectedIds by remember(items) { mutableStateOf(items.map { it.id }.toSet()) }
    val selected = items.filter { it.id in selectedIds }
    val mixed = selected.map { it.currency.trim().uppercase(Locale.US) }.distinct().size > 1
    AlertDialog(onDismissRequest = onClose, title = { Text("Shopping cart") }, text = { if (items.isEmpty()) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ShoppingCart, null, Modifier.size(48.dp)); Spacer(Modifier.height(8.dp)); Text("Your cart is empty."); Text("Add products from the marketplace to start checkout.", style = MaterialTheme.typography.bodySmall) } } else { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(selected.size.toString()+" of "+items.size+" selected • maximum 20 products per checkout", style = MaterialTheme.typography.bodySmall); LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 420.dp)) { items(items, key = { it.id }) { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(item.id in selectedIds, { checked -> selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id }); Column(Modifier.weight(1f)) { Text(item.title, style = MaterialTheme.typography.titleMedium); Text(item.currency+" "+String.format(Locale.US, "%,.2f", item.price), style = MaterialTheme.typography.bodySmall) }; TextButton(onClick = { onRemove(item) }) { Text("Remove") } }; Row(verticalAlignment = Alignment.CenterVertically) { Text("Qty"); TextButton(onClick = { onQuantityChange(item.id, ((quantities[item.id] ?: 1)-1).coerceAtLeast(1)) }, enabled = (quantities[item.id] ?: 1) > 1) { Text("−") }; Text((quantities[item.id] ?: 1).toString()); TextButton(onClick = { onQuantityChange(item.id, ((quantities[item.id] ?: 1)+1).coerceAtMost(item.quantity)) }, enabled = (quantities[item.id] ?: 1) < item.quantity) { Text("+") } } } } } }; if (mixed) Text("A single checkout cannot mix NGN and USD. Select one currency.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } } }, confirmButton = { Button(enabled = selected.isNotEmpty() && !mixed, onClick = { onCheckout(selected) }) { Text("Checkout "+selected.size.toString()+" product"+if (selected.size == 1) "" else "s") } }, dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
}

@Composable
private fun MarketplaceOrders(context: android.content.Context, orders: List<FynxRemoteSocialClient.MarketplaceOrder>, onRefresh: () -> Unit, onClose: () -> Unit) {
    var selected by remember { mutableStateOf<FynxRemoteSocialClient.MarketplaceOrder?>(null) }
    AlertDialog(onDismissRequest = onClose, title = { Text("My orders") }, text = { if (orders.isEmpty()) Text("No orders yet.") else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(orders, key = { it.id }) { order -> Card(onClick = { selected = order }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) { Text(order.productTitle.ifBlank { "FYNX order" }, style = MaterialTheme.typography.titleMedium); Text("${order.currency} ${String.format(Locale.US, "%,.2f", order.totalAmount)} • ${order.status}", color = MaterialTheme.colorScheme.primary); order.trackingReference?.let { Text("Tracking: $it", style = MaterialTheme.typography.bodySmall) } } } } } }, confirmButton = { TextButton(onClick = onRefresh) { Text("Refresh") } }, dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
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
private data class FynxMarketplaceMultiQuoteItem(
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
    val marketplaceFeeBuyer: Double,
    val total: Double
)
private data class FynxMarketplaceMultiQuote(
    val id: String,
    val expiresAt: String,
    val currency: String,
    val items: List<FynxMarketplaceMultiQuoteItem>,
    val subtotal: Double,
    val deliveryFee: Double,
    val fee: Double,
    val total: Double
)

private suspend fun requestMultiQuote(context: android.content.Context, lines: List<FynxMarketplaceMultiLine>): Result<FynxMarketplaceMultiQuote> {
    if (lines.isEmpty() || lines.size > 20) return Result.failure(IllegalArgumentException("Select between 1 and 20 products."))
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
        val q = JSONObject(raw).getJSONObject("quote")
        val quotedItems = q.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (i in 0 until quotedItems.length()) {
                val item = quotedItems.getJSONObject(i)
                add(
                    FynxMarketplaceMultiQuoteItem(
                        listingId = item.optString("listingId"),
                        sellerUsername = item.optString("sellerUsername"),
                        productTitle = item.optString("productTitle"),
                        quantity = item.optInt("quantity"),
                        unitPrice = item.optDouble("unitPrice"),
                        currency = item.optString("currency", q.optString("currency")).uppercase(Locale.US),
                        fulfillmentMethod = item.optString("fulfillmentMethod"),
                        shippingAddress = item.optJSONObject("shippingAddress"),
                        subtotal = item.optDouble("subtotal"),
                        deliveryFee = item.optDouble("deliveryFee"),
                        marketplaceFeeBuyer = item.optDouble("marketplaceFeeBuyer"),
                        total = item.optDouble("total")
                    )
                )
            }
        }
        require(items.size == lines.size) { "The server returned an incomplete checkout quote." }
        FynxMarketplaceMultiQuote(
            id = q.optString("id"),
            expiresAt = q.optString("expiresAt"),
            currency = q.optString("currency").uppercase(Locale.US),
            items = items,
            subtotal = q.optDouble("subtotal"),
            deliveryFee = q.optDouble("deliveryFee"),
            fee = q.optDouble("marketplaceFeeBuyer"),
            total = q.optDouble("total")
        )
    }
}
private suspend fun createMultiOrder(context: android.content.Context, lines: List<FynxMarketplaceMultiLine>, checkoutId: String): Result<JSONObject> {
    val array = JSONArray()
    lines.forEach { line ->
        val item = JSONObject().apply {
            put("listingId", line.listing.id.toLongOrNull() ?: throw IllegalArgumentException("Invalid product in cart."))
            put("quantity", line.quantity)
            put("fulfillmentMethod", line.method)
            if (line.method == "DELIVERY") {
                val address = line.address ?: throw IllegalArgumentException("Delivery address is required.")
                require(address.name.isNotBlank() && address.phone.isNotBlank() && address.address.isNotBlank()) { "Delivery address is required." }
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
    return FynxBackendClient.postJson(context,"/api/marketplace/checkout/multi-order",JSONObject().put("checkoutId",checkoutId).put("items",array).toString()).mapCatching { raw -> val o=JSONObject(raw); require(o.has("checkout")){"Protected multi-product checkout was not created."}; o }
}
@Composable
private fun FynxMarketplaceMultiCheckoutDialog(context: android.content.Context, items: List<FynxRemoteSocialClient.MarketplaceListing>, quantities: Map<String,Int>, onPaymentReady: (String,Double,String)->Unit, onClose: ()->Unit) {
    val scope=rememberCoroutineScope(); var methods by remember { mutableStateOf(items.associate { it.id to if(it.pickupAvailable)"PICKUP" else "DELIVERY" }) }; var name by remember{mutableStateOf("")};var phone by remember{mutableStateOf("")};var address by remember{mutableStateOf("")};var city by remember{mutableStateOf("")};var state by remember{mutableStateOf("")};var country by remember{mutableStateOf("Nigeria")};var quote by remember{mutableStateOf<FynxMarketplaceMultiQuote?>(null)};var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf<String?>(null)}
    fun lines()=items.map{val m=methods[it.id]?:"DELIVERY";FynxMarketplaceMultiLine(it,quantities[it.id]?:1,m,if(m=="DELIVERY")FynxMarketplaceCheckoutAddress(name,phone,address,city,state,country)else null)}
    fun review(){if(items.map{it.currency.trim().uppercase(Locale.US)}.distinct().size!=1){message="A single checkout cannot mix NGN and USD.";return};if(methods.values.any{it=="DELIVERY"}&&(name.isBlank()||phone.isBlank()||address.isBlank())){message="Name, phone and delivery address are required.";return};busy=true;scope.launch{requestMultiQuote(context,lines()).onSuccess{quote=it}.onFailure{message=it.message?:"Checkout quote could not be prepared."};busy=false}}
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text("Checkout "+items.size+" products")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){items.forEach{item->val m=methods[item.id]?:"DELIVERY";Card(Modifier.fillMaxWidth()){Column(Modifier.padding(8.dp)){Text(item.title,style=MaterialTheme.typography.titleMedium);Text(item.currency+" × "+(quantities[item.id]?:1));Row{if(item.deliveryAvailable)FilterChip(m=="DELIVERY",{methods=methods+(item.id to "DELIVERY");quote=null},label={Text("Delivery")});if(item.pickupAvailable)FilterChip(m=="PICKUP",{methods=methods+(item.id to "PICKUP");quote=null},label={Text("Pickup")})}}}};if(methods.values.any{it=="DELIVERY"}){OutlinedTextField(name,{name=it;quote=null},label={Text("Full name")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(phone,{phone=it;quote=null},label={Text("Phone")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(address,{address=it;quote=null},label={Text("Delivery address")},modifier=Modifier.fillMaxWidth());Row{OutlinedTextField(city,{city=it;quote=null},label={Text("City")},singleLine=true,modifier=Modifier.weight(1f));OutlinedTextField(state,{state=it;quote=null},label={Text("State")},singleLine=true,modifier=Modifier.weight(1f))}};Button(onClick={review()},enabled=!busy,modifier=Modifier.fillMaxWidth()){if(busy)CircularProgressIndicator(Modifier.height(18.dp))else Text("Review exact total")};quote?.let{q->
    Text("Review your exact Marketplace total",style=MaterialTheme.typography.titleSmall)
    Text("Quote valid for 5 minutes. The protected checkout is recalculated and confirmed by FYNX before payment.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Quote expires: "+runCatching { Instant.parse(q.expiresAt).toString().replace("T", " ").removeSuffix("Z") }.getOrElse { q.expiresAt }, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
    q.items.forEach { quoted ->
        val listing = items.firstOrNull { it.id == quoted.listingId }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                if (listing?.mediaIds?.isNotEmpty() == true) {
                    RemoteMarketMedia(context, listing.mediaIds.first(), Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)))
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
};message?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={Button(enabled=quote!=null&&!busy,onClick={val q=quote?:return@Button;if(marketplaceQuoteExpired(q.expiresAt)){quote=null;message="This quote has expired. Review the exact total again.";return@Button};busy=true;scope.launch{createMultiOrder(context,lines(),q.id).onSuccess{created->val c=created.getJSONObject("checkout");val serverId=c.optString("id").ifBlank{q.id};val serverTotal=c.optDouble("buyer_total",q.total);val serverCurrency=c.optString("currency",q.currency).uppercase(Locale.US);onPaymentReady(serverId,serverTotal,serverCurrency);busy=false}.onFailure{message=it.message?:"Protected checkout could not be created.";busy=false}}}){Text("Place order & pay")}},dismissButton={TextButton(onClick=onClose,enabled=!busy){Text("Cancel")}})
}
@Composable
private fun MarketplaceMultiProductPaymentDialog(context: android.content.Context, checkoutId: String, total: Double, currency: String, onPaid: ()->Unit, onClose: ()->Unit){var email by remember{mutableStateOf("")};var payment by remember{mutableStateOf<FynxMarketplacePayment?>(null)};var busy by remember{mutableStateOf(false)};var verifying by remember{mutableStateOf(false)};var message by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope();AlertDialog(onDismissRequest={if(!busy&&!verifying)onClose()},title={Text("Secure checkout")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Multi-product Marketplace order",style=MaterialTheme.typography.titleMedium);Text(currency+" "+String.format(Locale.US,"%,.2f",total),style=MaterialTheme.typography.titleLarge);if(payment==null)OutlinedTextField(email,{email=it},label={Text("Payment email")},singleLine=true,modifier=Modifier.fillMaxWidth())else{Text("Complete payment in Paystack, then return to FYNX.");Text("Reference: "+payment?.reference.orEmpty(),style=MaterialTheme.typography.labelSmall)};message?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={if(payment==null)Button(enabled=!busy,onClick={busy=true;scope.launch{initializeMarketplaceCheckoutGroupPayment(context,checkoutId,email).onSuccess{p->payment=p;openMarketplaceCheckout(context,p.authorizationUrl).onFailure{payment=null;message=it.message?:"Could not open payment checkout."}}.onFailure{message=it.message?:"Could not start payment."};busy=false}}){if(busy)CircularProgressIndicator(Modifier.size(18.dp))else Text("Continue to payment")}else Button(enabled=!verifying,onClick={verifying=true;scope.launch{verifyMarketplaceCheckoutGroupPayment(context,payment?.reference.orEmpty()).onSuccess{onPaid()}.onFailure{message=it.message?:"Payment is not verified yet."};verifying=false}}){if(verifying)CircularProgressIndicator(Modifier.size(18.dp))else Text("Verify payment")}},dismissButton={TextButton(onClick=onClose,enabled=!busy&&!verifying){Text("Close")}})}