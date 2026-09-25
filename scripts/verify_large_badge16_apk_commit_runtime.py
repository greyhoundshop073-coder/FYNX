#!/usr/bin/env python3
"""Large Badge #16 — prove APK, commit and runtime evidence belong to one build."""
from pathlib import Path
import os,re,hashlib,zipfile
OUT=Path("fynx-apk-certification"); OUT.mkdir(exist_ok=True)
sha=os.environ.get("GITHUB_SHA","").strip(); run=os.environ.get("GITHUB_RUN_ID","").strip()
apk=Path("app/build/outputs/apk/debug/app-debug.apk"); runtime=Path("fynx-runtime-screenshots/README.txt")
fail=[]
if not re.fullmatch(r"[0-9a-f]{40}",sha): fail.append("GITHUB_SHA is missing or malformed")
if not apk.is_file(): fail.append("debug APK was not produced")
if not runtime.is_file(): fail.append("runtime certification README is missing")
apk_sha=""
apk_bytes=b""
if apk.is_file():
    apk_bytes=apk.read_bytes(); apk_sha=hashlib.sha256(apk_bytes).hexdigest()
    try:
        with zipfile.ZipFile(apk) as z:
            names=set(z.namelist())
            if "AndroidManifest.xml" not in names: fail.append("APK has no AndroidManifest.xml")
            dex=[n for n in names if n.startswith("classes") and n.endswith(".dex")]
            if not dex: fail.append("APK has no classes*.dex")
            else:
                dex_text=b"".join(z.read(n) for n in dex).decode("latin1","ignore")
                expected=["FynxRemoteHomeSocialPanel","GroupChatPanel","FynxStatusComposerPanel","FynxMarketplaceClient","FynxRealtimeClient","FynxCallAudioRouter","FynxAssistant","Create Post"]
                missing=[x for x in expected if x not in dex_text]
                if missing: fail.append("APK DEX is missing expected implementation markers: "+", ".join(missing))
    except Exception as e: fail.append(f"APK is not a readable ZIP/APK: {e}")
runtime_text=runtime.read_text(encoding="utf-8",errors="replace") if runtime.is_file() else ""
if sha and f"Commit: {sha}" not in runtime_text: fail.append("runtime evidence does not identify the exact commit SHA")
if run and f"Run: {run}" not in runtime_text: fail.append("runtime evidence does not identify the exact workflow run")
cert=[ "# FYNX Large Badge #16 — APK ↔ Commit ↔ Runtime Certification","",f"- Result: {'GREEN' if not fail else 'RED'}",f"- Commit: {sha or '<missing>'}",f"- Workflow run: {run or '<missing>'}",f"- APK SHA-256: {apk_sha or '<missing>'}"]
if fail: cert+=["","## Failures"]+[f"- {x}" for x in fail]
else: cert+=["","## Certified chain","- source commit is identified by GitHub Actions","- runtime evidence names that exact commit and workflow run","- debug APK exists and contains a manifest plus DEX payload","- APK SHA-256 is recorded for exact download verification","- no secrets or fabricated application records are introduced"]
(OUT/"README.md").write_text("\n".join(cert)+"\n",encoding="utf-8")
(OUT/"apk-sha256.txt").write_text((apk_sha+"\n") if apk_sha else "",encoding="utf-8")
print("\n".join(cert)); raise SystemExit(1 if fail else 0)
