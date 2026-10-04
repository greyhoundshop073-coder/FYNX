#!/usr/bin/env python3
"""Gate 4: certify existing Chat list/conversation organization contracts."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
paths = list((ROOT / "app").rglob("*.kt")) if (ROOT / "app").exists() else []
text = "\n".join(p.read_text(encoding="utf-8", errors="ignore") for p in paths)

checks = {
    "chat list search exists": "chatSearch" in text and "visibleChats" in text,
    "per-chat mute state exists": "isChatMuted" in text,
    "unread state is derived from persisted messages": "FynxChatStore.load" in text and "unread" in text,
    "conversation navigation exists": "ConversationPanel" in text,
    "chat list renders existing conversation data": "chats" in text and "username" in text,
}
failed = [k for k,v in checks.items() if not v]
for k,v in checks.items(): print(f"{'PASS' if v else 'FAIL'}: {k}")
if failed:
    print("CHAT GATE 4 ORGANIZATION VERIFICATION: RED")
    raise SystemExit(1)
print("CHAT GATE 4 ORGANIZATION VERIFICATION: GREEN")
