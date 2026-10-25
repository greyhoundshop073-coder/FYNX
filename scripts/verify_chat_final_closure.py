from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/fynx/app/ui"

required = {
    "private conversation": APP / "ConversationPanel.kt",
    "group conversation": APP / "GroupChatPanel.kt",
    "chat settings": APP / "FynxChatSettingsPanel.kt",
    "group settings": APP / "FynxGroupsPanel.kt",
    "chat wallpaper": APP / "FynxChatWallpaper.kt",
    "chat models": APP / "ChatModels.kt",
    "chat store": APP / "FynxChatStore.kt",
}

missing = [name for name, path in required.items() if not path.exists()]
if missing:
    raise SystemExit("CHAT CLOSURE RED: missing required surfaces: " + ", ".join(missing))

checks = [
    ("private conversation", "ConversationPanel.kt", "FynxProductionMessaging"),
    ("private conversation persistence", "ConversationPanel.kt", "FynxChatStore"),
    ("group history", "GroupChatPanel.kt", "loadMessages"),
    ("group realtime", "GroupChatPanel.kt", "GroupMessage"),
    ("group search", "GroupChatPanel.kt", "searchQuery"),
    # The production panel uses the title-cased UI label "Chat Settings".
    ("chat settings", "FynxChatSettingsPanel.kt", "Chat Settings"),
    ("wallpaper", "FynxChatWallpaper.kt", "FynxChatWallpaperBackground"),
    ("unread model", "ChatModels.kt", "unreadCount"),
    ("persistent chat state", "FynxChatStore.kt", "unreadCount"),
]

for label, filename, needle in checks:
    text = (APP / filename).read_text(encoding="utf-8")
    if needle not in text:
        raise SystemExit(f"CHAT CLOSURE RED: {label} missing expected implementation marker: {needle}")

# User-facing recovery surfaces must not leak raw backend/exception messages.
for label, filename in [("private conversation", "ConversationPanel.kt"), ("group conversation", "FynxGroupsPanel.kt")]:
    source = (APP / filename).read_text(encoding="utf-8")
    if "error.message" in source or "it.message" in source:
        raise SystemExit(f"CHAT CLOSURE RED: {label} exposes raw exception text")

print("CHAT CLOSURE GREEN: required private/group chat surfaces, foundations, and friendly recovery errors are present")
