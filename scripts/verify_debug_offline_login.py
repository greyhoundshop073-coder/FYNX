from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
auth = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAuthPanel.kt").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")

checks = {
    "debug login requires an Android debuggable build": "ApplicationInfo.FLAG_DEBUGGABLE" in auth and "if (!isDebuggable || requestedUsername.isBlank()) return null" in auth,
    "saved-device login is immediate and does not wait for the backend": "val savedLocalUsername = localDebugAccountForLogin(context, requestedUsername)" in auth and "FynxAuthStore.save(context, savedLocalUsername)" in auth,
    "offline fallback requires a recognized network failure": "allowOfflineEntry && isLoginNetworkFailure(failure)" in auth,
    "network fallback reuses the debug-only local-account guard": "localDebugAccountForLogin(context, username)" in auth,
    "release and invalid-credential paths do not receive an unconditional local session": "if (allowOfflineEntry) {" not in auth and "FynxRemoteAuthClient.login(context, requestedUsername, password)" in auth,
    "network-fix branch opts into live-backend probe without making it mandatory for unrelated APK builds": "startsWith(github.head_ref, 'fix/network-health-probe-and-transport-detection-')" in workflow and "startsWith(github.ref_name, 'fix/network-health-probe-and-transport-detection-')" in workflow and "continue-on-error: true" not in workflow,
    "explicit full-runtime and home-cert runs retain the probe": "inputs.full_runtime == 'true'" in workflow and "[home-cert]" in workflow and "run: node scripts/verify_production_backend_reachability.mjs" in workflow,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("DEBUG LOGIN / APK PIPELINE RED: " + "; ".join(failed))
print(f"DEBUG LOGIN / APK PIPELINE GREEN ({len(checks)} checks)")
