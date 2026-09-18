package com.fynx.app.ui

import android.content.Intent
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val categories = listOf("All", "Electronics", "Fashion", "Home", "Beauty", "Vehicles", "Services")

    fun reload() {
        scope.launch {
            loading = true
            error = null
            FynxRemoteSocialClient.listings(context, query, category)
                .onSuccess { listings = it }
                .onFailure { error = it.message ?: "Marketplace could not load." }
            FynxRemoteSocialClient.orders(context).onSuccess { orders = it }
            loading = false
        }
    }

    fun contactSeller(username: String) {
        val normalized = username.removePrefix("@").trim()
        if (normalized.isNotBlank()) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FynxDeepLinkParser.chatAppLink(normalized))))
        }
    }

    LaunchedEffect(query, category) { reload() }

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
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Marketplace", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Discover products from FYNX sellers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BadgedBox(badge = { if (cart.isNotEmpty()) Badge { Text(cart.size.toString()) } }) { IconButton(onClick = { showCart = true }) { Icon(Icons.Default.ShoppingCart, "Cart") } }
                IconButton(onClick = { showOrders = true }) { Icon(Icons.Default.ReceiptLong, "Orders") }
                IconButton(onClick = { reload() }) { Icon(Icons.Default.Refresh, "Refresh") }
            }
            OutlinedTextField(value = query, onValueChange = { query = it.take(80) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search products or sellers") }, shape = FynxDesign.ControlShape)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { categories.forEach { item -> FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) }) } }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
            when {
                loading && listings.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                listings.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Storefront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp)); Spacer(Modifier.height(10.dp)); Text("No products yet", style = MaterialTheme.typography.titleLarge); Text("Be the first seller on FYNX", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(listings, key = { it.id }) { listing -> MarketplaceCard(l = listing, onProfile = { onOpenProfile(listing.sellerUsername) }, onContact = { contactSeller(listing.sellerUsername) }, onOpen = { selected = listing }) } }
            }
        }
        FloatingActionButton(onClick = { showSell = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Sell", modifier = Modifier.padding(end = 14.dp)) }
    }

    if (showSell) MarketplaceSellDialog(context, onPublished = { showSell = false; reload() }, onCancel = { showSell = false })
    selected?.let { listing -> MarketplaceDetails(l = listing, onProfile = { onOpenProfile(listing.sellerUsername); selected = null }, onContact = { contactSeller(listing.sellerUsername) }, onBuyNow = { selected = null; checkoutListing = listing }, onAddToCart = { if (cart.none { it.id == listing.id }) cart = cart + listing; selected = null }, onClose = { selected = null }) }
    checkoutListing?.let { listing -> FynxMarketplaceCheckoutDialog(context = context, listing = listing, onProtectedOrder = { order -> checkoutListing = null; orders = listOf(order) + orders.filterNot { it.id == order.id }; paymentOrder = order }, onClose = { checkoutListing = null }) }
    paymentOrder?.let { order -> MarketplacePaymentDialog(context = context, order = order, onPaid = { paymentOrder = null; protectedOrder = order; reload() }, onClose = { paymentOrder = null }) }
    protectedOrder?.let { order -> MarketplaceProtectedOrderDialog(order = order, onViewOrder = { protectedOrder = null; showOrders = true }, onContinue = { protectedOrder = null }) }
    if (showCart) MarketplaceCartDialog(items = cart, onRemove = { item -> cart = cart.filterNot { it.id == item.id } }, onCheckout = { listing -> showCart = false; checkoutListing = listing }, onClose = { showCart = false })
    if (showOrders) MarketplaceOrders(context, orders, onRefresh = { reload() }, onClose = { showOrders = false })
}

