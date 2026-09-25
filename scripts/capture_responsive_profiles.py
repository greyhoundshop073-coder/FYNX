#!/usr/bin/env python3
"""Capture the same installed FYNX APK at small/medium/large phone viewports."""
# Large Badge 15 full-runtime evidence capture.
from pathlib import Path
import subprocess, time, re

ROOT=Path("fynx-runtime-screenshots"); ROOT.mkdir(exist_ok=True)
profiles={"small":(720,1280),"medium":(1080,1920),"large":(1440,2560)}
PACKAGE="com.fynx.app"

def run(*args):
    return subprocess.run(args,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=False,timeout=30)

def dump(name):
    p=ROOT/f"responsive-{name}.xml"
    r=run("adb","shell","uiautomator","dump","/sdcard/fynx-responsive.xml")
    if r.returncode!=0: return False
    raw=subprocess.run(["adb","exec-out","cat","/sdcard/fynx-responsive.xml"],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=False).stdout
    if not raw.startswith(b"<?xml"): return False
    p.write_bytes(raw); return True

def shot(name):
    with (ROOT/f"responsive-{name}.png").open("wb") as out:
        return run("adb","exec-out","screencap","-p") if out.write(subprocess.run(["adb","exec-out","screencap","-p"],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=False).stdout) else None

orig=run("adb","shell","wm","size").stdout.strip()
m=re.search(r"(\d+)x(\d+)",orig)
original=m.group(1)+"x"+m.group(2) if m else None
fail=[]
try:
    for name,(w,h) in profiles.items():
        r=run("adb","shell","wm","size",f"{w}x{h}")
        if r.returncode!=0: fail.append(f"{name}: unable to set viewport {w}x{h}"); continue
        time.sleep(1)
        run("adb","shell","am","force-stop",PACKAGE)
        start=run("adb","shell","am","start","-W","-a","android.intent.action.VIEW","-d","fynx://home",PACKAGE)
        time.sleep(1.5)
        out=ROOT/f"responsive-{name}.png"
        with out.open("wb") as f:
            subprocess.run(["adb","exec-out","screencap","-p"],stdout=f,stderr=subprocess.STDOUT,check=False)
        if start.returncode!=0 or not out.exists() or out.stat().st_size<1000: fail.append(f"{name}: home launch/screenshot failed")
        if not dump(name): fail.append(f"{name}: UI hierarchy capture failed")
finally:
    if original:
        run("adb","shell","wm","size",original)
        time.sleep(1)

(ROOT/"responsive-capture.txt").write_text(
    "Responsive runtime profiles captured from the installed APK.\n"
    + "\n".join(f"{k}: {v[0]}x{v[1]}" for k,v in profiles.items())
    + "\nOriginal viewport: "+(original or "unknown")+"\n"
    + ("Failures: "+"; ".join(fail)+"\n" if fail else "Result: GREEN\n"),
    encoding="utf-8")
print((ROOT/"responsive-capture.txt").read_text())
raise SystemExit(1 if fail else 0)
