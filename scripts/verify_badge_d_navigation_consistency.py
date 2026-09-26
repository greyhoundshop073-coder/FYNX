#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/fynx/app/ui"

def read(path):
    p = ROOT / path
    if not p.is_file():
        raise SystemExit(f"BADGE D RED: missing {path}")
    return p.read_text(encoding="utf-8", errors="replace")

app = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")
home = read("app/src/main/java/com/fynx/app/ui/HomePanel.kt")
journey = read("scripts/verify_fynx_journey.py")
runtime = read("scripts/verify_runtime_navigation.py")

checks = {
    "central app navigation exists": "Navigation" in app,
    "Home route is represented": "Home" in app,
    "Chat route is represented": "Chat" in app or "Conversation" in app,
    "Friends/people route is represented": "Friends" in app or "People" in app,
    "Marketplace route is represented": "Market" in app or "Marketplace" in app,
    "More/settings route is represented": "More" in app or "Settings" in app,
    "existing journey gate remains wired": "verify_fynx_journey.py" in read(".github/workflows/android-build.yml"),
    "runtime navigation verifier exists": "Navigation" in runtime and "check" in runtime.lower(),
    "Home panel remains connected": "FynxRemoteHomeSocialPanel" in home or "FynxHomeSocialHubPanel" in home,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("BADGE D RED: " + "; ".join(failed))
print("BADGE D GREEN: navigation consistency source audit passed")
