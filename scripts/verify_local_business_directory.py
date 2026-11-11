from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
app = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text()
marketplace = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text()
directory = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxLocalBusinessDirectory.kt").read_text()
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxBusinessClient.kt").read_text()

checks = [
    ("Marketplace exposes the Local Businesses destination", "onOpenBusinesses" in marketplace and 'Text("Local businesses",' in marketplace),
    ("Marketplace route opens the dedicated directory panel", '"Local Businesses" -> FynxLocalBusinessesPanel' in app),
    ("directory is reachable from the Marketplace screen", 'onOpenBusinesses = { selected = "Local Businesses" }' in app),
    ("directory uses the authenticated FYNX backend base client without adding credentials", 'FynxBackendClient.get(context, url)' in directory),
    ("directory supports search, category and area filters", 'query=' in directory and 'category=' in directory and 'location=' in directory),
    ("directory supports pagination and stable business IDs", 'hasMore' in directory and 'items(businesses, key = { it.id })' in directory),
    ("directory only displays verified status supplied by backend", 'item.optBoolean("verified")' in directory),
    ("Business Account remains the source for saved business identity", 'FynxBusinessClient.load(context)' in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxBusinessAccount.kt").read_text()),
]
failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("Local business directory UI contract failed: " + "; ".join(failed))
print(f"LOCAL BUSINESS DIRECTORY UI CONTRACT GREEN ({len(checks)} checks)")
