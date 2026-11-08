from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        raise SystemExit(f"FAIL: required file missing: {relative}")
    return path.read_text(encoding="utf-8")


production = read("app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt")
outbox = read("app/src/main/java/com/fynx/app/ui/FynxOfflineOutbox.kt")
sync = read("app/src/main/java/com/fynx/app/ui/FynxOfflineSync.kt")
store = read("app/src/main/java/com/fynx/app/ui/FynxChatStore.kt")
conversation = read("app/src/main/java/com/fynx/app/ui/ConversationPanel.kt")
backend = read("backend/server.js")

structured_start = production.find("suspend fun sendStructuredMessage(")
structured_end = production.find("\n    suspend fun votePoll(", structured_start)
if structured_start < 0 or structured_end < 0:
    raise SystemExit("FAIL: structured-message send boundary is missing")
structured = production[structured_start:structured_end]

checks = [
    ("ordinary text/media sends retain the existing offline queue", "allowOfflineQueue: Boolean = true" in production and "FynxOfflineOutbox.enqueue" in production),
    ("ordinary outbox entries retain reply and media fields", all(token in production for token in ["replyToId = replyToId", "mediaId = mediaId", "mediaType = mediaType"])),
    ("outbox remains durable and account-scoped", "SharedPreferences" in outbox and "FynxAuthStore.accountStorageKey(context)" in outbox and "PREFS_PREFIX + encodedAccount" in outbox),
    ("outbox refuses to queue without an account identity", "val name = preferencesName(context) ?: return false" in outbox),
    ("legacy text outbox entries remain readable", 'o.optString("messageType").ifBlank { "text" }' in outbox),
    ("structured message type and payload are persisted", 'put("messageType", item.messageType)' in outbox and 'put("messagePayload", JSONObject(item.messagePayload))' in outbox),
    ("structured payload is restored from durable storage", 'o.optJSONObject("messagePayload")' in outbox and "messagePayload = payload" in outbox),
    ("structured sends have an opt-out used during retry", "allowOfflineQueue: Boolean = true" in structured and "!allowOfflineQueue" in structured),
    ("structured sends preserve a stable idempotency key", 'put("clientMessageId", requestId)' in structured and "requestId = requestId" in structured),
    ("structured transport failures are queued only when eligible", "isAmbiguousTransportFailure(result.exceptionOrNull())" in structured and "FynxOfflineOutbox.enqueue(context, item)" in structured),
    ("queued structured previews preserve type and payload", "messageType = normalizedType" in structured and "messagePayload = normalizedPayload" in structured),
    ("sync retries structured messages with original request ID", 'if (item.messageType == "text")' in sync and "sendStructuredMessage(" in sync and "requestId = item.requestId" in sync),
    ("sync never recursively queues failed retries", "allowOfflineQueue = false" in sync),
    ("successful sync replaces pending local message before removing queue item", sync.index("FynxChatStore.replaceMessage") < sync.index("FynxOfflineOutbox.remove(context, item.id)")),
    ("chat history persists structured payloads", 'put("messageType", message.messageType)' in store and 'put("messagePayload", JSONObject(message.messagePayload))' in store),
    ("backend permits location/contact/poll payloads without uploaded media", "messageType === 'text' && !text && !Number.isInteger(mediaId)" in backend),
    ("backend still requires uploaded media for sticker and GIF", "messageType === 'sticker' || messageType === 'gif'" in backend and "sticker and GIF messages require uploaded media" in backend),
    ("backend idempotency is enforced by client message ID", "client_message_id TEXT" in backend and "ON CONFLICT (sender_id, client_message_id)" in backend),
    ("location/contact/poll UI paths use structured transport", all(token in conversation for token in ['"location"', '"contact"', '"poll"', "sendStructuredMessage("])),
    ("structured media uploads before message delivery is queued", "uploadMedia(context, selectedAttachment)" in conversation and "sendStructuredMessage(" in conversation),
    ("pending media has a visible local placeholder", 'message.mediaId != null && message.attachmentUri == null' in conversation and "waiting to send" in conversation),
    ("pending polls cannot be voted before successful send", 'enabled = !message.id.startsWith("offline-")' in conversation),
]

for label, ok in checks:
    if not ok:
        raise SystemExit(f"FAIL: {label}")
    print(f"PASS: {label}")
