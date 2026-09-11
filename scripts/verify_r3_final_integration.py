from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BOOTSTRAP = (ROOT / "backend" / "serverBootstrap.js").read_text(encoding="utf-8")
ISOLATION = (ROOT / "backend" / "realtimeIsolationBootstrap.js").read_text(encoding="utf-8")
SERVER = (ROOT / "backend" / "server.js").read_text(encoding="utf-8")

# serverBootstrap embeds generated JavaScript in a template literal. Normalize
# escaped quotes so this gate checks the generated runtime invariants themselves.
BOOTSTRAP_CHECK = BOOTSTRAP.replace('\\"', '"')


def require(text, needle, label):
    if needle not in text:
        raise SystemExit(f"R3 final integration check failed: missing {label}: {needle}")

# Authentication and startup-chain invariants.
for needle, label in [
    ('const JWT_SECRET = process.env.JWT_SECRET || "";', "server JWT secret source"),
    ('if (!token || !JWT_SECRET) return socket.close(1008, "authentication required");', "WebSocket authentication requirement"),
    ('const user = jwt.verify(token, JWT_SECRET);', "server JWT verification"),
    ('const userId = String(user.sub);', "authenticated WebSocket user identity"),
    ('await import("./serverBootstrap.js");', "production bootstrap import"),
]:
    require(SERVER if "server" in label.lower() and "bootstrap import" not in label else (ISOLATION if "bootstrap import" in label else SERVER), needle, label)

# HTTP/realtime authorization must use the same privacy and block rules.
for needle, label in [
    ('const fynxRealtimeMessagingCanSend = async (senderId, targetId)', "shared message authorization helper"),
    ('fynxRealtimeMessagingBlocks(senderId, targetId)', "mutual block enforcement"),
    ('messages_visibility', "message visibility policy"),
    ("f.status='ACCEPTED'", "accepted friendship requirement"),
    ('conversation unavailable', "HTTP authorization failure"),
    ('fynxRealtimeMessagingAttach(socket, userId)', "authenticated realtime attachment"),
    ('body.type === "typing"', "typing handling"),
    ('body.type === "message_ack"', "ACK handling"),
    ('body.type === "read"', "read handling"),
    ('const fynxRealtimeMessagingMaxPacketBytes = 64 * 1024', "64 KB messaging packet limit"),
    ('if (!pool || !fynxRealtimeMessagingValidUserId(targetId) || targetId === senderId) return false;', "invalid/self target guard"),
    ('id=ANY($1::bigint[]) AND recipient_id=$2', "read recipient binding"),
    ('id=$1 AND recipient_id=$2', "ACK recipient binding"),
]:
    require(BOOTSTRAP_CHECK, needle, label)

# Outer isolation layer independently verifies the JWT and protects the active
# account session before legacy listeners can process realtime traffic.
for needle, label in [
    ('const secret = process.env.JWT_SECRET || "";', "isolation JWT secret source"),
    ('const user = jwt.verify(token, secret);', "independent isolation JWT verification"),
    ('currentSocketByUserId.set(userId, socket);', "per-user active socket mapping"),
    ('previous.__fynxStale = true;', "stale replacement marker"),
    ('previous.close(4001, "replaced realtime session")', "stale replacement close"),
    ('socket.__fynxStale = false;', "new socket active marker"),
    ('if (currentSocketByUserId.get(userId) !== socket || socket.__fynxStale) return;', "stale send guard"),
    ('if (currentSocketByUserId.get(userId) !== socket || socket.__fynxStale || socket.readyState !== 1) return;', "stale receive guard"),
    ('if (currentSocketByUserId.get(userId) === socket) currentSocketByUserId.delete(userId);', "safe close cleanup"),
]:
    require(ISOLATION, needle, label)

# Abuse/malformed-input protections must remain enforced before legacy handlers.
for needle, label in [
    ('function validReadOrAckPacket(raw, userId)', "read/ACK validator"),
    ('const MAX_PACKET_BYTES = 64 * 1024;', "isolation packet size limit"),
    ('Buffer.byteLength(raw.toString(), "utf8") > MAX_PACKET_BYTES', "oversized packet rejection"),
    ('MAX_READ_IDS = 100', "read batch limit"),
    ('READ_RATE_LIMIT = 120', "read rate limit"),
    ('ACK_RATE_LIMIT = 240', "ACK rate limit"),
    ('Number.isSafeInteger(id) || id <= 0', "invalid read ID rejection"),
    ('if (!validReadOrAckPacket(data, userId)) return;', "validation before legacy listener"),
    ('try { body = JSON.parse(raw.toString()); } catch { return true; }', "malformed JSON compatibility"),
    ('if (!body || typeof body !== "object" || Array.isArray(body)) return true;', "non-object compatibility"),
]:
    require(ISOLATION, needle, label)

# The outer process must be the production entrypoint, preserving the split
# isolation -> bootstrap startup architecture.
package_json = (ROOT / "backend" / "package.json").read_text(encoding="utf-8")
require(package_json, '"start": "node realtimeIsolationBootstrap.js"', "production realtime isolation entrypoint")

print("R3 final integration and security gate: GREEN")
