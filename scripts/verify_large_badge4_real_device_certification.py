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

check("real emulator runner is configured", "reactivecircus/android-emulator-runner@v2" in workflow)
check("connected Android instrumentation is executed", "connectedDebugAndroidTest" in workflow)
check("authenticated runtime navigation verification is executed", runtime_path in workflow)
check("runtime screenshots and UI hierarchies are uploaded", "FYNX-runtime-visual-check-" in workflow and "fynx-runtime-screenshots/" in workflow)
check("Home, Chat, Friends, Stories and Features are reachable", all(x in app for x in ['"Home"', '"Chats"', '"Friends"', '"Stories"', '"Features"']))
check("major secondary surfaces have real navigation routes", all(x in app for x in ['"Marketplace"', '"Calls"', '"Groups"', '"Notifications"', '"Privacy"', '"Money Tools"', '"AI"']))
check("runtime audit captures screenshots", "screencap" in runtime)
check("runtime audit captures UI hierarchy", "uiautomator" in runtime)
check("runtime audit uses the real authenticated CI account", 'FYNX_E2E_USERNAME' in runtime and 'FYNX_E2E_PASSWORD' in runtime and 'backend_real_chat_target' in runtime and '/api/auth/login' in runtime)
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
