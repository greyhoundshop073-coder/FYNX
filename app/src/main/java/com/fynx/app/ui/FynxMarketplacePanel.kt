package com.fynx.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun FynxMarketplacePanel(currentUsername: String = "preview", onOpenProfile: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listings by remember { mutableStateOf<List<FynxRemoteSocialClient.MarketplaceListing>>(emptyList()) }
    var orders by remember { mutableStateOf<List<FynxRemoteSocialClient.MarketplaceOrder>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<FynxRemoteSocialClient.MarketplaceListing?>(null) }
    var paymentOrder by remember { mutableStateOf<FynxRemoteSocialClient.MarketplaceOrder?>(null) }
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

    LaunchedEffect(query, category) { reload() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Marketplace", style = MaterialTheme.typography.headlineSmall)
                Text("Buy and sell with FYNX accounts", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BadgedBox(modifier = Modifier.padding(end = 2.dp), badge = { if (cart.isNotEmpty()) Badge { Text(cart.size.toString()) } }) { IconButton(onClick = { showCart = true }) { Icon(Icons.Default.ShoppingCart, "Cart") } }
            IconButton(onClick = { showOrders = true }) { Icon(Icons.Default.ReceiptLong, "Orders") }
            FynxMarketplaceSellerOrders(context, onChanged = { reload() })
            FynxMarketplaceReviews(context, orders, onChanged = { reload() })
            IconButton(onClick = { reload() }) { Icon(Icons.Default.Refresh, "Refresh") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showSell = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Sell")
            }
            OutlinedTextField(query, { query = it }, Modifier.weight(2f), singleLine = true, placeholder = { Text("Search products") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { item -> FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) }) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp)) }

        when {
            loading && listings.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            listings.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Storefront, null, Modifier.size(52.dp))
                    Text("No products yet", style = MaterialTheme.typography.titleLarge)
                    Text("Be the first seller on FYNX", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(listings, key = { it.id }) { listing ->
                    MarketplaceCard(l = listing, onProfile = { onOpenProfile(listing.sellerUsername) }, onOpen = { selected = listing })
                }
            }
        }
    }

    if (showSell) MarketplaceSellDialog(context, onPublished = { showSell = false; reload() }, onCancel = { showSell = false })
    selected?.let { listing ->
        MarketplaceDetails(
            l = listing,
            onProfile = { onOpenProfile(listing.sellerUsername); selected = null },
            onAddToCart = {
                if (cart.none { it.id == listing.id }) cart = cart + listing
                selected = null
            },
            onClose = { selected = null }
        )
    }
    paymentOrder?.let { order ->
        MarketplacePaymentDialog(
            context = context,
            order = order,
            onPaid = {
                paymentOrder = null
                reload()
            },
            onClose = { paymentOrder = null }
        )
    }
    if (showCart) MarketplaceCartDialog(
        context = context,
        items = cart,
        onRemove = { item -> cart = cart.filterNot { it.id == item.id } },
        onCheckout = { listing ->
            scope.launch {
                FynxRemoteSocialClient.createMarketplaceOrder(context, listing.id, 1)
                    .onSuccess { order ->
                        orders = listOf(order) + orders
                        cart = cart.filterNot { it.id == listing.id }
                        showCart = false
                        paymentOrder = order
                    }
                    .onFailure { error = it.message ?: "Checkout could not be started." }
            }
        },
        onClose = { showCart = false }
    )
    if (showOrders) MarketplaceOrders(context, orders, onRefresh = { reload() }, onClose = { showOrders = false })
}