@Composable
private fun MarketplaceProtectedOrderDialog(order: FynxRemoteSocialClient.MarketplaceOrder, onViewOrder: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(onDismissRequest = onContinue, icon = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) }, title = { Text("Order protected") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Payment received", style = MaterialTheme.typography.titleMedium); Text(order.productTitle.ifBlank { "FYNX order" }); Text("${order.currency} ${String.format(Locale.US, "%,.2f", order.totalAmount)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary); Text("Your payment is protected by FYNX. The seller will fulfill the order, and funds remain protected until the order reaches the appropriate completion state."); Text("Order #${order.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, confirmButton = { Button(onClick = onViewOrder) { Text("View order") } }, dismissButton = { TextButton(onClick = onContinue) { Text("Continue shopping") } })
}

@Composable
private fun MarketplaceCard(l: FynxRemoteSocialClient.MarketplaceListing, onProfile: () -> Unit, onContact: () -> Unit, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onProfile, modifier = Modifier.size(42.dp)) { FynxAvatar(l.sellerDisplayName.ifBlank { l.sellerUsername }, Modifier.size(36.dp).clip(RoundedCornerShape(50))) }
                Column(Modifier.weight(1f).padding(start = 4.dp)) { Text(l.sellerDisplayName.ifBlank { l.sellerUsername.removePrefix("@") }, fontWeight = FontWeight.SemiBold); Text(l.storeName.ifBlank { "FYNX Marketplace" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick = onOpen) { Text("View") }
            }
            if (l.mediaIds.isNotEmpty()) LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(l.mediaIds.take(12)) { mediaId -> RemoteMarketMedia(context = LocalContext.current, mediaId = mediaId, modifier = Modifier.width(310.dp).height(250.dp).clip(RoundedCornerShape(14.dp))) } } else Box(Modifier.fillMaxWidth().height(220.dp).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) { Icon(Icons.Default.ShoppingBag, "Product", Modifier.size(58.dp)) }
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(l.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); Text("${l.currency} ${String.format(Locale.US, "%,.2f", l.price)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                if (l.description.isNotBlank()) Text(l.description, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { AssistChip(onClick = onOpen, label = { Text(l.category) }); Text("${l.quantity} available", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (l.deliveryAvailable) Text("Delivery", style = MaterialTheme.typography.labelSmall); if (l.pickupAvailable) Text("Pickup", style = MaterialTheme.typography.labelSmall) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onContact) { Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Contact") }; TextButton(onClick = onOpen) { Text("View product") }; Spacer(Modifier.weight(1f)); Button(onClick = onOpen, enabled = l.quantity > 0) { Text("Buy now") } }
            }
        }
    }
}

@Composable
private fun RemoteMarketMedia(context: android.content.Context, mediaId: String, modifier: Modifier) { val mediaUrl = remember(mediaId) { FynxMarketplaceClient.mediaUrl(context, mediaId) }; FynxRemoteMedia(mediaUrl, "auto", modifier) }

