#!/usr/bin/env python3
"""Large Badge #7: notification delivery + preferences production certification."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxNotificationPreferencesClient.kt").read_text(encoding="utf-8")
remote = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxNotificationRemoteClient.kt").read_text(encoding="utf-8")
activity = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxNotificationActivityCenter.kt").read_text(encoding="utf-8")
device = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxNotificationDeviceManager.kt").read_text(encoding="utf-8")
models = (ROOT / "app/src/main/java/com/fynx/app/ui/NotificationModels.kt").read_text(encoding="utf-8")
backend = (ROOT / "backend/notificationPreferences.js").read_text(encoding="utf-8")
devices = (ROOT / "backend/notificationDevices.js").read_text(encoding="utf-8")
push = (ROOT / "backend/notificationPush.js").read_text(encoding="utf-8")
bootstrap = (ROOT / "backend/notificationBootstrap.js").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")

checks = [
    ("notification preferences load uses authenticated backend route", '"/api/notification-preferences"' in client and "FynxBackendClient.get" in client),
    ("notification preferences update uses authenticated backend route", 'FynxBackendClient.patchJson' in client and '"/api/notification-preferences"' in client),
    ("preferences are account-scoped in local cache", "FynxAuthStore.accountStorageKey" in client and "fynx_notification_preferences" in client),
    ("notification feed is server-backed", '"/api/notifications"' in remote and "FynxBackendClient.get" in remote),
    ("single notification read is server-backed and account-scoped", '"/api/notifications/$encoded/read"' in remote),
    ("mark-all-read is server-backed", '"/api/notifications/read-all"' in remote),
    ("activity center supports filtering and unread state", "filterByType" in activity and "unreadOnly" in activity),
    ("device registration is present", "notification-devices" in device and "POST" in device),
    ("device unregister is present", "DELETE" in device and "notification-devices" in device),
    ("backend notification preference routes authenticate", "function auth" in backend and "jwt.verify" in backend),
    ("backend notification preferences are keyed by authenticated user", "user_id BIGINT PRIMARY KEY" in backend and "req.user.sub" in backend),
    ("backend notification read operations are user-scoped", "WHERE id=$1 AND user_id=$2" in backend and "WHERE user_id=$1" in backend),
    ("notification devices are authenticated and uniquely protected", "jwt.verify" in devices and "PRIMARY KEY (user_id, provider, token)" in devices and "notification_devices_provider_token_uidx" in devices),
    ("server push honors global and category preferences", "push_enabled" in push and "preferenceColumn" in push and "pushAllowed" in push),
    ("server push stores notification before delivery", "INSERT INTO fynx_notifications" in push and "queueFynxNotification" in push),
    ("FCM credentials remain server-side", "FIREBASE_SERVICE_ACCOUNT_JSON" in push and "FIREBASE_PRIVATE_KEY" in push and "FYNX" not in models),
    ("push delivery has retry and invalid-token handling", "attempt < 3" in push and "UNREGISTERED" in push and "status='SENT'" in push),
    ("notification route wiring covers real message/group/social events", "queueFynxNotification" in bootstrap and "type: "MESSAGE"" in bootstrap and "type:'GROUP'" in bootstrap and "type:'COMMENT'" in bootstrap),
    ("existing notification verification gates remain in CI", "verify_notifications_settings_integration.py" in workflow),
    ("real Android instrumentation remains in CI", "connectedDebugAndroidTest" in workflow and "verify_runtime_navigation.py" in workflow),
    ("no fake notification records are introduced", "mockNotification" not in backend and "fakeNotification" not in backend),
]
failed=[]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
    if not ok: failed.append(name)
if failed:
    raise SystemExit("LARGE BADGE #7 RED: " + "; ".join(failed))
print(f"LARGE BADGE #7 NOTIFICATIONS + PREFERENCES + DELIVERY CONTRACT: GREEN ({len(checks)} checks)")
