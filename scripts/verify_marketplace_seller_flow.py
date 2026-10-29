from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

support = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerFlowSupport.kt").read_text()
marketplace = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text()
ad_support = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceCarouselSupport.kt").read_text()
hero = marketplace[marketplace.index("private fun MarketplaceSmartDealsHero"):marketplace.index("@Composable\nfun FynxMarketplacePanel")]
checkout = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceCheckout.kt").read_text()
detail_components = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceReferenceDetailComponents.kt").read_text()
marketplace_reference_components = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceReferenceComponents.kt").read_text()
design_system = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxDesignSystem.kt").read_text()
product_card = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceReferenceProductComponents.kt").read_text()
seller_center = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerCenterPanel.kt").read_text()
photo_enhancement = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePhotoEnhancement.kt").read_text()
app_gradle = (ROOT / "app/build.gradle.kts").read_text()
app_manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
file_provider_paths = (ROOT / "app/src/main/res/xml/fynx_file_paths.xml").read_text()
seller_orders = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerOrders.kt").read_text()
app_shell = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text()
saved_products_panel = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxSavedMarketplaceProductsPanel.kt").read_text()
storefront = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerStoreDialog.kt").read_text()
remote_client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text()
marketplace_client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceClient.kt").read_text()
discovery_client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxDiscoveryClient.kt").read_text()
order_lifecycle = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceOrderLifecycle.kt").read_text()
receipt = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceReceipt.kt").read_text()
order_timeline = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceOrderTimeline.kt").read_text()
marketplace_payments = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePayments.kt").read_text()
buy_together = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceBuyTogether.kt").read_text()
seller_manager = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerManager.kt").read_text()
shipping_settings = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceShippingSettingsCard.kt").read_text()
marketplace_reviews = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceReviews.kt").read_text()
discovery_routes = (ROOT / "backend/discoveryRoutes.js").read_text()
social_routes = (ROOT / "backend/socialRoutes.js").read_text()
marketplace_checkout_route = (ROOT / "backend/marketplaceCheckout.js").read_text()
settlement_routes = (ROOT / "backend/marketplaceSettlement.js").read_text()
shop_profile_routes = (ROOT / "backend/marketplaceShopProfiles.js").read_text()

import re

EXPECTED_DISCOVERY_CATEGORIES = ["All", "Electronics", "Fashion", "Personal Accessories", "Home", "Beauty", "Vehicles", "Services", "Food", "Property", "Agriculture", "Phones & Computers", "Furniture", "Baby & Kids", "Books", "Sports", "Tools", "Health", "Appliances", "Auto Parts", "Building Materials", "Office & Education", "Garden & Outdoor", "Handmade & Local", "Business & Workshop Supplies", "Other"]
EXPECTED_SELLER_CATEGORIES = EXPECTED_DISCOVERY_CATEGORIES[1:]

def _extract_string_list(pattern: str, source: str):
    match = re.search(pattern, source, re.DOTALL)
    if not match:
        return None
    return re.findall(r'"([^"]+)"', match.group(1))

