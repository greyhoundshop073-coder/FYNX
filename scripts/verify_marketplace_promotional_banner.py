from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PANEL = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text(encoding="utf-8")
BANNER = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePromotionalBanner.kt").read_text(encoding="utf-8")
WEEKLY_CLIENT = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceWeeklySellerClient.kt").read_text(encoding="utf-8")
SELLER_STORE = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerStoreDialog.kt").read_text(encoding="utf-8")
SELLER_CENTER = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerCenterPanel.kt").read_text(encoding="utf-8")
SPONSORED_CLIENT = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSponsoredPromotionClient.kt").read_text(encoding="utf-8")
SEASONAL_CLIENT = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSeasonalPromotionClient.kt").read_text(encoding="utf-8")
SEASONAL_ADMIN = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSeasonalPromotionAdminPanel.kt").read_text(encoding="utf-8")
ANNOUNCEMENTS = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt").read_text(encoding="utf-8")
MEDIA_PRIVACY = (ROOT / "backend/mediaPrivacy.js").read_text(encoding="utf-8")
REPUTATION = (ROOT / "backend/marketplaceReputation.js").read_text(encoding="utf-8")
FEED_CACHE = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceFeedCache.kt").read_text(encoding="utf-8")
MARKETPLACE_RELOAD = PANEL[PANEL.index("    fun reload()"):PANEL.index("    fun loadMoreListings()")]
PROMOTION_MODELS = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePromotionModels.kt").read_text(encoding="utf-8")

