from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    p = ROOT / path
    return p.read_text(encoding="utf-8") if p.is_file() else ""

def exists(path):
    return (ROOT / path).is_file()

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

server = read("backend/server.js")
realtime = read("backend/realtimeIsolationBootstrap.js")
media_privacy = read("backend/mediaPrivacy.js")
marketplace_completion = read("backend/marketplaceCompletion.js")
marketplace_media_privacy = read("backend/marketplaceMediaPrivacy.js")
client = read("app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt")
realtime_client = read("app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt")
auth = read("app/src/main/java/com/fynx/app/ui/FynxAuthStore.kt")
token_store = read("app/src/main/java/com/fynx/app/ui/FynxSecureTokenStore.kt")
workflow = read(".github/workflows/android-build.yml")

check("production security gate files exist", all(exists(p) for p in ["backend/server.js", "backend/realtimeIsolationBootstrap.js", "backend/mediaPrivacy.js", "app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt", "app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt", "app/src/main/java/com/fynx/app/ui/FynxAuthStore.kt", "app/src/main/java/com/fynx/app/ui/FynxSecureTokenStore.kt"]))
check("backend secrets come from environment", "process.env.JWT_SECRET" in server and "process.env.DATABASE_URL" in server and "process.env.OPENAI_API_KEY" in server)
check("missing JWT configuration fails closed", 'if (!token || !JWT_SECRET) return res.status(401)' in server)
check("HTTP authentication verifies JWT", "jwt.verify(token, JWT_SECRET)" in server)
check("realtime authentication verifies JWT", "jwt.verify(token, secret)" in realtime or "jwt.verify(token, JWT_SECRET)" in server)
check("realtime account isolation is enforced", "userId" in realtime_client and "currentAccountKey()" in realtime_client and "belongsToCurrentAccount" in realtime_client)
check("client API traffic requires HTTPS", 'require(normalized.isBlank() || normalized.startsWith("https://"))' in client and 'require(root.startsWith("https://"))' in client)
check("client only trusts backend media host", 'require(target.host.equals(configured.host, true))' in client)
# Verify the real streamed-response guard structurally: bytes are accumulated and the stream is aborted after the configured cap.
check("API responses have bounded size", "MAX_RESPONSE_BYTES" in client and "total += count" in client and "total > MAX_RESPONSE_BYTES" in client and "throw IOException" in client)
check("media downloads have bounded size", "maxBytes" in client and "total += count" in client and "total > maxBytes" in client and "FYNX media is too large" in client)
check("authenticated media endpoint exists", 'app.get("/api/media/:id", auth' in server)
check("media ownership and visibility are server checked", "owner_id" in server and "sender_id" in server and "recipient_id" in server and "blocks" in media_privacy)
check("active Marketplace listing media is readable by buyers without bypassing seller blocks", re.search(r"marketplace_listings ml\s+WHERE ml.active = TRUE\s+AND ml.quantity > 0", server) is not None and "ml.media_ids @> to_jsonb(ARRAY[mm.id::text])" in server and "ml.seller_id" in server and "ml.currency" in server)
media_route_match = re.search(r'app\.get\("/api/media/:id", auth, async \(req, res\) => \{(.*?)\n\}\);', server, re.S)
media_route = media_route_match.group(1) if media_route_match else ""
check("media authorization rules are separate EXISTS clauses without fragile joined ordering", "OR EXISTS (\n            SELECT 1 FROM messages m" in media_route and "OR EXISTS (\n            SELECT 1 FROM marketplace_listings ml" in media_route and "OR EXISTS (\n            SELECT 1 FROM statuses s" in media_route and "ORDER BY m.id" not in media_route)
check("media route SQL parentheses are balanced", bool(media_route) and media_route.count("(") == media_route.count(")"))

check("Marketplace media requires active in-stock NGN/USD listings", "ml.quantity > 0" in marketplace_completion and "ml.quantity>0" in marketplace_media_privacy and "ml.currency" in marketplace_completion and "ml.currency" in marketplace_media_privacy)
check("Marketplace media authorization changes cannot be masked by a one-hour private cache", "private, no-store" in marketplace_completion and "max-age=3600" not in marketplace_completion)
check("production transport security headers are enabled", "X-Content-Type-Options" in server and "Referrer-Policy" in server and "X-Frame-Options" in server and "Strict-Transport-Security" in server)
check("request body size is bounded", 'express.json({ limit: "18mb"' in server)
check("endpoint rate limiting is enabled", 'rateLimit("auth"' in server and 'rateLimit("assistant"' in server and 'rateLimit("media"' in server and 'rateLimit("messages"' in server)
check("database pool and query timeouts are bounded", "connectionTimeoutMillis" in server and "statement_timeout" in server and "query_timeout" in server)
check("message authorization prevents cross-account media use", "mm.owner_id = $2" in server and "m.sender_id = $2" in server and "m.recipient_id = $2" in server)
check("logout clears secure token storage", "FynxSecureTokenStore.save(context, null)" in auth)
check("secure token store is separated and Android-Keystore backed", "AndroidKeyStore" in token_store and "AES/GCM/NoPadding" in token_store and "FynxSecureTokenStore" in auth)
check("CI runs Phase C before build", "verify_phase_c_security_production.py" in workflow)
check("no obvious private API credentials in Android source", not re.search(r"sk-[A-Za-z0-9_-]{20,}|-----BEGIN (?:RSA |EC )?PRIVATE KEY-----", "\n".join(p.read_text(encoding="utf-8") for p in (ROOT / "app/src/main/java").rglob("*.kt"))))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("FYNX Phase C security/production audit failed: " + "; ".join(failed))
print(f"FYNX Phase C security/production audit passed ({len(checks)} checks)")
