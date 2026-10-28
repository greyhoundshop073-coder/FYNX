from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
auth = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAuthPanel.kt").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
navigation = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")

checks = {
    "debug login requires an Android debuggable build": "ApplicationInfo.FLAG_DEBUGGABLE" in auth and "if (!isDebuggable || requestedUsername.isBlank()) return null" in auth,
    "debug Sign In enters local session without a backend request": "localDebugBuild -> {" in auth and "FynxAuthStore.save(context, requestedUsername)" in auth and "onAuthenticated(requestedUsername)" in auth,
    "offline fallback requires a recognized network failure": "allowOfflineEntry && isLoginNetworkFailure(failure)" in auth,
    "network fallback reuses the debug-only local-account guard": "localDebugAccountForLogin(context, username)" in auth,
    "release and invalid-credential paths do not receive an unconditional local session": "if (allowOfflineEntry) {" not in auth and "FynxRemoteAuthClient.login(context, requestedUsername, password)" in auth,
    "live-backend probe is separately opt-in and not required for feature-branch APK runtime": "run_live_backend_probe:" in workflow and "inputs.run_live_backend_probe == true" in workflow and "github.ref == 'refs/heads/main'" in workflow and "if: ${{ (github.event_name == 'workflow_dispatch' && inputs.full_runtime == 'true') || (github.event_name == 'push' && contains(github.event.head_commit.message, '[home-cert]')) }}" not in workflow,
    "explicit live-backend probe remains available when deliberately requested": "inputs.run_live_backend_probe == true" in workflow and "github.ref == 'refs/heads/main'" in workflow and "run: node scripts/verify_production_backend_reachability.mjs" in workflow,
}
checks.update({
    "debug UI explicitly says local sign-in never contacts the backend":
        "Debug review mode: Leave password blank for quick local entry" in auth and
        "without contacting the FYNX backend" in auth,
    "debug UI-review automation chooses local entry before typing a password":
        'debug_option=find_control(xml,["Debug review mode"' in navigation and
        "if debug_option:" in navigation and
        "return enter_debug_local_ui(xml, reason, password_was_entered=False)" in navigation,
    "debug UI automation reports local-only state instead of claiming server authentication":
        'LOGIN_MODE="debug-only local UI entry (not server-authenticated)"' in navigation and
        'PASS debug-only local UI entry; this does not certify server authentication' in navigation,
})
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("DEBUG LOGIN / APK PIPELINE RED: " + "; ".join(failed))
print(f"DEBUG LOGIN / APK PIPELINE GREEN ({len(checks)} checks)")
