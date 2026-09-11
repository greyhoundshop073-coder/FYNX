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
    ("body.type === \"message_ack\"", "message ACK packet guard"),
    ("body.type === \"read\"", "read packet guard"),
    ("MAX_READ_IDS = 100", "read batch bound"),
    ("READ_RATE_LIMIT = 120", "read rate limit"),
    ("ACK_RATE_LIMIT = 240", "ACK rate limit"),
    ("if (!validReadOrAckPacket(data, userId)) return;", "read/ACK enforcement before legacy listener"),
]:
    require(ISOLATION, needle, label)

print("R3 messaging consistency gate: GREEN")
