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
    """Capture UI hierarchy reliably across Android 15 emulator shell output variants."""
    p=ROOT/f"responsive-{name}.xml"
    remote="/sdcard/fynx-responsive.xml"
    for _ in range(3):
        run("adb","shell","uiautomator","dump",remote)
        pulled=ROOT/f".responsive-{name}.xml"
        r=run("adb","pull",remote,str(pulled))
        if r.returncode==0 and pulled.exists():
            raw=pulled.read_bytes()
            # adb pull gives the actual XML without the shell's diagnostic text.
            if raw.lstrip().startswith(b"<?xml"):
                p.write_bytes(raw)
                pulled.unlink(missing_ok=True)
                return True
        pulled.unlink(missing_ok=True)
        time.sleep(1)
    return False

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
        ready=False
        for _ in range(12):
            if dump(name):
                xml= (ROOT/f"responsive-{name}.xml").read_text(encoding="utf-8",errors="replace")
                markers=("FYNX","Home","Chat","Friends","Status")
                if any(marker in xml for marker in markers):
                    ready=True
                    break
            time.sleep(1)
        out=ROOT/f"responsive-{name}.png"
        with out.open("wb") as f:
            subprocess.run(["adb","exec-out","screencap","-p"],stdout=f,stderr=subprocess.STDOUT,check=False)
        if start.returncode!=0 or not ready: fail.append(f"{name}: authenticated FYNX Home did not become ready before responsive capture")
        if not out.exists() or out.stat().st_size<1000: fail.append(f"{name}: home screenshot failed")
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
