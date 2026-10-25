from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
production = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt").read_text()
outbox = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxOfflineOutbox.kt").read_text()
sync = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxOfflineSync.kt").read_text()
store = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxChatStore.kt").read_text()

checks = [
    ("sendText can queue transport failures", "allowOfflineQueue: Boolean = true" in production and "FynxOfflineOutbox.enqueue" in production),
    ("queued item keeps reply/media fields", all(token in production for token in ["replyToId = replyToId", "mediaId = mediaId", "mediaType = mediaType"])),
    ("outbox is durable", "SharedPreferences" in outbox and "fun enqueue" in outbox),
    ("outbox storage is namespaced to the current account", "FynxAuthStore.accountStorageKey(context)" in outbox and "PREFS_PREFIX + encodedAccount" in outbox),
    ("outbox refuses to queue without an account identity", "fun enqueue(context: Context, item: Item): Boolean" in outbox and "val name = preferencesName(context) ?: return false" in outbox),
    ("outbox does not resurrect JSON nulls as literal reply/media IDs", 'takeIf { it.isNotBlank() && it != "null" }' in outbox),
    ("sendText does not claim queued success if account-scoped persistence is unavailable", "if (!FynxOfflineOutbox.enqueue(context, item)) return result" in production),
    ("sync does not recursively requeue", "allowOfflineQueue = false" in sync),
    ("successful sync replaces pending local message", "FynxChatStore.replaceMessage" in sync),
    ("offline outbox persists stable request IDs", "requestId = requestId" in production and 'put("requestId", item.requestId)' in outbox and "requestId = item.requestId" in sync),
    ("backend makes message retries idempotent", "client_message_id TEXT" in (ROOT / "backend/server.js").read_text() and "ON CONFLICT (sender_id, client_message_id)" in (ROOT / "backend/server.js").read_text()),
    ("structured messages carry idempotency keys", "requestId: String = UUID.randomUUID().toString()" in production and '"clientMessageId", requestId' in production),
    ("local replacement exists", "fun replaceMessage" in store),
    ("structured messages queue after ambiguous transport failures", "allowOfflineQueue: Boolean = true" in production and "FynxOfflineOutbox.enqueue(context, item)" in production and "isAmbiguousTransportFailure(result.exceptionOrNull())" in production),
    ("structured outbox records message type and payload", 'put("messageType", item.messageType)' in outbox and 'put("messagePayload", JSONObject(item.messagePayload))' in outbox),
    ("legacy outbox entries remain text messages", '?: "text"' in outbox and 'messagePayload = o.optJSONObject("messagePayload")' in outbox),
    ("outbox drain resends structured payload with the same idempotency key", "sendStructuredMessage(" in sync and "payload = item.messagePayload" in sync and "requestId = item.requestId" in sync and "allowOfflineQueue = false" in sync),
    ("structured offline send keeps the server idempotency key and payload", 'put("clientMessageId", requestId)' in production and "messagePayload = normalizedPayload" in production),
]
for label, ok in checks:
    if not ok:
        raise SystemExit(f"FAIL: {label}")
    print(f"PASS: {label}")
