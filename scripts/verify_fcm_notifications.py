from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

checks = []
def check(name, condition):
    checks.append((name, bool(condition)))

manifest = read("app/src/main/AndroidManifest.xml")
service = read("app/src/main/java/com/fynx/app/ui/FynxFirebaseMessagingService.kt")
device = read("app/src/main/java/com/fynx/app/ui/FynxNotificationDeviceManager.kt")
backend_devices = read("backend/notificationDevices.js")
push = read("backend/notificationPush.js")
bootstrap = read("backend/notificationBootstrap.js")
realtime = read("backend/realtimeIsolationBootstrap.js")
package = read("backend/package.json")

check("Firebase messaging dependency", 'com.google.firebase:firebase-messaging' in read("app/build.gradle.kts"))
check("Google services plugin", 'com.google.gms.google-services' in read("app/build.gradle.kts"))
check("Firebase service declared non-exported", 'android:name=".ui.FynxFirebaseMessagingService"' in manifest and 'android:exported="false"' in manifest)
check("Firebase messaging event intent", 'com.google.firebase.MESSAGING_EVENT' in manifest)
check("token refresh callback", 'override fun onNewToken' in service and 'onTokenChanged' in service)
check("authenticated token registration", '/api/notification-devices' in device and 'hasAccessToken' in device)
check("token ownership is account scoped", 'CREATE UNIQUE INDEX IF NOT EXISTS notification_devices_provider_token_uidx' in backend_devices and 'ON CONFLICT(provider, token)' in backend_devices)
check("server-side FCM credential only", 'FIREBASE_SERVICE_ACCOUNT_JSON' in push and 'FIREBASE_PRIVATE_KEY' in push and 'google-services.json' not in push)
check("no Firebase private key in Android source", 'FIREBASE_PRIVATE_KEY' not in service and 'FIREBASE_SERVICE_ACCOUNT_JSON' not in service)
check("FCM HTTP v1 send endpoint", 'fcm.googleapis.com/v1/projects/' in push and 'firebase.messaging' in push)
check("FCM retry and invalid-token cleanup", 'response.status !== 429' in push and 'UNREGISTERED' in push and 'enabled=FALSE' in push)
check("privacy-safe data payload", 'You have a new message.' in bootstrap and 'body: String(message)' in push)
check("notification deep-link routing", 'route' in service and 'Uri.parse(route)' in service)
check("group message push hook", 'queueFynxNotification' in bootstrap and 'group-message-' in bootstrap)
check("friend request push hook", 'friend-request-' in bootstrap and 'FRIEND_REQUEST' in bootstrap)
check("friend accepted push hook", 'friend-accepted-' in bootstrap and 'Friend request accepted' in bootstrap)
check("private message push hook", 'message-${message.id}' in bootstrap and 'type: "MESSAGE"' in bootstrap)
check("Home comment push hook", "type:'COMMENT'" in bootstrap and 'comment-${result.rows[0].id}' in bootstrap)
check("Home comment is account scoped", 'postOwner.rows[0] && String(postOwner.rows[0].author_id) !== String(req.user.sub)' in bootstrap)
check("Home reply push hook", "type:'COMMENT'" in realtime and 'reply-${row.id}-${recipientId}' in realtime)
check("reply recipients exclude actor", 'String(parentAuthorId) !== String(req.user.sub)' in realtime and 'String(postOwnerId) !== String(req.user.sub)' in realtime)
check("reply can notify parent commenter and post owner", 'replyRecipients.add(String(parentAuthorId))' in realtime and 'replyRecipients.add(String(postOwnerId))' in realtime)
check("backend starts through notification bootstrap", 'node notificationBootstrap.js' in package)
check("server-side secrets are not APK dependencies", 'FIREBASE_SERVICE_ACCOUNT_JSON' not in read("app/build.gradle.kts"))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit("FCM verification failed: " + ", ".join(failed))
print(f"FCM verification GREEN: {len(checks)} checks passed")