sell_dialog = marketplace[marketplace.index("private fun MarketplaceSellDialog"):marketplace.index("private fun contextIsVideo")]

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
    ("Marketplace category tiles use offline category artwork rather than the old generic icon mapping", "MarketplaceCategoryArtwork(itemCategory" in marketplace and "Canvas(modifier = modifier)" in marketplace and "private fun MarketplaceCategoryArtwork" in marketplace),
    ("Marketplace category artwork covers the current discovery taxonomy", all(token in marketplace for token in ('name.contains("electronics")', 'name.contains("fashion")', 'name.contains("personal accessories")', 'name.contains("home")', 'name.contains("beauty")', 'name.contains("vehicle")', 'name.contains("food")', 'name.contains("agriculture")', 'name.contains("baby")', 'name.contains("book")', 'name.contains("sport")', 'name.contains("tool")', 'name.contains("appliance")', 'name.contains("auto parts")', 'name.contains("building")', 'name.contains("office")', 'name.contains("garden")', 'name.contains("handmade")', 'name.contains("local")', 'name.contains("workshop")', 'name.contains("business")', 'Neutral storefront/box scene'))),
    ("Marketplace category artwork remains separate from real seller inventory photos", "never used as seller listing media" in marketplace and "MarketplaceCategoryArtwork" not in hero and "RemoteMarketMedia(" in hero),
    ("My Shop active listing thumbnails use the real seller media and a consistent crop", "mediaUrl = FynxMarketplaceClient.mediaUrl(context, listing.mediaIds.first())" in seller_center and "modifier = Modifier.size(88.dp),\n                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop" in seller_center),
    ("My Shop archived listing thumbnails use the real seller media and a consistent crop", "mediaUrl = FynxMarketplaceClient.mediaUrl(context, listing.mediaIds.first())" in seller_center and "modifier = Modifier.size(72.dp),\n                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop" in seller_center),
    ("seller photo cleanup uses on-device ML Kit subject segmentation instead of a remote processing API", "SubjectSegmentation.getClient" in photo_enhancement and "segmenter.process(input)" in photo_enhancement and "FynxProductionMessaging.uploadMedia" not in photo_enhancement),
    ("seller photo cleanup fails closed on Android 16+ while the beta segmentation SDK has an open native-crash report",
     "if (Build.VERSION.SDK_INT >= 36)" in photo_enhancement
     and "temporarily unavailable on Android 16 and newer" in photo_enhancement
     and photo_enhancement.index("if (Build.VERSION.SDK_INT >= 36)") < photo_enhancement.index("SubjectSegmentation.getClient")
     and "failure.message" in marketplace
     and 'it.contains("temporarily unavailable on Android 16 and newer")' in marketplace),
    ("Marketplace hides the unsupported photo cleanup action on Android 16 and explains originals remain publishable",
     "fun isSupportedOnCurrentAndroid(): Boolean = Build.VERSION.SDK_INT < 36" in photo_enhancement
     and "if (FynxMarketplacePhotoEnhancement.isSupportedOnCurrentAndroid())" in marketplace
     and "Photo background cleanup is temporarily unavailable on Android 16 and newer." in marketplace
     and "Your original photos still work and can be published normally." in marketplace),
    ("seller photo cleanup preserves the original and offers preview, Keep Original, Use Enhanced and Undo", all(token in marketplace for token in ("Review enhanced photo", "Keep Original", "Use Enhanced", "Text(\"Undo\"")) and "originalByEnhancedUri" in marketplace),
    ("re-enhancing a seller photo preserves the first original for Undo and deletes only the superseded generated image",
     "val rootOriginal = originalByEnhancedUri[original.toString()] ?: original" in marketplace
     and "originalByEnhancedUri = (originalByEnhancedUri - replacedOld.toString()) + (enhanced.toString() to rootOriginal)" in marketplace
     and "if (replacedOld != rootOriginal)" in marketplace
     and "FynxMarketplacePhotoEnhancement.discard(replacedOld)" in marketplace),
    ("seller photo cleanup uses app-private temporary files and a scoped FileProvider path", "FileProvider.getUriForFile" in photo_enhancement and 'name="marketplace_photo_enhancements"' in file_provider_paths and '"\u0024{context.packageName}.fileprovider"' in photo_enhancement),
    ("seller photo cleanup requests the Play services model only after the seller opts in, avoiding an install-time model download",
     "play-services-mlkit-subject-segmentation:16.0.0-beta1" in app_gradle
     and "installer.areModulesAvailable(segmenter)" in photo_enhancement
     and "installer.installModules(request)" in photo_enhancement
     and 'android:name="com.google.mlkit.vision.DEPENDENCIES"' not in app_manifest
     and 'android:value="subject_segment"' not in app_manifest),
    ("seller photo cleanup refuses missing foreground results instead of silently publishing an unchanged image as enhanced", "No product foreground was found." in photo_enhancement and "The background could not be separated clearly." in photo_enhancement),
    ("seller photo enhancement releases temporary subject, scaled-working, and output bitmaps on every failure path",
     "private fun renderProductPhoto(foreground: Bitmap): Bitmap" in photo_enhancement
     and "var subject: Bitmap? = null" in photo_enhancement
     and "var output: Bitmap? = null" in photo_enhancement
     and "var completed = false" in photo_enhancement
     and "subject?.takeIf { it !== working }?.recycle()" in photo_enhancement
     and "if (working !== foreground) working.recycle()" in photo_enhancement
     and "if (!completed) output?.recycle()" in photo_enhancement),
    ("seller photo cleanup keeps output proportions and uses a bounded working image", "MAX_WORKING_DIMENSION = 1600" in photo_enhancement and "SUBJECT_PADDING" in photo_enhancement and "canvas.drawBitmap(" in photo_enhancement),
    ("Marketplace discovery client sends category under the backend-supported query key", '&category=${URLEncoder.encode(category, "UTF-8")}' in remote_client and 'req.query?.category' in discovery_routes),
    ("Marketplace backend filters selected categories exactly and leaves All unfiltered", 'category.toLowerCase() !== "all"' in discovery_routes and 'where.push("l.category=$" + params.length)' in discovery_routes),
    ("seller form and discovery expose the same expanded category set",
     _extract_string_list(r"val categories = listOf\(([^)]*)\)", marketplace) == EXPECTED_DISCOVERY_CATEGORIES
     and _extract_string_list(r'Text\("Category"[\s\S]*?listOf\(([^)]*)\)\.forEach', marketplace) == EXPECTED_SELLER_CATEGORIES
     and '"REFURBISHED" to "Refurbished"' in marketplace),
    ("Marketplace seller flow exposes optional category-specific subcategories and resets stale selections when the main category changes",
     "internal fun marketplaceSubcategoriesFor(category: String)" in marketplace
     and 'Text("Subcategory (optional)"' in marketplace
     and "val categorySubcategories = marketplaceSubcategoriesFor(category)" in marketplace
     and 'onClick = { if (category != item) clearCategorySpecificFields(); category = item; subcategory = "" }' in marketplace
     and all(token in marketplace for token in ('"auto parts" ->', '"appliances" ->', '"building materials" ->', '"office & education" ->', '"garden & outdoor" ->', '"handmade & local" ->', '"personal accessories" ->', '"business & workshop supplies" ->'))),
    ("selected Marketplace subcategory is saved in the listing description while the existing main-category value remains the backend category",
     'listOf("Subcategory" to subcategory) + specifications' in marketplace
     and "createMarketplaceListing(context, title, listingDescription" in marketplace
     and "currency, category, condition" in marketplace),
    ("seller form shows category-specific product specifications", all(term in marketplace for term in ('"Brand"', '"Model / model number"', '"Size / fit"', '"Dimensions (e.g. 120 × 60 × 75 cm)"', '"Year"', '"Ingredients, skin/hair type, or expiry details"', '"What is included, duration, and service area"'))),
    ("switching seller category clears prior category-specific values to prevent incorrect details being published", "fun clearCategorySpecificFields()" in marketplace and "if (category != item) clearCategorySpecificFields()" in marketplace),
    ("category specifications are included in the saved product description with bounded length", 'append("\\n\\nProduct specifications\\n")' in marketplace and "MAX_DESCRIPTION_LENGTH" in marketplace and "listingDescription" in marketplace),
    ("Marketplace descriptions reserve space for category specifications within the backend 4,000-character limit",
     "val maximumSavedDescriptionLength = minOf(4_000, FynxMarketplaceSellerFlowSupport.MAX_DESCRIPTION_LENGTH)" in marketplace
     and "val descriptionBodyLimit = (maximumSavedDescriptionLength - specificationSection.length).coerceAtLeast(0)" in marketplace
     and "append(desc.trim().take(descriptionBodyLimit))" in marketplace
     and "append(specificationSection)" in marketplace
     and "Up to $descriptionBodyLimit characters are available for this category." in marketplace),
    ("seller publishes selected condition and composed specifications through the existing listing path", 'title, listingDescription, "", price.toDouble(), currency, category, condition' in marketplace and 'validListing(title, listingDescription' in marketplace),
    ("active marketplace remains backend listing driven", "FynxRemoteSocialClient.listings" in marketplace),
    ("Marketplace discovery includes the signed-in seller own active products while the detail UI disables self-purchase and backend checkout rejects it", 'const params = [req.user.sub]' in discovery_routes and 'l.seller_id<>$1' not in discovery_routes and 'canPurchase = !isOwnListing' in marketplace and 'Buyers can see it in Marketplace, but you cannot buy your own product.' in marketplace and 'enabled = canPurchase && l.quantity > 0' in marketplace and "you cannot purchase your own listing" in marketplace_checkout_route),
    ("Marketplace discovery hides sellers blocked in either direction without hiding the signed-in seller own listings", "NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$1 AND b.blocked_id=l.seller_id) OR (b.blocked_id=$1 AND b.blocker_id=l.seller_id))" in discovery_routes and "l.seller_id<>$1" not in discovery_routes),
    ("Marketplace listings fallback and public storefront apply the same two-way block filter while allowing the signed-in seller to see their own products", "NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$1 AND b.blocked_id=l.seller_id) OR (b.blocked_id=$1 AND b.blocker_id=l.seller_id))" in social_routes and "l.seller_id <> $1" not in social_routes),
    ("product details show the real media carousel with media type preserved per item", "items(l.mediaIds.take(12).mapIndexed" in marketplace and "l.mediaTypes.getOrNull(index)" in marketplace),
    ("product details show seller and fulfillment information", 'Text("Seller:' in marketplace and 'Text("Location:' in marketplace and "deliveryAvailable" in marketplace and "pickupAvailable" in marketplace),
    ("active Marketplace exposes the visible Message seller action wired to the real chat deep link", 'Text("Message seller")' in detail_components and "onContact = { contactSeller(listing.sellerUsername, listing.id) }" in marketplace and "FynxDeepLinkParser.chatAppLink" in marketplace and "Intent.ACTION_VIEW" in marketplace),
    ("active marketplace exposes Add to cart", "Add to cart" in marketplace and "cart = cart + listing" in marketplace),
    ("active marketplace Buy now enters the real checkout", "onBuyNow = { selected = null; checkoutListing = listing }" in marketplace and "FynxMarketplaceCheckoutDialog" in marketplace),
    ("confirmed single-item Buy Now payment clears retry state and removes the purchased product and quantity from the saved cart", "onPaid = { clearMarketplaceCheckoutOrderIdForOrder(context, order.id); paymentOrder = null; cart = cart.filterNot { it.id == order.listingId }; cartQuantities = cartQuantities.filterKeys { it != order.listingId }; protectedOrder = order; reload() }" in marketplace),
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
    ("cart checkout is blocked when a selected listing has become unavailable or cart quantity exceeds available stock", "val unavailable = selected.any { it.quantity <= 0 || (quantities[it.id] ?: 1) > it.quantity }" in marketplace and "enabled = selected.isNotEmpty() && !mixed && !unavailable" in marketplace and "Some selected products no longer have enough stock." in marketplace),
    ("marketplace Buy now and Add to cart are disabled for out-of-stock products and seller-owned listings", "onBuyNow" in marketplace and "enabled = canPurchase && l.quantity > 0" in marketplace and "Text(if (!canPurchase) \"Your listing\" else if (l.quantity > 0) \"Add to cart\" else \"Out of stock\")" in marketplace),
    ("marketplace checkout initializes payment against the protected order", "initializeMarketplacePayment(context, order.id, email)" in marketplace),
    ("marketplace checkout requires payment verification before protection confirmation", "verifyMarketplacePayment(context, payment?.reference.orEmpty())" in marketplace),
    ("single-item checkout rejects invalid quantities without converting zero into a purchase of one", "val quantity = quantityText.toIntOrNull() ?: 0" in checkout and "quantityText.toIntOrNull()?.coerceAtLeast(1)" not in checkout and "val quantityError = when" in checkout and "This product is currently out of stock." in checkout and "Choose a quantity of at least 1." in checkout and "Maximum available quantity is ${listing.quantity}." in checkout and "quantityError?.let" in checkout and "enabled = !busy && quantityError == null" in checkout),
    ("marketplace checkout rejects an expired client-side quote before order creation", "marketplaceQuoteExpired(q.expiresAt)" in checkout),
    ("marketplace checkout treats malformed or expired quote timestamps as unsafe", "Instant.parse(expiresAt)" in checkout and "getOrDefault(true)" in checkout),
    ("checkout invalidates the displayed quote whenever quantity, fulfillment or delivery contact/destination changes", "fun invalidateQuote()" in checkout and "quoteRequestGeneration += 1" in checkout and all(fragment in checkout for fragment in ('quantityText = it.filter(Char::isDigit); invalidateQuote()', 'fulfillment = "DELIVERY"; invalidateQuote()', 'fulfillment = "PICKUP"; invalidateQuote()', 'name = it; invalidateQuote()', 'phone = it; invalidateQuote()', 'address = it; invalidateQuote()', 'city = it; invalidateQuote()', 'state = it; invalidateQuote()'))),
    ("checkout ignores stale quote responses that finish after the buyer edits the form", "val requestGeneration = quoteRequestGeneration" in checkout and "if (quoteRequestGeneration == requestGeneration)" in checkout),
    ("multi-product checkout invalidates quotes on fulfillment and delivery detail edits", "fun invalidateQuote(){quoteRequestGeneration+=1;quote=null;message=null}" in marketplace and all(fragment in marketplace for fragment in ('methods=methods+(item.id to "DELIVERY");invalidateQuote()', 'methods=methods+(item.id to "PICKUP");invalidateQuote()', '{name=it;invalidateQuote()}', '{phone=it;invalidateQuote()}', '{address=it;invalidateQuote()}', '{city=it;invalidateQuote()}', '{state=it;invalidateQuote()}'))),
    ("multi-product checkout ignores quote responses that finish after the buyer changes the form", "val requestGeneration=quoteRequestGeneration" in marketplace and "if(quoteRequestGeneration==requestGeneration)" in marketplace),
    ("protected order lifecycle exposes fulfillment actions", "FynxMarketplaceOrderLifecycle" in marketplace and "Choose fulfillment" in marketplace),
    ("protected order lifecycle supports received confirmation", '"SHIPPED" -> "Confirm the order when you receive it."' in marketplace),
    ("protected order lifecycle supports completion after inspection", 'else -> "Inspect the order and complete it when everything is correct."' in marketplace),
    ("protected orders expose dispute reporting", "disputeMarketplaceOrder" in marketplace and "Open dispute" in marketplace),
    ("protected orders expose cancellation for unpaid orders", "cancelMarketplaceOrder" in marketplace and "PAYMENT_PENDING" in marketplace),
    ("completed protected orders support seller reviews", "reviewMarketplaceOrder" in marketplace and "Submit review" in marketplace),
    ("seller publish still requires a real listing area", "location.trim().isNotBlank()" in marketplace and "validListing(title, listingDescription, price.toDoubleOrNull(), quantity.toIntOrNull(), media)" in marketplace),
    ("seller publishing presents the approved category choices once, with subcategories in a separate selector", sell_dialog.count('Text("Category", style = MaterialTheme.typography.labelLarge)') == 1 and 'Text("Subcategory (optional)"' in sell_dialog),
    ("expanded Marketplace categories expose specific details for appliances, auto parts, and building materials", all(token in marketplace for token in ('"Electronics", "Phones & Computers", "Appliances" ->', '"Personal Accessories" ->', '"Home", "Furniture" ->', '"Beauty", "Health" ->', '"Baby & Kids" ->', '"Books" ->', '"Property" ->', '"Auto Parts" ->', '"Building Materials" ->', '"Part number / compatible vehicle"', '"Material / grade"'))),
    ("saved-product IDs hydrate from the authenticated endpoint and reset per account", "var savedProductIds by remember(cartOwnerKey)" in marketplace and "var savedProductBusyIds by remember(cartOwnerKey)" in marketplace and "LaunchedEffect(cartOwnerKey)" in marketplace and "FynxRemoteSocialClient.savedMarketplaceProducts(context)" in marketplace and "savedProductStateLoaded = true" in marketplace),
    ("saved-product state sync cannot overwrite a user toggle made while the request is in flight", "val requestGeneration = savedProductStateGeneration" in marketplace and "if (savedProductStateGeneration == requestGeneration)" in marketplace and "savedProductStateGeneration += 1" in marketplace),
    ("saved-product toggles resolve server state before mutation when local state is unknown", "toggleMarketplaceProductSaved(context, listingId, knownSavedProductState(listingId))" in marketplace and "val current = knownSaved?.let { Result.success(it) } ?: marketplaceProductSaved(context, listingId)" in remote_client and "if (currentlySaved) unsaveMarketplaceProduct(context, listingId)" in remote_client),
    ("seller listing location remains human-readable and privacy-preserving", "FynxPostLocationClient.currentPlace(context)" in marketplace and "your exact GPS coordinates are not published with the listing." in marketplace),
    ("buyer nearby discovery resolves the user area and requests nearby listings with the active search/filter", "FynxPostLocationClient.currentPlace(context)" in marketplace and "FynxRemoteSocialClient.nearbyMarketplaceListings(context, requestQuery, requestCategory, requestNearbyLabel)" in marketplace),
    ("My Shop has separate overview, profile, listings, orders, delivery/drivers, and earnings/payout destinations", all(f'"{section}"' in seller_center for section in ("Overview", "Shop profile", "Listings", "Orders", "Delivery & drivers", "Earnings & payouts"))),
    ("My Shop overview links to each management destination and uses existing loaded counts", "Your shop at a glance" in seller_center and 'activeSection = "Shop profile"' in seller_center and 'activeSection = "Listings"' in seller_center and 'activeSection = "Orders"' in seller_center and 'activeSection = "Delivery & drivers"' in seller_center and 'activeSection = "Earnings & payouts"' in seller_center and "listings.size" in seller_center and "moneyRecords.size" in seller_center),
    ("My Shop preserves authenticated shop profile editing and save path", 'Text("Shop identity"' in seller_center and '"/api/marketplace/my-shop"' in seller_center and 'Text("Save shop details")' in seller_center),
    ("first My Shop save creates a seller shop profile and later saves update that same authenticated seller row", "export function ensureMarketplaceShopProfileSchema(pool)" in shop_profile_routes and "CREATE TABLE IF NOT EXISTS marketplace_shop_profiles" in shop_profile_routes and "app.patch('/api/marketplace/my-shop', auth" in shop_profile_routes and "INSERT INTO marketplace_shop_profiles (seller_id, shop_name, description, location, contact_phone)" in shop_profile_routes and "ON CONFLICT (seller_id) DO UPDATE SET" in shop_profile_routes and "WHERE u.id = $1" in shop_profile_routes and "req.user.sub" in shop_profile_routes and "if (shopName.length < 2)" in shop_profile_routes),
    ("My Shop profile save success uses semantic success green while profile failures remain errors", 'shopMessage?.let { Text(it, color = if (it == "Shop profile saved.") androidx.compose.ui.graphics.Color(0xFF16A34A) else MaterialTheme.colorScheme.error) }' in seller_center and 'shopError?.let { Text(it, color = MaterialTheme.colorScheme.error) }' in seller_center),
    ("My Shop clears stale profile errors when any field changes and before retrying save", all(fragment in seller_center for fragment in ("shopName = it.take(120); shopMessage = null; shopError = null", "shopDescription = it.take(800); shopMessage = null; shopError = null", "shopLocation = it.take(160); shopMessage = null; shopError = null", "shopContactPhone = it.take(40); shopMessage = null; shopError = null", "shopSaving = true\n                            shopMessage = null\n                            shopError = null"))),
    ("My Shop preserves listing edit, delete, and per-listing delivery/pickup settings", 'Icon(Icons.Default.Edit, "Edit listing")' in seller_center and 'Icon(Icons.Default.Delete, "Remove listing")' in seller_center and "FynxMarketplaceShippingSettingsCard(" in seller_center),
    ("My Shop listing edit uses the approved exact category list and preserves existing unknown legacy categories without free-text typos",
     "val standardMarketplaceCategories = listOf(" in seller_center
     and "val editCategoryOptions = if (" in seller_center
     and "DropdownMenuItem(" in seller_center
     and "editCategory = option" in seller_center
     and "standardMarketplaceCategories + editCategory" in seller_center
     and "onValueChange = { editCategory = it.take(40) }" not in seller_center
     and _extract_string_list(r"val standardMarketplaceCategories = listOf\(([^)]*)\)", seller_center) == EXPECTED_SELLER_CATEGORIES
     and "DropdownMenu(" in seller_center
     and all(value in seller_center for value in ('"Phones & Computers"', '"Auto Parts"', '"Business & Workshop Supplies"'))),
    ("My Shop edit listing offers matching subcategories and clears stale choices when the main category changes",
     'var editSubcategory by rememberSaveable { mutableStateOf("") }' in seller_center
     and seller_center.count("editSubcategory = marketplaceDescriptionSubcategory(listing.description)") == 2
     and "val editSubcategoryOptions = marketplaceSubcategoriesFor(editCategory)" in seller_center
     and 'if (!editCategory.equals(option, ignoreCase = true)) editSubcategory = ""' in seller_center
     and 'Text("Subcategory (optional)"' in seller_center
     and "editSubcategoryOptions.forEach { option ->" in seller_center
     and "internal fun marketplaceSubcategoriesFor(category: String)" in marketplace),
    ("My Shop edit persists the selected subcategory through its existing authenticated listing update",
     "marketplaceDescriptionWithSubcategory(editDescription, editSubcategory)" in seller_center
     and "updateListing(context, selectedListing.id, editTitle, savedDescription, editCategory" in seller_center
     and seller_center.count("description = savedDescription,") == 2
     and "private fun marketplaceDescriptionSubcategory(description: String)" in seller_center
     and "private fun marketplaceDescriptionWithSubcategory(description: String, subcategory: String)" in seller_center
     and 'val cleaned = Regex("' in seller_center
     and 'val cleaned = Regex("(?m)' not in seller_center
     and 'val subcategoryLine = "Subcategory: ${subcategory.trim()}' in seller_center),
    ("My Shop edit reserves the backend description limit for category subcategory metadata",
     "val maximumDescriptionLength = minOf(" in seller_center
     and "FynxMarketplaceSellerFlowSupport.MAX_DESCRIPTION_LENGTH" in seller_center
     and "val specificationSection = buildString" in seller_center
     and "val descriptionBodyLimit = (" in seller_center
     and "maximumDescriptionLength - specificationSection.length - separator.length" in seller_center
     and "val limitedBody = descriptionBody.take(descriptionBodyLimit).trimEnd()" in seller_center
     and ").take(maximumDescriptionLength)" in seller_center
     and '.put("description", cleanDescription.take(4000))' in marketplace_client
     and ".slice(0, 4000)" in social_routes),
