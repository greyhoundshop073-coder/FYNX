from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
panel = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAdvertisingCampaignPanel.kt").read_text()
marketplace = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceClient.kt").read_text()
advertising = (ROOT / "backend/marketplaceAdvertising.js").read_text()
sponsored = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSponsoredPromotionClient.kt").read_text()

checks = [
    ("campaign composer loads the advertiser's own Marketplace listings", "FynxMarketplaceClient.myListings(context)" in panel and '"/api/marketplace/my-listings"' in marketplace),
    ("campaign selection excludes inactive and out-of-stock listings", "listingOptions.filter { it.active && it.quantity > 0 }" in panel),
    ("campaign creation requires an explicitly selected product", "selectedListing != null" in panel and "selectedListing ?: return@Button" in panel),
    ("campaign payload carries the canonical selected listing ID", 'put("creativeType", "product")' in panel and 'put("creativeRef", creative.id)' in panel),
    ("campaign creation uses a unique idempotency key", 'put("idempotencyKey", "android-" + java.util.UUID.randomUUID().toString())' in panel),
    ("backend validates that the product is active and owned by the advertiser", 'FROM marketplace_listings WHERE id=$1 AND seller_id=$2 AND COALESCE(active,TRUE)=TRUE FOR SHARE' in advertising),
    ("backend rejects invalid or unowned creative references before insert", "validateCreativeReference(client, auth.userId, creativeType, creativeRef)" in advertising and "creativeReferenceError(creativeType)" in advertising),
    ("Marketplace sponsored feed resolves campaign creative_ref to its exact listing", 'ON l.id::text = c.creative_ref AND l.seller_id = c.owner_id' in advertising and '"/api/advertising/marketplace-feed"' in sponsored),
    ("payment authorization is restricted to HTTPS", 'url.startsWith("https://", ignoreCase = true)' in panel),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("Marketplace ad-to-product integration failed: " + "; ".join(failed))
print(f"Marketplace ad-to-product integration GREEN ({len(checks)} checks)")
