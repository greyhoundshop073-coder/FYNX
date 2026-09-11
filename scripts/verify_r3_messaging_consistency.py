from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BOOTSTRAP = (ROOT / "backend" / "serverBootstrap.js").read_text(encoding="utf-8")
ISOLATION = (ROOT / "backend" / "realtimeIsolationBootstrap.js").read_text(encoding="utf-8")

# R3 deliberately has two realtime layers:
# - serverBootstrap owns authenticated messaging policy and typing/message delivery.
# - realtimeIsolationBootstrap owns stale-session isolation plus read/ACK abuse guards.
# The old gate incorrectly assumed every packet handler lived in serverBootstrap.

def require(text, needle, label):
    if needle not in text:
        raise SystemExit(f"R3 messaging consistency check failed: missing {label}: {needle}")

# HTTP + messaging authorization invariants.
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
    ("conversation unavailable", "HTTP shared-authorization failure"),
    ("const fynxRealtimeMessagingMaxPacketBytes = 64 * 1024", "64 KB realtime packet guard"),
    ("if (!pool || !fynxRealtimeMessagingValidUserId(targetId) || targetId === senderId) return false;", "invalid/self target guard"),
]:
    require(BOOTSTRAP, needle, label)

# SQL authorization must bind state changes to the authenticated recipient.
require(BOOTSTRAP, "id=ANY($1::bigint[]) AND recipient_id=$2", "read receipts recipient binding")
require(BOOTSTRAP, "id=$1 AND recipient_id=$2", "delivery ACK recipient binding")

# Read/ACK traffic is intentionally intercepted by the outer isolation layer.
# This is not a weaker path: it runs before the legacy socket message listeners,
# and its identity is independently derived from a verified JWT.
for needle, label in [
    ("const user = jwt.verify(token, JWT_SECRET);", "independent JWT verification"),
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