@Composable
private fun MarketplaceDetails(l: FynxRemoteSocialClient.MarketplaceListing, onProfile: () -> Unit, onContact: () -> Unit, onBuyNow: () -> Unit, onAddToCart: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onClose, title = { Text(l.title) }, text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (l.mediaIds.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(l.mediaIds.take(12)) { mediaId -> RemoteMarketMedia(LocalContext.current, mediaId, Modifier.width(280.dp).height(210.dp).clip(RoundedCornerShape(12.dp))) } }
        Text("${l.currency} ${String.format(Locale.US, "%,.2f", l.price)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        if (l.description.isNotBlank()) Text(l.description)
        Text("Seller: ${l.sellerDisplayName.ifBlank { l.sellerUsername }}")
        Text("${l.quantity} available • ${l.condition}")
        if (l.location.isNotBlank()) Text("Location: ${l.location}")
        if (l.deliveryAvailable) Text("Delivery available${l.deliveryFee?.let { " • ${l.currency} ${String.format(Locale.US, "%,.2f", it)} fee" } ?: ""}")
        if (l.pickupAvailable) Text("Pickup available")
        Text("🛡 FYNX protected payment", fontWeight = FontWeight.SemiBold)
        Text("Payment stays protected through the existing FYNX order lifecycle until the appropriate completion state.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var title by remember { mutableStateOf("") }; var desc by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }; var quantity by remember { mutableStateOf("1") }; var category by remember { mutableStateOf("Electronics") }; var location by remember { mutableStateOf("") }; var delivery by remember { mutableStateOf(false) }; var pickup by remember { mutableStateOf(true) }; var media by remember { mutableStateOf<List<Uri>>(emptyList()) }; var showCamera by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(FynxMarketplaceSellerFlowSupport.MAX_PRODUCT_MEDIA)) { uris -> if (uris.isNotEmpty()) media = FynxMarketplaceSellerFlowSupport.normalizedMedia(context, media + uris) }
    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, title = { Text("Sell on FYNX") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Add product media, then enter the key details.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Product media (${media.size}/${FynxMarketplaceSellerFlowSupport.MAX_PRODUCT_MEDIA})", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, enabled = !busy && media.size < FynxMarketplaceSellerFlowSupport.MAX_PRODUCT_MEDIA, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Collections, null); Spacer(Modifier.width(5.dp)); Text("Choose media") }; OutlinedButton(onClick = { showCamera = true }, enabled = !busy && media.size < FynxMarketplaceSellerFlowSupport.MAX_PRODUCT_MEDIA, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(5.dp)); Text("Camera") } }
        if (media.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(vertical = 2.dp)) { items(media, key = { it.toString() }) { uri -> Box(Modifier.width(78.dp).height(78.dp)) { val isVideo = contextIsVideo(LocalContext.current, uri); if (isVideo) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) { Icon(Icons.Default.Videocam, "Video") } else AndroidView(factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } }, update = { imageView -> imageView.setImageURI(uri) }, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))); IconButton(onClick = { media = media.filterNot { it == uri } }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) { Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error) } } } }
        OutlinedTextField(title, { title = it }, label = { Text("Product name") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(price, { price = it }, label = { Text("Price (NGN)") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, minLines = 3, modifier = Modifier.fillMaxWidth()); OutlinedTextField(location, { location = it }, label = { Text("Location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("Electronics", "Fashion", "Home", "Beauty", "Vehicles", "Services").forEach { item -> FilterChip(category == item, { category = item }, label = { Text(item) }) } }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(delivery, { delivery = it }); Text("Delivery") }; Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(pickup, { pickup = it }); Text("Pickup") }; error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(enabled = !busy && FynxMarketplaceSellerFlowSupport.validListing(title, desc, price.toDoubleOrNull(), quantity.toIntOrNull(), media), onClick = { busy = true; error = null; scope.launch { FynxRemoteSocialClient.createMarketplaceListing(context, title, desc, "", price.toDouble(), FynxMarketplaceSellerFlowSupport.DEFAULT_CURRENCY, category, "NEW", quantity.toIntOrNull() ?: 1, location, delivery, pickup, null, media).onSuccess { onPublished() }.onFailure { error = it.message ?: "Listing could not be published."; busy = false } } }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Publish") } }, dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") } })
    if (showCamera) Dialog(onDismissRequest = { showCamera = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().safeDrawingPadding()) { FynxCameraCapturePanel(onCaptured = { uri, _ -> media = FynxMarketplaceSellerFlowSupport.addMedia(context, media, uri); showCamera = false }, onDismiss = { showCamera = false }) } } }
}

private fun contextIsVideo(context: android.content.Context, uri: Uri): Boolean { val mime = context.contentResolver.getType(uri).orEmpty().lowercase(); return mime.startsWith("video/") || uri.toString().lowercase().let { it.endsWith(".mp4") || it.endsWith(".webm") || it.endsWith(".3gp") || it.endsWith(".mkv") } }

@Composable
private fun MarketplaceCartDialog(items: List<FynxRemoteSocialClient.MarketplaceListing>, onRemove: (FynxRemoteSocialClient.MarketplaceListing) -> Unit, onCheckout: (FynxRemoteSocialClient.MarketplaceListing) -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Shopping cart") }, text = { if (items.isEmpty()) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ShoppingCart, null, Modifier.size(48.dp)); Spacer(Modifier.height(8.dp)); Text("Your cart is empty."); Text("Add products from the marketplace to start checkout.", style = MaterialTheme.typography.bodySmall) } } else { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(items, key = { it.id }) { item -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.title, style = MaterialTheme.typography.titleMedium); Text("${item.currency} ${String.format(Locale.US, "%,.2f", item.price)} • ${item.quantity} available", style = MaterialTheme.typography.bodySmall) }; TextButton(onClick = { onCheckout(item) }, enabled = item.quantity > 0) { Text("Checkout") }; TextButton(onClick = { onRemove(item) }) { Text("Remove") } } } } } } }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
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
