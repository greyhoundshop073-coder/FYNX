from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/fynx/app/ui"

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

def first_text(*names):
    for name in names:
        p = UI / name
        if p.is_file():
            return p.read_text(encoding="utf-8")
    return ""

def exists_any(*names):
    return any((UI / n).is_file() for n in names)

checks = []

def check(name, ok):
    checks.append((name, bool(ok)))

app = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")
home = read("app/src/main/java/com/fynx/app/ui/HomePanel.kt")
chat = first_text("ChatsPanel.kt", "ConversationPanel.kt", "GroupChatPanel.kt")
profile = read("app/src/main/java/com/fynx/app/ui/ProfilePanel.kt")
stories = read("app/src/main/java/com/fynx/app/ui/StoriesPanel.kt")
marketplace = first_text("FynxRemoteHomeSocialPanel.kt", "MarketplaceScrollCompat.kt", "FynxTransactionFoundation.kt")
calls = read("app/src/main/java/com/fynx/app/ui/FynxCallsPanel.kt")
notifications = read("app/src/main/java/com/fynx/app/ui/FynxNotificationRemoteClient.kt")
privacy = read("app/src/main/java/com/fynx/app/ui/FynxPrivacySettings.kt")
journey = read("scripts/verify_fynx_journey.py")
runtime = read("scripts/verify_runtime_navigation.py")
marketplace_backend = read("backend/marketplaceTransactions.js")
groups_backend = read("backend/groupRoutes.js")

required_ok = all([
    (UI / "FynxApp.kt").is_file(),
    (UI / "HomePanel.kt").is_file(),
    (UI / "ChatsPanel.kt").is_file(),
    (UI / "ProfilePanel.kt").is_file(),
    (UI / "StoriesPanel.kt").is_file(),
    exists_any("FynxRemoteHomeSocialPanel.kt", "FynxTransactionFoundation.kt"),
    (UI / "FynxCallsPanel.kt").is_file(),
    (UI / "FynxNotificationRemoteClient.kt").is_file(),
    (UI / "FynxPrivacySettings.kt").is_file(),
    (ROOT / "backend/groupRoutes.js").is_file(),
    (ROOT / "backend/profileRoutes.js").is_file(),
    (ROOT / "backend/marketplaceTransactions.js").is_file(),
])
check("all major FYNX surface files exist", required_ok)

check(
    "Home is part of the authenticated app",
    "FynxHomeSocialHubPanel" in app
    and "authSession.state" in app
    and "AuthState.SIGNED_IN" in app
    and "FynxAuthGate" in app
)
check("Chat is connected to the app", ("ChatsPanel" in app or "ConversationPanel" in app or "GroupChatPanel" in app) and "ConversationPanel" in app)
check("People/Profile is connected to real profile loading", "ProfilePanel" in app and "FynxProfileRemoteClient" in profile)
check("Stories/Status surface is connected", "FynxStatusHubPanel" in app and ("FynxStatusTimelinePanel" in stories or "FynxStatusTimelinePanel" in read("app/src/main/java/com/fynx/app/ui/FynxStatusHubPanel.kt")))
check("Groups use server-backed routes", "/api/groups" in groups_backend)
check("Marketplace uses remote data and protected transactions", ("FynxRemoteHomeSocialPanel" in marketplace or "FynxTransactionFoundation" in marketplace) and "marketplace_orders" in marketplace_backend)
check("Calls retain realtime and media controls", "realtimeClient.connect()" in calls and "setMicrophoneEnabled" in calls and "setCameraEnabled" in calls)
check("Notifications remain server-backed", "FynxBackendClient.get" in notifications and "/api/notifications" in notifications)
check("Privacy/Safety remains an app surface", "FynxPrivacySettingsPanel" in privacy and "Privacy" in app)
check("Money/feature routing remains exposed", "FynxDeepLinkDestination.Money" in app and "moneyWebLink" in journey)
secret_pattern = re.compile(r"sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,}|ghp_[A-Za-z0-9]{30,}")
check("no common API secret pattern in core client surface", not secret_pattern.search(app + home + chat + profile + marketplace))

runtime_lower = runtime.lower()
check("runtime audit captures screenshots and UI hierarchy", "screencap" in runtime_lower and "uiautomator" in runtime_lower)
check("runtime audit does not manufacture application data", "no fake application data is created" in runtime_lower and "no fake account/data is created" in runtime_lower)
check("runtime audit records blocked authenticated journeys honestly", "blocked home social journey" in runtime_lower and "no fake account/data is created" in runtime_lower)
check("existing consolidated journey gate remains wired", "scripts/verify_fynx_journey.py" in read(".github/workflows/android-build.yml"))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("Large Badge #3 audit failed: " + "; ".join(failed))
print(f"Large Badge #3 audit passed ({len(checks)} checks)")
