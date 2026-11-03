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
    'path == "/health"',
    'FynxSecureTokenStore',
]
for needle in required:
    if needle not in backend:
        violations.append(f"central transport invariant missing: {needle}")
connection_manager = Path("app/src/main/java/com/fynx/app/ui/FynxAppConnectionManager.kt").read_text(encoding="utf-8")
for needle in ['manager.activeNetwork ?: return false', 'capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)', 'manager.registerDefaultNetworkCallback(newCallback)', 'override fun onCapabilitiesChanged(', '= refreshDefaultNetwork()', 'private fun refreshDefaultNetwork()', 'probeJob?.cancel()', 'probeJob = null']:
    if needle not in connection_manager:
        violations.append(f"app connection default-network/reprobe invariant missing: {needle}")
backend_client = backend
if 'manager.allNetworks' in backend_client:
    violations.append("backend availability must not count a non-default network as the HTTP route")
network_quality = Path("app/src/main/java/com/fynx/app/ui/FynxNetworkQuality.kt").read_text(encoding="utf-8")
if 'manager.allNetworks' in network_quality:
    violations.append("network quality must be measured on the default HTTP route")
for needle in ['val active = manager.activeNetwork ?: return false', 'capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)']:
    if needle not in backend_client:
        violations.append(f"backend default-network invariant missing: {needle}")
for needle in ['val active = manager.activeNetwork ?: return Level.OFFLINE', 'capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)']:
    if needle not in network_quality:
        violations.append(f"network-quality default-network invariant missing: {needle}")
for needle in ['FynxBackendClient.get(context, path)', '/api/money-planner/rates']:
    if needle not in currency:
        violations.append(f"currency backend routing invariant missing: {needle}")
if 'https://open.er-api.com' in currency:
    violations.append("currency converter still calls external rate provider directly")

workflow = Path(".github/workflows/android-build.yml").read_text(encoding="utf-8")
if "github.event.pull_request.head.ref || github.ref_name" not in workflow:
    violations.append("Android push/PR runtime tests must share a source-branch concurrency group")
if "cancel-in-progress: true" not in workflow:
    violations.append("Android runtime tests must cancel stale same-branch runs")

if violations:
    raise SystemExit("FYNX network transport gate failed:\n- " + "\n- ".join(violations))
print("FYNX network transport gate passed")
