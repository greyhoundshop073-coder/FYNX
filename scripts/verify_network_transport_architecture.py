#!/usr/bin/env python3
from pathlib import Path

ROOT = Path("app/src/main/java")
violations = []
allowed_open = {"FynxBackendClient.kt"}
allowed_okhttp = {"FynxRealtimeClient.kt"}
allowed_websocket = {"FynxRealtimeClient.kt"}

for path in ROOT.rglob("*.kt"):
    text = path.read_text(encoding="utf-8")
    name = path.name
    if "openConnection(" in text and name not in allowed_open:
        violations.append(f"direct HttpURLConnection/openConnection in {path}")
    if "OkHttpClient" in text and name not in allowed_okhttp:
        violations.append(f"direct OkHttp transport in {path}")
    if "newWebSocket(" in text and name not in allowed_websocket:
        violations.append(f"direct WebSocket transport in {path}")

backend = Path("app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt").read_text(encoding="utf-8")
currency = Path("app/src/main/java/com/fynx/app/ui/CurrencyConverterPanel.kt").read_text(encoding="utf-8")
media_client = Path("app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt").read_text(encoding="utf-8")
server = Path("backend/server.js").read_text(encoding="utf-8")
required = [
    'PRODUCTION_BASE_URL = "https://fynx-backend-testing-testing.up.railway.app"',
    'requiresAuthentication = !isPublicAuthPath(path)',
    'FynxSecureTokenStore',
    'app.post("/api/media/upload", auth, express.raw({ type: "application/octet-stream", limit: "12mb" })',
    'suspend fun postBytes(context: Context, path: String, mimeType: String, body: ByteArray)',
    'executeBinaryRequest(context, root, path, mimeType, body',
]
for needle in required:
    if needle not in backend:
        violations.append(f"central transport invariant missing: {needle}")
if 'FynxBackendClient.postBytes(context, "/api/media/upload", effectiveMimeType, bytes)' not in media_client:
    violations.append("media uploads must use the central binary transport")
if 'Base64.encodeToString(bytes' in media_client:
    violations.append("media uploads must not Base64-expand payloads")
if 'app.post("/api/media/upload", auth, express.raw({ type: "application/octet-stream", limit: "12mb" })' not in server:
    violations.append("backend must expose authenticated bounded binary media upload")

for needle in ['FynxBackendClient.get(context, path)', '/api/money-planner/rates']:
    if needle not in currency:
        violations.append(f"currency backend routing invariant missing: {needle}")
if 'https://open.er-api.com' in currency:
    violations.append("currency converter still calls external rate provider directly")

if violations:
    raise SystemExit("FYNX network transport gate failed:\n- " + "\n- ".join(violations))
print("FYNX network transport gate passed")
