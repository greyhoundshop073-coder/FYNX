#!/usr/bin/env python3
"""Regression contracts for the backend-independent Android runtime capture harness."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = (ROOT / "app/src/androidTest/java/com/fynx/app/ui/FynxRuntimeChatFixtureSeedTest.kt").read_text(encoding="utf-8")
CAMERA = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt").read_text(encoding="utf-8")
RUNNER = (ROOT / "scripts/run_android_runtime_certification.sh").read_text(encoding="utf-8")
RUNTIME_NAV = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")
STATUS_RUNTIME = (ROOT / "scripts/verify_status_creator_runtime.py").read_text(encoding="utf-8")
CHAT_PANEL = (ROOT / "app/src/main/java/com/fynx/app/ui/ChatsPanel.kt").read_text(encoding="utf-8")
GROUPS_PANEL = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupsPanel.kt").read_text(encoding="utf-8")
GROUP_CHAT_PANEL = (ROOT / "app/src/main/java/com/fynx/app/ui/GroupChatPanel.kt").read_text(encoding="utf-8")
EMULATOR_WRAPPER = (ROOT / "scripts/run_android_runtime_emulator_certification.sh").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
CONTACTS_PANEL = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxContactsPanel.kt").read_text(encoding="utf-8")

chat_command = (
    "FYNX_CHAT_GROUPS_VISUALS_ONLY=1 FYNX_LOCAL_VISUALS_ONLY=1 "
    "python3 scripts/verify_authenticated_runtime_navigation.py"
)
workflow_script = WORKFLOW.split("script: |", 1)[1].split("\n        env:", 1)[0]
workflow_commands = [line.strip() for line in workflow_script.splitlines() if line.strip()]
checks = {
    "emulator runner executes compound certification through one Bash script": (
        workflow_commands == ["bash scripts/run_android_runtime_emulator_certification.sh"]
        and "if [ \"$preflight_status\" -eq 0 ]; then" in EMULATOR_WRAPPER
        and 'exit "$runtime_status"' in EMULATOR_WRAPPER
    ),
    "emulator wrapper records optional fixture capture truthfully": (
        'if adb shell test -d "$fixture_remote"' in EMULATOR_WRAPPER
        and "NOT_CAPTURED: remote fixture directory was absent:" in EMULATOR_WRAPPER
        and 'fixture_status_file="$fixture_dir/capture-status.txt"' in EMULATOR_WRAPPER
    ),
    "offline fixture clears backend credentials": (
        'remove("access_token")' in FIXTURE
        and "FynxBackendClient.accessToken(context)" in FIXTURE
        and 'assertNull("Local visual fixture must never inherit a server credential"' in FIXTURE
    ),
    "offline fixture opens a local signed-in debug session": (
        '.putBoolean("signed_in", true)' in FIXTURE
        and 'FynxAuthStore.load(context).state == AuthState.SIGNED_IN' in FIXTURE
    ),
    "local-only visual capture uses the exact disposable fixture identity, not server E2E credentials": (
        'USERNAME="fynx_local_runtime" if FORCE_LOCAL_VISUALS else os.environ.get("FYNX_E2E_USERNAME","").strip()' in RUNTIME_NAV
        and '-e username "fynx_local_runtime"' in RUNNER
    ),
    "contacts screen gates previously granted permissions behind an account-scoped disclosure": (
        "FynxContactsDisclosure.PREFERENCES_NAME" in CONTACTS_PANEL
        and "FynxContactsDisclosure.preferenceKey(accountKey)" in CONTACTS_PANEL
        and "permission && disclosureAccepted" in CONTACTS_PANEL
        and 'Text("Before you use contact matching"' in CONTACTS_PANEL
    ),
    "Groups-tab capture waits for the actual New group action in the ChatsPanel Groups section": (
        'groups_xml = tap_control(chat_again, ["Groups"], "chat-groups-groups-tab", ["New group"])' in RUNTIME_NAV
        and 'Text("＋ New group")' in CHAT_PANEL
    ),
    "New group opens the dedicated Groups manager before the Create group dialog": (
        '["New group"],\n        "chat-groups-open-groups-manager"' in RUNTIME_NAV
        and '["Create group"],\n        "chat-groups-open-create-dialog"' in RUNTIME_NAV
        and 'Icon(Icons.Default.Add, "Create group")' in GROUPS_PANEL
    ),
    "Status runtime unwinds group and chat screens using visible controls before Home": (
        'for back_label in ("Back", "Back to Chats", "Back to Marketplace")' in STATUS_RUNTIME
        and 'find_click_target(current, back_label)' in STATUS_RUNTIME
        and 'IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ArrowBack, "Back"' in GROUP_CHAT_PANEL
    ),
    "camera callback uses a public SAM interface": (
        "fun interface FynxCameraEditedCaptureCallback" in CAMERA
        and "private fun interface FynxCameraEditedCaptureCallback" not in CAMERA
    ),
    "camera composable no longer exposes a six-argument FunctionN callback": (
        "onCapturedWithTextEdits: FynxCameraEditedCaptureCallback? = null" in CAMERA
        and "List<FynxCameraTextOverlay>, String, String) -> Unit" not in CAMERA
    ),
    "camera wrapper forwards the typed callback directly": (
        "onCapturedWithTextEdits = onCapturedWithTextEdits" in CAMERA
    ),
    "Chat and Groups verifier exit code is captured by the shell": (
        chat_command + "\n    NAV_EXIT=$?" in RUNNER
        and chat_command + "  NAV_EXIT=$?" not in RUNNER
    ),
    "failed phone-number lookups are not presented as users missing from FYNX": (
        "failedContactNumbers" in CONTACTS_PANEL
        and "FynxContactMatchState.UNAVAILABLE" in CONTACTS_PANEL
        and 'FynxContactMatchState.UNAVAILABLE -> "Could not check"' in CONTACTS_PANEL
        and 'Text("Match unavailable"' in CONTACTS_PANEL
    ),
    "contacts disclosure copy explains the data flow before allowing access": (
        "normalised phone number to FYNX servers over HTTPS" in CONTACTS_PANEL
        and "The saved contact name is not included" in CONTACTS_PANEL
        and 'Text("Not now")' in CONTACTS_PANEL
        and 'Text("Continue")' in CONTACTS_PANEL
    ),
}
failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(("PASS: " if passed else "FAIL: ") + name)
if failed:
    print(file=sys.stderr)
    print(f"{len(failed)} runtime harness contract(s) failed.", file=sys.stderr)
    raise SystemExit(1)
print(f"All {len(checks)} Android runtime harness contracts passed.")
