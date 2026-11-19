#!/usr/bin/env python3
"""Regression contracts for the backend-independent Android runtime capture harness."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = (ROOT / "app/src/androidTest/java/com/fynx/app/ui/FynxRuntimeChatFixtureSeedTest.kt").read_text(encoding="utf-8")
CAMERA = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt").read_text(encoding="utf-8")
RUNNER = (ROOT / "scripts/run_android_runtime_certification.sh").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")

chat_command = (
    "FYNX_CHAT_GROUPS_VISUALS_ONLY=1 FYNX_LOCAL_VISUALS_ONLY=1 "
    "python3 scripts/verify_authenticated_runtime_navigation.py"
)
checks = {
    "offline fixture clears backend credentials": (
        'remove("access_token")' in FIXTURE
        and "FynxBackendClient.accessToken(context)" in FIXTURE
        and 'assertNull("Local visual fixture must never inherit a server credential"' in FIXTURE
    ),
    "offline fixture opens a local signed-in debug session": (
        '.putBoolean("signed_in", true)' in FIXTURE
        and 'FynxAuthStore.load(context).state == AuthState.SIGNED_IN' in FIXTURE
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
    "missing optional fixture directories are recorded honestly": (
        'if adb shell test -d "$fixture_remote"' in WORKFLOW
        and "NOT_CAPTURED: remote fixture directory was absent:" in WORKFLOW
        and 'fixture_status_file="$fixture_dir/capture-status.txt"' in WORKFLOW
        and 'exit "$runtime_status"' in WORKFLOW
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
