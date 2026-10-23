from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

support = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerFlowSupport.kt").read_text()
marketplace = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text()
checkout = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceCheckout.kt").read_text()
detail_components = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceReferenceDetailComponents.kt").read_text()
product_card = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceReferenceProductComponents.kt").read_text()
seller_center = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerCenterPanel.kt").read_text()
app_shell = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text()
saved_products_panel = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxSavedMarketplaceProductsPanel.kt").read_text()
remote_client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text()
marketplace_client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceClient.kt").read_text()
order_lifecycle = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceOrderLifecycle.kt").read_text()
discovery_routes = (ROOT / "backend/discoveryRoutes.js").read_text()
social_routes = (ROOT / "backend/socialRoutes.js").read_text()
marketplace_checkout_route = (ROOT / "backend/marketplaceCheckout.js").read_text()

import re

EXPECTED_DISCOVERY_CATEGORIES = ["All", "Electronics", "Fashion", "Home", "Beauty", "Vehicles", "Services", "Food", "Property", "Agriculture", "Phones & Computers", "Furniture", "Baby & Kids", "Books", "Sports", "Tools", "Health", "Other"]
EXPECTED_SELLER_CATEGORIES = EXPECTED_DISCOVERY_CATEGORIES[1:]

def _extract_string_list(pattern: str, source: str):
    match = re.search(pattern, source, re.DOTALL)
    if not match:
        return None
    return re.findall(r'"([^"]+)"', match.group(1))

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
    ("Marketplace discovery client sends category under the backend-supported query key", '&category=${URLEncoder.encode(category, "UTF-8")}' in remote_client and 'req.query?.category' in discovery_routes),
    ("Marketplace backend filters selected categories exactly and leaves All unfiltered", 'category.toLowerCase() !== "all"' in discovery_routes and 'where.push("l.category=$" + params.length)' in discovery_routes),
    ("seller form and discovery expose the same expanded category set",
     _extract_string_list(r"val categories = listOf\(([^)]*)\)", marketplace) == EXPECTED_DISCOVERY_CATEGORIES
     and _extract_string_list(r'Text\("Category"[\s\S]*?listOf\(([^)]*)\)\.forEach', marketplace) == EXPECTED_SELLER_CATEGORIES
     and _extract_string_list(r'FYNX uses a human-readable area[\s\S]*?listOf\(([^)]*)\)\.forEach', marketplace) == EXPECTED_SELLER_CATEGORIES
     and '"REFURBISHED" to "Refurbished"' in marketplace),
    ("seller form shows category-specific product specifications", all(term in marketplace for term in ('"Brand"', '"Model / model number"', '"Size / fit"', '"Dimensions (e.g. 120 × 60 × 75 cm)"', '"Year"', '"Ingredients, skin/hair type, or expiry details"', '"What is included, duration, and service area"'))),
    ("category specifications are included in the saved product description with bounded length", 'append("\\n\\nProduct specifications\\n")' in marketplace and "MAX_DESCRIPTION_LENGTH" in marketplace and "listingDescription" in marketplace),
    ("seller publishes selected condition and composed specifications through the existing listing path", 'title, listingDescription, "", price.toDouble(), currency, category, condition' in marketplace and 'validListing(title, listingDescription' in marketplace),
    ("active marketplace remains backend listing driven", "FynxRemoteSocialClient.listings" in marketplace),
    ("Marketplace discovery includes the signed-in seller own active products while the detail UI disables self-purchase and backend checkout rejects it", 'const params = [req.user.sub]' in discovery_routes and 'l.seller_id<>$1' not in discovery_routes and 'canPurchase = !isOwnListing' in marketplace and 'Buyers can see it in Marketplace, but you cannot buy your own product.' in marketplace and 'enabled = canPurchase && l.quantity > 0' in marketplace and "you cannot purchase your own listing" in marketplace_checkout_route),
    ("Marketplace discovery hides sellers blocked in either direction without hiding the signed-in seller own listings", "NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$1 AND b.blocked_id=l.seller_id) OR (b.blocked_id=$1 AND b.blocker_id=l.seller_id))" in discovery_routes and "l.seller_id<>$1" not in discovery_routes),
    ("Marketplace listings fallback and public storefront apply the same two-way block filter while allowing the signed-in seller to see their own products", "NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$1 AND b.blocked_id=l.seller_id) OR (b.blocked_id=$1 AND b.blocker_id=l.seller_id))" in social_routes and "l.seller_id <> $1" not in social_routes),
    ("product details show the real media carousel", "items(l.mediaIds.take(12))" in marketplace),
    ("product details show seller and fulfillment information", 'Text("Seller:' in marketplace and 'Text("Location:' in marketplace and "deliveryAvailable" in marketplace and "pickupAvailable" in marketplace),
    ("active Marketplace exposes the visible Message seller action wired to the real chat deep link", 'Text("Message seller")' in detail_components and "onContact = { contactSeller(listing.sellerUsername, listing.id) }" in marketplace and "FynxDeepLinkParser.chatAppLink" in marketplace and "Intent.ACTION_VIEW" in marketplace),
    ("active marketplace exposes Add to cart", "Add to cart" in marketplace and "cart = cart + listing" in marketplace),
    ("active marketplace Buy now enters the real checkout", "onBuyNow = { selected = null; checkoutListing = listing }" in marketplace and "FynxMarketplaceCheckoutDialog" in marketplace),
    ("confirmed single-item Buy Now payment removes the purchased product and quantity from the saved cart", "onPaid = { paymentOrder = null; cart = cart.filterNot { it.id == order.listingId }; cartQuantities = cartQuantities.filterKeys { it != order.listingId }; protectedOrder = order; reload() }" in marketplace),
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
    ("marketplace cart passes selected products into multi-product checkout", "onCheckout = { selectedItems -> showCart = false; multiCheckoutItems = selectedItems }" in marketplace and "multiCheckoutItems?.let { selectedItems -> FynxMarketplaceMultiCheckoutDialog(context, selectedItems, cartQuantities" in marketplace and "multiPaymentListingIds = selectedItems.map { it.id }.toSet()" in marketplace),
    ("marketplace Buy now and Add to cart are disabled for out-of-stock products and seller-owned listings", "onBuyNow" in marketplace and "enabled = canPurchase && l.quantity > 0" in marketplace and "Text(if (!canPurchase) \"Your listing\" else if (l.quantity > 0) \"Add to cart\" else \"Out of stock\")" in marketplace),
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
    ("seller publish still requires a real listing area", "location.trim().isNotBlank()" in marketplace and "validListing(title, listingDescription, price.toDoubleOrNull(), quantity.toIntOrNull(), media)" in marketplace),
    ("seller listing location remains human-readable and privacy-preserving", "FynxPostLocationClient.currentPlace(context)" in marketplace and "your exact GPS coordinates are not published with the listing." in marketplace),
    ("buyer nearby discovery resolves the user area and requests nearby listings with the active search/filter", "FynxPostLocationClient.currentPlace(context)" in marketplace and "FynxRemoteSocialClient.nearbyMarketplaceListings(context, requestQuery, requestCategory, requestNearbyLabel)" in marketplace),
    ("My Shop has separate overview, profile, listings, orders, delivery/drivers, and earnings/payout destinations", all(f'"{section}"' in seller_center for section in ("Overview", "Shop profile", "Listings", "Orders", "Delivery & drivers", "Earnings & payouts"))),
    ("My Shop overview links to each management destination and uses existing loaded counts", "Your shop at a glance" in seller_center and 'activeSection = "Shop profile"' in seller_center and 'activeSection = "Listings"' in seller_center and 'activeSection = "Orders"' in seller_center and 'activeSection = "Delivery & drivers"' in seller_center and 'activeSection = "Earnings & payouts"' in seller_center and "listings.size" in seller_center and "moneyRecords.size" in seller_center),
    ("My Shop preserves authenticated shop profile editing and save path", 'Text("Shop identity"' in seller_center and '"/api/marketplace/my-shop"' in seller_center and 'Text("Save shop details")' in seller_center),
    ("My Shop profile save success uses semantic success green while profile failures remain errors", 'shopMessage?.let { Text(it, color = if (it == "Shop profile saved.") androidx.compose.ui.graphics.Color(0xFF16A34A) else MaterialTheme.colorScheme.error) }' in seller_center and 'shopError?.let { Text(it, color = MaterialTheme.colorScheme.error) }' in seller_center),
    ("My Shop clears stale profile errors when any field changes and before retrying save", all(fragment in seller_center for fragment in ("shopName = it.take(120); shopMessage = null; shopError = null", "shopDescription = it.take(800); shopMessage = null; shopError = null", "shopLocation = it.take(160); shopMessage = null; shopError = null", "shopContactPhone = it.take(40); shopMessage = null; shopError = null", "shopSaving = true\n                            shopMessage = null\n                            shopError = null"))),
    ("My Shop preserves listing edit, delete, and per-listing delivery/pickup settings", 'Icon(Icons.Default.Edit, "Edit listing")' in seller_center and 'Icon(Icons.Default.Delete, "Remove listing")' in seller_center and "FynxMarketplaceShippingSettingsCard(" in seller_center),
    ("My Shop edit form and authenticated backend update description, category, condition, and location while retaining seller ownership and price-watch notifications", all(fragment in seller_center for fragment in ('label = { Text("Product description") }', 'label = { Text("Category") }', 'Text("Condition", style = MaterialTheme.typography.labelLarge)', 'listOf("NEW", "USED", "REFURBISHED")', 'label = { Text("Product location") }', 'verticalScroll(rememberScrollState())')) and all(fragment in marketplace_client for fragment in ('"description", cleanDescription.take(4000)', '"category", cleanCategory.take(40)', '"condition", cleanCondition', '"location", cleanLocation.take(160)')) and all(fragment in social_routes for fragment in ('description = $4, category = $5, condition = $6, location = $7', 'WHERE id = $1 AND seller_id = $2 FOR UPDATE', 'if (priceChanged)'))),
    ("My Shop listing cards display the first uploaded product image and an explicit fallback when no media exists", 'FynxRemoteMedia(' in seller_center and 'FynxMarketplaceClient.mediaUrl(context, listing.mediaIds.first())' in seller_center and 'Product image unavailable' in seller_center),
    ("My Shop keeps seller-order review in Orders and driver assignments in Delivery & drivers", 'if (activeSection == "Orders")' in seller_center and "FynxMarketplaceSellerOrders(context = context" in seller_center and 'if (activeSection == "Delivery & drivers")' in seller_center and "showDriverAssignments = true" in seller_center),
    ("My Shop preserves earnings, settlement history, and payout account management", 'if (activeSection == "Earnings & payouts")' in seller_center and 'Text("Earnings history"' in seller_center and 'Text("Payout account"' in seller_center and 'showPayoutAccount = true' in seller_center),
    ("My Shop reports partial settlement-history failures instead of silently presenting incomplete earnings as complete", "var settlementFailures = 0" in seller_center and "settlementFailures += 1" in seller_center and "Some settlement records could not load. Refresh My Shop to retry." in seller_center and "moneyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }" in seller_center),
    ("My Shop earnings history distinguishes a failed empty load from a genuine empty history and offers retry", 'moneyError?.let { message ->' in seller_center and 'TextButton(onClick = { loadMoney() }, enabled = !moneyLoading)' in seller_center and 'Text("Retry earnings")' in seller_center and "moneyRecords.isEmpty() && moneyError == null" in seller_center),

    ("My Shop distinguishes successful payout-account save feedback from errors", 'val isAccountSuccess = accountMessage == "Payout account verified and connected."' in seller_center and "if (isAccountSuccess) androidx.compose.ui.graphics.Color(0xFF16A34A) else MaterialTheme.colorScheme.error" in seller_center),
    ("My Shop payout dialog uses success green for verified save feedback and error colour for failures", 'accountMessage?.let { Text(it, color = if (it == "Payout account verified and connected.") androidx.compose.ui.graphics.Color(0xFF16A34A) else MaterialTheme.colorScheme.error) }' in seller_center),
    ("seller orders offer retry on initial failure and preserve loaded rows when refresh fails", all(fragment in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerOrders.kt").read_text() for fragment in ('error != null && orders.isEmpty()', 'TextButton(onClick = { load() }, enabled = !loading) { Text("Retry seller orders") }', 'Some order details could not refresh: ${error!!}'))),
    ("seller order handover-photo selection is shown as success while action failures remain errors", 'message?.let { Text(it, color = if (it == "Handover photo selected; it will upload when you confirm handover.") androidx.compose.ui.graphics.Color(0xFF16A34A) else MaterialTheme.colorScheme.error) }' in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerOrders.kt").read_text()),
    ("Marketplace Home exposes direct My Shop, Orders, and Saved Products shortcuts", all(f'Text("{label}"' in marketplace for label in ("My Shop", "Orders", "Saved products")) and "onOpenSavedProducts" in marketplace and 'onOpenSavedProducts = { selected = "Saved Products" }' in app_shell),
    ("product cards expose a stateful Save action wired to saved-product APIs", "isFavorite = listing.id in savedProductIds" in marketplace and "onFavorite = { toggleSavedProductFromCard(listing.id) }" in marketplace and "FynxRemoteSocialClient.saveMarketplaceProduct(context, listingId)" in marketplace and "FynxRemoteSocialClient.unsaveMarketplaceProduct(context, listingId)" in marketplace and 'Icons.Default.Favorite else Icons.Default.FavoriteBorder' in product_card),
    ("product-card Save disables repeated taps and shows pending feedback", "isFavoriteBusy = listing.id in savedProductBusyIds" in marketplace and "enabled = !isFavoriteBusy" in product_card and "CircularProgressIndicator(" in product_card),
    ("product cards show truthful inventory availability from listing quantity", '"Out of stock"' in product_card and '"Only ${listing.quantity} left"' in product_card and "listing.quantity <= 0" in product_card and "listing.quantity <= 5" in product_card),
    ("Saved Products can open the canonical listing in Marketplace", 'Text("View product")' in saved_products_panel and "onOpenListing(product.id)" in saved_products_panel and 'FynxSavedMarketplaceProductsPanel(onOpenListing = { listingId -> marketplaceListingId = listingId; selected = "Marketplace" })' in app_shell and "loadExactMarketplaceListing(context, listingId)" in marketplace),
    ("Marketplace Orders shortcut fetches independently and shows loading/error states", "fun reloadOrders()" in marketplace and "ordersLoading" in marketplace and "ordersError" in marketplace and "Loading your orders…" in marketplace and "Orders could not load." in marketplace and "onOrders = { showOrders = true; reloadOrders() }" in marketplace),
    ("successful listing publish refreshes the seller-owned inventory endpoint rather than public discovery", "myListings(context).getOrThrow().firstOrNull { it.id == id }" in remote_client and "General discovery deliberately excludes the signed-in seller's own products." in remote_client),
    ("seller inventory refresh hides soft-deleted listings after the backend returns inactive history", 'FynxMarketplaceClient.myListings(context)' in seller_center and '.onSuccess { listings = it.filter { listing -> listing.active } }' in seller_center),
    ("My Shop distinguishes an inventory load failure from a genuinely empty inventory and offers retry", 'listings.isEmpty() && error != null' in seller_center and 'Text("Retry loading listings")' in seller_center),
    ("buyer order delivery detail failures offer an explicit retry", 'deliveryDetailsError?.let' in order_lifecycle and 'Text("Retry delivery details")' in order_lifecycle and '.onFailure { deliveryDetailsError' in order_lifecycle),
    ("fallback Marketplace search matches shop name, location and category", all(term in social_routes for term in ("COALESCE(l.store_name", "COALESCE(l.location", "COALESCE(l.category")) and "COALESCE(l.store_name" in discovery_routes),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)

if failed:
    raise SystemExit("Marketplace seller/product-flow verification failed: " + ", ".join(failed))

print(f"Marketplace seller/product-flow verification GREEN ({len(checks)} checks)")
