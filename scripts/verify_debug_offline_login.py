from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
auth = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAuthPanel.kt").read_text(encoding="utf-8")
network_test = (ROOT / "app/src/androidTest/java/com/fynx/app/FynxNetworkRecoveryContractTest.kt").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")

checks = {
    "debug login requires an Android debuggable build": "ApplicationInfo.FLAG_DEBUGGABLE" in auth and "if (!isDebuggable || requestedUsername.isBlank()) return null" in auth,
    "saved-device login is immediate and does not wait for the backend": "val savedLocalUsername = localDebugAccountForLogin(context, requestedUsername)" in auth and "FynxAuthStore.save(context, savedLocalUsername)" in auth,
    "offline fallback requires a recognized network failure": "allowOfflineEntry && isLoginNetworkFailure(failure)" in auth,
    "network fallback reuses the debug-only local-account guard": "localDebugAccountForLogin(context, username)" in auth,
    "release and invalid-credential paths do not receive an unconditional local session": "if (allowOfflineEntry) {" not in auth and "FynxRemoteAuthClient.login(context, requestedUsername, password)" in auth,
    "live-backend probe is separately opt-in and not required for feature-branch APK runtime": "run_live_backend_probe:" in workflow and "inputs.run_live_backend_probe == true" in workflow and "github.ref == 'refs/heads/main'" in workflow and "if: ${{ (github.event_name == 'workflow_dispatch' && inputs.full_runtime == 'true') || (github.event_name == 'push' && contains(github.event.head_commit.message, '[home-cert]')) }}" not in workflow,
    "explicit live-backend probe remains available when deliberately requested": "inputs.run_live_backend_probe == true" in workflow and "github.ref == 'refs/heads/main'" in workflow and "run: node scripts/verify_production_backend_reachability.mjs" in workflow,
    "Android instrumentation keeps live Railway reachability opt-in": "InstrumentationRegistry.getArguments()" in network_test and 'getString("fynxRequireLiveBackend")' in network_test and "assumeTrue(" in network_test and "fynxRequireLiveBackend=true" in network_test and "android.testInstrumentationRunnerArguments.fynxRequireLiveBackend" not in workflow,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("DEBUG LOGIN / APK PIPELINE RED: " + "; ".join(failed))
print(f"DEBUG LOGIN / APK PIPELINE GREEN ({len(checks)} checks)")
