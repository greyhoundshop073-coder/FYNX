#!/usr/bin/env python3
"""FYNX screenshot/layout regression gate.

Baselines are intentionally opt-in: until an approved PNG is placed in
visual-baselines/, the gate reports READY/PENDING and does not fail CI.
Once a baseline exists, every listed surface is compared against the runtime
PNG captured by the emulator. This detects layout/spacing/order changes; it
never auto-rearranges the FYNX UI.
"""
from __future__ import annotations
import json, shutil, subprocess, sys
from pathlib import Path

BASE=Path("visual-baselines")
RUNTIME=Path("fynx-runtime-screenshots")
REPORT=Path("fynx-visual-regression")
REPORT.mkdir(parents=True, exist_ok=True)
MANIFEST=BASE/"manifest.json"

if not MANIFEST.exists():
    (REPORT/"README.txt").write_text(
        "FYNX visual regression: PENDING\n"
        "No manifest exists; no screenshot comparison was performed.\n",
        encoding="utf-8")
    print("FYNX visual regression: PENDING (manifest missing)")
    raise SystemExit(0)

data=json.loads(MANIFEST.read_text(encoding="utf-8"))
surfaces=data.get("surfaces", [])
threshold=int(data.get("max_changed_pixels", 0))

if not surfaces:
    (REPORT/"README.txt").write_text(
        "FYNX visual regression: PENDING\nNo approved baseline surfaces are configured yet.\n",
        encoding="utf-8")
    print("FYNX visual regression: PENDING (no approved baselines)")
    raise SystemExit(0)

if shutil.which("compare") is None or shutil.which("identify") is None:
    print("ERROR: ImageMagick compare/identify is required when visual baselines are active.")
    raise SystemExit(2)

failures=[]
pending=[]
for item in surfaces:
    name=item["name"]
    baseline=BASE/item["baseline"]
    runtime=RUNTIME/item["runtime"]
    if not baseline.exists():
        pending.append(f"{name}: baseline missing ({baseline})")
        continue
    if not runtime.exists():
        failures.append(f"{name}: runtime screenshot missing ({runtime})")
        continue

    dim_base=subprocess.run(["identify","-format","%wx%h",str(baseline)],text=True,capture_output=True)
    dim_run=subprocess.run(["identify","-format","%wx%h",str(runtime)],text=True,capture_output=True)
    if dim_base.returncode or dim_run.returncode:
        failures.append(f"{name}: unable to read image dimensions")
        continue
    if dim_base.stdout != dim_run.stdout:
        failures.append(f"{name}: dimensions differ baseline={dim_base.stdout} runtime={dim_run.stdout}")
        continue

    metric=subprocess.run(
        ["compare","-metric","AE",str(baseline),str(runtime),"/tmp/fynx-visual-diff.png"],
        text=True,capture_output=True)
    raw=(metric.stderr or metric.stdout or "").strip()
    try:
        changed=int(float(raw.split()[0]))
    except (ValueError,IndexError):
        failures.append(f"{name}: ImageMagick comparison failed: {raw}")
        continue
    allowed=int(item.get("max_changed_pixels", threshold))
    (REPORT/f"{name}-metric.txt").write_text(
        f"baseline={baseline}\nruntime={runtime}\nchanged_pixels={changed}\nallowed={allowed}\n",
        encoding="utf-8")
    if changed > allowed:
        failures.append(f"{name}: {changed} changed pixels exceeds allowed {allowed}")

result="GREEN" if not failures else "RED"
lines=[
    "# FYNX Visual/Layout Regression",
    f"- Result: {result}",
    f"- Commit: {__import__('os').environ.get('GITHUB_SHA','local')}",
    f"- Runtime screenshots: {RUNTIME}",
    "",
]
if pending:
    lines += ["## Pending baseline approval"] + [f"- {x}" for x in pending] + [""]
if failures:
    lines += ["## Failures"] + [f"- {x}" for x in failures]
if not failures and not pending:
    lines.append("All approved visual baselines matched within configured thresholds.")
(REPORT/"README.md").write_text("\n".join(lines)+"\n", encoding="utf-8")
print("\n".join(lines))
raise SystemExit(1 if failures else 0)
