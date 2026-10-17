from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
auth = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAuthPanel.kt").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")

runtime = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")

checks = {
    "debug offline account is restricted to a debuggable build": "ApplicationInfo.FLAG_DEBUGGABLE" in auth and "if (!isDebuggable || requestedUsername.isBlank()) return null" in auth,
    "offline account must already be saved and match the requested username": "?: return null" in auth and "saved.takeIf { it.equals(normalizedUsername, ignoreCase = true) }" in auth and "saved == null -> normalizedUsername" not in auth,
    "server Sign In always requires a password and calls remote authentication": "password.isBlank() -> error = \"Enter your username and password.\"" in auth and "FynxRemoteAuthClient.login(context, requestedUsername, password)" in auth,
    "offline access is an explicit separate debug-only action": "Continue offline (debug only)" in auth and "savedLocalUsername = localDebugAccountForLogin(context, username)" in auth,
    "network/auth failures are not silently converted into local sessions": "allowOfflineEntry" not in auth and "isLoginNetworkFailure" not in auth and "onFailure { failure ->" in auth,
    "runtime server-auth test clears persisted local account and tokens": 'run("adb","shell","pm","clear",PACKAGE)' in runtime and "before server-authentication certification" in runtime,
    "network-fix branch opts into live-backend probe without making it mandatory for unrelated APK builds": "startsWith(github.head_ref, 'fix/network-health-probe-and-transport-detection-')" in workflow and "startsWith(github.ref_name, 'fix/network-health-probe-and-transport-detection-')" in workflow and "continue-on-error: true" not in workflow,
    "explicit full-runtime and home-cert runs retain the probe": "inputs.full_runtime == 'true'" in workflow and "[home-cert]" in workflow and "run: node scripts/verify_production_backend_reachability.mjs" in workflow,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("DEBUG LOGIN / APK PIPELINE RED: " + "; ".join(failed))
print(f"DEBUG LOGIN / APK PIPELINE GREEN ({len(checks)} checks)")
