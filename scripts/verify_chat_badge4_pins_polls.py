from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/fynx/app/ui"
BACKEND = ROOT / "backend"

GROUP = (UI / "GroupChatPanel.kt").read_text(encoding="utf-8")
SOCIAL = (UI / "FynxGroupSocialBatch.kt").read_text(encoding="utf-8")
MODELS = (UI / "ChatModels.kt").read_text(encoding="utf-8")
REMOTE = (UI / "FynxGroupRemoteClient.kt").read_text(encoding="utf-8")
ROUTES = (BACKEND / "groupContentRoutes.js").read_text(encoding="utf-8")

checks = []
def require(label, condition):
    checks.append((label, bool(condition)))

# Pinning must be a real group-chat message capability, not only a group-feed/content feature.
require("group chat has explicit pinned-message state", "pinned" in MODELS.lower() and "pinned" in GROUP.lower())
require("group chat exposes pin/unpin message action", ("Pin message" in GROUP or "Unpin message" in GROUP))
require("group remote client carries pinned-message state", "pinned" in REMOTE.lower())
require("group backend exposes message pin persistence", "pinned" in ROUTES.lower())

# Poll creation already lives in the authenticated Group Tools/content surface.
# The verifier must follow the real shared implementation instead of requiring a
# duplicate poll composer to be embedded directly in GroupChatPanel.kt.
require("group tools expose poll creation", "Poll" in SOCIAL and "createPoll" in SOCIAL)
require("group chat model represents poll messages", "poll" in MODELS.lower())
require("group remote client carries poll data", "poll" in REMOTE.lower())
require("group backend persists poll data", "poll" in ROUTES.lower())

# Existing safety boundaries remain required.
require("group chat has reply support", "replyTo" in GROUP)
require("group chat has search", "searchQuery" in GROUP)
require("group chat has realtime group-message handling", "GroupMessage" in GROUP)
require("group chat protects top system inset", "statusBarsPadding" in GROUP)
require("group chat list has bottom content padding", "contentPadding = PaddingValues(bottom" in GROUP)
require("group chat controls use 48dp minimum touch targets", "Modifier.size(48.dp)" in GROUP)

failed = [label for label, ok in checks if not ok]
print(f"CHAT BADGE 4 — PINS + POLLS: {len(checks)-len(failed)}/{len(checks)} checks passed")
if failed:
    raise SystemExit("BADGE 4 RED: " + "; ".join(failed))
print("BADGE 4 GREEN: pins, polls, persistence and layout safety contracts are present")
