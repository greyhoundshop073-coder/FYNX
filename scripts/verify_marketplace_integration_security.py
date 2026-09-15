from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

panel = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text()
remote = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text()
deep_link = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxDeepLink.kt").read_text()
discovery = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxDiscoveryClient.kt").read_text()
seller_center = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerCenterPanel.kt").read_text()
privacy = (ROOT / "backend/mediaPrivacy.js").read_text()
marketplace_privacy = (ROOT / "backend/marketplaceMediaPrivacy.js").read_text()
discovery_routes = (ROOT / "backend/discoveryRoutes.js").read_text()

checks = [
    ("Marketplace uses backend-driven listings", "FynxRemoteSocialClient.listings" in panel),
    ("Marketplace keeps stable listing IDs in UI keys", "items(listings, key = { it.id })" in panel),
    ("Marketplace seller profile opens through the existing profile callback", "onProfile = { onOpenProfile(listing.sellerUsername) }" in panel),
    ("Marketplace contact seller uses the existing chat deep link", "FynxDeepLinkParser.chatAppLink" in panel and "Intent.ACTION_VIEW" in panel),
    ("Marketplace discovery has a dedicated backend route", "/api/marketplace/discovery" in discovery),
    ("Marketplace deep links have a canonical marketplace destination", "FynxDeepLinkDestination.Marketplace" in deep_link and "MARKETPLACE_PATH = \"/marketplace\"" in deep_link),
    ("Marketplace ad creation preserves the canonical listing ID", "createMarketplaceAd" in remote and "Listing ID: $listingId" in remote),
    ("Marketplace ad creation uses the real social-post backend", '"/api/social/posts"' in remote),
    ("Marketplace seller center remains a real listing/inventory surface", "FynxMarketplaceSellerCenterPanel" in seller_center and "FynxMarketplaceClient.Listing" in seller_center),
    ("Marketplace media privacy filters marketplace listing responses", "/api/marketplace/listings" in privacy),
    ("Marketplace media access has a dedicated privacy guard", "marketplace_listings" in marketplace_privacy and "mediaId" in marketplace_privacy),
    ("Marketplace listing detail route is authenticated", 'app.get("/api/marketplace/listing/:id", auth' in discovery_routes),
    ("Marketplace discovery route is authenticated", "app.get(\"/api/marketplace/discovery" in discovery_routes and "auth" in discovery_routes),
    ("Marketplace checkout remains protected", "FynxMarketplaceCheckoutDialog" in panel and "MarketplaceProtectedOrderDialog" in panel),
    ("Marketplace payment verification remains connected", "verifyMarketplacePayment" in panel),
    ("Marketplace order lifecycle remains connected", "FynxMarketplaceOrderLifecycle" in panel),
    ("Marketplace cart prevents duplicate listing IDs", "cart.none { it.id == listing.id }" in panel),
    ("Marketplace product media remains bounded to 12", "mediaIds.take(12)" in panel),
    ("Marketplace has no hardcoded payment secret in the active panel", "sk_live_" not in panel and "PAYSTACK_SECRET" not in panel and "STRIPE_SECRET" not in panel),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)

if failed:
    raise SystemExit("Marketplace integration/security audit failed: " + ", ".join(failed))

print(f"Marketplace integration/security audit GREEN ({len(checks)} checks)")