@Composable
private fun MarketplaceCard(l: FynxRemoteSocialClient.MarketplaceListing, onProfile: () -> Unit, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onProfile) { Text(l.sellerDisplayName.ifBlank { l.sellerUsername }) }
                Column(Modifier.weight(1f)) {
                    Text(l.storeName.ifBlank { "FYNX Marketplace" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onOpen) { Text("View") }
            }
            if (l.mediaIds.isNotEmpty()) RemoteMarketMedia(l.mediaIds.first())
            else Box(Modifier.fillMaxWidth().height(180.dp), Alignment.Center) { Icon(Icons.Default.ShoppingBag, "Product", Modifier.size(60.dp)) }
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(l.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("${l.currency} ${String.format(Locale.US, "%,.2f", l.price)}", color = MaterialTheme.colorScheme.primary)
                }
                Text(l.description, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${l.category} • ${l.condition} • ${l.quantity} available", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (l.deliveryAvailable || l.pickupAvailable) Text(buildString { if (l.deliveryAvailable) append("Delivery"); if (l.deliveryAvailable && l.pickupAvailable) append(" • "); if (l.pickupAvailable) append("Pickup") }, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RemoteMarketMedia(mediaId: String) {
    val context = LocalContext.current
    var uri by remember(mediaId) { mutableStateOf<Uri?>(null) }
    var bitmap by remember(mediaId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(mediaId) {
        uri = FynxProductionMessaging.cacheRemoteMedia(context, mediaId, "/api/marketplace/media/$mediaId").getOrNull()
        val local = uri
        if (local != null) bitmap = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(local).use { BitmapFactory.decodeStream(it) } }.getOrNull() }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "Product", modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.fillMaxWidth().height(220.dp), Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
private fun MarketplaceDetails(l: FynxRemoteSocialClient.MarketplaceListing, onProfile: () -> Unit, onAddToCart: () -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text(l.title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (l.mediaIds.isNotEmpty()) RemoteMarketMedia(l.mediaIds.first())
            Text("${l.currency} ${String.format(Locale.US, "%,.2f", l.price)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(l.description)
            Text("Seller: ${l.sellerDisplayName.ifBlank { l.sellerUsername }}")
            Text("${l.quantity} available • ${l.condition}")
            if (l.location.isNotBlank()) Text("Location: ${l.location}")
            if (l.deliveryAvailable) Text("Delivery available${l.deliveryFee?.let { " • ${l.currency} ${String.format(Locale.US, "%,.2f", it)} fee" } ?: ""}")
        }
    }, confirmButton = { Button(onClick = onAddToCart, enabled = l.quantity > 0) { Icon(Icons.Default.ShoppingCart, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Add to cart") } }, dismissButton = { TextButton(onClick = onProfile) { Text("View seller") } })
}

@Composable
private fun MarketplacePaymentDialog(
    context: android.content.Context,
    order: FynxRemoteSocialClient.MarketplaceOrder,
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
        title = { Text("Secure checkout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(order.productTitle.ifBlank { "FYNX order" }, style = MaterialTheme.typography.titleMedium)
                Text("${order.currency} ${String.format(Locale.US, "%,.2f", order.totalAmount)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                if (payment == null) {
                    Text("Enter the email you want to use for payment. Your FYNX password or Paystack secret key is never requested here.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Payment email") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("Checkout was opened in your browser. After completing payment, return to FYNX and tap Verify payment.", style = MaterialTheme.typography.bodySmall)
                    Text("Reference: ${payment!!.reference}", style = MaterialTheme.typography.labelSmall)
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when {
                payment == null -> Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            initializeMarketplacePayment(context, order.id, email)
                                .onSuccess { checkout ->
                                    payment = checkout
                                    openMarketplaceCheckout(context, checkout.authorizationUrl)
                                        .onFailure {
                                            payment = null
                                            message = it.message ?: "Could not open payment checkout."
                                        }
                                }
                                .onFailure { message = it.message ?: "Could not start payment." }
                            busy = false
                        }
                    }
                ) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Continue to payment") }
                else -> Button(
                    enabled = !verifying,
                    onClick = {
                        verifying = true
                        message = null
                        scope.launch {
                            verifyMarketplacePayment(context, payment!!.reference)
                                .onSuccess { onPaid() }
                                .onFailure { message = it.message ?: "Payment is not verified yet. Complete checkout first." }
                            verifying = false
                        }
                    }
                ) { if (verifying) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Verify payment") }
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy && !verifying) { Text("Close") } }
    )
}

@Composable
private fun MarketplaceSellDialog(context: android.content.Context, onPublished: () -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var category by remember { mutableStateOf("Electronics") }
    var location by remember { mutableStateOf("") }
    var delivery by remember { mutableStateOf(false) }
    var pickup by remember { mutableStateOf(true) }
    var media by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { media = it }

    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, title = { Text("Sell on FYNX") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(arrayOf("image/*", "video/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AddAPhoto, null); Spacer(Modifier.width(5.dp)); Text(if (media == null) "Add product photo/video" else "Media selected")
            }
            OutlinedTextField(title, { title = it }, label = { Text("Product name") }, singleLine = true)
            OutlinedTextField(price, { price = it }, label = { Text("Price (NGN)") }, singleLine = true)
            OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, singleLine = true)
            OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, minLines = 3)
            OutlinedTextField(location, { location = it }, label = { Text("Location") }, singleLine = true)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Electronics", "Fashion", "Home", "Beauty", "Vehicles", "Services").forEach { item -> FilterChip(category == item, { category = item }, label = { Text(item) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(delivery, { delivery = it }); Text("Delivery") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(pickup, { pickup = it }); Text("Pickup") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        Button(enabled = !busy && title.isNotBlank() && desc.isNotBlank() && price.toDoubleOrNull() != null && media != null, onClick = {
            busy = true; error = null
            scope.launch {
                FynxRemoteSocialClient.createMarketplaceListing(context, title, desc, "", price.toDouble(), "NGN", category, "NEW", quantity.toIntOrNull() ?: 1, location, delivery, pickup, null, listOfNotNull(media))
                    .onSuccess { onPublished() }
                    .onFailure { error = it.message ?: "Listing could not be published."; busy = false }
            }
        }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Publish") }
    }, dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") } })
}

@Composable
private fun MarketplaceCartDialog(
    context: android.content.Context,
    items: List<FynxRemoteSocialClient.MarketplaceListing>,
    onRemove: (FynxRemoteSocialClient.MarketplaceListing) -> Unit,
    onCheckout: (FynxRemoteSocialClient.MarketplaceListing) -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Shopping cart") },
        text = {
            if (items.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ShoppingCart, null, Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Your cart is empty.")
                    Text("Add products from the marketplace to start checkout.", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    Text("${item.currency} ${String.format(Locale.US, "%,.2f", item.price)} • ${item.quantity} available", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { onCheckout(item) }, enabled = item.quantity > 0) { Text("Checkout") }
                                TextButton(onClick = { onRemove(item) }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}

@Composable
private fun MarketplaceOrders(context: android.content.Context, orders: List<FynxRemoteSocialClient.MarketplaceOrder>, onRefresh: () -> Unit, onClose: () -> Unit) {
    var selected by remember { mutableStateOf<FynxRemoteSocialClient.MarketplaceOrder?>(null) }
    AlertDialog(onDismissRequest = onClose, title = { Text("My orders") }, text = {
        if (orders.isEmpty()) Text("No orders yet.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    }, confirmButton = { TextButton(onClick = onRefresh) { Text("Refresh") } }, dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
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
    AlertDialog(onDismissRequest = onClose, title = { Text("Order ${order.status}") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(order.productTitle)
            Text("Total: ${order.currency} ${String.format(Locale.US, "%,.2f", order.totalAmount)}")
            Text("Protected order. Complete payment through an approved payment provider before shipment.", style = MaterialTheme.typography.bodySmall)
            if (order.status == "PAID" || order.status == "SHIPPED" || order.status == "INSPECTION") {
                Text("Next step", style = MaterialTheme.typography.labelLarge)
                Text(when (order.status) {
                    "PAID" -> "Choose delivery or pickup so the seller can fulfill the order."
                    "SHIPPED" -> "Confirm the order when you receive it."
                    else -> "Inspect the order and complete it when everything is correct."
                }, style = MaterialTheme.typography.bodySmall)
            }
            if (dispute) OutlinedTextField(details, { details = it }, label = { Text("What happened?") }, minLines = 3)
            else if (order.status == "PAYMENT_PENDING") Text("You can cancel this unpaid order.")
            else if (order.status == "COMPLETED") {
                Text("Rate seller")
                Row { (1..5).forEach { star -> TextButton(onClick = { rating = star }) { Text(if (star <= rating) "★" else "☆") } } }
                OutlinedTextField(comment, { comment = it }, label = { Text("Review") }, minLines = 2)
            }
        }
    }, confirmButton = {
        when {
            dispute -> Button(onClick = { scope.launch { FynxRemoteSocialClient.disputeMarketplaceOrder(context, order.id, "OTHER", details).onSuccess { onChanged() } } }) { Text("Open dispute") }
            order.status == "PAYMENT_PENDING" -> Button(onClick = { scope.launch { FynxRemoteSocialClient.cancelMarketplaceOrder(context, order.id).onSuccess { onChanged() } } }) { Text("Cancel order") }
            order.status == "PAID" || order.status == "SHIPPED" || order.status == "INSPECTION" -> Button(onClick = { showLifecycle = true }) { Text(when (order.status) { "PAID" -> "Choose fulfillment"; "SHIPPED" -> "Confirm received"; else -> "Complete order" }) }
            order.status == "COMPLETED" -> Button(onClick = { scope.launch { FynxRemoteSocialClient.reviewMarketplaceOrder(context, order.id, rating, comment).onSuccess { onChanged() } } }) { Text("Submit review") }
            else -> Spacer(Modifier.size(1.dp))
        }
    }, dismissButton = { TextButton(onClick = { if (!dispute && order.status != "COMPLETED") dispute = true else onClose() }) { Text(if (!dispute && order.status != "COMPLETED") "Report problem" else "Close") } })
    if (showLifecycle) {
        FynxMarketplaceOrderLifecycle(context, order, onChanged = { showLifecycle = false; onChanged() }, onClose = { showLifecycle = false })
    }
}
