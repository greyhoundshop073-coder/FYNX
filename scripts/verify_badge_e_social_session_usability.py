#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/fynx/app/ui"

def read(name):
    p = UI / name
    if not p.is_file():
        raise SystemExit(f"BADGE E RED: missing {name}")
    return p.read_text(encoding="utf-8", errors="replace")

home = read("FynxRemoteHomeSocialPanel.kt")
app = read("FynxApp.kt")
conversation = read("FynxConversationPanel.kt")
group = read("FynxGroupChatPanel.kt")
profile = read("ProfilePanel.kt")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8", errors="replace")

checks = {
    "Home social surface exists": "LazyColumn" in home,
    "Home reaction/share actions are present": any(x in home for x in ["Like", "React", "Share"]),
    "Conversation has message composer": "TextField" in conversation or "BasicTextField" in conversation,
    "Conversation has send action": "Send" in conversation,
    "Group chat has message composer": "TextField" in group or "BasicTextField" in group,
    "Group chat has send action": "Send" in group,
    "Profile surface is wired": "Profile" in profile,
    "App contains chat entry": "Chat" in app or "Conversation" in app,
    "Production journey audit remains enabled": "verify_fynx_journey.py" in workflow,
    "Large product completeness gate remains enabled": "verify_large_badge11_product_completeness.py" in workflow,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("BADGE E RED: " + "; ".join(failed))
print("BADGE E GREEN: social-session usability source audit passed")
