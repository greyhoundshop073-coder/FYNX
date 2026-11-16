#!/usr/bin/env python3
"""Release/CI guard for FYNX repair workflows: generated fixes must use reviewable PRs."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
CANONICAL = "integration/fynx-single-apk-20261013"
WORKFLOWS = (
    ".github/workflows/communication-repair.yml",
    ".github/workflows/contact-share-repair.yml",
    ".github/workflows/media-viewer-repair.yml",
)

def contract_errors(source: str):
    errors = []
    if not re.search(r"(?m)^\s*branches:\s*\n\s*-\s*"+re.escape(CANONICAL)+r"\s*$", source):
        errors.append("push events are not restricted to the canonical integration branch")
    if f"ref: {CANONICAL}" not in source:
        errors.append("workflow does not check out the canonical integration branch")
    if not re.search(r"(?m)^\s*contents:\s*write\s*$", source):
        errors.append("workflow does not declare contents write permission")
    if not re.search(r"(?m)^\s*pull-requests:\s*write\s*$", source):
        errors.append("workflow does not declare pull-request write permission")
    if 'git push origin "HEAD:refs/heads/$BRANCH_NAME"' not in source:
        errors.append("workflow does not push a uniquely named review branch")
    if "gh pr create" not in source or f"--base {CANONICAL}" not in source:
        errors.append("workflow does not open a pull request against the canonical integration branch")
    if re.search(r"git\s+push\s+origin\s+(?:HEAD:)?(?:main|refs/heads/main)(?:\s|$)", source, re.I):
        errors.append("workflow contains a direct push to main")
    if re.search(r"(?m)^\s*ref:\s*main\s*$", source):
        errors.append("workflow checks out main instead of the canonical integration branch")
    return errors

def self_test():
    safe = f'''branches:
      - {CANONICAL}
permissions:
  contents: write
  pull-requests: write
with:
  ref: {CANONICAL}
run: |
  git push origin "HEAD:refs/heads/$BRANCH_NAME"
  gh pr create --base {CANONICAL}
'''
    assert not contract_errors(safe), "safe-workflow positive test failed"
    unsafe = safe.replace('git push origin "HEAD:refs/heads/$BRANCH_NAME"', "git push origin HEAD:main")
    assert any("direct push to main" in item for item in contract_errors(unsafe)), "direct-main negative test failed"
    wrong_base = safe.replace(f"--base {CANONICAL}", "--base main")
    assert any("pull request" in item for item in contract_errors(wrong_base)), "wrong-base negative test failed"

def main():
    self_test()
    failures = []
    for relative in WORKFLOWS:
        path = ROOT / relative
        if not path.is_file():
            failures.append(f"{relative}: file is missing")
            continue
        errors = contract_errors(path.read_text(encoding="utf-8"))
        if errors:
            failures.extend(f"{relative}: {error}" for error in errors)
        else:
            print(f"PASS: {relative} routes automation through a canonical-branch PR")
    if failures:
        for failure in failures:
            print("FAIL: " + failure)
        raise SystemExit(1)
    print("PASS: safe repair workflow self-tests (positive + negative)")
    print(f"Safe repair workflow contract passed ({len(WORKFLOWS)} workflows)")

if __name__ == "__main__":
    main()
