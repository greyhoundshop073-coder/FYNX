from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/fynx/app/ui"

PRIVATE = (UI / "ConversationPanel.kt").read_text(encoding="utf-8")
GROUP = (UI / "GroupChatPanel.kt").read_text(encoding="utf-8")
MODELS = (UI / "ChatModels.kt").read_text(encoding="utf-8")

checks = []
def require(label, condition):
    checks.append((label, bool(condition)))

# Existing user-facing parity foundations.
require("private chat message model supports audio attachments", '"audio"' in MODELS and "attachmentType" in MODELS)
require("group chat has document attachment flow", "documentPicker" in GROUP)
require("group chat has voice recording flow", "MediaRecorder" in GROUP and "voice" in GROUP.lower())
require("group chat has search", "searchQuery" in GROUP)
require("group chat has replies", "replyTo" in GROUP)
require("group chat has realtime group-message handling", "GroupMessage" in GROUP)

# Layout safety: Android edge-to-edge requires critical content to avoid system bars.
require("private chat protects top system inset", "statusBarsPadding" in PRIVATE)
require("group chat protects top system inset", "statusBarsPadding" in GROUP)
require("group chat list has bottom content padding", "contentPadding = PaddingValues(bottom" in GROUP)
require("group chat interactive controls use 48dp minimum touch targets", "Modifier.size(48.dp)" in GROUP)

# Do not silently certify music-file parity until the picker is actually wired.
require("group chat exposes a phone audio/music picker", "audio/*" in GROUP)

failed = [label for label, ok in checks if not ok]
print(f"CHAT/GROUP PARITY + LAYOUT: {len(checks)-len(failed)}/{len(checks)} checks passed")
if failed:
    raise SystemExit("GATE RED: " + "; ".join(failed))
print("GATE GREEN: parity and layout safety contracts are present")
