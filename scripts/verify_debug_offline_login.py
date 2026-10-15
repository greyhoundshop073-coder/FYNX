from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
auth = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAuthPanel.kt").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
app_state = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text(encoding="utf-8")

checks = {
    "saved local debug sessions without a server token return to authentication": "stored.state == AuthState.SIGNED_IN && FynxBackendClient.hasAccessToken(context)" in app_state and "debugOfflineSession" not in app_state,
    "login always shows the password field": 'FynxAuthField(password, { password = it }, "Password", keyboardType = KeyboardType.Password, password = true)' in auth and "Saved-device login for this debug APK" not in auth,
    "online login requests a server token before entering the app": "FynxRemoteAuthClient.login(context, requestedUsername, password)" in auth and "FynxAuthStore.save(context, savedLocalUsername)" not in auth,
    "network failures never create a tokenless local session": "allowOfflineEntry" not in auth and "localDebugAccountForLogin" not in auth and "isLoginNetworkFailure" not in auth and "FynxRemoteAuthClient.login(context, requestedUsername, password)" in auth,
    "expired backend sessions return the app to authentication": "FynxBackendClient.sessionExpiredEvents.collect" in app_state and "authSession = AuthSession()" in app_state,
    "live backend smoke separates liveness from database readiness": "node scripts/verify_live_backend_session.mjs" in workflow and "verify_production_backend_reachability.mjs" not in workflow and "health?.database" not in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8") and '/ready' in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8"),
    "Marketplace discovery SQL uses bound pagination parameters": 'LIMIT $${params.length - 1} OFFSET $${params.length}' in (ROOT / "backend/discoveryRoutes.js").read_text(encoding="utf-8"),
    "live backend smoke exercises Marketplace category and location filters": '"/api/marketplace/discovery?category=Electronics"' in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8") and '"/api/marketplace/discovery?location=Lagos"' in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8"),
    "live backend smoke establishes mutual follow before messaging": (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8").count("/api/social/follow/") >= 2 and '/api/messages' in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8") and "/api/friends/request" not in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8"),
    "live smoke uses backend-valid unique account names": "username: `fynxnet_${random}_${suffix}`" in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8") and 'randomBytes(8)' in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8"),
    "live backend smoke verifies authenticated writes and private post cleanup": "/api/auth/login" in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8") and 'method: "POST", token' in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8") and 'visibility: "ONLY_ME"' in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8"),
    "post visibility guard accepts app-supported audience values": all(x in (ROOT / "backend/socialHardening.js").read_text(encoding="utf-8") for x in ['"FRIENDS_ONLY"', '"SELECTED_PEOPLE"']) and 'visibility: "FRIENDS_ONLY"' in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8"),
    "isolated smoke account feeds authenticated emulator runtime": "FYNX_RUNTIME_USERNAME" in (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8") and "FYNX_RUNTIME_PASSWORD" in (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8") and "FYNX_RUNTIME_USERNAME=" in (ROOT / "scripts/verify_live_backend_session.mjs").read_text(encoding="utf-8"),
    "network and invalid-credential failures remain on authentication screen": "onFailure { failure ->" in auth and "onAuthenticated(localUsername)" not in auth and "FynxRemoteAuthClient.login(context, requestedUsername, password)" in auth,
    "network-fix branch requires live backend authentication and write-path checks": "github.ref == 'refs/heads/fix/network-auth-railway-main-20261015'" in workflow and "node scripts/verify_live_backend_session.mjs" in workflow and "continue-on-error: true" not in workflow,
    "explicit full-runtime and home-cert runs retain the live probe": "inputs.full_runtime == 'true'" in workflow and "[home-cert]" in workflow and "node scripts/verify_live_backend_session.mjs" in workflow,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("DEBUG LOGIN / APK PIPELINE RED: " + "; ".join(failed))
print(f"DEBUG LOGIN / APK PIPELINE GREEN ({len(checks)} checks)")
