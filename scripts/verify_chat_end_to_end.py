#!/usr/bin/env python3
"""Static integration gate for the production FYNX one-to-one Chat path.

This verifier deliberately checks the existing architecture rather than replacing it:
Android conversation UI -> realtime client -> production messaging client -> backend
message routes/database -> authenticated realtime server -> Render preload layers.

It is a source/integration gate, not a fake runtime test and does not invent users or
messages. A failure means the inspected production wiring is incomplete or inconsistent.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        fail(f"missing required file: {path}")
    return p.read_text(encoding="utf-8")


def require(text: str, pattern: str, label: str, flags: int = 0) -> None:
    if not re.search(pattern, text, flags):
        fail(label)


def forbid(text: str, pattern: str, label: str, flags: int = 0) -> None:
    if re.search(pattern, text, flags):
        fail(label)


def fail(message: str) -> None:
    failures.append(message)


failures: list[str] = []

conversation = read("app/src/main/java/com/fynx/app/ui/ConversationPanel.kt")
realtime = read("app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt")
messaging = read("app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt")
backend = read("backend/server.js")
isolation = read("backend/realtimeIsolationBootstrap.js")
compat = read("backend/chatRealtimeCompatibility.js")
preload = read("backend/renderScalabilityPreload.js")
package_json = read("backend/package.json")

# 1. Android conversation entry and authenticated identity resolution.
require(conversation, r"FynxBackendClient\.currentUserId\(context\)", "ConversationPanel must resolve the authenticated current user")
require(conversation, r"FynxProductionMessaging\.history\(context,", "ConversationPanel must load production conversation history")
require(conversation, r"realtimeClient\.connect\(\)", "ConversationPanel must connect its realtime transport")
require(conversation, r"realtimeClient\.close\(\)", "ConversationPanel must close realtime transport on disposal")
require(conversation, r"FynxProductionMessaging\.sendText\(context,", "ConversationPanel must send through production messaging")
require(conversation, r"FynxProductionMessaging\.markRead\(context,", "ConversationPanel must persist read state through production messaging")
require(conversation, r"realtimeClient\.acknowledgeMessage\(", "ConversationPanel must acknowledge received messages")
require(conversation, r"realtimeClient\.sendRead\(", "ConversationPanel must send realtime read acknowledgements")
require(conversation, r"realtimeClient\.sendTyping\(", "ConversationPanel must use the realtime typing transport")

# 2. Realtime client contract: authenticated websocket, message/status/typing handling.
require(realtime, r"/realtime", "FynxRealtimeClient must target the backend realtime endpoint")
require(realtime, r"token", "FynxRealtimeClient must authenticate realtime sessions with a token")
require(realtime, r"typing", "FynxRealtimeClient must support typing events")
require(realtime, r"message_ack", "FynxRealtimeClient must support message acknowledgements")
require(realtime, r"read", "FynxRealtimeClient must support read events")
require(realtime, r"Event\.Typing", "FynxRealtimeClient must expose typing events to the UI")
require(realtime, r"Event\.MessageStatus", "FynxRealtimeClient must expose message status events")

# 3. Production messaging API must keep history/send/read/media on the real backend.
require(messaging, r"/api/messages", "Production messaging client must use the backend messages API")
require(messaging, r"history\(", "Production messaging client must implement history")
require(messaging, r"sendText\(", "Production messaging client must implement text/media send")
require(messaging, r"markRead\(", "Production messaging client must implement persistent read state")
require(messaging, r"uploadMedia\(", "Production messaging client must preserve media upload support")
require(messaging, r"cacheRemoteMedia\(", "Production messaging client must preserve remote media caching")

# 4. Backend message data model and API path.
require(backend, r"CREATE TABLE IF NOT EXISTS messages", "Backend must own the production messages table")
for field in ("sender_id", "recipient_id", "text", "created_at", "delivered_at", "read_at"):
    require(backend, rf"\b{field}\b", f"messages table must retain {field}")
require(backend, r"/api/messages", "Backend must expose the production messages API")
require(backend, r"findUserByUsername", "Backend message routes must resolve real users")
require(backend, r"broadcastMessage", "Backend must broadcast persisted messages to realtime recipients")
require(backend, r"markPendingDelivered", "Backend must process pending delivery state")
require(backend, r"/realtime", "Backend must expose the realtime websocket path")

# 5. Realtime authentication/isolation must remain in front of the application handler.
require(isolation, r"jwt\.verify", "Realtime isolation must authenticate websocket users")
require(isolation, r"message_ack", "Realtime isolation must validate message acknowledgements")
require(isolation, r"read", "Realtime isolation must validate read packets")
require(isolation, r"MAX_READ_IDS", "Realtime isolation must retain bounded read packet protection")
require(isolation, r"packet|size|64", "Realtime isolation must retain a packet-size guard")

# 6. Typing compatibility layer: narrow, authenticated, recipient-scoped, rate-limited.
require(compat, r"jwt\.verify", "Typing compatibility must authenticate the websocket token")
require(compat, r"type === \"typing\"", "Typing compatibility must explicitly intercept typing packets")
require(compat, r"recipientId", "Typing compatibility must require a recipient")
require(compat, r"isTyping", "Typing compatibility must require a boolean typing state")
require(compat, r"recipientId === userId", "Typing compatibility must prevent self-targeting")
require(compat, r"TYPING_LIMIT\s*=\s*120", "Typing compatibility must retain a bounded rate limit")
require(compat, r"socketByUserId", "Typing compatibility must track authenticated recipient sockets")
require(compat, r"type: \"typing\"", "Typing compatibility must relay the canonical typing event")

# The compatibility layer must delegate non-typing traffic instead of becoming a second
# message transport. This is the key preservation rule for the existing implementation.
require(compat, r"return callback\(data, \.\.\.args\)", "Typing compatibility must delegate non-typing websocket messages")
require(compat, r"return listener\(socket, req, \.\.\.rest\)", "Typing compatibility must preserve the existing connection listener")

# 7. Render startup must load the compatibility layer before the server bootstrap.
require(preload, r"import \"\.\/scalability\.js\"", "Render preload must retain scalability bootstrap")
require(preload, r"import \"\.\/chatRealtimeCompatibility\.js\"", "Render preload must load Chat realtime compatibility")
require(package_json, r"node --import \.\/renderScalabilityPreload\.js realtimeIsolationBootstrap\.js", "Render start command must use the guarded realtime preload chain")

# 8. Guard against accidental local/fake chat implementations in the production path.
forbidden = [
    (conversation, r"https?://(?:localhost|127\.0\.0\.1)", "ConversationPanel must not hard-code a local backend URL"),
    (messaging, r"https?://(?:localhost|127\.0\.0\.1)", "Production messaging client must not hard-code a local backend URL"),
    (compat, r"process\.env\.[A-Z0-9_]+\s*=", "Realtime compatibility must not mutate environment secrets"),
]
for text, pattern, label in forbidden:
    forbid(text, pattern, label)

if failures:
    print("CHAT END-TO-END VERIFICATION: RED")
    for failure in failures:
        print(f"- {failure}")
    sys.exit(1)

print("CHAT END-TO-END VERIFICATION: GREEN")
print("- Android ConversationPanel -> realtime + production messaging wiring present")
print("- authenticated realtime -> typing/read/ack compatibility present")
print("- backend messages -> persistence/delivery/broadcast wiring present")
print("- Render preload -> scalability + Chat compatibility chain present")
print("- no hard-coded local backend path in the production Chat clients")
