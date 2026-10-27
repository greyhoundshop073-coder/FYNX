from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

def check(name, ok):
    if ok:
        print("PASS: " + name)
    else:
        raise SystemExit("Full real-device audit failed: " + name)

workflow = read(".github/workflows/android-build.yml")
app = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")
runtime_path = "scripts/verify_authenticated_runtime_navigation.py" if (ROOT / "scripts/verify_authenticated_runtime_navigation.py").is_file() else "scripts/verify_runtime_navigation.py"
runtime = read(runtime_path).lower()
runtime_script = read("scripts/run_android_runtime_certification.sh").lower()

check("real emulator runner is configured", "reactivecircus/android-emulator-runner@v2" in workflow)
check(
    "connected Android instrumentation is executed by the extracted runner",
    "connecteddebugandroidtest" in runtime_script and "bash scripts/run_android_runtime_certification.sh" in workflow.lower(),
)
check("authenticated runtime navigation verification is executed", runtime_path.lower() in runtime_script)
check(
    "Status lifecycle certification runs after authenticated runtime navigation",
    "python3 scripts/verify_authenticated_status_lifecycle.py" in runtime_script and
    runtime_script.index("verify_authenticated_runtime_navigation.py") < runtime_script.index("verify_authenticated_status_lifecycle.py"),
)
check("runtime screenshots and UI hierarchies are uploaded", "fynx-runtime-visual-check-" in workflow.lower() and "fynx-runtime-screenshots/" in workflow.lower())
check("Home, Chat, Friends, Stories and Features are reachable", all(x in app for x in ['"Home"', '"Chats"', '"Friends"', '"Stories"', '"Features"']))
check("major secondary surfaces have real navigation routes", all(x in app for x in ['"Marketplace"', '"Calls"', '"Groups"', '"Notifications"', '"Privacy"', '"Money Tools"', '"AI"']))
check("runtime audit captures screenshots", "screencap" in runtime)
check("runtime audit captures UI hierarchy", "uiautomator" in runtime)
check("runtime audit uses the real authenticated CI account", 'fynx_e2e_username' in runtime and 'fynx_e2e_password' in runtime and 'backend_real_chat_target' in runtime and '/api/auth/login' in runtime)
check("runtime audit does not create test accounts or application data", '/api/auth/register' not in runtime and 'create test account' not in runtime and 'fake account' not in runtime)

for path in [
    "app/src/androidTest/java/com/fynx/app/FynxSmokeTest.kt",
    "app/src/androidTest/java/com/fynx/app/FynxDeepLinkContractTest.kt",
    "app/src/androidTest/java/com/fynx/app/FynxNetworkRecoveryContractTest.kt",
    "app/src/androidTest/java/com/fynx/app/FynxCallTransportHardeningTest.kt",
    "app/src/androidTest/java/com/fynx/app/R1AccountIsolationTest.kt",
]:
    check("required instrumentation test exists: " + path, (ROOT / path).is_file())

print("Large Badge #4 audit passed: full real-device user-journey certification is wired")
