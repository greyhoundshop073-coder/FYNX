from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
auth = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAuthPanel.kt").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
app_state = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text(encoding="utf-8")

checks = {
    "debug login requires an Android debuggable build": "ApplicationInfo.FLAG_DEBUGGABLE" in auth and "if (!isDebuggable || requestedUsername.isBlank()) return null" in auth,
    "saved local debug sessions without a server token return to authentication": "stored.state == AuthState.SIGNED_IN && FynxBackendClient.hasAccessToken(context)" in app_state and "debugOfflineSession" not in app_state,
    "login always shows the password field": 'FynxAuthField(password, { password = it }, "Password", keyboardType = KeyboardType.Password, password = true)' in auth and "Saved-device login for this debug APK" not in auth,
    "online login requests a server token before entering the app": "FynxRemoteAuthClient.login(context, requestedUsername, password)" in auth and "FynxAuthStore.save(context, savedLocalUsername)" not in auth,
    "offline fallback requires a recognized network failure": "allowOfflineEntry && isLoginNetworkFailure(failure)" in auth,
    "network fallback reuses the debug-only local-account guard": "localDebugAccountForLogin(context, username)" in auth,
    "expired backend sessions return the app to authentication": "FynxBackendClient.sessionExpiredEvents.collect" in app_state and "authSession = AuthSession()" in app_state,
    "live backend smoke separates liveness from database readiness": "node scripts/verify_live_backend_session.mjs" in workflow and "verify_production_backend_reachability.mjs" not in workflow and "health?.database" not in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8") and '/ready' in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8"),
    "live backend smoke verifies authenticated writes": "/api/auth/login" in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8") and 'method: "POST", token' in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8"),
    "release and invalid-credential paths do not receive an unconditional local session": "if (allowOfflineEntry) {" not in auth and "FynxRemoteAuthClient.login(context, requestedUsername, password)" in auth,
    "network-fix branch requires live backend authentication and write-path checks": "github.ref == 'refs/heads/fix/network-auth-railway-main-20261015'" in workflow and "node scripts/verify_live_backend_session.mjs" in workflow and "continue-on-error: true" not in workflow,
    "explicit full-runtime and home-cert runs retain the live probe": "inputs.full_runtime == 'true'" in workflow and "[home-cert]" in workflow and "node scripts/verify_production_backend_reachability.mjs" in workflow,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("DEBUG LOGIN / APK PIPELINE RED: " + "; ".join(failed))
print(f"DEBUG LOGIN / APK PIPELINE GREEN ({len(checks)} checks)")
