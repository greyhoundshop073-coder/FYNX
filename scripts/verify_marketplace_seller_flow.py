from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

support = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerFlowSupport.kt").read_text()
marketplace = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text()
checkout = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceCheckout.kt").read_text()

checks = [
    ("seller support keeps a bounded real media limit", "const val MAX_PRODUCT_MEDIA = 12" in support),
    ("seller support accepts image and video media", 'mime.startsWith("image/") || mime.startsWith("video/")' in support),
    ("seller support removes duplicate media", ".distinct()" in support),
    ("seller support appends media through the shared guardrail", "fun addMedia(context: Context, existing: List<Uri>, uri: Uri)" in support and "normalizedMedia(context, existing + uri)" in support),
    ("seller support validates bounded title input", "MAX_TITLE_LENGTH = 120" in support and "title.length <= MAX_TITLE_LENGTH" in support),
    ("seller support validates bounded description input", "MAX_DESCRIPTION_LENGTH = 5000" in support and "description.length <= MAX_DESCRIPTION_LENGTH" in support),
    ("seller support validates finite positive price and bounded quantity", "price.isFinite()" in support and "MAX_QUANTITY = 1_000_000" in support and "quantity in 1..MAX_QUANTITY" in support),
    ("seller support validates the final media count", "media.size <= MAX_PRODUCT_MEDIA" in support),
    ("active seller flow uses multi-media picker", "PickMultipleVisualMedia" in marketplace and "MAX_PRODUCT_MEDIA" in marketplace),
    ("active seller flow offers camera capture", "FynxCameraCapturePanel" in marketplace and "showCamera" in marketplace),
    ("active seller flow previews selected media", "ImageView" in marketplace and "contextIsVideo" in marketplace),
    ("active seller flow supports media removal", 'media = media.filterNot { it == uri }' in marketplace),
    ("active seller flow publishes the selected media list", "createMarketplaceListing" in marketplace and ", media)" in marketplace),
    ("active marketplace remains backend listing driven", "FynxRemoteSocialClient.listings" in marketplace),
    ("product details show the real media carousel", "items(l.mediaIds.take(12))" in marketplace),
    ("product details use the reference summary and description with live listing data", "MarketplaceReferenceProductSummary(" in marketplace and "MarketplaceReferenceDescriptionCard(listing = l)" in marketplace and 'Text("Seller:' in marketplace and "deliveryAvailable" in marketplace and "pickupAvailable" in marketplace),
    ("active marketplace exposes seller messaging through the product summary", "MarketplaceReferenceProductSummary(" in marketplace and "onMessageSeller = onContact" in marketplace and "FynxDeepLinkParser.chatAppLink" in marketplace and "Intent.ACTION_VIEW" in marketplace),
    ("active marketplace exposes Add to cart", "Add to cart" in marketplace and "cart = cart + listing" in marketplace),
    ("active marketplace Buy now enters the real checkout", "onBuyNow = { selected = null; checkoutListing = listing }" in marketplace and "FynxMarketplaceCheckoutDialog" in marketplace),
    ("active marketplace details are scrollable for phone screens", "verticalScroll(rememberScrollState())" in marketplace),
    ("active marketplace retains protected checkout", "FynxMarketplaceCheckoutDialog" in marketplace),
    ("active marketplace retains payment verification", "verifyMarketplacePayment" in marketplace),
    ("marketplace header respects the status bar", "statusBarsPadding()" in marketplace),
    ("marketplace uses a responsive two-column product grid", "GridCells.Fixed(2)" in marketplace),
    ("marketplace leaves free bottom scroll space above navigation", "bottom = 132.dp" in marketplace),
    ("marketplace sell button respects navigation and keyboard insets", "navigationBarsPadding().imePadding()" in marketplace),
    ("marketplace product cards show the seller avatar and name", "FynxProfileRemoteClient.get" in marketplace and "l.sellerDisplayName" in marketplace),
    ("marketplace product cards keep media square and bounded", "aspectRatio(1f)" in marketplace),
    ("marketplace search is debounced before backend reload", "delay(if (query.isBlank()) 0L else 350L)" in marketplace),
    ("marketplace top sellers are derived from real successful sales", "successfulSales" in marketplace and "Top Sellers (Highest Sales)" in marketplace),
    ("marketplace filtered empty state can clear filters", "No matching products" in marketplace and "Clear filters" in marketplace),
    ("active marketplace retains order protection", "MarketplaceProtectedOrderDialog" in marketplace),
    ("marketplace cart opens from the real cart button", "showCart = true" in marketplace and "Icons.Default.ShoppingCart" in marketplace),
    ("marketplace cart prevents duplicate listing entries", "cart.none { it.id == listing.id }" in marketplace),
    ("marketplace cart restores an account-scoped saved snapshot when the panel is recreated", "readFynxMarketplaceCart(context, cartOwnerKey)" in marketplace and "var cart by remember(cartOwnerKey) { mutableStateOf(restoredMarketplaceCart.items) }" in marketplace and "var cartQuantities by remember(cartOwnerKey) { mutableStateOf(restoredMarketplaceCart.quantities) }" in marketplace and "remember(context, cartOwnerKey)" in marketplace),
    ("marketplace cart persists item and quantity changes in the matching account scope", "LaunchedEffect(cartOwnerKey, cart, cartQuantities)" in marketplace and "writeFynxMarketplaceCart(context, cartOwnerKey, cart, cartQuantities)" in marketplace and "marketplace_cart_snapshot_v1" in marketplace and "marketplaceCartStorageKey(ownerKey)" in marketplace),
    ("marketplace saved cart is bounded to 20 distinct listings", "minOf(array.length(), 20)" in marketplace and "items.distinctBy { it.id }.take(20)" in marketplace),
    ("marketplace saved cart rejects blank/duplicate IDs and invalid quantities", 'if (id.isBlank() || cartQuantity !in 1..999 || items.any { it.id == id }) continue' in marketplace),
    ("marketplace saved cart rejects invalid prices and missing currencies", '!listing.price.isFinite() || listing.price < 0.0 || listing.currency.isBlank()' in marketplace),
    ("marketplace saved cart remains a snapshot and checkout uses server-authoritative validation", "readFynxMarketplaceCart(context, cartOwnerKey)" in marketplace and "requestMultiQuote(context,lines())" in marketplace and "createMultiOrder(context,lines(),q.id)" in marketplace),
    ("marketplace cart checkout uses the selected real listing", "onCheckout = { selectedItems -> showCart = false; multiCheckoutItems = selectedItems }" in marketplace and "FynxMarketplaceMultiCheckoutDialog" in marketplace),
    ("marketplace Buy now is disabled when the real listing is out of stock", "onBuyNow" in marketplace and "enabled = l.quantity > 0" in marketplace),
    ("marketplace checkout initializes payment against the protected order", "initializeMarketplacePayment(context, order.id, email)" in marketplace),
    ("marketplace checkout requires payment verification before protection confirmation", "verifyMarketplacePayment(context, payment?.reference.orEmpty())" in marketplace),
    ("marketplace checkout rejects an expired client-side quote before order creation", "marketplaceQuoteExpired(q.expiresAt)" in checkout),
    ("marketplace checkout treats malformed or expired quote timestamps as unsafe", "Instant.parse(expiresAt)" in checkout and "getOrDefault(true)" in checkout),
    ("protected order lifecycle exposes fulfillment actions", "FynxMarketplaceOrderLifecycle" in marketplace and "Choose fulfillment" in marketplace),
    ("protected order lifecycle supports received confirmation", '"SHIPPED" -> "Confirm the order when you receive it."' in marketplace),
    ("protected order lifecycle supports completion after inspection", 'else -> "Inspect the order and complete it when everything is correct."' in marketplace),
    ("protected orders expose dispute reporting", "disputeMarketplaceOrder" in marketplace and "Open dispute" in marketplace),
    ("protected orders expose cancellation for unpaid orders", "cancelMarketplaceOrder" in marketplace and "PAYMENT_PENDING" in marketplace),
    ("completed protected orders support seller reviews", "reviewMarketplaceOrder" in marketplace and "Submit review" in marketplace),
    ("seller publish still requires a real listing area", "location.trim().isNotBlank()" in marketplace and "validListing(title, desc, price.toDoubleOrNull(), quantity.toIntOrNull(), media)" in marketplace),
    ("seller listing location remains human-readable and privacy-preserving", "FynxPostLocationClient.currentPlace(context)" in marketplace and "your exact GPS coordinates are not published with the listing." in marketplace),
    ("marketplace pagination deduplicates listing IDs across existing results and within each new page", "appendDistinctMarketplacePage(listings, page.listings) { it.id }" in marketplace and "internal fun <T, K> appendDistinctMarketplacePage(" in marketplace and "knownKeys.add(keySelector(it))" in marketplace),
    ("buyer nearby discovery still uses the shared smart-location resolver", "FynxPostLocationClient.currentPlace(context)" in marketplace and ("nearbyMarketplaceListings(context, query, category, nearbyLabel)" in marketplace or "nearbyMarketplaceListings(context, requestQuery, requestCategory, requestNearbyLabel)" in marketplace)),
    ("cart selection toggles individual real listing IDs", "selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id" in marketplace),
    ("cart checkout only submits selected items", "onCheckout(selected)" in marketplace and "selected.isNotEmpty() && !mixed" in marketplace),
    ("cart prevents cross-currency checkout before submission", "val mixed = selected.map { it.currency.trim().uppercase(Locale.US) }.distinct().size > 1" in marketplace and "A single checkout cannot mix NGN and USD. Select one currency." in marketplace),
    ("cart quantity changes stay within available stock and a minimum of one", "((quantities[item.id] ?: 1)-1).coerceAtLeast(1)" in marketplace and "((quantities[item.id] ?: 1)+1).coerceAtMost(item.quantity)" in marketplace),
    ("cart exposes an actionable empty state", "Your cart is empty." in marketplace and "Add products from the marketplace to start checkout." in marketplace),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)

if failed:
    raise SystemExit("Marketplace seller/product-flow verification failed: " + ", ".join(failed))

print(f"Marketplace seller/product-flow verification GREEN ({len(checks)} checks)")
