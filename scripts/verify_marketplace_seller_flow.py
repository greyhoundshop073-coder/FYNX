from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

support = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerFlowSupport.kt").read_text()
marketplace = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text()

checks = [
    ("seller support keeps a bounded real media limit", "const val MAX_PRODUCT_MEDIA = 12" in support),
    ("seller support accepts image and video media", 'mime.startsWith("image/") || mime.startsWith("video/")' in support),
    ("seller support removes duplicate media", ".distinct()" in support),
    ("seller support appends media through the shared guardrail", "fun addMedia(context: Context, existing: List<Uri>, uri: Uri)" in support and "normalizedMedia(context, existing + uri)" in support),
    ("seller support validates positive price and quantity", "price > 0.0" in support and "quantity > 0" in support),
    ("active seller flow uses multi-media picker", "PickMultipleVisualMedia" in marketplace and "MAX_PRODUCT_MEDIA" in marketplace),
    ("active seller flow offers camera capture", "FynxCameraCapturePanel" in marketplace and "showCamera" in marketplace),
    ("active seller flow previews selected media", "ImageView" in marketplace and "contextIsVideo" in marketplace),
    ("active seller flow supports media removal", 'media = media.filterNot { it == uri }' in marketplace),
    ("active seller flow publishes the selected media list", "createMarketplaceListing" in marketplace and ", media)" in marketplace),
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