checks = [
    ("Marketplace home uses the new full-width promotional banner", "MarketplacePromotionalBanner(" in PANEL and "weeklySeller = weeklySeller" in PANEL),
    ("normal live Marketplace feed uses the new banner instead of the legacy compact tile", 'item(key = "marketplace-hero") {\n                        MarketplacePromotionalBanner(' in PANEL and PANEL.count("MarketplacePromotionalBanner(") >= 2 and PANEL.count("MarketplaceSmartDealsHero(") == 1),
    ("weekly seller ranking is loaded through the authenticated Marketplace client", "FynxMarketplaceWeeklySellerClient.load(context)" in PANEL and "/api/marketplace/sellers/weekly" in WEEKLY_CLIENT),
    ("approved paid product campaigns are loaded into Marketplace", "FynxMarketplaceSponsoredPromotionClient.load(context)" in PANEL and "/api/advertising/marketplace-feed" in SPONSORED_CLIENT),
    ("paid advertisements are visibly marked sponsored", "sponsoredPromotions.take(3)" in BANNER and 'eyebrow = "SPONSORED"' in BANNER),
    ("real product and campaign slides are prioritized before the generic welcome fallback", BANNER.index("featuredListings.forEachIndexed") < BANNER.index("key = \"fynx-welcome\"") and "WELCOME TO FYNX MARKETPLACE" in BANNER and "Discover more. Shop with confidence." in BANNER),
    ("sponsored impressions and clicks feed campaign metrics", 'recordMetric(context, campaignId, "impression")' in BANNER and 'recordMetric(context, campaignId, "click")' in BANNER and "impression" in SPONSORED_CLIENT and "click" in SPONSORED_CLIENT),
    ("weekly award remains separate from paid campaign ranking", "marketplace_ad_campaigns" not in REPUTATION[REPUTATION.find("app.get('/api/marketplace/sellers/weekly'"):REPUTATION.find("app.get('/api/marketplace/sellers/:username/reputation'")]),

    ("weekly winner product is fetched from the exact canonical listing before display", "loadExactMarketplaceListing(context, winner.featuredListingId)" in PANEL and "weeklySellerListing = exact" in PANEL),
    ("banner uses a full-width fixed-height photographic slide", "Modifier.width(width).height(MARKETPLACE_PROMO_BANNER_HEIGHT_DP.dp)" in BANNER and "MARKETPLACE_PROMO_BANNER_HEIGHT_DP = 224" in BANNER),
    ("banner uses real FYNX media with a crop fit", "FynxRemoteMedia(" in BANNER and "contentScale = ContentScale.Crop" in BANNER),
    ("banner rotates automatically on a timer and supports manual horizontal swipes", "MARKETPLACE_PROMO_ROTATION_MS = 6_500L" in BANNER and "LaunchedEffect(slides.map { it.key })" in BANNER and "delay(MARKETPLACE_PROMO_ROTATION_MS)" in BANNER and "animateScrollToItem(next)" in BANNER and "userScrollEnabled = true" in BANNER),
    ("banner remains a normal vertical Marketplace feed item while listings load and after loading", 'item(key = "marketplace-loading-hero")' in PANEL and 'item(key = "marketplace-hero")' in PANEL and "loading && listings.isEmpty() -> LazyColumn(" in PANEL and "MarketplacePromotionalBanner(" in PANEL),
    ("empty Marketplace catalogue still displays the promotional banner above its empty state", 'item(key = "marketplace-empty-hero")' in PANEL and 'item(key = "marketplace-empty-state")' in PANEL and 'listings.isEmpty() -> LazyColumn(' in PANEL and "No products yet" in PANEL),
    ("promotional banner does not use sticky or overlay placement over product cards", "stickyHeader" not in PANEL and "absoluteOffset(" not in BANNER and "offset(" not in BANNER and "Column(modifier.fillMaxWidth()" in BANNER),
    ("weekly seller award is labelled clearly and displays completed-order evidence", "BEST SELLER OF THE WEEK" in BANNER and "completedOrders" in BANNER and "uniqueBuyers" in BANNER),
    ("weekly seller award opens the real seller or canonical product", "onOpenListing(slide.listing)" in BANNER and "onOpenSeller(slide.sellerUsername)" in BANNER),
    ("seasonal calendar banners include Christmas, New Year, Easter and other fixed-date events", "Merry Christmas from FYNX!" in BANNER and "Happy New Year!" in BANNER and "easterSunday(year)" in BANNER and "Happy Independence Day, Nigeria!" in BANNER),
    ("featured product banner labels avoid claiming unsupported discounts or paid status", "FEATURED FROM FYNX SELLERS" in BANNER and "special discount" not in BANNER.lower()),
    ("legacy Marketplace product selector remains in place for regression safety", "private fun MarketplaceSmartDealsHero(" in PANEL and "selectMarketplaceAdvertisingCandidates" in PANEL),
    ("weekly rank is sourced from completed order records rather than advertising data", "o.status = 'COMPLETED'" in REPUTATION and "o.completed_at >= w.week_start" in REPUTATION and "marketplace_ad_campaigns" not in REPUTATION[REPUTATION.index("app.get('/api/marketplace/sellers/weekly'"):REPUTATION.index("app.get('/api/marketplace/sellers/:username/reputation'")]),
    ("weekly history is persisted for closed weeks", "marketplace_weekly_seller_awards" in REPUTATION and "ON CONFLICT (week_start, weekly_rank) DO NOTHING" in REPUTATION),
    ("seller storefront shows historical weekly recognition without ad-based scoring", "FynxMarketplaceWeeklySellerClient.history(context, username)" in SELLER_STORE and "FYNX Seller Hall of Fame" in SELLER_STORE and "not advertising spend" in SELLER_STORE),
    ("private Seller Center shows the signed-in seller weekly awards", "FynxMarketplaceWeeklySellerClient.currentSellerHistory(context)" in SELLER_CENTER and "Weekly seller recognition" in SELLER_CENTER and "Best Seller of the Week" in SELLER_CENTER),
    ("seasonal feed client is connected to the authenticated API", "/api/marketplace/seasonal-feed" in SEASONAL_CLIENT and "listForAdmin" in SEASONAL_CLIENT),
    ("Owner/Admin seasonal scheduler is exposed only inside the existing Admin Control Center", "FynxMarketplaceSeasonalPromotionAdminPanel()" in ANNOUNCEMENTS and "d.role == \"OWNER\" || d.role == \"ADMIN\"" in ANNOUNCEMENTS),
    ("Owner/Admin seasonal scheduler uses native Lagos date/time pickers instead of raw ISO text inputs", "DatePickerDialog(" in SEASONAL_ADMIN and "TimePickerDialog(" in SEASONAL_ADMIN and 'TimeZone.getTimeZone("Africa/Lagos")' in SEASONAL_ADMIN and "onSelected" in SEASONAL_ADMIN and "Starts at (ISO 8601)" not in SEASONAL_ADMIN and "Ends at (ISO 8601)" not in SEASONAL_ADMIN),
    ("seasonal scheduler validates Nigeria-time windows and blocks invalid or over-45-day campaigns", "marketplaceSeasonalWindowIsValid(startsAt, endsAt)" in SEASONAL_ADMIN and "duration > 0L" in SEASONAL_ADMIN and "45L * 24L * 60L * 60L * 1000L" in SEASONAL_ADMIN and "End time must be after the start time" in SEASONAL_ADMIN),
    ("Owner/Admin campaign list distinguishes live, scheduled, ended and paused campaigns", '"LIVE"' in SEASONAL_ADMIN and '"SCHEDULED"' in SEASONAL_ADMIN and '"ENDED"' in SEASONAL_ADMIN and '"PAUSED"' in SEASONAL_ADMIN and "formatMarketplaceSeasonalDateTime(campaign.startsAt)" in SEASONAL_ADMIN),
    ("seasonal scheduler still supports secure FYNX artwork upload", "Choose photo" in SEASONAL_ADMIN and "uploadArtwork(context, artworkUri!!)" in SEASONAL_ADMIN),
    ("scheduled variable-date promotions are passed into the real Marketplace banner", "seasonalPromotions = seasonalPromotions" in PANEL and "seasonalPromotions: List<FynxMarketplaceSeasonalPromotion>" in BANNER),
    ("scheduled celebration artwork is displayed through authenticated FYNX media", "FynxRemoteMedia(" in BANNER and "slide.artworkMediaId" in BANNER and 'mediaUrl = "/api/media/${slide.artworkMediaId}"' in BANNER and "contentScale = ContentScale.Crop" in BANNER),
    ("only active scheduled banner artwork is public through media privacy", "marketplace_seasonal_promotions WHERE artwork_media_id=$1" in MEDIA_PRIVACY and "active=TRUE AND starts_at <= NOW() AND ends_at > NOW()" in MEDIA_PRIVACY),


    ("Marketplace first page is cached per account and exact search scope", "FynxMarketplaceFeedCache.read(context, requestQuery, requestCategory, cacheLocation)" in PANEL and "FynxMarketplaceFeedCache.write(" in PANEL and "FynxAuthStore.accountStorageKey(context)" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceFeedCache.kt").read_text(encoding="utf-8")),
    ("cached Marketplace products render while the live request refreshes", "Showing saved products while FYNX checks for updates." in PANEL and "FynxMarketplaceFeedCache.read(context, requestQuery, requestCategory, cacheLocation)" in PANEL),
    ("stale Marketplace cache does not revive listings that are no longer active", 'val active = row.optBoolean("active", true)' in FEED_CACHE and "|| !active) continue" in FEED_CACHE and "active=active" in FEED_CACHE),
    ("stale Marketplace cache never revives listings with zero available stock", "quantity <= 0" in FEED_CACHE and "cachedListingsWithNoAvailableStockAreNotRestored" in (ROOT / "app/src/androidTest/java/com/fynx/app/ui/FynxMarketplaceFeedCacheTest.kt").read_text(encoding="utf-8")),
    ("sponsored promotions retain a bounded last-known feed during temporary outages", '"sponsored_promotions"' in SPONSORED_CLIENT and "readResponse(" in SPONSORED_CLIENT and "writeResponse(" in SPONSORED_CLIENT),
    ("weekly seller awards retain a bounded last-known result during temporary outages", '"weekly_sellers"' in WEEKLY_CLIENT and "readResponse(" in WEEKLY_CLIENT and "writeResponse(" in WEEKLY_CLIENT),
    ("Marketplace browsing does not request order history until Orders is opened", "reloadOrders()" not in MARKETPLACE_RELOAD and "showOrders = true; reloadOrders()" in PANEL),
    ("failed refresh preserves saved products and warns that availability may have changed", "Showing saved products from your last visit." in PANEL and "product availability may have changed" in PANEL),
    ("filter changes do not show another search's products as matching results", "Never present another search/filter" in PANEL and "listings = listOfNotNull(pinned)" in PANEL),
    ("seller card details load in bounded parallel batches", "pending.chunked(4)" in PANEL and "reputationCall = async" in PANEL and "profileCall = async" in PANEL and ".awaitAll()" in PANEL),
    ("temporary seller metadata failures do not erase previously loaded details", "sellerReputations.filterKeys { it in visibleKeys } + reputationUpdates" in PANEL and "sellerPhotoIds.filterKeys { it in visibleKeys } + photoUpdates" in PANEL),
    ("empty Marketplace promo slides show a branded storefront illustration without inventing a product image",
     "val showBrandedFallback = !hasRemoteArtwork && !slide.isWeeklyWinner" in BANNER
     and "Icons.Default.Storefront" in BANNER
     and "contentDescription = \"FYNX Marketplace\"" in BANNER
     and "fillMaxWidth(if (showBrandedFallback) 0.70f else 0.78f)" in BANNER),
    ("seasonal banner artwork receives the same image-readable overlay treatment as real listing media",
     "val hasRemoteArtwork = listing?.mediaIds?.isNotEmpty() == true || !slide.artworkMediaId.isNullOrBlank()" in BANNER
     and "alpha = if (hasRemoteArtwork) 0.18f else 0.58f" in BANNER),
    ("seasonal promotions retain a last-known schedule for offline display", "writeResponse(context" in SEASONAL_CLIENT and "seasonal_promotions" in SEASONAL_CLIENT and "readResponse(" in SEASONAL_CLIENT),
    ("cached seasonal promotions require valid timestamps and an active, in-window campaign", "isMarketplaceSeasonalPromotionEligible(promotion, now)" in SEASONAL_CLIENT and "starts.isBefore(ends) && !now.isBefore(starts) && now.isBefore(ends)" in PROMOTION_MODELS and "if (!promotion.active" in PROMOTION_MODELS),

    ("Marketplace listing cache serialization and parsing run off the UI thread", "withContext(Dispatchers.IO) { FynxMarketplaceFeedCache.read(" in PANEL and "withContext(Dispatchers.IO) {" in PANEL and "FynxMarketplaceFeedCache.write(context" in PANEL),
    ("public seller storefront fetches products, profile and shop identity concurrently", "coroutineScope {" in SELLER_STORE and "FynxProfileRemoteClient.get(context" in SELLER_STORE and "/api/marketplace/shops/" in SELLER_STORE),
    ("public seller storefront preserves products and displays stale-data notice after refresh errors", "Showing previously loaded products." in SELLER_STORE and "storefrontError != null && sellerListings.isEmpty()" in SELLER_STORE and "storefrontLoading && sellerListings.isEmpty()" in SELLER_STORE),
    ("Seller Center inventory state is scoped to the signed-in seller account", "remember(sellerAccountKey) { mutableStateOf(emptyList<FynxMarketplaceClient.Listing>()) }" in SELLER_CENTER and "LaunchedEffect(sellerAccountKey) { refresh() }" in SELLER_CENTER),
    ("Seller Center keeps previously loaded inventory visible during refresh failures", "Previously loaded listings are shown" in SELLER_CENTER and "if (loading && listings.isEmpty() && archivedListings.isEmpty())" in SELLER_CENTER and "Refreshing your inventory…" in SELLER_CENTER),
    ("Seller Center retains same-account shop profile details during transient refresh errors", "Previously saved details are still shown" in SELLER_CENTER and "Keep this account's last confirmed shop details visible" in SELLER_CENTER),
]

failed = []
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
    if not ok:
        failed.append(name)

if failed:
    raise SystemExit("Marketplace promotional banner verification failed: " + ", ".join(failed))
print(f"Marketplace promotional banner verification GREEN ({len(checks)} checks)")