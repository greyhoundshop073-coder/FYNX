from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
auth = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAuthPanel.kt").read_text(encoding="utf-8")
network_test = (ROOT / "app/src/androidTest/java/com/fynx/app/FynxNetworkRecoveryContractTest.kt").read_text(encoding="utf-8")
store = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAuthStore.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")

checks = {
    "normal Sign In always requires a password and calls the real backend": '"Password"' in auth and 'password.isBlank() -> error = "Enter your username and password."' in auth and "FynxRemoteAuthClient.login(context, requestedUsername, password)" in auth,
    "offline UI preview is explicit and never the normal Sign In action": "Preview offline (debug only)" in auth and "Offline preview only" in auth and "saveDebugOfflinePreview" in auth,
    "offline preview has a separate persisted marker": "KEY_DEBUG_OFFLINE_PREVIEW" in store and "fun isDebugOfflinePreview" in store and "fun saveDebugOfflinePreview" in store,
    "failed remote login never creates a local signed-in session": "allowOfflineEntry" not in auth and "onAuthenticated(localUsername)" not in auth,
    "health and readiness probes remain public without a bearer token": 'path == "/health" || path == "/ready"' in client,
    "protected requests fail locally rather than sending an empty bearer token": "if (requiresAuthentication && bearerToken.isNullOrBlank()) throw FynxUnauthorizedException()" in client and 'setRequestProperty("Authorization", "Bearer $bearerToken")' in client,
    "only explicitly marked debug previews survive startup without a server token": "FynxAuthStore.isDebugOfflinePreview(context)" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text(encoding="utf-8") and "FynxAuthStore.clear(context)" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text(encoding="utf-8"),
    "live-backend probe is separately opt-in and not required for feature-branch APK runtime": "run_live_backend_probe:" in workflow and "inputs.run_live_backend_probe == true" in workflow and "github.ref == 'refs/heads/main'" in workflow and "if: ${{ (github.event_name == 'workflow_dispatch' && inputs.full_runtime == 'true') || (github.event_name == 'push' && contains(github.event.head_commit.message, '[home-cert]')) }}" not in workflow,
    "explicit live-backend probe remains available when deliberately requested": "inputs.run_live_backend_probe == true" in workflow and "github.ref == 'refs/heads/main'" in workflow and "run: node scripts/verify_production_backend_reachability.mjs" in workflow,
    "Android instrumentation keeps live Render reachability opt-in": "InstrumentationRegistry.getArguments()" in network_test and 'getString("fynxRequireLiveBackend")' in network_test and "assumeTrue(" in network_test and "fynxRequireLiveBackend=true" in network_test and "android.testInstrumentationRunnerArguments.fynxRequireLiveBackend" not in workflow,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("DEBUG LOGIN / APK PIPELINE RED: " + "; ".join(failed))
print(f"DEBUG LOGIN / APK PIPELINE GREEN ({len(checks)} checks)")
