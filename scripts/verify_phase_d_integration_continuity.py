from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

checks = []
def check(name, ok): checks.append((name, bool(ok)))

app = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")
backend = read("backend/server.js")
client = read("app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt")
realtime = read("app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt")
auth = read("app/src/main/java/com/fynx/app/ui/FynxAuthStore.kt")
notifications = read("app/src/main/java/com/fynx/app/ui/FynxNotificationRemoteClient.kt")
deep_link = read("app/src/main/java/com/fynx/app/ui/FynxDeepLink.kt")
share = read("app/src/main/java/com/fynx/app/ui/FynxShare.kt")
marketplace = read("app/src/main/java/com/fynx/app/ui/FynxMarketplaceRemotePanel.kt")
ai_security = read("scripts/verify_ai_security.py")
workflow = read(".github/workflows/android-build.yml")

required = [
    "app/src/main/java/com/fynx/app/ui/FynxApp.kt",
    "app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt",
    "app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt",
    "app/src/main/java/com/fynx/app/ui/FynxAuthStore.kt",
    "app/src/main/java/com/fynx/app/ui/FynxSecureTokenStore.kt",
    "app/src/main/java/com/fynx/app/ui/FynxNotificationRemoteClient.kt",
    "app/src/main/java/com/fynx/app/ui/FynxDeepLink.kt",
    "app/src/main/java/com/fynx/app/ui/FynxShare.kt",
    "app/src/main/java/com/fynx/app/ui/FynxMarketplaceRemotePanel.kt",
    "backend/server.js",
    "backend/realtimeIsolationBootstrap.js",
    "backend/mediaPrivacy.js",
    "backend/notificationPreferences.js",
    "backend/marketplaceTransactions.js",
]
check("cross-layer integration files all exist", all((ROOT / p).is_file() for p in required))
check("authenticated app state gates the live UI", "FynxAuthGate" in app and "AuthState.SIGNED_IN" in app)
check("HTTP client is the shared authenticated backend path", "FynxBackendClient" in app and "Authorization" in client and "Bearer" in client)
check("realtime client is account-bound", "currentAccountKey()" in realtime and "belongsToCurrentAccount" in realtime and "hasAccessToken" in realtime)
check("logout/session clearing remains connected", "FynxSecureTokenStore" in auth and "clear" in auth)
check("notifications have client and server wiring", "FynxBackendClient.get" in notifications and "/api/notifications" in notifications and "app.get('/api/notifications'" in read("backend/notificationPreferences.js"))
check("deep links are shared through the same parser", "FynxDeepLinkParser" in share and "fun parse" in deep_link)
check("deep links reach live app destinations", all(x in app for x in ["FynxDeepLinkDestination.Profile", "FynxDeepLinkDestination.Chat", "FynxDeepLinkDestination.Group", "FynxDeepLinkDestination.Marketplace", "FynxDeepLinkDestination.Stories", "FynxDeepLinkDestination.Money"]))
check("marketplace UI uses remote data and protected transactions", "FynxMarketplaceClient.listings" in marketplace and "FynxMarketplaceClient.createListing" in marketplace and "marketplace_orders" in read("backend/marketplaceTransactions.js"))
# Verify the actual AI security gate is wired into CI and its server-side credential/auth controls exist.
check("AI integration remains behind the existing security gate", "verify_ai_security.py" in workflow and "OPENAI_API_KEY" in ai_security and "authenticate(req)" in ai_security and "secret" not in ai_security.lower())
# Verify the actual CI ordering rather than requiring the journey script to reference itself.
journey_step = workflow.find("python3 scripts/verify_fynx_journey.py")
production_step = workflow.find("python3 scripts/verify_fynx_production.py")
build_step = workflow.find("Build, test and lint")
check("journey and production certification gates remain in the CI chain", journey_step >= 0 and production_step >= 0 and build_step >= 0 and journey_step < build_step and production_step < build_step)
check("all earlier consolidated gates run before the final Android build", workflow.index("Verify consolidated Phase C security and production readiness") < build_step)
check("integration continuity gate will execute before build", "verify_phase_d_integration_continuity.py" in workflow and workflow.index("verify_phase_d_integration_continuity.py") < build_step)
check("backend exposes authenticated media path", 'app.get("/api/media/:id", auth' in backend or "app.get('/api/media/:id', auth" in backend)
check("client media and API transport stay HTTPS constrained", "https://" in client and "configured.host" in client)
check("no obvious client API-key literals are introduced", not re.search(r"sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,}|ghp_[A-Za-z0-9]{30,}", app + client + realtime))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("Phase D integration continuity failed: " + "; ".join(failed))
print(f"Phase D integration continuity passed ({len(checks)} checks)")
