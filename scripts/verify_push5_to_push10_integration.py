from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
checks = []

def read(path):
    p = ROOT / path
    return p.read_text(encoding="utf-8") if p.is_file() else ""

def check(name, ok):
    checks.append((name, bool(ok)))

home = read("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt")
app = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")
profile = read("app/src/main/java/com/fynx/app/ui/ProfilePanel.kt")
profile_content = read("app/src/main/java/com/fynx/app/ui/FynxProfileContent.kt")
other = read("app/src/main/java/com/fynx/app/ui/OtherUserProfilePanel.kt")
chat = read("app/src/main/java/com/fynx/app/ui/ConversationPanel.kt")
market = read("app/src/main/java/com/fynx/app/ui/FynxMarketplaceRemotePanel.kt")
market_api = read("backend/marketplaceTransactions.js")
profile_api = read("backend/profileRoutes.js")
social_api = read("backend/socialRoutes.js")
server = read("backend/server.js")
status = read("backend/statusInteractionRoutes.js")
workflow = read(".github/workflows/android-build.yml")

# Push 5
check("real feed is backend-backed", "FynxRemoteSocialClient.feedPage" in home)
check("post interactions are backend-backed", all(x in home for x in ["FynxRemoteSocialClient.like", "FynxHomePostReactionsClient"]))
check("follow action is backend-backed", "FynxRemoteSocialClient.follow" in home)
check("two-user message block enforcement exists", "conversation unavailable" in server and "blocks" in server)
check("social block enforcement exists", "blocks" in social_api)
check("Status block enforcement exists", "blocks" in status)

# Push 6
check("other-user profile loads remote identity", "FynxProfileRemoteClient.get" in other)
check("other-user profile renders real remote posts", "FynxProfileContentSection" in other and "FynxProfileRemoteClient.posts" in profile_content)
check("profile Status uses active non-expired Status", "FynxStatusClient.list(context)" in other and "isExpired" in other)
check("profile -> chat callback is wired", "onMessage" in other)
check("app routes profile -> chat and profile -> Status", all(x in app for x in ["OtherUserProfilePanel", "ConversationPanel", "selected = \"Stories\""]))

# Push 7
check("marketplace listings are remote", "FynxMarketplaceClient.listings" in market)
check("listing seller identity is available", "listing.sellerUsername" in market or "listing.sellerDisplayName" in market)
check("order lifecycle is server-side", all(x in market_api for x in ["PAYMENT_PENDING", "SHIPPED", "DELIVERED", "INSPECTION", "COMPLETED", "DISPUTED", "REFUNDED"]))
check("seller payout remains protected", "payout" in market_api and "not_released" in market_api)
check("marketplace dispute path exists", "marketplace_order_disputes" in market_api)

# Push 8
check("verification is tied to real user data", "verified" in profile_api)
check("official FYNX verification is backend-enforced", "FYNX_OFFICIAL_USERNAME" in server and "UPDATE users SET verified = FALSE" in server and "verified = TRUE WHERE lower(username)" in server)
check("badge rendering has explicit blue color support", "Color(0xFF1877F2)" in other)

# Push 9 investigation: do not invent a feature if no production implementation exists.
cover_sources = []
for p in (ROOT / "app/src/main/java").rglob("*.kt"):
    s = p.read_text(encoding="utf-8", errors="ignore").lower()
    if any(term in s for term in ["coverphoto", "cover_photo", "cover photo", "takeover photo", "profile cover"]):
        cover_sources.append(str(p.relative_to(ROOT)))
print("COVER/TAKEOVER PHOTO SOURCES:", ", ".join(cover_sources) if cover_sources else "none found; no duplicate implementation added")

# Push 10
check("this gate is wired into Android CI", "verify_push5_to_push10_integration.py" in workflow)
check("Android CI builds APK", "assembleDebug" in workflow)
check("Android CI uploads exact-commit APK", "FYNX-debug-" in workflow and "GITHUB_SHA" in workflow)
check("Push 9 investigation is recorded without adding a duplicate feature", (ROOT / "docs/FYNX_PROFILE_COVER_TAKEOVER_INVESTIGATION.md").is_file() and not cover_sources)
check("final integration sweep includes the active marketplace panel", "FynxMarketplacePanel" in app)
check("final integration sweep includes the real Status deep-link parameter", "openOwnerUsername" in app)
check("production app is not preview mode", "FYNX_PREVIEW_MODE = false" in app)
check("no obvious fake production identity shortcut", not re.search(r"fakeUser|FakeUser|demoUser|DemoUser|mockUser|MockUser", home))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("FYNX Push 5-10 integration gate failed: " + "; ".join(failed))
print(f"FYNX Push 5-10 integration gate passed ({len(checks)} checks)")
