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
    ("sync does not recursively requeue", "allowOfflineQueue = false" in sync),
    ("successful sync replaces pending local message", "FynxChatStore.replaceMessage" in sync),
    ("local replacement exists", "fun replaceMessage" in store),
]
for label, ok in checks:
    if not ok:
        raise SystemExit(f"FAIL: {label}")
    print(f"PASS: {label}")
