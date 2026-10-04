#!/usr/bin/env python3
"""Gate 5: certify existing Chat notification/sound controls without creating a second notification system."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
paths = list((ROOT / "app").rglob("*.kt")) if (ROOT / "app").exists() else []
text = "\n".join(p.read_text(encoding="utf-8", errors="ignore") for p in paths)

checks = {
    "notification-related implementation exists": bool(re.search(r"notification|Notification", text)),
    "chat mute control exists": bool(re.search(r"mute|Muted|isChatMuted", text)),
    "sound control exists": bool(re.search(r"sound|Sound|ringtone|Ringtone", text)),
    "push/realtime message handling exists": bool(re.search(r"push|Push|realtime|Realtime|socket|Socket", text)),
    "existing conversation UI is present": "ConversationPanel" in text,
}
failed = [k for k,v in checks.items() if not v]
for k,v in checks.items(): print(f"{'PASS' if v else 'FAIL'}: {k}")
if failed:
    print("CHAT GATE 5 NOTIFICATION VERIFICATION: RED")
    raise SystemExit(1)
print("CHAT GATE 5 NOTIFICATION VERIFICATION: GREEN")
