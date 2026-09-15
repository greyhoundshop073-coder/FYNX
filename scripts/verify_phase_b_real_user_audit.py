from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
checks = []

def check(name, ok):
    checks.append((name, bool(ok)))

def exists(path):
    return (ROOT / path).is_file()

def read(path):
    p = ROOT / path
    return p.read_text(encoding="utf-8") if p.is_file() else ""

# This gate audits the actual production files and symbols used by FYNX.
# It must not require historical/renamed filenames or literal comments that
# are not part of the runtime architecture.
critical_files = [
    "app/src/main/java/com/fynx/app/ui/FynxApp.kt",
    "app/src/main/java/com/fynx/app/MainActivity.kt",
    "app/src/main/java/com/fynx/app/ui/FynxAuthStore.kt",
    "app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt",
    "app/src/main/java/com/fynx/app/ui/ProfilePanel.kt",
    "app/src/main/java/com/fynx/app/ui/OtherUserProfilePanel.kt",
    "app/src/main/java/com/fynx/app/ui/ConversationPanel.kt",
    "app/src/main/java/com/fynx/app/ui/FynxMarketplaceRemotePanel.kt",
    "app/src/main/java/com/fynx/app/ui/FynxPrivacySettings.kt",
    "app/src/main/java/com/fynx/app/ui/FynxDeepLink.kt",
    "app/src/main/java/com/fynx/app/ui/FynxNotificationDeviceManager.kt",
    "app/src/main/java/com/fynx/app/ui/FynxFirebaseMessagingService.kt",
    "backend/profileRoutes.js",
    "backend/socialRoutes.js",
    "backend/groupRoutes.js",
    "backend/marketplaceTransactions.js",
    "backend/notificationDevices.js",
    "backend/notificationPush.js",
    "backend/notificationBootstrap.js",
]
check("critical real-user surfaces are present", all(map(exists, critical_files)))

app = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")
main = read("app/src/main/java/com/fynx/app/MainActivity.kt")
auth = read("app/src/main/java/com/fynx/app/ui/FynxAuthStore.kt")
client = read("app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt")
profile = read("app/src/main/java/com/fynx/app/ui/ProfilePanel.kt")
other_profile = read("app/src/main/java/com/fynx/app/ui/OtherUserProfilePanel.kt")
chat = read("app/src/main/java/com/fynx/app/ui/ConversationPanel.kt")
market = read("app/src/main/java/com/fynx/app/ui/FynxMarketplaceRemotePanel.kt")
privacy = read("app/src/main/java/com/fynx/app/ui/FynxPrivacySettings.kt")
deep_link = read("app/src/main/java/com/fynx/app/ui/FynxDeepLink.kt")
fcm_client = read("app/src/main/java/com/fynx/app/ui/FynxNotificationDeviceManager.kt")
fcm_service = read("app/src/main/java/com/fynx/app/ui/FynxFirebaseMessagingService.kt")
profile_api = read("backend/profileRoutes.js")
social_api = read("backend/socialRoutes.js")
group_api = read("backend/groupRoutes.js")
market_api = read("backend/marketplaceTransactions.js")
notification_devices = read("backend/notificationDevices.js")
notification_push = read("backend/notificationPush.js")
notification_bootstrap = read("backend/notificationBootstrap.js")

check("production app is not in preview mode", "FYNX_PREVIEW_MODE = false" in app)
check("signed-in gate protects the production surface", "AuthState.SIGNED_IN" in app)
check("backend client owns authenticated API access", "hasAccessToken" in client and "Authorization" in client)
check("logout clears the secure local token and session state", "fun clear(context: Context)" in auth and "FynxSecureTokenStore.save(context, null)" in auth)
check("profile uses real backend identity and counts", "FynxProfileRemoteClient" in profile and "followerCount" in profile and "followingCount" in profile)
check("other-user profile does not expose private follower/following lists", 'ProfileStat("Followers"' not in other_profile and 'ProfileStat("Following"' not in other_profile and "followerCount" not in other_profile and "followingCount" not in other_profile)
check("profile privacy is enforced server-side", "connectionsVisible:self" in profile_api and "privacy" in profile_api.lower())
check("private chat is connected to authenticated backend flow", "FynxBackendClient" in chat and ("send" in chat.lower() or "message" in chat.lower()))
check("marketplace is remote/backend-backed", "FynxMarketplaceClient.listings" in market and "FynxMarketplaceClient.createListing" in market)
check("marketplace transaction protection remains server-side", all(x in market_api for x in ["marketplace_orders", "PAYMENT_PENDING", "DISPUTED", "marketplace_order_disputes", "payout:'not_released'"]))
check("group membership and messages are server-authoritative", all(x in group_api for x in ["fynx_group_members", "fynx_group_messages", "app.get('/api/groups/:groupId/messages'", "app.post('/api/groups/:groupId/messages'", "jwt.verify"]))
check("friend/social actions are backend-backed", "registerSocialRoutes" in social_api and all(x in social_api for x in ["/api/friends", "/api/friends/request", "friendships"]))
check("privacy settings surface is connected", "FynxPrivacySettingsPanel" in app and "FynxPrivacySettings" in privacy)
check("deep-link parser and live activity routing are connected", "FynxDeepLinkParser.parse" in main and all(x in app for x in ["FynxDeepLinkDestination.Profile", "FynxDeepLinkDestination.Chat", "FynxDeepLinkDestination.Group", "FynxDeepLinkDestination.Marketplace"]) and all(x in deep_link for x in ["fun parse", "profileWebLink", "chatWebLink", "groupWebLink", "marketplaceWebLink"]))
check("FCM token registration is authenticated and account-scoped", "hasAccessToken" in fcm_client and "/api/notification-devices" in fcm_client)
check("FCM service handles token refresh and incoming data", "onNewToken" in fcm_service and "onMessageReceived" in fcm_service)
check("notification device storage is user-scoped", "user_id" in notification_devices and "REFERENCES users(id)" in notification_devices)
check("notification sender keeps credentials server-side", "FIREBASE_SERVICE_ACCOUNT_JSON" in notification_push and "FIREBASE_PRIVATE_KEY" in notification_push and "firebase" in notification_push.lower())
check("notification bootstrap is idempotent integration glue", "async function patchOnce" in notification_bootstrap and "changed = false" in notification_bootstrap and "patches" in notification_bootstrap)

client_sources = [p.read_text(encoding="utf-8") for p in (ROOT / "app/src/main/java/com/fynx/app/ui").glob("*.kt")]
all_client = "\n".join(client_sources)
check("no hard-coded API secret pattern in client source", not re.search(r"sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,}|ghp_[A-Za-z0-9]{30,}", all_client))
check("no obvious fake production identity shortcut", not re.search(r"fakeUser|FakeUser|demoUser|DemoUser|mockUser|MockUser", all_client))
check("removed AI image/video generation is not reintroduced", "image generation" not in app.lower() and "video generation" not in app.lower())

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("FYNX Phase B real-user audit failed: " + "; ".join(failed))
print(f"FYNX Phase B real-user audit passed ({len(checks)} checks)")
