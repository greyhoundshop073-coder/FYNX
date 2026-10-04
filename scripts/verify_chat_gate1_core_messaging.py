#!/usr/bin/env python3
"""Gate 1 certification gate for FYNX Chat core messaging.

This verifier deliberately reuses the existing production Chat end-to-end
verifier instead of creating a second messaging implementation. It adds only
the Gate 1 acceptance checks needed to certify the core lifecycle.
"""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []


def read(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        failures.append(f"missing required file: {path}")
        return ""
    return p.read_text(encoding="utf-8")


def require(text: str, pattern: str, label: str) -> None:
    if not re.search(pattern, text, re.MULTILINE):
        failures.append(label)


def run_existing_gate() -> None:
    """Run the canonical existing Chat integration gate; do not duplicate it."""
    verifier = ROOT / "scripts/verify_chat_end_to_end.py"
    if not verifier.is_file():
        failures.append("canonical verify_chat_end_to_end.py is missing")
        return
    result = subprocess.run([sys.executable, str(verifier)], cwd=ROOT, text=True, capture_output=True)
    if result.returncode != 0:
        failures.append("canonical Chat end-to-end verifier is not GREEN")
        if result.stdout.strip():
            print(result.stdout.strip())
        if result.stderr.strip():
            print(result.stderr.strip())


conversation = read("app/src/main/java/com/fynx/app/ui/ConversationPanel.kt")
messaging = read("app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt")
realtime = read("app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt")
store = read("app/src/main/java/com/fynx/app/ui/FynxChatStore.kt")

# Core lifecycle: reuse the production message state and transport already in Chat.
require(messaging, r"sendText\(", "production messaging must expose the text-send path")
require(messaging, r"history\(", "production messaging must expose the history path")
require(messaging, r"markRead\(", "production messaging must expose the persistent read path")
require(realtime, r"message_ack", "realtime transport must retain message acknowledgements")
require(realtime, r"MessageStatus", "realtime transport must expose message status events")
require(conversation, r"FynxProductionMessaging\.sendText\(", "ConversationPanel must use production messaging for text sends")
require(conversation, r"FynxProductionMessaging\.history\(", "ConversationPanel must restore production history")
require(conversation, r"FynxChatStore\.save\(", "ConversationPanel must persist conversation state locally")
require(store, r"load\(", "Chat store must provide local conversation restoration")
require(store, r"save\(", "Chat store must provide local conversation persistence")

# Reconciliation/duplicate protection: require the existing durable merge path,
# rather than introducing another queue or message repository.
require(messaging, r"distinctBy\s*\{\s*it\.id\s*\}", "production messaging must deduplicate reconciled messages by id")
require(messaging, r"sortedBy\s*\{\s*it\.timestamp\s*\}", "production messaging must restore deterministic message ordering")
require(conversation, r"FynxChatStore\.load\(", "conversation must start from local state before remote reconciliation")

run_existing_gate()

if failures:
    print("CHAT GATE 1 — CORE MESSAGING: RED")
    for failure in failures:
        print(f"- {failure}")
    raise SystemExit(1)

print("CHAT GATE 1 — CORE MESSAGING: GREEN")
print("- canonical Chat end-to-end verifier passed")
print("- production send/history/read lifecycle is wired")
print("- realtime acknowledgement/status path is wired")
print("- local persistence/restart path is wired")
print("- reconciliation deduplicates by message id and preserves ordering")
