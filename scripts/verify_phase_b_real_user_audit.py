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

critical_files = [
    "app/src/main/java/com/fynx/app/ui/FynxApp.kt",
    "app/src/main/java/com/fynx/app/MainActivity.kt",
    "app/src/main/java/com/fynx/app/ui/FynxAuthStore.kt",
    "app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt",
    "app/src/main/java/com/fynx/app/ui/ProfilePanel.kt",
    "app/src/main/java/com/fynx/app/ui/OtherUserProfilePanel.kt",
    "app/src/main/java/com/fynx/app/ui/ConversationPanel.kt",
    "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt",
    "app/src/main/java/com/fynx/app/ui/FynxMarketplaceClient.kt",
    "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSafety.kt",
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
    "backend/statusManagementRoutes.js",
    "backend/socialPostReactionBootstrap.js",
    "backend/marketplaceCompletion.js",
]
check("critical real-user surfaces are present", all(map(exists, critical_files)))

app = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")
main = read("app/src/main/java/com/fynx/app/MainActivity.kt")
auth = read("app/src/main/java/com/fynx/app/ui/FynxAuthStore.kt")
client = read("app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt")
profile = read("app/src/main/java/com/fynx/app/ui/ProfilePanel.kt")
other_profile = read("app/src/main/java/com/fynx/app/ui/OtherUserProfilePanel.kt")
chat = read("app/src/main/java/com/fynx/app/ui/ConversationPanel.kt")
market = read("app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt")
market_client = read("app/src/main/java/com/fynx/app/ui/FynxMarketplaceClient.kt")
market_safety = read("app/src/main/java/com/fynx/app/ui/FynxMarketplaceSafety.kt")
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
status_management = read("backend/statusManagementRoutes.js")
reaction_bootstrap = read("backend/socialPostReactionBootstrap.js")
marketplace_completion = read("backend/marketplaceCompletion.js")
status_routes = read("backend/statusInteractionRoutes.js")
social_routes = read("backend/socialRoutes.js")
home = read("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt")
timeline = read("app/src/main/java/com/fynx/app/ui/FynxStatusTimelinePanel.kt")
messages = read("backend/server.js")

