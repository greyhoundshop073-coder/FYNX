from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

app = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")
calls = read("app/src/main/java/com/fynx/app/ui/FynxCallsPanel.kt")
notifications = read("app/src/main/java/com/fynx/app/ui/FynxNotificationRemoteClient.kt")
notification_backend = read("backend/notificationPreferences.js")
admin = read("app/src/main/java/com/fynx/app/ui/FynxAdminClient.kt")
privacy = read("app/src/main/java/com/fynx/app/ui/FynxPrivacySettings.kt")
profile_backend = read("backend/profileRoutes.js")
profile_client = read("app/src/main/java/com/fynx/app/ui/FynxProfileRemoteClient.kt")
profile_panel = read("app/src/main/java/com/fynx/app/ui/ProfilePanel.kt")
other_profile_panel = read("app/src/main/java/com/fynx/app/ui/OtherUserProfilePanel.kt")

required_files = [
    "app/src/main/java/com/fynx/app/ui/FynxApp.kt",
    "app/src/main/java/com/fynx/app/ui/FynxCallsPanel.kt",
    "app/src/main/java/com/fynx/app/ui/FynxNotificationRemoteClient.kt",
    "app/src/main/java/com/fynx/app/ui/FynxAdminClient.kt",
    "app/src/main/java/com/fynx/app/ui/FynxPrivacySettings.kt",
    "backend/adminRoutes.js",
    "backend/notificationPreferences.js",
    "backend/privacyRoutes.js",
    "backend/groupRoutes.js",
    "backend/marketplaceTransactions.js",
]
check("all core journey integration files exist", all((ROOT / p).is_file() for p in required_files))
check("authenticated app gate exists", "FynxAuthGate" in app and "AuthState.SIGNED_IN" in app)
check("profile to chat and call navigation exists", "ConversationPanel" in app and "onVoiceCall" in app and "onVideoCall" in app)
check("calls panel has permission recovery and realtime events", "RequestMultiplePermissions" in calls and "realtimeClient.connect()" in calls and '"invite"' in calls)
check("calls panel cleans media on terminal paths", "mediaEngine.disconnect()" in calls and "FynxCallsStore.updateStatus" in calls)

# The Android notification client delegates transport to FynxBackendClient.
# Verify the public contract without depending on one exact whitespace/layout form.
notification_load_ok = (
    "FynxBackendClient.get" in notifications
    and "/api/notifications" in notifications
)
notification_read_ok = (
    "FynxBackendClient.postJson" in notifications
    and "/api/notifications/" in notifications
    and "/read" in notifications
)
notification_load_method = re.search(
    r"\bfun\s+load\s*\(",
    notifications
) is not None
notification_mark_read_method = re.search(
    r"\bfun\s+markRead\s*\(",
    notifications
) is not None
check(
    "server notification client delegates to notification API",
    "object FynxNotificationRemoteClient" in notifications
    and notification_load_method
    and notification_load_ok
    and notification_mark_read_method
    and notification_read_ok
)
check("server notification API is registered", "app.get('/api/notifications'" in notification_backend and "app.post('/api/notifications/:id/read'" in notification_backend)
check("admin center is server-role gated", "FynxAdminClient.dashboard" in app and "adminRole" in app and 'adminRole != null' in app)
check("privacy/safety surface is wired", "Privacy" in app and "FynxPrivacySettingsPanel" in app)
check(
    "profile stats are server-authoritative and self-only",
    "followerCount" in profile_backend
    and "followingCount" in profile_backend
    and "connectionsVisible:self" in profile_backend
    and "app.get('/api/social/me/followers'" in profile_backend
    and "app.get('/api/social/me/following'" in profile_backend
    and "WHERE f.followed_id=$1" in profile_backend
    and "WHERE f.follower_id=$1" in profile_backend
    and "suspend fun followers" in profile_client
    and "suspend fun following" in profile_client
)
check(
    "profile stats UI matches the private connections rule",
    'ProfileStat("Posts"' in profile_panel
    and 'ProfileStat("Followers"' in profile_panel
    and 'ProfileStat("Following"' in profile_panel
    and "ProfileConnectionsDialog" in profile_panel
    and "FynxProfileRemoteClient.followers" in profile_panel
    and "FynxProfileRemoteClient.following" in profile_panel
    and '"Followers"' not in other_profile_panel
    and '"Following"' not in other_profile_panel
)

check("owner/admin client exposes server controls", all(x in admin for x in ["dashboard", "admins", "setAccountStatus", "grantAdmin", "revokeAdmin"]))
check("removed AI image/video generation is not reintroduced", "image generation" not in app.lower() and "video generation" not in app.lower())
secret_pattern = re.compile(r"sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,}|ghp_[A-Za-z0-9]{30,}")
all_text = "\n".join(read(p) for p in ["app/src/main/java/com/fynx/app/ui/FynxApp.kt", "app/src/main/java/com/fynx/app/ui/FynxAdminClient.kt"])
check("no common API secret pattern in client files", not secret_pattern.search(all_text))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("FYNX journey audit failed: " + "; ".join(failed))
print(f"FYNX journey audit passed ({len(checks)} checks)")
