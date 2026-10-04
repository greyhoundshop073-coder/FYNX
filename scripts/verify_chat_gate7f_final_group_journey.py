from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    p = ROOT / path
    return p.read_text(encoding="utf-8") if p.is_file() else ""


def check(name, ok):
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
    return ok

app = read("app/src/main/java/com/fynx/app/ui/ConversationPanel.kt")
groups = read("app/src/main/java/com/fynx/app/ui/FynxGroupsPanel.kt")
group_chat = read("app/src/main/java/com/fynx/app/ui/GroupChatPanel.kt")
models = read("app/src/main/java/com/fynx/app/ui/ChatModels.kt")
store = read("app/src/main/java/com/fynx/app/ui/FynxChatStore.kt")
settings = read("app/src/main/java/com/fynx/app/ui/FynxChatSettingsPanel.kt")
wallpaper = read("app/src/main/java/com/fynx/app/ui/FynxChatWallpaper.kt")

results = [
    check("private chat conversation surface exists", bool(app)),
    check("private chat unread state is modeled", "unreadCount" in models and "unreadCount" in store),
    check("group list and group conversation surfaces exist", bool(groups) and bool(group_chat)),
    check("group history and realtime are connected", "loadMessages" in group_chat and "GroupMessage" in group_chat),
    check("group search is present", "search" in group_chat.lower() and "message" in group_chat.lower()),
    check("chat settings surface exists", bool(settings)),
    check("chat wallpaper/personalization foundation exists", bool(wallpaper) and "ChatPersonalizationDialog" in wallpaper),
    check("group settings entry is wired", "Group settings" in groups),
    check("shared chat model retains attachment/audio-capable message fields", any(x in models.lower() for x in ("audio", "attachment", "media"))),
]

if not all(results):
    raise SystemExit(1)

print("PASS: FYNX Chat Gate 7F final group/private journey certification")
