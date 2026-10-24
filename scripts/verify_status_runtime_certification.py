from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
runtime = (ROOT / "scripts/verify_authenticated_status_lifecycle.py").read_text(encoding="utf-8")
auth = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAuthPanel.kt").read_text(encoding="utf-8")

def syntax_check(source):
    try:
        compile(source, "scripts/verify_authenticated_status_lifecycle.py", "exec")
        return True
    except SyntaxError as exc:
        print(f"Python syntax error in Status lifecycle test at line {exc.lineno}: {exc.msg}")
        return False

checks = {
    "runtime certification variant inherits debug dependencies without enabling the debug-only saved-device path":
        'create("runtimeCert")' in gradle and 'initWith(getByName("debug"))' in gradle and
        'isDebuggable = false' in gradle and 'signingConfig = signingConfigs.getByName("debug")' in gradle,
    "runtime certification APK is built by the full Status/Stories CI job":
        "assembleRuntimeCert" in workflow and "verify_authenticated_status_lifecycle.py" in workflow,
    "runtime certification uses the non-debuggable APK, real sign-in UI and real account credentials":
        'app/build/outputs/apk/runtimeCert/app-runtimeCert.apk' in runtime and
        'FYNX_E2E_USERNAME' in runtime and 'FYNX_E2E_PASSWORD' in runtime and 'fynx://home' in runtime,
    "Text Status must be verified by server retrieval and real Story viewer before cleanup":
        '"/api/statuses"' in runtime and 'TEXT_MARKER' in runtime and
        'authenticated-server-status-text-viewer' in runtime and 'delete_through_viewer' in runtime,
    "music-only certification selects a published catalogue track and verifies backend MUSIC status":
        'FYNX Music' in runtime and '"MUSIC"' in runtime and 'musicCatalogueId' in runtime,
    "test-created Statuses are owner-cleaned and API tokens are never emitted":
        'finally:' in runtime and 'cleanup_status' in runtime and
        'access tokens are never written' in runtime,
    "login username/password hierarchies remain in memory and are never saved as artifacts":
        "def read_ui_unpersisted()" in runtime and
        'latest = read_ui_unpersisted()' in runtime and
        'dump_ui("authenticated-server-status-login-filled.xml")' not in runtime and
        'dump_ui("authenticated-server-status-login-wait.xml")' not in runtime,
    "long runtime script is syntax-compiled before expensive emulator certification":
        syntax_check(runtime),
    "existing debug login contract remains untouched":
        'ApplicationInfo.FLAG_DEBUGGABLE' in auth and 'localDebugAccountForLogin' in auth and
        'FynxRemoteAuthClient.login(context, requestedUsername, password)' in auth,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("STATUS RUNTIME CERTIFICATION SOURCE GATE RED: " + "; ".join(failed))
print(f"STATUS RUNTIME CERTIFICATION SOURCE GATE GREEN ({len(checks)} checks)")
