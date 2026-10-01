#!/usr/bin/env python3
"""FYNX external-service readiness inventory; no secrets and no fake service claims."""
from pathlib import Path
import os,re
ROOT=Path(".github/workflows/android-build.yml").read_text(encoding="utf-8")
lines=[]
def item(name,status,detail):
    lines.append(f"- **{name}: {status}** — {detail}")
item("GitHub Actions","ACTIVE","Primary CI/build, emulator, artifacts and certification runner.")
item("Firebase / FCM","ACTIVE","Firebase Messaging is wired in the Android app and server-side notification delivery; credentials remain server-side.")
item("Google Cloud Workload Identity","ACTIVE","GitHub Actions authenticates to the FYNX GCP project without storing a long-lived key in the repository.")
item("Firebase Test Lab / Robo","BLOCKED","Previously attempted; the WIF service account could not enable the required Tool Results API/permissions. No fake GREEN result is claimed.")
bs_user=bool(os.environ.get("BROWSERSTACK_USERNAME","").strip()); bs_key=bool(os.environ.get("BROWSERSTACK_ACCESS_KEY","").strip())
item("BrowserStack App Automate","READY" if bs_user and bs_key else "PARTIAL","Real-device upload is enabled only when both repository secrets are configured; current workflow otherwise records an intentional skip.")
item("Maestro","ACTIVE","Launch/auth journey runs inside the same emulator used for runtime certification.")
item("Render backend","ACTIVE","Production backend reachability and backend integration gates are already part of CI.")
item("Visual regression baselines","PENDING APPROVAL","Runtime screenshots are captured and compared when approved baseline PNGs exist; missing baselines are reported rather than invented.")
out=Path("fynx-external-service-inventory"); out.mkdir(exist_ok=True)
(out/"README.md").write_text("# FYNX External Service Readiness Inventory\n\n" + "\n".join(lines) + "\n",encoding="utf-8")
print("\n".join(lines))