("My Shop stock editor and inventory cards label total stock and backend rejects quantities below existing reservations", 'label = { Text("Total stock (0 = out of stock)") }' in seller_center and '${listing.quantity} total stock • ${listing.category} • ${listing.condition}' in seller_center and "Stock cannot be reduced below items already reserved by active orders." in seller_center and "if (quantity < reservedQuantity)" in social_routes and "reserved_quantity FROM marketplace_listings WHERE id = $1 AND seller_id = $2 FOR UPDATE" in social_routes),
    ("buyer-facing Marketplace uses available stock after reservations while My Shop retains total stock", social_routes.count("AS available_quantity") >= 3 and discovery_routes.count("AS available_quantity") >= 2 and 'val stockValue = if (o.has("available_quantity") && !o.isNull("available_quantity")) {' in remote_client and 'o.opt("available_quantity")' in remote_client and "marketplaceRequiredInventoryQuantity(stockValue)" in remote_client and 'label = { Text("Total stock (0 = out of stock)") }' in seller_center),
    ("paginated Marketplace discovery parses available_quantity instead of total quantity", 'FynxMarketplaceClient.parseListings(raw, preferAvailableStock = true)' in discovery_client and 'if (preferAvailableStock)' in marketplace_client and 'else null' in marketplace_client[marketplace_client.index("val stockValue = if (preferAvailableStock)"):marketplace_client.index("return Listing(")] and 'o.opt("available_quantity")' in marketplace_client and "marketplaceRequiredInventoryQuantity(stockValue)" in marketplace_client and "AS available_quantity" in discovery_routes),
    ("public Marketplace detail and fallback parsers use available stock while My Shop explicitly keeps total stock", 'parseListings(raw, preferAvailableStock = true, activeOmittedMeansActive = true)' in marketplace_client and 'parseListingObject(JSONObject(raw).getJSONObject("listing"), preferAvailableStock = true)' in marketplace_client and 'parseListings(raw, preferAvailableStock = false)' in marketplace_client and 'if (preferAvailableStock)' in marketplace_client and 'else null' in marketplace_client[marketplace_client.index("val stockValue = if (preferAvailableStock)"):marketplace_client.index("return Listing(")] and 'o.opt("available_quantity")' in marketplace_client and 'o.opt("quantity")' in marketplace_client and "marketplaceRequiredInventoryQuantity(stockValue)" in marketplace_client),
    ("My Shop edit form and authenticated backend update description, category, condition, and location while retaining seller ownership and price-watch notifications", (
       'label = { Text("Product description") }' in seller_center
       and (
           'label = { Text("Category") }' in seller_center
           or (
               'val standardMarketplaceCategories = listOf(' in seller_center
               and 'Text(editCategory.ifBlank { "Choose category" }' in seller_center
               and "DropdownMenu(" in seller_center
               and "DropdownMenuItem(" in seller_center
               and "editCategory = option" in seller_center
           )
       )
       and 'Text("Condition", style = MaterialTheme.typography.labelLarge)' in seller_center
       and 'listOf("NEW", "USED", "REFURBISHED")' in seller_center
       and 'label = { Text("Product location") }' in seller_center
       and "verticalScroll(rememberScrollState())" in seller_center
    ) and all(fragment in marketplace_client for fragment in ('"description", cleanDescription.take(4000)', '"category", cleanCategory.take(40)', '"condition", cleanCondition', '"location", cleanLocation.take(160)')) and all(fragment in social_routes for fragment in ('description = $4, category = $5, condition = $6, location = $7', 'WHERE id = $1 AND seller_id = $2 FOR UPDATE', 'if (priceChanged)'))),
    ("My Shop listing cards display the first uploaded product image and an explicit fallback when no media exists", 'FynxRemoteMedia(' in seller_center and 'FynxMarketplaceClient.mediaUrl(context, listing.mediaIds.first())' in seller_center and 'Product image unavailable' in seller_center),
    ("My Shop keeps seller-order review in Orders and driver assignments in Delivery & drivers", 'if (activeSection == "Orders")' in seller_center and "FynxMarketplaceSellerOrders(context = context" in seller_center and 'if (activeSection == "Delivery & drivers")' in seller_center and "showDriverAssignments = true" in seller_center),
    ("My Shop preserves earnings, settlement history, and payout account management", 'if (activeSection == "Earnings & payouts")' in seller_center and 'Text("Recent earnings history (latest 100; showing 20)"' in seller_center and 'Text("Payout account"' in seller_center and 'showPayoutAccount = true' in seller_center),
    ("My Shop earnings use one authenticated seller-scoped summary query rather than per-order settlement requests", 'app.get(\'/api/marketplace/settlement/seller/summary\', auth' in settlement_routes and 'WHERE o.seller_id = $1' in settlement_routes and '"/api/marketplace/settlement/seller/summary"' in seller_center and 'settlements' in seller_center and '"/api/marketplace/settlement/order/$orderId"' not in seller_center and "payoutStatus: String(row.payout_status || row.funds || 'UNKNOWN')" in settlement_routes and 'moneyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }' in seller_center),
    ("My Shop fails closed on missing or malformed payout balances, amounts, and currency instead of defaulting to zero",
     all(fragment in seller_center for fragment in (
         "marketplaceRequiredCurrency(row.opt(\"currency\"))",
         "marketplaceRequiredBalance(row.opt(\"protectedFunds\"))",
         "marketplaceRequiredBalance(row.opt(\"availableForPayout\"))",
         "marketplaceRequiredBalance(row.opt(\"payoutPending\"))",
         "marketplaceRequiredBalance(row.opt(\"paidOut\"))",
         "marketplaceRequiredAmount(row.opt(\"sellerNetAmount\"))",
         "marketplaceRequiredBalance(row.opt(\"marketplaceFee\"))",
     ))
     and all(fragment in support for fragment in (
         "internal fun marketplaceRequiredAmount(value: Any?): Double",
         "internal fun marketplaceRequiredBalance(value: Any?): Double",
         "internal fun marketplaceRequiredCurrency(value: Any?): String",
         "if (!amount.isFinite())",
         "amount < 0.0",
     ))
     and 'row.optDouble("availableForPayout", 0.0)' not in seller_center
     and 'row.optDouble("sellerNetAmount", 0.0)' not in seller_center),
    ("My Shop hides current payout totals while refreshing and clears previous totals after a failed earnings request", "if (!moneyLoading && moneyError == null) {\n                        currencies.forEach { currency ->" in seller_center and "Checking current balances…" in seller_center and "Current balances are hidden until refresh succeeds." in seller_center and "moneyRecords = emptyList()\n                moneyTotals = emptyMap()\n                moneyTotalsFromBackend = false" in seller_center),
    ("My Shop earnings history distinguishes a failed empty load from a genuine empty history and offers retry", 'moneyError?.let { message ->' in seller_center and 'TextButton(onClick = { loadMoney() }, enabled = !moneyLoading)' in seller_center and 'Text("Retry earnings")' in seller_center and "moneyRecords.isEmpty() && moneyError == null" in seller_center),

    ("My Shop distinguishes successful payout-account save feedback from errors", 'val isAccountSuccess = accountMessage == "Payout account verified and connected."' in seller_center and "if (isAccountSuccess) androidx.compose.ui.graphics.Color(0xFF16A34A) else MaterialTheme.colorScheme.error" in seller_center),
    ("My Shop payout dialog uses success green for verified save feedback and error colour for failures", 'accountMessage?.let { Text(it, color = if (it == "Payout account verified and connected.") androidx.compose.ui.graphics.Color(0xFF16A34A) else MaterialTheme.colorScheme.error) }' in seller_center),
    ("seller orders offer retry on initial failure and preserve loaded rows when refresh fails", all(fragment in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerOrders.kt").read_text() for fragment in ('error != null && orders.isEmpty()', 'TextButton(onClick = { load() }, enabled = !loading) { Text("Retry seller orders") }', 'Some order details could not refresh: ${error!!}'))),
    ("seller order handover-photo selection is shown as success while action failures remain errors", 'message?.let { Text(it, color = if (it == "Handover photo selected; it will upload when you confirm handover.") androidx.compose.ui.graphics.Color(0xFF16A34A) else MaterialTheme.colorScheme.error) }' in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerOrders.kt").read_text()),
    ("Marketplace Home exposes direct My Shop, Orders, and Saved Products shortcuts", all(f'Text("{label}"' in marketplace for label in ("My Shop", "Orders", "Saved products")) and "onOpenSavedProducts" in marketplace and 'onOpenSavedProducts = { selected = "Saved Products" }' in app_shell),
    ("product cards expose a stateful Save action routed through the authoritative saved-product toggle", "isFavorite = listing.id in savedProductIds" in marketplace and "onFavorite = { toggleSavedProductFromCard(listing.id) }" in marketplace and "FynxRemoteSocialClient.toggleMarketplaceProductSaved(context, listingId, knownSavedProductState(listingId))" in marketplace and "suspend fun toggleMarketplaceProductSaved" in remote_client and "suspend fun saveMarketplaceProduct" in remote_client and "suspend fun unsaveMarketplaceProduct" in remote_client and 'Icons.Default.Favorite else Icons.Default.FavoriteBorder' in product_card),
    ("product-card Save disables repeated taps and shows pending feedback", "isFavoriteBusy = listing.id in savedProductBusyIds" in marketplace and "enabled = !isFavoriteBusy" in product_card and "CircularProgressIndicator(" in product_card),
    ("product cards show truthful inventory availability from listing quantity", '"Out of stock"' in product_card and '"Only ${listing.quantity} left"' in product_card and "listing.quantity <= 0" in product_card and "listing.quantity <= 5" in product_card),
    ("Saved Products hides listings when either the buyer or seller has blocked the other", "WHERE sp.user_id = $1 AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$1 AND b.blocked_id=l.seller_id) OR (b.blocked_id=$1 AND b.blocker_id=l.seller_id))" in social_routes),
    ("Saved Products can open the canonical listing in Marketplace", 'Text("View product")' in saved_products_panel and "onOpenListing(product.id)" in saved_products_panel and 'FynxSavedMarketplaceProductsPanel(onOpenListing = { listingId -> marketplaceListingId = listingId; selected = "Marketplace" })' in app_shell and "loadExactMarketplaceListing(context, listingId)" in marketplace),
    ("Marketplace Orders shortcut fetches independently and shows loading/error states", "fun reloadOrders()" in marketplace and "ordersLoading" in marketplace and "ordersError" in marketplace and "Loading your orders…" in marketplace and "Orders could not load." in marketplace and "onOrders = { showOrders = true; reloadOrders() }" in marketplace),
    ("successful Marketplace publish returns its confirmed listing ID without a second inventory request", "): Result<String> = runCatching {" in remote_client and "val id = JSONObject(raw).getJSONObject(\"listing\").optString(\"id\").trim()" in remote_client and "myListings(context).getOrNull()?.firstOrNull { it.id == id }" not in remote_client and "Check My Shop before trying again." in remote_client),
    ("accepted listing ID is shown with explicit My Shop and Marketplace recovery actions", "onPublished = { listingId -> showSell = false; publishSuccessListingId = listingId; reload() }" in marketplace and "var publishSuccessListingId by remember { mutableStateOf<String?>(null) }" in marketplace and 'Text("Product published")' in marketplace and 'Text("Product ID: $listingId", style = MaterialTheme.typography.labelLarge)' in marketplace and 'Text("Open My Shop")' in marketplace and 'Text("Refresh Marketplace")' in marketplace and "onPublished: (String) -> Unit" in marketplace and "createMarketplaceListing" in marketplace and "l.seller_id<>$1" not in discovery_routes and "enabled = canPurchase && l.quantity > 0" in marketplace),
    ("My Shop separates active inventory from an explicit archived-listings recovery section", 'FynxMarketplaceClient.myListings(context)' in seller_center and 'listings = inventory.filter { listing -> listing.active }' in seller_center and 'archivedListings = inventory.filterNot { listing -> listing.active }' in seller_center and 'Text("Archived listings"' in seller_center),
    ("archived Marketplace products can only be republished by their seller when unreserved stock is available", 'app.post("/api/marketplace/listings/:id/reactivate", auth' in social_routes and 'WHERE id = $1 AND seller_id = $2 FOR UPDATE' in social_routes and 'availableQuantity < 1' in social_routes and 'SET active = TRUE' in social_routes),
    ("My Shop exposes explicit republish with success/error feedback and returns the restored row to active inventory", 'FynxMarketplaceClient.reactivateListing(context, listing.id)' in seller_center and 'Text("Republish listing")' in seller_center and 'archivedListings = archivedListings.filterNot { it.id == listing.id }' in seller_center and 'listingActionError = marketplaceSafeError(it, "Listing could not be republished.")' in seller_center),
    ("My Shop distinguishes an inventory load failure from a genuinely empty inventory and offers retry", 'listings.isEmpty() && error != null' in seller_center and 'Text("Retry loading listings")' in seller_center),
    ("buyer order delivery detail failures offer an explicit retry", 'deliveryDetailsError?.let' in order_lifecycle and 'Text("Retry delivery details")' in order_lifecycle and '.onFailure { deliveryDetailsError' in order_lifecycle),
    ("fallback Marketplace search matches shop name, location and category", all(term in social_routes for term in ("COALESCE(l.store_name", "COALESCE(l.location", "COALESCE(l.category")) and "COALESCE(l.store_name" in discovery_routes),
]

checks.extend([
    ("public seller storefront has an explicit loading state for its own listing request", "var storefrontLoading by remember(username)" in storefront and "Loading this seller’s products…" in storefront),
    ("public seller storefront surfaces listing failures and provides a retry action", "storefrontError != null" in storefront and "Products could not load. Check your connection and try again." in storefront and "Retry products" in storefront and "storefrontReloadKey += 1" in storefront),
    ("public seller storefront retries by restarting the seller-specific request", "LaunchedEffect(username, viewerUsername, storefrontReloadKey)" in storefront and 'FynxRemoteSocialClient.listings(context, seller = username.removePrefix("@").trim())' in storefront),
    ("failed storefront requests do not silently substitute a partial Marketplace feed as the seller's complete catalogue", "storefrontListings.orEmpty()" in storefront and "storefrontListings ?: listings.filter" not in storefront),
    ("public seller storefront distinguishes a successful empty catalogue from a failed request", "else if (storefrontLoading)" in storefront and "else if (storefrontError != null)" in storefront and "else if (sellerListings.isEmpty())" in storefront),
    ("public seller storefront shows real follow state, follower count, and safe follow/unfollow recovery without allowing self-follow", "viewerUsername: String" in storefront and "profile.followedByCurrentUser" in storefront and "profile.followerCount" in storefront and "FynxRemoteSocialClient.follow(context, username, wasFollowing)" in storefront and 'followedByViewer == false -> "Follow shop"' in storefront and 'followedByViewer == true -> "Following"' in storefront and "!isOwnStore" in storefront and "followError" in storefront and 'Text("Retry follow status")' in storefront),
    ("Marketplace, My Shop and Saved Products discard account-scoped transient UI state when the signed-in account changes", "val marketplaceAccountKey = authSession.username?.removePrefix(\"@\")?.trim()?.lowercase(java.util.Locale.US)" in app_shell and '"Marketplace" -> key(marketplaceAccountKey) { FynxMarketplacePanel(' in app_shell and '"Seller Center" -> key(marketplaceAccountKey) { FynxMarketplaceSellerCenterPanel() }' in app_shell and '"Saved Products" -> key(marketplaceAccountKey) { FynxSavedMarketplaceProductsPanel(' in app_shell),
])

checks.extend([
    ("seller earnings endpoint computes uncapped currency totals with exclusive accounting buckets", "FROM seller_settlements" in settlement_routes and "GROUP BY currency" in settlement_routes and "AS available_for_payout" in settlement_routes and "LIMIT 100" in settlement_routes and "AS summary_bucket" in settlement_routes and "FROM classified_settlements" in settlement_routes),
    ("My Shop reads authoritative seller totals and warns when the backend only has a limited summary", "moneyTotalsFromBackend" in seller_center and "totalsArray" in seller_center and "This backend only provides the latest 100 settlements. Totals below may not include older orders." in seller_center and "Recent earnings history (latest 100; showing 20)" in seller_center),
    ("seller catalogue and saved-products APIs return ordered MIME types with indexable media-ID joins", "WITH ORDINALITY AS listing_media(media_id, ord)" in social_routes and "ORDER BY listing_media.ord" in social_routes and "AS media_types" in social_routes and "mm.id = CASE WHEN listing_media.media_id ~" in social_routes and social_routes.count("${marketplaceMediaTypesProjection}") == 3 and "media_types: Array.isArray(row.media_types)" in social_routes),
    ("Marketplace discovery and exact-listing APIs return ordered MIME types with indexable media-ID joins", "WITH ORDINALITY AS listing_media(media_id, ord)" in discovery_routes and "ORDER BY listing_media.ord" in discovery_routes and "AS media_types" in discovery_routes and "mm.id = CASE WHEN listing_media.media_id ~" in discovery_routes and discovery_routes.count("${marketplaceMediaTypesProjection}") == 2 and "media_types:Array.isArray(row.media_types)" in discovery_routes),
    ("Marketplace parsers preserve media MIME types and render product cards, details, storefront and My Shop without guessing", all(fragment in marketplace_client for fragment in ('o.optJSONArray("media_types")', "mediaTypes: List<String>", "fun mediaDisplayType(")) and 'FynxMarketplaceClient.parseListings(raw, preferAvailableStock = true)' in discovery_client and 'o.optJSONArray("media_types")' in remote_client and all("mediaDisplayType" in source and "mediaTypes" in source for source in (product_card, marketplace, seller_center, storefront))),
])

checks.extend([
    ("My Shop inventory, profile and payout refreshes are independent and each drops stale responses", 'scope.launch {\n            val inventoryResult = runCatching { FynxMarketplaceClient.myListings(context).getOrThrow() }' in seller_center and 'scope.launch {\n            val shopResult = runCatching {\n                val raw = FynxBackendClient.get(context, "/api/marketplace/my-shop").getOrThrow()' in seller_center and 'scope.launch {\n            val payoutResult = runCatching {\n                val raw = FynxBackendClient.get(context, "/api/marketplace/settlement/payout-account").getOrThrow()' in seller_center and seller_center.count("if (requestGeneration != refreshGeneration) return@launch") >= 3 and "loading = false" in seller_center and "shopLoading = false" in seller_center and "accountLoading = false" in seller_center and "loadMoney()" in seller_center),
    ("My Shop refresh rejects stale overlapping inventory/profile/payout responses", "var refreshGeneration by remember { mutableIntStateOf(0) }" in seller_center and "val requestGeneration = ++refreshGeneration" in seller_center and seller_center.count("if (requestGeneration != refreshGeneration) return@launch") >= 3),
    ("My Shop profile and payout GET responses are parsed inside exception-safe result boundaries", ('FynxBackendClient.get(context, "/api/marketplace/my-shop")\n                .mapCatching { raw ->' in seller_center or 'val raw = FynxBackendClient.get(context, "/api/marketplace/my-shop").getOrThrow()' in seller_center) and ('FynxBackendClient.get(context, "/api/marketplace/settlement/payout-account")\n                .mapCatching { raw ->' in seller_center or 'val raw = FynxBackendClient.get(context, "/api/marketplace/settlement/payout-account").getOrThrow()' in seller_center) and 'val shopResult = runCatching {' in seller_center and 'val payoutResult = runCatching {' in seller_center),
    ("failed inventory refresh clears old listing rows and labels the overview count unavailable", "Inventory is not current. Retry refresh before relying on listing availability." in seller_center and 'listings = emptyList()\n                    archivedListings = emptyList()' in seller_center and 'Listing count unavailable until refresh succeeds' in seller_center),
    ("failed or malformed payout-account refresh clears previous account and verification state", "payoutAccounts = emptyList()" in seller_center and "payoutAccount = null" in seller_center and 'accountMessage = marketplaceSafeError(failure, "Payout account status could not load.")' in seller_center),
    ("My Shop refresh button is disabled while the refresh is active", 'IconButton(onClick = { refresh() }, enabled = !loading) { Icon(Icons.Default.Refresh, "Refresh My Shop") }' in seller_center),
    ("shop-profile save parses the response inside mapCatching so malformed success cannot strand the save spinner", 'FynxBackendClient.patchJson(context, "/api/marketplace/my-shop", payload.toString())\n                                    .mapCatching { raw ->' in seller_center and 'shopSaving = false' in seller_center),
    ("seller orders JSON parsing is contained by mapCatching and rejects malformed order payloads", 'FynxBackendClient.get(context, "/api/marketplace/seller/orders")\n                .mapCatching { raw ->' in seller_orders and 'Seller orders response was incomplete.' in seller_orders and 'Seller orders response contains an invalid order.' in seller_orders),
    ("seller order requests reject stale responses and always clear loading for the current request", "var orderLoadGeneration by remember { mutableIntStateOf(0) }" in seller_orders and "if (requestGeneration != orderLoadGeneration) return@launch" in seller_orders and "loading = false" in seller_orders),
    ("seller-order read-only refresh avoids duplicate parent My Shop refresh and is disabled in flight", 'confirmButton = { TextButton(enabled = !loading, onClick = { load() }) { Text("Refresh") } }' in seller_orders and 'onClick = { load(); onChanged() }' not in seller_orders),
    ("seller-order refresh clears stale rows and selected delivery actions before a new request", "orders = emptyList()\n        selected = null\n        scope.launch" in seller_orders and 'orders = emptyList()\n                    selected = null\n                    error = marketplaceSafeError(failure, "Seller orders could not load.")' in seller_orders),
    ("seller-order payload rejects invalid amounts and missing status instead of displaying a false zero or pending order", 'order.optDouble("totalAmount", Double.NaN)' in seller_orders and '!total.isFinite() || total < 0.0' in seller_orders and 'order.optString("status").trim()' in seller_orders and 'Seller orders response contains an order without status.' in seller_orders and 'total = order.optDouble("totalAmount")' not in seller_orders),
])


checks.extend([
    ("multi-product quote validates every requested product and reconciles aggregate fees",
     "parseMarketplaceMultiCheckoutQuoteResponse(raw, expectedLines)" in marketplace
     and 'requiredText(item, "listingId")' in marketplace
     and 'requiredText(item, "fulfillmentMethod")' in marketplace
     and 'sumsTo(marketplaceFeeTotal, items.sumOf { it.marketplaceFee })' in marketplace
     and 'sumsTo(total, items.sumOf { it.total })' in marketplace),
    ("multi-product orders are validated against the reviewed quote before payment initialization",
     "parseMarketplaceMultiCheckoutOrderResponse(raw, quote)" in marketplace
     and 'if (status != "PAYMENT_PENDING")' in marketplace
     and 'No payment was started; review the exact total again.' in marketplace
     and 'createMultiOrder(context,lines(),q)' in marketplace),
])

checks.extend([
    ("single-item order rejects price drift from the reviewed quote before payment",
     "expectedQuote: FynxMarketplaceCheckoutQuote? = null" in checkout
     and "expectedQuote = expectedQuote" in checkout
     and 'if (status != "PAYMENT_PENDING")' in checkout
     and "stableOrderId,\n                            q" in checkout
     and "No payment was started; review the exact total again." in checkout),
])

checks.extend([
    ("Marketplace share, seller inventory, shipping settings and review errors also sanitize transport details",
     all("marketplaceSafeError(" in source for source in (buy_together, seller_manager, shipping_settings, marketplace_reviews))
     and "marketplaceSafeError(it, \"Unable to load your listings.\")" in seller_manager
     and "marketplaceSafeError(it, \"Shipping settings could not be saved.\")" in shipping_settings
     and "marketplaceSafeError(it, \"Review could not be submitted.\")" in marketplace_reviews),
    ("Marketplace receipts, order timelines and payment failures also sanitize transport details",
     all("marketplaceSafeError(" in source for source in (receipt, order_timeline, marketplace_payments))
     and "marketplaceSafeError(error, \"Could not start protected payment.\")" in marketplace_payments
     and "normalized.startsWith(\"java.net.\")" in support),
    ("Marketplace and My Shop sanitize raw HTTP/JSON error bodies before displaying errors",
     "internal fun marketplaceSafeError(error: Throwable?, fallback: String): String" in support
     and "request_id" in support
     and all("marketplaceSafeError(" in source for source in (marketplace, checkout, seller_center, seller_orders, storefront, saved_products_panel, order_lifecycle))),
])

checks.extend([
    ("Marketplace sell-form category, condition and currency chips use the approved Azure Blue palette",
     "val selectedChipColors = fynxMarketplaceSelectedChipColors()" in marketplace
     and marketplace.count("colors = selectedChipColors") >= 3
     and "selectedContainerColor = if (isLight) FynxDesign.AzureBlue" in marketplace_reference_components
     and "val AzureBlue = Color(0xFF007FFF)" in design_system
     and "val ClassicGreen = Color(0xFF008000)" in design_system
     and "val CrimsonRed = Color(0xFFDC143C)" in design_system
     and "private const val MARKETPLACE_LISTING_REQUEST_TIMEOUT_MS = 15_000L" in marketplace),
    ("multi-product checkout Delivery and Pickup selections keep Marketplace Azure Blue colors",
     'label={Text("Delivery")},enabled=!busy,colors=fynxMarketplaceSelectedChipColors()' in marketplace
     and 'label={Text("Pickup")},enabled=!busy,colors=fynxMarketplaceSelectedChipColors()' in marketplace),
    ("My Shop selected section tab scrolls fully into view on narrow screens",
     "val shopSectionsScrollState = rememberLazyListState()" in seller_center
     and "LaunchedEffect(activeSection)" in seller_center
     and "shopSectionsScrollState.animateScrollToItem(selectedIndex)" in seller_center
     and "LazyRow(" in seller_center
     and "itemsIndexed(shopSections, key = { _, section -> section })" in seller_center),
    ("My Shop overview avoids showing zero inventory/earnings while requests are still loading or have failed",
     'val listingSummary = when {' in seller_center
     and 'loading -> "Loading listings…"' in seller_center
     and 'error != null -> "Listing count unavailable until refresh succeeds"' in seller_center
     and 'moneyLoading -> "Checking recent earnings…"' in seller_center
     and 'moneyError != null -> "Recent earnings unavailable until refresh succeeds"' in seller_center
     and 'Text("$listingSummary • $settlementSummary"' in seller_center),
])

checks.extend([
    ("multi-product checkout quote failures sanitize raw transport responses",
     'result.onSuccess{quote=it}.onFailure{message=marketplaceSafeError(it, "Checkout quote could not be prepared.")}' in marketplace
     and 'message=it.message?:"Checkout quote could not be prepared."' not in marketplace),
    ("seller order settlement JSON parsing is contained and rejects an incomplete accounting payload",
     'FynxBackendClient.get(context, "/api/marketplace/settlement/order/${order.id}")\n                .mapCatching { raw ->' in seller_orders
     and 'response.optJSONObject("accounting") == null' in seller_orders
     and '.onSuccess { response -> settlement = response }' in seller_orders),
    ("seller order delivery evidence parsing is exception-safe and never fabricates zero financial detail",
     'FynxBackendClient.get(context, "/api/marketplace/orders/${order.id}/delivery-details")\n                .mapCatching { raw ->' in seller_orders
     and "internal fun marketplaceMoneyOrUnavailable(value: Any?, currency: String): String" in seller_orders
     and 'marketplaceMoneyOrUnavailable(accounting.opt("buyerTotal")' in seller_orders
     and 'accounting.optDouble("buyerTotal", 0.0)' not in seller_orders
     and 'escrow.optDouble("amount", 0.0)' not in seller_orders
     and 'op.optDouble("amount", 0.0)' not in seller_orders
     and 'entry.optDouble("amount", 0.0)' not in seller_orders),
    ("buyer order lifecycle delivery JSON parsing is inside a caught result boundary",
     'FynxBackendClient.get(context, "/api/marketplace/orders/${order.id}/delivery-details")\n                .mapCatching { raw ->' in order_lifecycle
     and '.onSuccess { details ->' in order_lifecycle
     and 'deliveryDetails = details.first' in order_lifecycle),
    ("public seller shop profile JSON failures are retryable without hiding product listings",
     'var shopProfileError by remember(username)' in storefront
     and 'FynxBackendClient.get(context, "/api/marketplace/shops/" + username.removePrefix("@").trim())\n            .mapCatching { raw ->' in storefront
     and 'shopProfileError?.let { message ->' in storefront
     and 'TextButton(onClick = { storefrontReloadKey += 1 }, enabled = !profileLoading) { Text("Retry shop details") }' in storefront),
])
checks.extend([
    ("Marketplace product details receives the Status share callback", "onShareToStatus: (String, String) -> Unit" in marketplace and "onShareToStatus = onShareListingToStatus" in marketplace and "onShareToStatus = onShareToStatus" in marketplace[marketplace.index("private fun MarketplaceDetails("):]),
    ("missing listing availability never defaults to active except for verified active-only public endpoints",
     "marketplaceRequiredListingActive(o.opt(\"active\"))" in marketplace_client
     and "internal fun marketplaceRequiredListingActive(value: Any?)" in support
     and "activeOmittedMeansActive = true" in marketplace_client
     and "activeOmittedMeansActive = true" in remote_client
     and "active = o.optBoolean(\"active\", true)" not in marketplace_client
     and "active = o.optBoolean(\"active\", true)" not in remote_client),
    ("buyer-facing listing parsers never substitute total stock when available stock is missing",
     "if (preferAvailableStock)" in marketplace_client
     and "else null" in marketplace_client[marketplace_client.index("val stockValue = if (preferAvailableStock)"):marketplace_client.index("return Listing(")]
     and "val stockValue = if (o.has(\"available_quantity\") && !o.isNull(\"available_quantity\"))" in remote_client
     and "marketplaceRequiredInventoryQuantity(stockValue)" in marketplace_client
     and "marketplaceRequiredInventoryQuantity(stockValue)" in remote_client),
    ("Marketplace listing values fail closed instead of defaulting missing price/currency/quantity",
     "marketplaceRequiredListingPrice(o.opt(\"price\"))" in marketplace_client
     and "marketplaceRequiredMarketplaceCurrency(o.opt(\"currency\"))" in marketplace_client
     and "marketplaceRequiredInventoryQuantity(stockValue)" in marketplace_client
     and "o.optDouble(\"price\", 0.0)" not in marketplace_client
     and "o.optString(\"currency\", \"NGN\")" not in marketplace_client),
    ("discovery uses the canonical strict Marketplace listing parser",
     "FynxMarketplaceClient.parseListings(raw, preferAvailableStock = true)" in discovery_client
     and "private fun parseListings(raw: String): List<FynxMarketplaceClient.Listing>" not in discovery_client),
    ("active Marketplace list/storefront parser rejects malformed listing prices and missing currencies",
     "private fun parseListings(raw: String, activeOmittedMeansActive: Boolean = false): List<MarketplaceListing>" in remote_client
     and "marketplaceRequiredListingPrice(o.opt(\"price\"))" in remote_client[remote_client.index("private fun parseListings(raw: String, activeOmittedMeansActive: Boolean = false): List<MarketplaceListing>"):]
     and "marketplaceRequiredMarketplaceCurrency(o.opt(\"currency\"))" in remote_client[remote_client.index("private fun parseListings(raw: String, activeOmittedMeansActive: Boolean = false): List<MarketplaceListing>"):]
     and "marketplaceRequiredInventoryQuantity(stockValue)" in remote_client[remote_client.index("private fun parseListings(raw: String, activeOmittedMeansActive: Boolean = false): List<MarketplaceListing>"):]
     and "o.optDouble(\"price\")" not in remote_client[remote_client.index("private fun parseListings(raw: String, activeOmittedMeansActive: Boolean = false): List<MarketplaceListing>"):]
     and "o.optString(\"currency\", \"NGN\")" not in remote_client[remote_client.index("private fun parseListings(raw: String, activeOmittedMeansActive: Boolean = false): List<MarketplaceListing>"):]),
    ("verified order receipts validate identity, signature, item money and reconciled totals before UI exposure",
     "validateMarketplaceOrderReceipt(receipt)" in receipt
     and "marketplaceReceiptAmountsMatch(lineSubtotal, expectedSubtotal)" in receipt
     and "marketplaceReceiptAmountsMatch(marketplaceFee, buyerFee + sellerFee)" in receipt
     and "marketplaceReceiptAmountsMatch(buyerTotal, subtotal + delivery + buyerFee - discount)" in receipt
     and "marketplaceRequiredReceiptAmount(receipt.opt(\"buyerTotal\"))" in receipt
     and "signatureAlgorithm" in receipt and "?signature=$signature" in receipt),
    ("combined checkout receipts validate every item and reconcile all aggregate totals before UI exposure",
     "validateMarketplaceCheckoutReceipt(receipt)" in receipt
     and "items.length() !in 1..20" in receipt
     and "marketplaceReceiptAmountsMatch(subtotal, sumSubtotal)" in receipt
     and "marketplaceReceiptAmountsMatch(delivery, sumDelivery)" in receipt
     and "marketplaceReceiptAmountsMatch(marketplaceFee, sumMarketplaceFee)" in receipt
     and "marketplaceReceiptAmountsMatch(buyerTotal, sumBuyerTotal)" in receipt),

    ("Great Deals carousel selects only valid, active, in-stock unique listings and keeps one deterministic currency",
     "selectMarketplaceAdvertisingCandidates" in ad_support
     and "it.active" in ad_support
     and "it.quantity > 0" in ad_support
     and "it.price.isFinite()" in ad_support
     and ".distinctBy { it.id.trim() }" in ad_support
     and 'groupingBy { it.currency.trim().uppercase(Locale.US) }' in ad_support),
    ("Great Deals carousel uses complete real successful-sales evidence with a safe price-order fallback",
     "successfulSalesBySeller" in ad_support
     and "reputation.successfulSales" in marketplace
     and ("thenBy { it.price }" in ad_support or "compareBy<MarketplaceAdvertisingCandidate> { it.price }" in ad_support)
     and "hasCompleteSalesEvidence" in ad_support
     and "sameCurrency.sortedWith(stableTieBreakers)" in ad_support),
    ("Great Deals advert slides show real listing media, seller identity, price and exact-listing navigation",
     "RemoteMarketMedia(" in hero
     and '"Sold by $sellerName"' in hero
     and 'String.format(Locale.US, "%,.2f", listing.price)' in hero
     and "onClick = { onOpenListing(listing) }" in hero
     and "sellerReputations = sellerReputations" in marketplace),
    ("Great Deals advert rotation supports small lists, loops through a duplicate-first slide and runs in a cancellable composition effect",
     "MARKETPLACE_AD_ROTATION_INTERVAL_MS" in hero
     and "nextMarketplaceAdvertisingDisplayIndex" in hero
     and "if (deals.size > 1) deals + deals.first() else deals" in hero
     and "rowState.animateScrollToItem(target)" in hero),
    ("Great Deals advert has accessible manual next navigation and a truthful empty state",
     'contentDescription = "Next advertised product"' in hero
     and '"Products will appear here when live seller listings are available."' in hero
     and 'contentDescription = "Refresh advertised products"' in hero),

])
failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)

if failed:
    raise SystemExit("Marketplace seller/product-flow verification failed: " + ", ".join(failed))

print(f"Marketplace seller/product-flow verification GREEN ({len(checks)} checks)")
