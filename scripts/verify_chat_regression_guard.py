from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/fynx/app/ui"

required = [
    "ConversationPanel.kt",
    "GroupChatPanel.kt",
    "FynxChatSettingsPanel.kt",
    "FynxChatWallpaper.kt",
    "ChatModels.kt",
    "FynxChatStore.kt",
]

for name in required:
    if not (APP / name).exists():
        raise SystemExit(f"CHAT REGRESSION RED: required existing surface missing: {name}")

conversation = (APP / "ConversationPanel.kt").read_text(encoding="utf-8")
group = (APP / "GroupChatPanel.kt").read_text(encoding="utf-8")
models = (APP / "ChatModels.kt").read_text(encoding="utf-8")
store = (APP / "FynxChatStore.kt").read_text(encoding="utf-8")

contracts = {
    "private messaging": (conversation, ("FynxProductionMessaging", "FynxChatStore")),
    "group history": (group, ("loadMessages",)),
    "group realtime": (group, ("GroupMessage",)),
    "group search": (group, ("searchQuery",)),
    "unread model": (models, ("unreadCount",)),
    "unread persistence": (store, ("unreadCount",)),
}

for label, (source, needles) in contracts.items():
    for needle in needles:
        if needle not in source:
            raise SystemExit(f"CHAT REGRESSION RED: {label} contract missing: {needle}")

print("CHAT REGRESSION GREEN: completed Chat/Group foundations remain present")
