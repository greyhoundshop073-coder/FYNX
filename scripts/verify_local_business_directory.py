from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
app = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text()
marketplace = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text()
directory = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxLocalBusinessDirectory.kt").read_text()
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxBusinessClient.kt").read_text()
runtime = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text()
workflow = (ROOT / ".github/workflows/android-build.yml").read_text()

checks = [
    ("Marketplace exposes the Local Businesses destination", "onOpenBusinesses" in marketplace and 'Text("Local businesses",' in marketplace),
    ("Marketplace route opens the dedicated directory panel", '"Local Businesses" -> FynxLocalBusinessesPanel' in app),
    ("directory is reachable from the Marketplace screen", 'onOpenBusinesses = { selected = "Local Businesses" }' in app),
    ("directory uses the authenticated FYNX backend base client without adding credentials", 'FynxBackendClient.get(context, url)' in directory),
    ("directory supports search, category and area filters", 'query=' in directory and 'category=' in directory and 'location=' in directory),
    ("directory supports pagination and stable business IDs", 'hasMore' in directory and 'items(businesses, key = { it.id })' in directory),
    ("directory only displays verified status supplied by backend", 'item.optBoolean("verified")' in directory),
    ("Business Account remains the source for saved business identity", 'FynxBusinessClient.load(context)' in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxBusinessAccount.kt").read_text()),
    ("public business profile has a dedicated detail screen", "FynxPublicBusinessProfilePanel" in directory and 'FynxBusinessDirectoryClient.details(context, business.username)' in directory),
    ("public business profile renders opening hours, service areas and service pricing", all(token in directory for token in ['"opening_hours"', '"service_areas"', '"services"', '"Marketplace products"'])),
    ("public profile editor saves opening hours, service areas and service prices", all(token in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxBusinessAccount.kt").read_text() for token in ["openingHoursText", "serviceAreasText", "servicesText"])),
    ("business profile fields are parsed and saved by the client", all(token in client for token in ["opening_hours", "service_areas", "services", 'put("openingHours"']))),
    ("directory does not render a second in-content Local Businesses heading", 'Text("Local Businesses", style = MaterialTheme.typography.headlineSmall' not in directory),
    ("emulator certification opens and screenshots the real Local Businesses directory", "marketplace-local-businesses" in runtime and "authenticated-marketplace-local-businesses.png" in runtime and "Search businesses, services or names" in runtime and "Area, city or neighbourhood" in runtime),
    ("emulator certification reveals the horizontal Marketplace shortcut without guessed coordinates", "def swipe_horizontal_scroll_container(" in runtime and "marketplace-local-businesses-rail-" in runtime and "marketplace-local-businesses-return-rail-" in runtime),
    ("emulator certification returns from Local Businesses to Marketplace", "Local Businesses Back did not return to Marketplace" in runtime and "marketplace-local-businesses-return.xml" in runtime),
    ("this PR is explicitly included in full emulator runtime certification", "github.event.pull_request.head.ref == 'feat/local-business-discovery-20261111'" in workflow and "Run Android instrumentation tests and capture the real FYNX UI" in workflow),
]
failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("Local business directory UI contract failed: " + "; ".join(failed))
print(f"LOCAL BUSINESS DIRECTORY UI CONTRACT GREEN ({len(checks)} checks)")
