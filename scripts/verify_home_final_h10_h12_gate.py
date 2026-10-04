from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
checks = {
    "home_media_frame": ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeMediaFrame.kt",
    "home_media_sizing": ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeMediaSizing.kt",
}
missing = [name for name, path in checks.items() if not path.exists()]
if missing:
    print("FINAL HOME GATE RED: missing required Home files:", ", ".join(missing))
    raise SystemExit(1)
print("FINAL HOME GATE GREEN: required Home media foundation files present")
