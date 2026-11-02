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

checks = [
    ("Marketplace home uses the new full-width promotional banner", "MarketplacePromotionalBanner(" in PANEL and "weeklySeller = weeklySeller" in PANEL),
    ("normal live Marketplace feed uses the new banner instead of the legacy compact tile", 'item(key = "marketplace-hero") {\n                        MarketplacePromotionalBanner(' in PANEL and PANEL.count("MarketplacePromotionalBanner(") >= 2 and PANEL.count("MarketplaceSmartDealsHero(") == 1),
    ("weekly seller ranking is loaded through the authenticated Marketplace client", "FynxMarketplaceWeeklySellerClient.load(context)" in PANEL and "/api/marketplace/sellers/weekly" in WEEKLY_CLIENT),
    ("approved paid product campaigns are loaded into Marketplace", "FynxMarketplaceSponsoredPromotionClient.load(context)" in PANEL and "/api/advertising/marketplace-feed" in SPONSORED_CLIENT),
    ("paid advertisements are visibly marked sponsored", "sponsoredPromotions.take(3)" in BANNER and 'eyebrow = "SPONSORED"' in BANNER),
    ("FYNX Marketplace brand message is part of the normal rotation", "THE FYNX MARKETPLACE EXPERIENCE" in BANNER and "Discover more with FYNX" in BANNER),
    ("sponsored impressions and clicks feed campaign metrics", 'recordMetric(context, campaignId, "impression")' in BANNER and 'recordMetric(context, campaignId, "click")' in BANNER and "impression" in SPONSORED_CLIENT and "click" in SPONSORED_CLIENT),
    ("weekly award remains separate from paid campaign ranking", "marketplace_ad_campaigns" not in REPUTATION[REPUTATION.find("app.get('/api/marketplace/sellers/weekly'"):REPUTATION.find("app.get('/api/marketplace/sellers/:username/reputation'")]),

    ("weekly winner product is fetched from the exact canonical listing before display", "loadExactMarketplaceListing(context, winner.featuredListingId)" in PANEL and "weeklySellerListing = exact" in PANEL),
    ("banner uses a full-width fixed-height photographic slide", "Modifier.width(width).height(MARKETPLACE_PROMO_BANNER_HEIGHT_DP.dp)" in BANNER and "MARKETPLACE_PROMO_BANNER_HEIGHT_DP = 224" in BANNER),
    ("banner uses real FYNX media with a crop fit", "FynxRemoteMedia(" in BANNER and "contentScale = ContentScale.Crop" in BANNER),
    ("banner supports timed rotation and manual horizontal swiping", "MARKETPLACE_PROMO_ROTATION_MS = 6_500L" in BANNER and "animateScrollToItem(next)" in BANNER and "userScrollEnabled = true" in BANNER),
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
    ("scheduler supports ISO date windows and uploaded FYNX image artwork", "Starts at (ISO 8601)" in SEASONAL_ADMIN and "Ends at (ISO 8601)" in SEASONAL_ADMIN and "Choose photo" in SEASONAL_ADMIN and "uploadArtwork(context, artworkUri!!)" in SEASONAL_ADMIN),
    ("scheduled variable-date promotions are passed into the real Marketplace banner", "seasonalPromotions = seasonalPromotions" in PANEL and "seasonalPromotions: List<FynxMarketplaceSeasonalPromotion>" in BANNER),
    ("scheduled celebration artwork is displayed through authenticated FYNX media", "FynxRemoteMedia(" in BANNER and "slide.artworkMediaId" in BANNER and 'mediaUrl = "/api/media/${slide.artworkMediaId}"' in BANNER and "contentScale = ContentScale.Crop" in BANNER),
    ("only active scheduled banner artwork is public through media privacy", "marketplace_seasonal_promotions WHERE artwork_media_id=$1" in MEDIA_PRIVACY and "active=TRUE AND starts_at <= NOW() AND ends_at > NOW()" in MEDIA_PRIVACY),

]

failed = []
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
    if not ok:
        failed.append(name)

if failed:
    raise SystemExit("Marketplace promotional banner verification failed: " + ", ".join(failed))
print(f"Marketplace promotional banner verification GREEN ({len(checks)} checks)")