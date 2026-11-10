from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
def read(path):
    return (ROOT / path).read_text(encoding="utf-8")
social = read("backend/socialRoutes.js")
transactions = read("backend/marketplaceTransactions.js")
client = read("app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt")
storefront = read("app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerStoreDialog.kt")
product_cards = read("app/src/main/java/com/fynx/app/ui/FynxMarketplaceReferenceProductComponents.kt")
marketplace_panel = read("app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt")
purchase_start = transactions.find("my-purchased-listings")
purchase_end = transactions.find("app.get('/api/marketplace/orders',", purchase_start)
purchase_route = transactions[purchase_start:purchase_end] if purchase_start >= 0 and purchase_end > purchase_start else ""
checks = [
    ("listing likes have account/product uniqueness", "CREATE TABLE IF NOT EXISTS marketplace_listing_likes" in social and "PRIMARY KEY (user_id, listing_id)" in social),
    ("seller engagement is authenticated and viewer-specific", 'app.get("/api/marketplace/shops/:username/listing-engagement", auth' in social and "COUNT(DISTINCT likes.user_id)" in social and "likedByCurrentUser" in social),
    ("product likes are idempotent and reject self-likes", 'app.post("/api/marketplace/listings/:id/like", auth' in social and "ON CONFLICT (user_id, listing_id) DO NOTHING" in social and "you cannot like your own product" in social),
    ("purchase history is private to the signed-in buyer", "app.get('/api/marketplace/my-purchased-listings', auth" in transactions and "WHERE buyer_id = $1" in purchase_route),
    ("only confirmed order states count as previous purchases", "status IN ('PAID', 'SHIPPED', 'DELIVERED', 'INSPECTION', 'COMPLETED', 'DISPUTED')" in purchase_route and all(x not in purchase_route for x in ("PAYMENT_PENDING", "CANCELLED", "REFUNDED"))),
    ("Android client reads seller likes and buyer purchase history", "marketplaceSellerListingEngagement" in client and "/listing-engagement" in client and "setMarketplaceListingLike" in client and "myPurchasedMarketplaceListingIds" in client and "/api/marketplace/my-purchased-listings" in client),
    ("public shop uses a three-column photo-backed grid", "sellerListings.chunked(3)" in storefront and "MarketplaceSellerProductGridCard" in storefront and "aspectRatio(1f)" in storefront and "FynxRemoteMedia(" in storefront),
    ("product cards show private bought-before, live likes, price and stock", "Bought before" in storefront and "previouslyPurchasedListingIds" in storefront and "likeCount" in storefront and '"Available: " + listing.quantity' in storefront),
    ("seller medals map to existing reputation tiers", '"ELITE SELLER" -> "GOLD SELLER"' in storefront and '"TOP SELLER" -> "SILVER SELLER"' in storefront and '"TRUSTED SELLER" -> "BRONZE SELLER"' in storefront and '"RISING SELLER" -> "RISING SELLER"' in storefront and '"NEW SELLER" -> "NEW SELLER"' in storefront),
    ("seller ranking and weekly awards use existing reputation records", "reputation?.let { MarketplaceSellerTierBadge(it.tier) }" in storefront and "FynxMarketplaceWeeklySellerClient.history" in storefront),
    ("verified seller medals appear on public shop, product cards, seller recommendations and product details", "reputation?.let { MarketplaceSellerTierBadge(it.tier) }" in storefront and "sellerRating?.let { MarketplaceSellerTierBadge(it.tier) }" in product_cards and marketplace_panel.count("MarketplaceSellerTierBadge(it.tier)") >= 2),
    ("failed likes and purchase-history reads provide explicit retry controls", 'Text("Retry likes")' in storefront and 'Text("Retry history")' in storefront and "purchaseHistoryError" in storefront),
    ("public seller profile keeps product cards close to the first viewport with a compact identity header", "Modifier.size(64.dp).clip(CircleShape)" in storefront and "Modifier.fillMaxWidth().padding(14.dp)" in storefront and 'Text("Message seller"' in storefront and "Arrangement.spacedBy(8.dp)" in storefront and "sellerListings.chunked(3)" in storefront),
    ("likes, buyer purchase history, public shop identity and buyer reviews load independently inside the profile's structured parallel scope", storefront.count("LaunchedEffect(username, viewerUsername, storefrontReloadKey)") == 1 and "coroutineScope {" in storefront and storefront.index("FynxRemoteSocialClient.marketplaceSellerListingEngagement") < storefront.index("LaunchedEffect(username) {") and storefront.index("FynxRemoteSocialClient.myPurchasedMarketplaceListingIds") < storefront.index("LaunchedEffect(username) {") and storefront.index("/reviews?limit=10&offset=0") < storefront.index("LaunchedEffect(username) {")),
    ("public shop description is separate from personal seller biography and branding uses independent shop media", "shopDescription = shop.optString(\"description\")" in storefront and "authoritativeShopBio" not in storefront and "About this shop" in storefront and "Seller profile" in storefront and "shopLogoMediaId" in storefront and "shopCoverMediaId" in storefront),
    ("public seller catalogue supports offset pagination and automatically requests the next page near the bottom", "marketplaceSellerListingsPage" in client and "storefrontHasMore" in storefront and "snapshotFlow" in storefront and "loadMoreStorefront()" in storefront),
    ("public shop allows a buyer to contact a seller when no active products are available", "onContactShop: () -> Unit" in storefront and "?: onContactShop()" in storefront and "onContactShop = { contactSeller(username, \"\") }" in marketplace_panel),
]
failed = [name for name, passed in checks if not passed]
for name, passed in checks:
    print(f"{'PASS' if passed else 'FAIL'}: {name}")
if failed:
    raise SystemExit(f"{len(failed)} public Marketplace storefront contract checks failed.")
print(f"PASS: all {len(checks)} public Marketplace storefront contract checks")
