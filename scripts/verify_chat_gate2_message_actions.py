#!/usr/bin/env python3
"""Static certification gate for the existing FYNX Chat message-action path.

This gate verifies existing implementations rather than creating alternate ones.
It is intentionally source-level: the Android/APK journey remains covered by the
normal build/test pipeline and must be verified before Chat is certified.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
failures = []


def read(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        failures.append(f"missing required file: {path}")
        return ""
    return p.read_text(encoding="utf-8")


def require(text: str, pattern: str, label: str) -> None:
    if not re.search(pattern, text, re.MULTILINE):
        failures.append(label)

conversation = read("app/src/main/java/com/fynx/app/ui/ConversationPanel.kt")
messaging = read("app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt")
backend = read("backend/server.js")

# Existing client capabilities: verify before adding anything new.
for method in ("editMessage", "deleteMessage", "setReaction", "setPinned"):
    require(messaging, rf"suspend fun {method}\(", f"Production messaging must retain {method}()")

# Existing UI must actually invoke the message actions.
for method in ("editMessage", "deleteMessage", "setReaction", "setPinned"):
    require(conversation, rf"FynxProductionMessaging\.{method}\(", f"ConversationPanel must invoke {method}()")

# Reply state is already part of the production send contract.
require(messaging, r"replyToId: String\?", "Production messaging must retain replyToId")
require(messaging, r"put\(\"replyToId\"", "Production messaging must send replyToId to the backend")
require(conversation, r"replyToId", "ConversationPanel must retain reply state")

# Server-side authorization/data paths for action state.
require(backend, r"/api/messages/:id", "Backend must expose message action routes")
require(backend, r"/api/messages/:id/reaction", "Backend must expose the reaction route")
require(backend, r"/api/messages/:id/pin", "Backend must expose the pin route")
require(backend, r"deleted", "Backend must retain deleted-message state")
require(backend, r"edited", "Backend must retain edited-message state")
require(backend, r"reply_to_id|replyToId", "Backend must retain reply linkage")
require(backend, r"reaction", "Backend must persist reaction state")
require(backend, r"pinned", "Backend must persist pinned state")

# Guard against accidental fake/local action implementations.
if re.search(r"https?://(?:localhost|127\.0\.0\.1)", conversation):
    failures.append("ConversationPanel must not hard-code a local backend URL")
if re.search(r"https?://(?:localhost|127\.0\.0\.1)", messaging):
    failures.append("Production messaging must not hard-code a local backend URL")

if failures:
    print("CHAT GATE 2 MESSAGE ACTIONS: RED")
    for failure in failures:
        print(f"- {failure}")
    sys.exit(1)

print("CHAT GATE 2 MESSAGE ACTIONS: GREEN")
print("- existing edit/delete/reaction/pin client paths present")
print("- existing reply linkage present")
print("- existing backend action/state paths present")
print("- no local/fake Chat backend path detected")
