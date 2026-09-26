#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/fynx/app/ui"

def read(name):
    p = UI / name
    if not p.is_file():
        raise SystemExit(f"BADGE C RED: missing {name}")
    return p.read_text(encoding="utf-8", errors="replace")

files = list(UI.glob("*.kt"))
all_ui = "
".join(p.read_text(encoding="utf-8", errors="replace") for p in files)

required = [
    "FynxApp.kt", "FynxRemoteHomeSocialPanel.kt", "FynxHomeSocialHubPanel.kt",
    "FynxConversationPanel.kt", "FynxGroupChatPanel.kt",
]
for name in required:
    read(name)

checks = {
    "Home feed has automatic scrolling/list state": "LazyColumn" in read("FynxRemoteHomeSocialPanel.kt"),
    "Home feed avoids obsolete manual refresh control": 'Icon(Icons.Default.Refresh, "Refresh feed")' not in read("FynxRemoteHomeSocialPanel.kt"),
    "Home feed has duplicate-safe pagination": "existingIds" in read("FynxRemoteHomeSocialPanel.kt") or "distinctBy" in read("FynxRemoteHomeSocialPanel.kt"),
    "Conversation exposes send action": "Send" in read("FynxConversationPanel.kt"),
    "Group chat exposes send action": "Send" in read("FynxGroupChatPanel.kt"),
    "App owns navigation surface": "Navigation" in read("FynxApp.kt"),
    "No obvious fully transparent click target": not bool(re.search(r'alpha\s*=\s*0(?:\.0+)?[^\n]{0,120}(?:clickable|Button|IconButton)', all_ui)),
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("BADGE C RED: " + "; ".join(failed))
print("BADGE C GREEN: overlap/invisible-touch source audit passed")
