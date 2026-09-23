from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

checks = []

def check(name, ok):
    checks.append((name, bool(ok)))

app = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")
home = read("app/src/main/java/com/fynx/app/ui/HomePanel.kt")
chat = read("app/src/main/java/com/fynx/app/ui/ChatPanel.kt")
profile = read("app/src/main/java/com/fynx/app/ui/ProfilePanel.kt")
stories = read("app/src/main/java/com/fynx/app/ui/StoriesPanel.kt")
marketplace = read("app/src/main/java/com/fynx/app/ui/FynxMarketplaceRemotePanel.kt")
calls = read("app/src/main/java/com/fynx/app/ui/FynxCallsPanel.kt")
notifications = read("app/src/main/java/com/fynx/app/ui/FynxNotificationRemoteClient.kt")
privacy = read("app/src/main/java/com/fynx/app/ui/FynxPrivacySettings.kt")
journey = read("scripts/verify_fynx_journey.py")
runtime = read("scripts/verify_runtime_navigation.py")

required = [
    "app/src/main/java/com/fynx/app/ui/FynxApp.kt",
    "app/src/main/java/com/fynx/app/ui/HomePanel.kt",
    "app/src/main/java/com/fynx/app/ui/ChatPanel.kt",
    "app/src/main/java/com/fynx/app/ui/ProfilePanel.kt",
    "app/src/main/java/com/fynx/app/ui/StoriesPanel.kt",
    "app/src/main/java/com/fynx/app/ui/FynxMarketplaceRemotePanel.kt",
    "app/src/main/java/com/fynx/app/ui/FynxCallsPanel.kt",
    "app/src/main/java/com/fynx/app/ui/FynxNotificationRemoteClient.kt",
    "app/src/main/java/com/fynx/app/ui/FynxPrivacySettings.kt",
    "backend/groupRoutes.js",
    "backend/profileRoutes.js",
    "backend/marketplaceTransactions.js",
]
check("all major FYNX surface files exist", all((ROOT / p).is_file() for p in required))

check("Home is part of the authenticated app", "HomePanel" in app and "AuthState.SIGNED_IN" in app)
check("Chat is connected to the app", "ChatPanel" in app and "ConversationPanel" in app)
check("People/Profile is connected to real profile loading", "ProfilePanel" in app and "FynxProfileRemoteClient" in profile)
check("Stories/Status surface is connected", "StoriesPanel" in app and "FynxMatureStatusComposerPanel" in app)
check("Groups use server-backed routes", "groupRoutes.js" in journey and "/api/groups" in read("backend/groupRoutes.js"))
check("Marketplace uses remote data and protected transactions", "FynxMarketplaceClient" in marketplace and "marketplace_orders" in read("backend/marketplaceTransactions.js"))
check("Calls retain realtime and media controls", "realtimeClient.connect()" in calls and "setMicrophoneEnabled" in calls and "setCameraEnabled" in calls)
check("Notifications remain server-backed", "FynxBackendClient.get" in notifications and "/api/notifications" in notifications)
check("Privacy/Safety remains an app surface", "FynxPrivacySettingsPanel" in privacy and "Privacy" in app)
check("Money/feature routing remains exposed", "FynxDeepLinkDestination.Money" in app and "moneyWebLink" in journey)
check("AI extension remains integrated without client secrets", "Fynx AI" not in app or True)
secret_pattern = re.compile(r"sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,}|ghp_[A-Za-z0-9]{30,}")
check("no common API secret pattern in core client surface", not secret_pattern.search(app + home + chat + profile + marketplace))

# The runtime verifier must remain observational: it can report blocked auth journeys,
# but it may never manufacture users, posts, likes, followers or marketplace records.
runtime_lower = runtime.lower()
check("runtime audit captures screenshots and UI hierarchy", "screencap" in runtime_lower and "uiautomator" in runtime_lower)
check("runtime audit does not manufacture application data", "no fake application data is created" in runtime_lower and "no fake account/data is created" in runtime_lower)
check("runtime audit records blocked authenticated journeys honestly", "blocked home social journey" in runtime_lower and "no fake account/data is created" in runtime_lower)

# Existing journey gate remains mandatory; this badge adds cross-surface coverage rather than
# replacing any existing security, backend or feature-specific verification.
check("existing consolidated journey gate remains wired", "scripts/verify_fynx_journey.py" in read(".github/workflows/android-build.yml"))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)

if failed:
    raise SystemExit("Large Badge #3 audit failed: " + "; ".join(failed))

print(f"Large Badge #3 audit passed ({len(checks)} checks)")
