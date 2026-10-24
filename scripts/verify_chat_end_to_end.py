#!/usr/bin/env python3
"""Static integration gate for the production FYNX one-to-one Chat path."""
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
groups = read("app/src/main/java/com/fynx/app/ui/FynxGroupsPanel.kt")
realtime = read("app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt")
messaging = read("app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt")
offline_sync = read("app/src/main/java/com/fynx/app/ui/FynxOfflineSync.kt")
runtime_certification = read("scripts/verify_authenticated_runtime_navigation.py")
backend = read("backend/server.js")
isolation = read("backend/realtimeIsolationBootstrap.js")
compat = read("backend/chatRealtimeCompatibility.js")
preload = read("backend/renderScalabilityPreload.js")
package_json = read("backend/package.json")

# 0. Runtime crash regression: both conversation composables are created while
# entering their screens. PickMultipleVisualMedia rejects maxItems <= 1 at
# construction time, so a one-item gallery must use the single-item contract.
for panel, label in ((conversation, "Private Chat"), (groups, "Group Chat")):
    forbid(
        panel,
        r"PickMultipleVisualMedia\s*\(\s*maxItems\s*=\s*1\s*\)",
        f"{label} must not instantiate PickMultipleVisualMedia with maxItems=1",
    )
    require(
        panel,
        r"ActivityResultContracts\.PickVisualMedia\s*\(\s*\)",
        f"{label} must use the supported single-item photo/video picker contract",
    )

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

# 1b. Both group text-send entry points must remain pending until the server returns its
# canonical message. An optimistic bubble is not evidence of delivery or read receipt.
optimistic_group_send_states = re.findall(
    r"val optimistic = ChatMessage\(text\.trim\(\), true, UUID\.randomUUID\(\)\.toString\(\), delivered = (true|false), read = (true|false), replyToId = replyToId\)",
    groups,
)
if len(optimistic_group_send_states) != 2:
    fail("Group keyboard-send and composer-button callbacks must both be covered by the optimistic delivery-state regression gate")
elif any(delivered != "false" or read != "false" for delivered, read in optimistic_group_send_states):
    fail("Group text messages must stay pending/unread until the backend confirms delivery; optimistic UI must not claim delivery or read state")

# 1c. Runtime certification must exercise the real group voice-recording/send route,
# then confirm a newly persisted audio media ID/type across force-stop and reopening.
require(runtime_certification, r"def certify_group_voice_message\(group_id:str\)->None:", "Runtime certification must include real group voice-message capture")
require(runtime_certification, r"certify_group_voice_message\(group_id\)", "Runtime certification must invoke the group voice-message journey")
require(runtime_certification, r"Stop recording and send", "Group voice journey must stop/send through the actual composer control")
require(runtime_certification, r"attachmentType.*audio", "Group voice journey must verify the backend audio attachment type")
require(runtime_certification, r"force-stop/relaunch and reopening the same group", "Group voice journey must verify persistence after app restart")

# 1d. If no preconfigured peer secrets exist, the runtime may provision a dedicated
# run-scoped account only on the explicitly isolated HTTPS Railway testing host.
require(runtime_certification, r"def provision_isolated_runtime_peer\\(", "Runtime certification must have a dedicated isolated peer provisioning helper")
require(runtime_certification, r'parsed\\.scheme!="https"', "Automatic peer provisioning must require HTTPS")
require(runtime_certification, r'not host\\.endswith\\("\\.up\\.railway\\.app"\\)', "Automatic peer provisioning must be limited to Railway test hosts")
require(runtime_certification, r'"testing" not in host', "Automatic peer provisioning must explicitly reject non-testing hosts")
require(runtime_certification, r'base\\+"/api/auth/register"', "Automatic peer provisioning must use the existing backend registration endpoint")
peer_guard_at = runtime_certification.find('if parsed.scheme!="https" or not host.endswith(".up.railway.app") or "testing" not in host:')
peer_register_at = runtime_certification.find('base+"/api/auth/register"')
if not (0 <= peer_guard_at < peer_register_at):
    fail("Automatic peer provisioning must reject non-testing hosts before sending a registration request")

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

# 4. Attachment send must share the same completion/error path as text sends.
require(conversation, r"val sendResult = if \(selectedAttachment != null\)", "Attachment send must produce a shared send result")
require(conversation, r"uploadMedia\(context, selectedAttachment\)", "Attachment send must upload through production media")
require(conversation, r"sendResult\s*\.onSuccess", "Attachment send must update UI from the shared success path")
require(conversation, r"sendResult\s*\.onSuccess[\s\S]{0,3000}\.onFailure", "Attachment send must surface failures from the shared failure path")
require(conversation, r"attachment = null; attachmentType = null", "Successful attachment send must clear the pending attachment")

