#!/usr/bin/env python3
"""Guard the isolated, read-only Status creator runtime certification."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
runtime = (ROOT / "scripts/verify_status_creator_runtime.py").read_text(encoding="utf-8")
runner = (ROOT / "scripts/run_android_runtime_certification.sh").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
status_gate = (ROOT / "scripts/verify_r5b_status.py").read_text(encoding="utf-8")

checks = [
    (
        "runtime check uses the live app session and does not force-stop or cold-start FYNX",
        'pidof' in runtime and 'refusing to cold-start' in runtime
        and 'force-stop' not in runtime and 'am start' not in runtime,
    ),
    (
        "runtime check navigates from Marketplace/My Shop through visible controls to Home",
        'Back to Marketplace' in runtime and 'find_click_target(current, "Home")' in runtime
        and 'Create your status' in runtime,
    ),
    (
        "Status creator entry verifies Voice and Text from the rendered UI",
        'tap("Create your status", "create", ("Voice", "Text"))' in runtime
        and 'find_click_target(creator, "Voice")' in runtime
        and 'find_click_target(creator, "Text")' in runtime,
    ),
    (
        "runtime check dismisses the creator without creating or submitting content",
        'input", "keyevent", "4"' in runtime
        and 'no status, text, photo, voice recording, or media is created or submitted' in runtime
        and 'FynxStatusClient' not in runtime and '/api/statuses' not in runtime,
    ),
    (
        "runtime check records UI hierarchy, screenshots, crash/ANR evidence and an explicit result",
        'authenticated-status-home.png' in runtime and 'authenticated-status-create.png' in runtime
        and 'FATAL EXCEPTION' in runtime and '## Result:' in runtime,
    ),
    (
        "runtime check is executed after the existing navigation capture on a green run",
        'python3 scripts/verify_status_creator_runtime.py' in runner
        and 'STATUS_EXIT=$?' in runner and 'TEST_EXIT="$STATUS_EXIT"' in runner,
    ),
    (
        "a red instrumentation run remains red even if the read-only Status check runs afterward",
        'instrumentation result remains $TEST_EXIT' in runner
        and runner.count('python3 scripts/verify_status_creator_runtime.py') >= 2,
    ),
    (
        "CI runs the Status runtime certification contract",
        'python3 scripts/verify_status_creator_runtime_contract.py' in workflow,
    ),
    (
        "CI syntax-checks the new runtime and contract scripts",
        'scripts/verify_status_creator_runtime.py' in workflow
        and 'scripts/verify_status_creator_runtime_contract.py' in workflow
        and 'bash -n scripts/run_android_runtime_certification.sh' in workflow,
    ),
    (
        "existing Status source gate still verifies the Home creator callback and Voice/Text choices",
        'Home Status plus opens the full Add status hub' in status_gate
        and 'Add status exposes video recording and gallery selection' in status_gate
        and 'Add status Voice opens the composer in voice mode' in status_gate,
    ),
]

failed = []
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
    if not ok:
        failed.append(name)

if failed:
    raise SystemExit("Status runtime certification contract RED: " + "; ".join(failed))
print(f"Status creator runtime certification contract GREEN ({len(checks)} checks)")
