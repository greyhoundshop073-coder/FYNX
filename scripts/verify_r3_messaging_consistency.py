from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BOOTSTRAP = (ROOT / "backend" / "serverBootstrap.js").read_text(encoding="utf-8")
ISOLATION = (ROOT / "backend" / "realtimeIsolationBootstrap.js").read_text(encoding="utf-8")

# serverBootstrap contains a JavaScript template literal that generates the
# runtime server. Its embedded packet strings are escaped in the source file.
# Normalize only those escape sequences before checking the actual invariants.
BOOTSTRAP_CHECK = BOOTSTRAP.replace('\\"', '"')


def require(text, needle, label):
    if needle not in text:
        raise SystemExit(f"R3 messaging consistency check failed: missing {label}: {needle}")

# HTTP + realtime messaging authorization invariants.
for needle, label in [
    ("const fynxRealtimeMessagingCanSend = async (senderId, targetId)", "shared message authorization helper"),
    ("fynxRealtimeMessagingBlocks(senderId, targetId)", "mutual block check"),
    ("messages_visibility", "message visibility policy"),
    ("f.status='ACCEPTED'", "accepted-friend policy"),
    ("const fynxRealtimeMessagingRead = async (userId, ids)", "read helper"),
    ("recipient_id=$2", "recipient-bound message state update"),
    ("const fynxRealtimeMessagingAck = async (userId, id)", "ACK helper"),
    ("fynxRealtimeMessagingAttach(socket, userId)", "authenticated realtime attachment"),
    ("body.type === \"typing\"", "typing packet handling"),
    ("body.type === \"message_ack\"", "message ACK handling"),
    ("body.type === \"read\"", "read packet handling"),
    ("conversation unavailable", "HTTP shared-authorization failure"),
    ("const fynxRealtimeMessagingMaxPacketBytes = 64 * 1024", "64 KB realtime packet guard"),
    ("if (!pool || !fynxRealtimeMessagingValidUserId(targetId) || targetId === senderId) return false;", "invalid/self target guard"),
]:
    require(BOOTSTRAP_CHECK, needle, label)

# SQL authorization must bind state changes to the authenticated recipient.
require(BOOTSTRAP_CHECK, "id=ANY($1::bigint[]) AND recipient_id=$2", "read receipts recipient binding")
require(BOOTSTRAP_CHECK, "id=$1 AND recipient_id=$2", "delivery ACK recipient binding")

# The outer isolation layer independently authenticates the socket and applies
# abuse protection before the legacy socket message listeners receive packets.
for needle, label in [
    ("const secret = process.env.JWT_SECRET || \"\";", "JWT secret loading"),
    ("const user = jwt.verify(token, secret);", "independent JWT verification"),
    ("currentSocketByUserId.set(userId, socket);", "per-user active socket isolation"),
    ("function validReadOrAckPacket(raw, userId)", "read/ACK packet validation"),
    ("const MAX_PACKET_BYTES = 64 * 1024;", "isolation packet size guard"),
    ("Buffer.byteLength(raw.toString(), \"utf8\") > MAX_PACKET_BYTES", "oversized packet rejection"),
    ("body.type === \"message_ack\"", "message ACK packet guard"),
    ("body.type === \"read\"", "read packet guard"),
    ("body.messageIds.length > MAX_READ_IDS", "read batch bound"),
    ("Number.isSafeInteger(id) || id <= 0", "invalid read ID rejection"),
    ("MAX_READ_IDS = 100", "read batch limit"),
    ("READ_RATE_LIMIT = 120", "read rate limit"),
    ("ACK_RATE_LIMIT = 240", "ACK rate limit"),
    ("if (!validReadOrAckPacket(data, userId)) return;", "read/ACK enforcement before legacy listener"),
]:
    require(ISOLATION, needle, label)

# Malformed JSON is deliberately passed to the existing authenticated handler,
# which already ignores malformed non-call realtime packets without killing the socket.
for needle, label in [
    ('try { body = JSON.parse(raw.toString()); } catch { return true; }', "malformed JSON compatibility"),
    ('if (!body || typeof body !== "object" || Array.isArray(body)) return true;', "non-object packet compatibility"),
]:
    require(ISOLATION, needle, label)

# Account/session isolation regression invariants: a replaced socket must be
# marked stale, prevented from sending/receiving, and unable to clear the newer
# active session when its delayed close event arrives.
for needle, label in [
    ('previous.__fynxStale = true;', "old socket stale marker"),
    ('previous.close(4001, "replaced realtime session")', "old socket replacement close"),
    ('socket.__fynxStale = false;', "new socket active marker"),
    ('if (currentSocketByUserId.get(userId) !== socket || socket.__fynxStale) return;', "stale socket send guard"),
    ('if (currentSocketByUserId.get(userId) !== socket || socket.__fynxStale || socket.readyState !== 1) return;', "stale socket message guard"),
    ('if (currentSocketByUserId.get(userId) === socket) currentSocketByUserId.delete(userId);', "old close cannot remove replacement session"),
]:
    require(ISOLATION, needle, label)

print("R3 messaging consistency gate: GREEN")
