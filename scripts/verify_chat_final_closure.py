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
    ("private conversation", "ConversationPanel.kt", "sendMessage"),
    ("group history", "GroupChatPanel.kt", "loadMessages"),
    ("group realtime", "GroupChatPanel.kt", "GroupMessage"),
    ("group search", "GroupChatPanel.kt", "searchQuery"),
    ("chat settings", "FynxChatSettingsPanel.kt", "Chat settings"),
    ("wallpaper", "FynxChatWallpaper.kt", "FynxChatWallpaperBackground"),
    ("unread model", "ChatModels.kt", "unreadCount"),
    ("persistent chat state", "FynxChatStore.kt", "unreadCount"),
]

for label, filename, needle in checks:
    text = (APP / filename).read_text(encoding="utf-8")
    if needle not in text:
        raise SystemExit(f"CHAT CLOSURE RED: {label} missing expected implementation marker: {needle}")

print("CHAT CLOSURE GREEN: required private/group chat surfaces and foundations are present")