check("two-real-user status visibility path", "FynxStatusClient.list(context)" in timeline and "expires_at > NOW()" in status_routes)
check("two-real-user post visibility path", "FynxRemoteSocialClient.feedPage" in home and "visibility" in social_routes)
check("two-real-user block enforcement", "blocks" in social_routes and "blocks" in messages and "conversation unavailable" in messages)
check("production app is not in preview mode", "FYNX_PREVIEW_MODE = false" in app)
check("signed-in gate protects the production surface", "AuthState.SIGNED_IN" in app)
check("backend client owns authenticated API access", "hasAccessToken" in client and "Authorization" in client)
check("logout clears the secure local token and session state", "fun clear(context: Context)" in auth and "FynxSecureTokenStore.save(context, null)" in auth)
check("profile uses real backend identity and counts", "FynxProfileRemoteClient" in profile and "followerCount" in profile and "followingCount" in profile)
check("Account & Profile settings use the active profile instead of reloading stale local identity", "SettingsPanel(profile = profile, settings = settings" in profile and "fun SettingsPanel(\n    profile: FynxProfile," in profile and "AccountProfileSettingsPanel(\n            profile = profile," in profile and "private fun AccountProfileSettingsPanel(\n    profile: FynxProfile," in profile and "FynxPreferencesStore.loadProfile(context, null)" not in profile)
check("other-user profile does not expose private follower/following lists", not re.search(r"(?:followers?|following)\s+(?:list|members?|user|people|names)", other_profile, re.IGNORECASE))
check("profile privacy is enforced server-side", "connectionsVisible:self" in profile_api and "privacy" in profile_api.lower())
check("private chat is connected to authenticated backend flow", ("FynxProductionMessaging" in chat and "sendText" in chat and "history" in chat) or ("FynxBackendClient" in chat and ("send" in chat.lower() or "message" in chat.lower())))
# The production Marketplace surface is FynxMarketplacePanel. Marketplace network creation and safety live in their dedicated clients.
check("marketplace is remote/backend-backed", "FynxRemoteSocialClient.listings" in market and "fun createListing(" in market_client and "fun analyze(" in market_safety)
check("marketplace transaction protection remains server-side", all(x in market_api for x in ["marketplace_orders", "PAYMENT_PENDING", "DISPUTED", "marketplace_order_disputes", "payout:'not_released'"]))
check("group membership and messages are server-authoritative", all(x in group_api for x in ["fynx_group_members", "fynx_group_messages", "app.get('/api/groups/:groupId/messages'", "app.post('/api/groups/:groupId/messages'", "jwt.verify"]))
check("friend/social actions are backend-backed", "registerSocialRoutes" in social_api and all(x in social_api for x in ["/api/friends", "/api/friends/request", "friendships"]))
check("privacy settings surface is connected", "FynxPrivacySettingsPanel" in app and "FynxPrivacySettings" in privacy)
check("deep-link parser and live activity routing are connected", "FynxDeepLinkParser.parse" in main and all(x in app for x in ["FynxDeepLinkDestination.Profile", "FynxDeepLinkDestination.Chat", "FynxDeepLinkDestination.Group", "FynxDeepLinkDestination.Marketplace"]) and all(x in deep_link for x in ["fun parse", "profileWebLink", "chatWebLink", "groupWebLink", "marketplaceWebLink"]))
check("FCM token registration is authenticated and account-scoped", "hasAccessToken" in fcm_client and "/api/notification-devices" in fcm_client)
check("FCM service handles token refresh and incoming data", "onNewToken" in fcm_service and "onMessageReceived" in fcm_service)
check("notification device storage is user-scoped", "user_id" in notification_devices and "REFERENCES users(id)" in notification_devices)
check("notification sender keeps credentials server-side", "FIREBASE_SERVICE_ACCOUNT_JSON" in notification_push and "FIREBASE_PRIVATE_KEY" in notification_push and "firebase" in notification_push.lower())
check("notification bootstrap is idempotent integration glue", "async function patchOnce" in notification_bootstrap and "changed = false" in notification_bootstrap and "patches" in notification_bootstrap and "source.includes(patch.replacement)" in notification_bootstrap and "Skip a patch once its complete replacement is already present" in notification_bootstrap)
check("status archive route has valid media URL template syntax", "\\`" not in status_management and "mediaUrl:row.media_id == null ? null : `/api/media/${row.media_id}`" in status_management)
check("database bootstrap parameterized update is outside schema batch", "UPDATE users SET verified = TRUE WHERE lower(username) = $1;" not in messages and 'await pool.query("UPDATE users SET verified = TRUE WHERE lower(username) = $1", [officialFynxUsername]);' in messages)
check("database bootstrap SQL has no literal escaped newline", r"JSONB;\n    ALTER TABLE messages ADD COLUMN IF NOT EXISTS reaction" not in messages)
check("database bootstrap avoids anonymous foreign-key block", "DO $ BEGIN ALTER TABLE messages ADD CONSTRAINT messages_media_fk" not in messages and 'await pool.query("ALTER TABLE messages ADD CONSTRAINT messages_media_fk' in messages and 'error?.code !== "42710"' in messages)
check("notifications use canonical safety table and migrate legacy columns", "FROM safety_settings WHERE user_id=$1" in read("backend/notificationPreferences.js") and "FROM fynx_account_safety" not in read("backend/notificationPreferences.js") and "ALTER TABLE fynx_notifications ADD COLUMN IF NOT EXISTS route TEXT" in read("backend/notificationPreferences.js") and "ALTER TABLE safety_settings ADD COLUMN IF NOT EXISTS updated_at" in read("backend/notificationPreferences.js"))
check("social reaction bootstrap preserves nested template literals", "await pool.query(\\`" in reaction_bootstrap and "WHERE \\${where.join(\' AND \')}" in reaction_bootstrap and "ORDER BY r.created_at DESC LIMIT 200\\`," in reaction_bootstrap)
check("marketplace completion has valid template delimiters", "\\`" not in marketplace_completion)

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
