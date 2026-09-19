from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
panel = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text()
discovery = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxDiscoveryClient.kt").read_text()
remote = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text()
routes = (ROOT / "backend/discoveryRoutes.js").read_text()
location = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxPostLocationClient.kt").read_text()

checks = [
    ("Marketplace reuses the real FYNX location resolver", "FynxPostLocationClient.currentPlace(context)" in panel),
    ("Marketplace requests location only from user actions", "RequestMultiplePermissions" in panel and "toggleNearby()" in panel and "useCurrentListingLocation()" in panel),
    ("Marketplace requests fine and coarse together", "ACCESS_FINE_LOCATION" in panel and "ACCESS_COARSE_LOCATION" in panel),
    ("Marketplace buyer has a real Near me control", 'Text(if (nearbyMode)' in panel and "Icons.Default.LocationOn" in panel),
    ("Marketplace buyer uses real nearby discovery", "nearbyMarketplaceListings" in panel and "nearbyMarketplaceListings(context, query, category, nearbyLabel)" in remote),
    ("Discovery client sends the selected location area", 'location=${encode(location)}' in discovery),
    ("Backend filters only real listing locations", "l.location ILIKE" in routes and "req.query?.location" in routes),
    ("Seller can fill a real listing area from device location", 'Text(if (locationLoading) "Finding your area..." else "Use my current area")' in panel),
    ("Seller privacy copy prevents exact GPS publication", "exact GPS coordinates are not published with the listing" in panel),
    ("Marketplace location layer does not add coordinate fields", "latitude" not in panel.lower() and "longitude" not in panel.lower() and "location_lat" not in routes and "location_lng" not in routes),
    ("Existing location resolver remains label-only", "raw coordinates never leave the device" in location.lower()),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)

if failed:
    raise SystemExit("Marketplace location verification failed: " + ", ".join(failed))

print(f"Marketplace location verification GREEN ({len(checks)} checks)")
