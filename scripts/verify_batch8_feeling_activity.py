from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
composer = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8")
library = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxFeelingActivityLibrary.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text(encoding="utf-8")
models = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text(encoding="utf-8")
feed = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text(encoding="utf-8")
routes = (ROOT / "backend/socialRoutes.js").read_text(encoding="utf-8")
bootstrap = (ROOT / "backend/socialMultiMediaBootstrap.js").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")

checks = [
    ("Feeling/Activity chip is enabled", 'ComposerQuickChip("Feeling/Activity", Icons.Default.SentimentSatisfied, enabled = !posting && postingAllowed)' in composer),
    ("Real Feeling/Activity picker exists", "showFeelingActivityPicker" in composer and 'AlertDialog(' in composer and 'FynxFeelingActivityLibrary.options' in composer),
    ("Picker supports search", "feelingActivitySearch" in composer and 'label = { Text("Search") }' in composer),
    ("Selection can be removed before publishing", 'selectedFeelingActivity = null' in composer and '"Remove feeling or activity"' in composer),
    ("Real library contains feelings and activities", 'FynxFeelingActivityOption("FEELING"' in library and 'FynxFeelingActivityOption("ACTIVITY"' in library),
    ("Post client accepts Feeling/Activity", "feelingActivity: FynxFeelingActivityOption? = null" in client),
    ("Post client sends Feeling/Activity metadata", '"feelingActivityType"' in client and '"feelingActivity"' in client),
    ("Backend schema persists Feeling/Activity", "feeling_activity_type TEXT" in routes and "feeling_activity TEXT" in routes),
    ("Backend validates Feeling/Activity type", "allowedFeelingActivityTypes" in routes and "invalid feeling or activity type" in routes),
    ("Single-post backend stores Feeling/Activity", "feeling_activity_type,feeling_activity,text_background" in routes),
    ("Feed exposes Feeling/Activity", "feelingActivityType:x.feeling_activity_type" in routes and "feelingActivity:x.feeling_activity" in routes),
    ("Android feed model parses Feeling/Activity", "feelingActivityType: String?" in models and 'o.optString("feelingActivity")' in models),
    ("Home feed renders Feeling/Activity", "post.feelingActivity" in feed and "SentimentSatisfied" in feed),
    ("Multi-media bootstrap carries Feeling/Activity", "feelingActivityType" in bootstrap and "feeling_activity_type,feeling_activity" in bootstrap),
    ("CI has Batch 8 verification gate", "scripts/verify_batch8_feeling_activity.py" in workflow),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)

if failed:
    raise SystemExit("Batch 8 Feeling/Activity verification failed: " + ", ".join(failed))

print(f"Batch 8 Feeling/Activity verification GREEN ({len(checks)} checks)")
