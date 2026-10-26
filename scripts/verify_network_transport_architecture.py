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
required = [
    'PRODUCTION_BASE_URL = "https://fynx-backend-testing-testing.up.railway.app"',
    'requiresAuthentication = !isPublicAuthPath(path)',
    'FynxSecureTokenStore',
]

for needle in [
    "private fun safeHttpErrorMessage(status: Int, body: String): String",
    'serverMessage.equals("Application not found", ignoreCase = true)',
    '"The FYNX service is temporarily unavailable."',
    '"FYNX backend returned HTTP $status: ${FynxBackendClient.safeHttpErrorMessage(status, body)}"',
]:
    if needle not in backend:
        violations.append(f"backend error presentation invariant missing: {needle}")
for needle in required:
    if needle not in backend:
        violations.append(f"central transport invariant missing: {needle}")
for needle in ['FynxBackendClient.get(context, path)', '/api/money-planner/rates']:
    if needle not in currency:
        violations.append(f"currency backend routing invariant missing: {needle}")
app_shell = Path("app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text(encoding="utf-8")
connection_start = app_shell.find("DisposableEffect(lifecycleOwner, authSession.state)")
connection_end = app_shell.find("DisposableEffect(authSession.state, authSession.username", connection_start)
connection_effect = app_shell[connection_start:connection_end] if connection_start >= 0 and connection_end > connection_start else ""
if "if (authSession.state == AuthState.SIGNED_IN)" not in connection_effect:
    violations.append("app-level backend health indicator must start for every signed-in session, including tokenless debug saved-device entry")
if "FynxBackendClient.hasAccessToken(context)" in connection_effect:
    violations.append("public backend health probe must not be gated on an access token")

network_settings = Path("app/src/main/java/com/fynx/app/ui/DataNetworkSettingsPanel.kt").read_text(encoding="utf-8")
remote_auth = Path("app/src/main/java/com/fynx/app/ui/FynxRemoteAuthClient.kt").read_text(encoding="utf-8")
for needle in ['"FYNX backend connection"', 'FynxBackendClient.health(context)', 'FynxBackendClient.get(context, "/api/me")', 'FynxRemoteAuthClient.connectSavedAccount(context, backendPassword)']:
    if needle not in network_settings:
        violations.append(f"visible backend connection panel missing: {needle}")
for needle in ['suspend fun connectSavedAccount(', 'FynxBackendClient.isUnauthorizedFailure(failure)', 'FynxAuthStore.hasAccount(context)']:
    if needle not in remote_auth:
        violations.append(f"saved-account backend connector missing: {needle}")

if 'https://open.er-api.com' in currency:
    violations.append("currency converter still calls external rate provider directly")

if violations:
    raise SystemExit("FYNX network transport gate failed:\n- " + "\n- ".join(violations))
print("FYNX network transport gate passed")
