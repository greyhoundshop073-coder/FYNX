from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/fynx/app/ui"

PRIVATE = (UI / "ConversationPanel.kt").read_text(encoding="utf-8")
ACTIVE_GROUP = (UI / "FynxGroupsPanel.kt").read_text(encoding="utf-8")
APP = (UI / "FynxApp.kt").read_text(encoding="utf-8")
MODELS = (UI / "ChatModels.kt").read_text(encoding="utf-8")

checks = []
def require(label, condition):
    checks.append((label, bool(condition)))

# The route check prevents a green gate from certifying an unused legacy screen.
require("app routes group conversations to the active FynxGroupConversationPanel", "FynxGroupConversationPanel(groupId = openGroup!!" in APP)
require("active group conversation protects top system inset", "statusBarsPadding" in ACTIVE_GROUP)
require("active group conversation has bottom content padding", "contentPadding = PaddingValues(bottom" in ACTIVE_GROUP)
require("active group conversation uses 48dp minimum touch targets", "Modifier.size(48.dp)" in ACTIVE_GROUP)
require("active group conversation supports message search", "searchQuery" in ACTIVE_GROUP)
require("active group conversation supports reply targets", "replyToId" in ACTIVE_GROUP)
require("active group conversation loads authenticated remote history", "FynxGroupRemoteClient.loadMessages(context, groupId)" in ACTIVE_GROUP)
require("active group conversation handles realtime group-message events", "FynxRealtimeClient.Event.GroupMessage" in ACTIVE_GROUP and "realtime.connect()" in ACTIVE_GROUP)
require("active group realtime connection is cancelled and closed with the screen", "awaitCancellation()" in ACTIVE_GROUP and "realtime.close()" in ACTIVE_GROUP)
require("failed group messages remain visible instead of being silently deleted", "it.copy(delivered = false, read = false)" in ACTIVE_GROUP and "messages = messages.map" in ACTIVE_GROUP)
require("active group conversation exposes a retry action for unsent messages", 'TextButton(enabled = !sending, onClick = {' in ACTIVE_GROUP and 'Text("Retry")' in ACTIVE_GROUP)

# Shared private-chat and model contracts remain covered.
require("private chat protects top system inset", "statusBarsPadding" in PRIVATE)
require("message model supports audio attachments", "attachmentType: String?" in MODELS and "audio" in MODELS and "ChatMessage" in MODELS)

failed = [label for label, ok in checks if not ok]
print(f"CHAT/GROUP ACTIVE-SCREEN PARITY + LAYOUT: {len(checks)-len(failed)}/{len(checks)} checks passed")
if failed:
    raise SystemExit("GATE RED: " + "; ".join(failed))
print("GATE GREEN: active Chat/Group route, realtime, and layout contracts are present")
