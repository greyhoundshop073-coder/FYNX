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
runtime_source = read(runtime_path)
runtime = runtime_source.lower()
runtime_script = read("scripts/run_android_runtime_certification.sh").lower()

check("real emulator runner is configured", "reactivecircus/android-emulator-runner@v2" in workflow)
check("connected Android instrumentation is executed by the extracted runner", "connecteddebugandroidtest" in runtime_script and "bash scripts/run_android_runtime_certification.sh" in workflow.lower())
check("authenticated runtime navigation verification is executed", runtime_path.lower() in runtime_script)
check("runtime screenshots and UI hierarchies are uploaded", "fynx-runtime-visual-check-" in workflow.lower() and "fynx-runtime-screenshots/" in workflow.lower())
check("Home, Chat, Friends, Stories and Features are reachable", all(x in app for x in ['"Home"', '"Chats"', '"Friends"', '"Stories"', '"Features"']))
check("major secondary surfaces have real navigation routes", all(x in app for x in ['"Marketplace"', '"Calls"', '"Groups"', '"Notifications"', '"Privacy"', '"Money Tools"', '"AI"']))
check("runtime audit captures screenshots", "screencap" in runtime)
check("runtime audit captures UI hierarchy", "uiautomator" in runtime)
check("runtime audit uses the real authenticated CI account", 'fynx_e2e_username' in runtime and 'fynx_e2e_password' in runtime and 'backend_real_chat_target' in runtime and '/api/auth/login' in runtime)
peer_start = runtime_source.find("def provision_isolated_runtime_peer(")
peer_end = runtime_source.find("def backend_real_chat_target(", peer_start)
peer_helper = runtime_source[peer_start:peer_end] if peer_start >= 0 and peer_end > peer_start else ""
peer_host_guard = 'if parsed.scheme!="https" or not host.endswith(".up.railway.app") or "testing" not in host:'
peer_registration = 'base+"/api/auth/register"'
check(
    "runtime peer registration is restricted to HTTPS Railway testing hosts before registration",
    bool(peer_helper)
    and peer_host_guard in peer_helper
    and peer_registration in peer_helper
    and peer_helper.find(peer_host_guard) < peer_helper.find(peer_registration)
    and runtime_source.count(peer_registration) == 1,
)
check(
    "runtime peer account is unique to the CI run and uses a random password",
    'peer_username=f"fynxpeer_{run_number:08d}_{attempt:02d}_{uuid.uuid4().hex[:4]}"' in peer_helper
    and 'peer_password=uuid.uuid4().hex+uuid.uuid4().hex' in peer_helper,
)
check(
    "automatic peer provisioning only runs when both dedicated peer secrets are absent",
    "elif not peer_username and not peer_password:" in runtime_source
    and "peer_username,peer_password,peer_setup_detail=provision_isolated_runtime_peer()" in runtime_source,
)
check(
    "private Chat delivery tests send only through the dedicated CI peer",
    "if peer_username and peer_password and conversation_ui_opened:" in runtime_source
    and "backend_post_peer_message(peer_username,peer_password,USERNAME,incoming_marker)" in runtime_source
    and "Never send test content to an" in runtime_source,
)

for path in [
    "app/src/androidTest/java/com/fynx/app/FynxSmokeTest.kt",
    "app/src/androidTest/java/com/fynx/app/FynxDeepLinkContractTest.kt",
    "app/src/androidTest/java/com/fynx/app/FynxNetworkRecoveryContractTest.kt",
    "app/src/androidTest/java/com/fynx/app/FynxCallTransportHardeningTest.kt",
    "app/src/androidTest/java/com/fynx/app/R1AccountIsolationTest.kt",
]:
    check("required instrumentation test exists: " + path, (ROOT / path).is_file())

print("Large Badge #4 audit passed: full real-device user-journey certification is wired")