# 5. Voice contract: the client must enforce the backend's authoritative 120-second limit
# both at send time and at the recorder itself, so the UI cannot continue recording past it.
require(messaging, r"MAX_VOICE_DURATION_MS\s*=\s*120_000L", "Android messaging must retain the backend's 120-second voice limit")
require(messaging, r"voiceDurationMs !in 0L\.\.MAX_VOICE_DURATION_MS", "Android messaging must reject voice durations beyond the backend limit")
require(conversation, r"setMaxDuration\(120_000\)", "Voice recorder must enforce the 120-second maximum at capture time")
require(conversation, r"MEDIA_RECORDER_INFO_MAX_DURATION_REACHED", "Voice recorder must handle the maximum-duration callback")
require(conversation, r"stopRecordingAction\s*=\s*::stopRecording", "Voice maximum-duration callback must terminate through the production stop/send path")
require(conversation, r"voiceDurationMs = duration", "ConversationPanel must send the actual recorded voice duration to the production API")

# 5b. Offline send reconciliation: never discard the durable request mapping until the
# server-accepted message can replace its local pending bubble with a canonical server ID.
guard_at = offline_sync.find("if (currentUserId.isNullOrBlank()) {")
break_at = offline_sync.find("break", guard_at) if guard_at >= 0 else -1
replace_at = offline_sync.find("FynxChatStore.replaceMessage")
remove_at = offline_sync.find("FynxOfflineOutbox.remove")
if not (0 <= guard_at < break_at < replace_at < remove_at):
    fail("Offline outbox must retain a server-accepted message when current-user identity cannot be resolved; retry must keep its stable request ID")

# 6. Backend message data model and API path.
require(backend, r"CREATE TABLE IF NOT EXISTS messages", "Backend must own the production messages table")
for field in ("sender_id", "recipient_id", "text", "created_at", "delivered_at", "read_at"):
    require(backend, rf"\b{field}\b", f"messages table must retain {field}")
require(backend, r"/api/messages", "Backend must expose the production messages API")
require(backend, r"findUserByUsername", "Backend message routes must resolve real users")
require(backend, r"broadcastMessage", "Backend must broadcast persisted messages to realtime recipients")
require(backend, r"markPendingDelivered", "Backend must process pending delivery state")
require(backend, r"/realtime", "Backend must expose the realtime websocket path")

# 7. Realtime authentication/isolation must remain in front of the application handler.
require(isolation, r"jwt\.verify", "Realtime isolation must authenticate websocket users")
require(isolation, r"message_ack", "Realtime isolation must validate message acknowledgements")
require(isolation, r"read", "Realtime isolation must validate read packets")
require(isolation, r"MAX_READ_IDS", "Realtime isolation must retain bounded read packet protection")
require(isolation, r"packet|size|64", "Realtime isolation must retain a packet-size guard")

# 8. Typing compatibility layer: narrow, authenticated, recipient-scoped, rate-limited.
require(compat, r"jwt\.verify", "Typing compatibility must authenticate the websocket token")
require(compat, r"type === \"typing\"", "Typing compatibility must explicitly intercept typing packets")
require(compat, r"recipientId", "Typing compatibility must require a recipient")
require(compat, r"isTyping", "Typing compatibility must require a boolean typing state")
require(compat, r"recipientId === userId", "Typing compatibility must prevent self-targeting")
require(compat, r"TYPING_LIMIT\s*=\s*120", "Typing compatibility must retain a bounded rate limit")
require(compat, r"socketByUserId", "Typing compatibility must track authenticated recipient sockets")
require(compat, r"type: \"typing\"", "Typing compatibility must relay the canonical typing event")
require(compat, r"return callback\(data, \.\.\.args\)", "Typing compatibility must delegate non-typing websocket messages")
require(compat, r"return listener\(socket, req, \.\.\.rest\)", "Typing compatibility must preserve the existing connection listener")

# 9. Render startup must load the compatibility layer before the server bootstrap.
require(preload, r"import \"\.\/scalability\.js\"", "Render preload must retain scalability bootstrap")
require(preload, r"import \"\.\/chatRealtimeCompatibility\.js\"", "Render preload must load Chat realtime compatibility")
require(package_json, r"node --import \.\/renderScalabilityPreload\.js realtimeIsolationBootstrap\.js", "Render start command must use the guarded realtime preload chain")

# 10. Guard against accidental local/fake chat implementations in the production path.
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
print("- attachment send -> shared success/failure completion path present")
print("- voice capture -> recorder-level 120-second enforcement present")
print("- voice contract -> Android 120-second limit aligned with backend")
print("- Render preload -> scalability + Chat compatibility chain present")
print("- no hard-coded local backend path in the production Chat clients")
