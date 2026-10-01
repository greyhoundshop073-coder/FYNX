#!/usr/bin/env python3
"""Static contract gate for Large Badge #12 authenticated runtime certification."""
from pathlib import Path
import re
import sys

root=Path(__file__).resolve().parents[1]
workflow=(root/".github/workflows/android-build.yml").read_text(encoding="utf-8")
runtime=(root/"scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")
checks=[
    ("workflow references FYNX_E2E_USERNAME secret", "FYNX_E2E_USERNAME" in workflow),
    ("workflow references FYNX_E2E_PASSWORD secret", "FYNX_E2E_PASSWORD" in workflow),
    ("runtime defines a production base URL", 'FYNX_PRODUCTION_BASE_URL' in runtime and 'https://fynx-ai-backend.onrender.com' in runtime),
    ("runtime reads username only from environment", 'os.environ.get("FYNX_E2E_USERNAME"' in runtime),
    ("runtime reads password only from environment", 'os.environ.get("FYNX_E2E_PASSWORD"' in runtime),
    ("runtime does not contain a literal password assignment", not re.search(r"PASSWORD\s*=\s*[\"\'][^\"\']+[\"\']", runtime)),
    ("runtime does not create test accounts", "create test account" not in runtime.lower() and "register test account" not in runtime.lower()),
    ("runtime records authenticated result", "## Result: " in runtime),
]
failed=[name for name,ok in checks if not ok]
for name,ok in checks:
    print(f'{"PASS" if ok else "FAIL"}: {name}')
if failed:
    print("LARGE BADGE #12 RUNTIME CONTRACT: RED")
    sys.exit(1)
print(f"LARGE BADGE #12 RUNTIME CONTRACT: GREEN ({len(checks)} checks)")
