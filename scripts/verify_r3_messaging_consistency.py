from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BOOTSTRAP = (ROOT / "backend" / "serverBootstrap.js").read_text(encoding="utf-8")
ISOLATION = (ROOT / "backend" / "realtimeIsolationBootstrap.js").read_text(encoding="utf-8")

REQUIRED_BOOTSTRAP = [
    "const fynxRealtimeMessagingCanSend = async (senderId, targetId)",
    "fynxRealtimeMessagingBlocks(senderId, targetId)",
    "messages_visibility",
    "f.status='ACCEPTED'",
    "const fynxRealtimeMessagingRead = async (userId, ids)",
    "recipient_id=$2",
    "const fynxRealtimeMessagingAck = async (userId, id)",
    "AND recipient_id=$2",
    "fynxRealtimeMessagingAttach(socket, userId)",
    "body.type === \"message_ack\"",
    "body.type === \"read\"",
    "body.type === \"typing\"",
]

for needle in REQUIRED_BOOTSTRAP:
    if needle not in BOOTSTRAP:
        raise SystemExit(f"R3 messaging consistency check failed: missing {needle}")

if "conversation unavailable" not in BOOTSTRAP:
    raise SystemExit("R3 messaging consistency check failed: HTTP send path lacks shared authorization failure")

if "id=ANY($1::bigint[]) AND recipient_id=$2" not in BOOTSTRAP:
    raise SystemExit("R3 messaging consistency check failed: read receipts are not recipient-bound")

if "id=$1 AND recipient_id=$2" not in BOOTSTRAP:
    raise SystemExit("R3 messaging consistency check failed: delivery ACK is not recipient-bound")

if "const fynxRealtimeMessagingMaxPacketBytes = 64 * 1024" not in BOOTSTRAP:
    raise SystemExit("R3 messaging consistency check failed: realtime packet size guard missing")

if "if (!pool || !fynxRealtimeMessagingValidUserId(targetId) || targetId === senderId) return false;" not in BOOTSTRAP:
    raise SystemExit("R3 messaging consistency check failed: self/invalid target guard missing")

# The outer isolation layer must authenticate before the per-user realtime bridge
# is allowed to establish account-specific behavior.
if "const user = jwt.verify(token, JWT_SECRET);" not in ISOLATION:
    raise SystemExit("R3 messaging consistency check failed: isolation layer lacks JWT verification")
if "currentSocketByUserId.set(userId, socket);" not in ISOLATION:
    raise SystemExit("R3 messaging consistency check failed: authenticated socket is not isolated by user")

print("R3 messaging consistency gate: GREEN")
