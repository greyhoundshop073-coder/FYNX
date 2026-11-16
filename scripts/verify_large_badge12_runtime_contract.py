#!/usr/bin/env python3
"""Mandatory static contract for genuine Large Badge #12 authenticated runtime certification."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
CANONICAL = "integration/fynx-single-apk-20261013"
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
runtime = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")
runner = (ROOT / "scripts/run_android_runtime_certification.sh").read_text(encoding="utf-8")

checks = [
    ("contract verifier is wired into Android CI", "python3 scripts/verify_large_badge12_runtime_contract.py" in workflow),
    ("dedicated FYNX runtime credentials come from GitHub Actions secrets",
     "FYNX_E2E_USERNAME: ${{ secrets.FYNX_E2E_USERNAME }}" in workflow and
     "FYNX_E2E_PASSWORD: ${{ secrets.FYNX_E2E_PASSWORD }}" in workflow),
    ("Android runtime step requires strict Badge 12 authentication",
     'FYNX_REQUIRE_BADGE12_AUTH: "1"' in workflow),
    ("all canonical-target pull requests run the runtime certification step",
     f"github.event.pull_request.base.ref == '{CANONICAL}'" in workflow),
    ("runtime reads username and password only from environment",
     'os.environ.get("FYNX_E2E_USERNAME"' in runtime and 'os.environ.get("FYNX_E2E_PASSWORD"' in runtime),
    ("runtime does not contain a literal password assignment",
     not re.search(r"""PASSWORD\s*=\s*["\'][^"\']+["\']""", runtime)),
    ("runtime does not create test accounts",
     "create test account" not in runtime.lower() and "register test account" not in runtime.lower()),
    ("strict runner mode checks the mandatory-auth flag",
     'if [ "${FYNX_REQUIRE_BADGE12_AUTH:-0}" = "1" ]; then' in runner),
    ("strict runner blocks when dedicated credentials are absent",
     "FYNX_E2E_USERNAME" in runner and "FYNX_E2E_PASSWORD" in runner and "Result: BLOCKED" in runner),
    ("strict runner verifies backend and database health before login",
     'b?.service!=="fynx-backend"' in runner and 'b?.database!=="ready"' in runner),
    ("strict runner strips local and visual-only modes",
     "unset FYNX_LOCAL_VISUALS_ONLY" in runner and "unset FYNX_MARKETPLACE_VISUALS_ONLY" in runner and
     "unset FYNX_AI_VISUALS_ONLY" in runner),
    ("strict runner requires a real login marker and GREEN runtime report",
     "PASS real account authenticated through the FYNX login flow" in runner and 'grep -Fq "## Result: GREEN"' in runner),
    ("strict runner separately requires server-authenticated Home Feed evidence",
     'FYNX_HOME_FEED_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py' in runner and
     "Session: server-authenticated Home Feed session." in runner),
    ("Chat, Friends and Stories screenshots are mandatory",
     all(name in runtime for name in ['"authenticated-chat.png"', '"authenticated-friends.png"', '"authenticated-stories.png"'])),
    ("Chat, Friends and Stories accessibility hierarchies are mandatory",
     all(name in runtime for name in ['"authenticated-chat.xml"', '"authenticated-friends.xml"', '"authenticated-stories.xml"'])),
    ("Home, Features, AI and Wallet remain part of authenticated evidence",
     all(name in runtime for name in ['"authenticated-home.png"', '"authenticated-features.png"', '"authenticated-ai.png"', '"authenticated-wallet.png"'])),
    ("runtime writes an explicit result record",
     "## Result: " in runtime),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f'{"PASS" if ok else "FAIL"}: {name}')
if failed:
    print("LARGE BADGE #12 AUTHENTICATED RUNTIME CONTRACT: RED")
    sys.exit(1)
print(f"LARGE BADGE #12 AUTHENTICATED RUNTIME CONTRACT: GREEN ({len(checks)} checks)")
