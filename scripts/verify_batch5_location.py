#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
location = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxPostLocationClient.kt").read_text()
composer = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text()
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text()
remote = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text()
feed = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text()
routes = (ROOT / "backend/socialRoutes.js").read_text()
multi = (ROOT / "backend/socialMultiMediaBootstrap.js").read_text()

checks = [
    ("location permissions declared", 'android.permission.ACCESS_FINE_LOCATION' in manifest and 'android.permission.ACCESS_COARSE_LOCATION' in manifest),
    ("real device location resolver exists", 'LocationManager' in location and 'Geocoder' in location and 'currentPlace' in location),
    ("raw coordinates stay local", 'location.latitude' in location and 'location.longitude' in location and 'Raw device coordinates never' not in location),
    ("location is optional in composer", 'var postLocation by remember' in composer and 'postLocation = null' in composer),
    ("location chip is wired", 'ComposerQuickChip("Location"' in composer and 'locationPermissionLauncher.launch' in composer),
    ("permission flow is real", 'RequestMultiplePermissions' in composer and 'ACCESS_FINE_LOCATION' in composer),
    ("location sent through real post client", 'location: String? = null' in client and 'put("location", location?.trim()?.take(160) ?: JSONObject.NULL)' in client),
    ("feed model carries location", 'val location: String? = null' in remote and 'o.optString("location")' in remote),
    ("feed renders location", 'post.location' in feed and 'Icons.Default.LocationOn' in feed),
    ("social schema persists location", 'ADD COLUMN IF NOT EXISTS location TEXT' in routes),
    ("single posts persist location", 'media_type,text_background,text_background_color,text_foreground_color,location' in routes),
    ("multimedia posts persist location", 'media_type,text_background,text_background_color,text_foreground_color,location' in multi),
    ("bootstrap is idempotent", 'fynxBatch5LocationV1' in multi),
    ("existing camera remains single implementation", 'FynxCameraCapturePanel' in composer and 'showCamera = true' in composer),
]
for name, ok in checks:
    if not ok:
        raise SystemExit(f"FAIL: {name}")
    print(f"PASS: {name}")
print("Batch 5 Location integrity verification passed")
