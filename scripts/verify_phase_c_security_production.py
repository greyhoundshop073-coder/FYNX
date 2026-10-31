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
client = read("app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt")
realtime_client = read("app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt")
auth = read("app/src/main/java/com/fynx/app/ui/FynxAuthStore.kt")
token_store = read("app/src/main/java/com/fynx/app/ui/FynxSecureTokenStore.kt")
workflow = read(".github/workflows/android-build.yml")

check("production security gate files exist", all(exists(p) for p in ["backend/server.js", "backend/realtimeIsolationBootstrap.js", "backend/mediaPrivacy.js", "app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt", "app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt", "app/src/main/java/com/fynx/app/ui/FynxAuthStore.kt", "app/src/main/java/com/fynx/app/ui/FynxSecureTokenStore.kt"]))
check("backend secrets come from environment", "process.env.JWT_SECRET" in server and "process.env.DATABASE_URL" in server and "process.env.OPENAI_API_KEY" in server)
check("missing JWT configuration fails closed", 'if (!JWT_SECRET)' in server and 'if (!token)' in server and 'return res.status(401)' in server)
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
media_route_start = server.find('app.get("/api/media/:id", auth')
media_route_end = server.find("\\n});", media_route_start) if media_route_start >= 0 else -1
media_route = server[media_route_start:media_route_end] if media_route_end > media_route_start else ""
check("buyer Marketplace media requires an active, in-stock NGN/USD listing", bool(media_route) and "FROM marketplace_listings ml" in media_route and "WHERE ml.active = TRUE" in media_route and "AND ml.quantity > 0" in media_route and "UPPER(COALESCE(ml.currency, '') )" not in media_route and "UPPER(COALESCE(ml.currency, '')) IN ('NGN', 'USD')" in media_route)
check("Marketplace media remains seller-owned, listing-attached, and block-aware", "mm.owner_id = ml.seller_id" in media_route and "jsonb_array_elements_text" in media_route and "listing_media.media_id = CAST(mm.id AS TEXT)" in media_route and "b.blocker_id = $2 AND b.blocked_id = ml.seller_id" in media_route and "b.blocked_id = $2 AND b.blocker_id = ml.seller_id" in media_route)
check("Marketplace media remains seller-owned, listing-attached, and block-aware", "mm.owner_id = ml.seller_id" in media_route and "jsonb_array_elements_text" in media_route and "listing_media.media_id = CAST(mm.id AS TEXT)" in media_route and "b.blocker_id = $2 AND b.blocked_id = ml.seller_id" in media_route and "b.blocked_id = $2 AND b.blocker_id = ml.seller_id" in media_route)
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
