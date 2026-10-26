from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
auth = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAuthPanel.kt").read_text(encoding="utf-8")
app = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text(encoding="utf-8")
activity = (ROOT / "app/src/main/java/com/fynx/app/MainActivity.kt").read_text(encoding="utf-8")
runtime = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")

checks = {
    "development APK opens directly into the app without a live backend token": "val isDebugBuild = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0" in app and "isDebugBuild -> AuthSession(" in app and 'stored.username?.takeIf { it.isNotBlank() } ?: "fynx"' in app,
    "debug inspection identity is persisted for panels using the local auth store": "Persist the debug-only local inspection identity" in app and "FynxAuthStore.save(context, authSession.username?.takeIf { it.isNotBlank() } ?: \"fynx\")" in app,
    "release builds keep the real access-token authentication gate": "stored.state == AuthState.SIGNED_IN && FynxBackendClient.hasAccessToken(context) -> stored" in app and "if (!FYNX_PREVIEW_MODE && authSession.state != AuthState.SIGNED_IN)" in app,
    "local inspection sessions do not trigger server push-device registration or notification permission": "FynxAuthStore.load(this).state != AuthState.SIGNED_IN ||" in activity and "!FynxBackendClient.hasAccessToken(this)" in activity and "notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)" in activity,
    "runtime certification accepts the intentional direct debug Home entry without claiming server auth": 'if _home_surface_ready(xml):' in runtime and 'LOGIN_MODE = "debug-only local UI entry (not server-authenticated)"' in runtime and 'server authentication is not certified' in runtime,
    "backend errors are translated to readable messages rather than rendering raw JSON": "friendlyAuthFailureMessage(failure)" in auth and "org.json.JSONObject(payload).optString(\"message\").trim()" in auth and "raw.contains(\"Application not found\", ignoreCase = true)" in auth,
    "debug login requires an Android debuggable build": "ApplicationInfo.FLAG_DEBUGGABLE" in auth and "if (!isDebuggable || requestedUsername.isBlank()) return null" in auth,
    "saved-device login is immediate and does not wait for the backend": "val savedLocalUsername = localDebugAccountForLogin(context, requestedUsername)" in auth and "FynxAuthStore.save(context, savedLocalUsername)" in auth,
    "offline fallback requires a recognized network failure": "allowOfflineEntry && isLoginNetworkFailure(failure)" in auth,
    "network fallback reuses the debug-only local-account guard": "localDebugAccountForLogin(context, username)" in auth,
    "release and invalid-credential paths do not receive an unconditional local session": "if (allowOfflineEntry) {" not in auth and "FynxRemoteAuthClient.login(context, requestedUsername, password)" in auth,
    "live-backend probe is separately opt-in and not required for feature-branch APK runtime": "run_live_backend_probe:" in workflow and "inputs.run_live_backend_probe == true" in workflow and "github.ref == 'refs/heads/main'" in workflow and "if: ${{ (github.event_name == 'workflow_dispatch' && inputs.full_runtime == 'true') || (github.event_name == 'push' && contains(github.event.head_commit.message, '[home-cert]')) }}" not in workflow,
    "explicit live-backend probe remains available when deliberately requested": "inputs.run_live_backend_probe == true" in workflow and "github.ref == 'refs/heads/main'" in workflow and "run: node scripts/verify_production_backend_reachability.mjs" in workflow,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("DEBUG LOGIN / APK PIPELINE RED: " + "; ".join(failed))
print(f"DEBUG LOGIN / APK PIPELINE GREEN ({len(checks)} checks)")
