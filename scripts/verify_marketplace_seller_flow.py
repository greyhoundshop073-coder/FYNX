from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

support = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerFlowSupport.kt").read_text()
marketplace = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text()

checks = [
    ("seller support keeps a bounded real media limit", "const val MAX_PRODUCT_MEDIA = 12" in support),
    ("seller support accepts image and video media", 'mime.startsWith("image/") || mime.startsWith("video/")' in support),
    ("seller support removes duplicate media", ".distinct()" in support),
    ("seller support validates positive price and quantity", "price > 0.0" in support and "quantity > 0" in support),
    ("active marketplace remains backend listing driven", "FynxRemoteSocialClient.listings" in marketplace),
    ("active marketplace retains protected checkout", "FynxMarketplaceCheckoutDialog" in marketplace),
    ("active marketplace retains payment verification", "verifyMarketplacePayment" in marketplace),
    ("active marketplace retains order protection", "MarketplaceProtectedOrderDialog" in marketplace),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)

if failed:
    raise SystemExit("Marketplace seller-flow verification failed: " + ", ".join(failed))

print(f"Marketplace seller-flow verification GREEN ({len(checks)} checks)")
