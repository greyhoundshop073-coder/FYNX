from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PANEL = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text(encoding="utf-8")
BANNER = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePromotionalBanner.kt").read_text(encoding="utf-8")
WEEKLY_CLIENT = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceWeeklySellerClient.kt").read_text(encoding="utf-8")
REPUTATION = (ROOT / "backend/marketplaceReputation.js").read_text(encoding="utf-8")

checks = [
    ("Marketplace home uses the new full-width promotional banner", "MarketplacePromotionalBanner(" in PANEL and "weeklySeller = weeklySeller" in PANEL),
    ("weekly seller ranking is loaded through the authenticated Marketplace client", "FynxMarketplaceWeeklySellerClient.load(context)" in PANEL and "/api/marketplace/sellers/weekly" in WEEKLY_CLIENT),
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
]

failed = []
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
    if not ok:
        failed.append(name)

if failed:
    raise SystemExit("Marketplace promotional banner verification failed: " + ", ".join(failed))
print(f"Marketplace promotional banner verification GREEN ({len(checks)} checks)")
