#!/usr/bin/env python3
"""Exercise real FYNX runtime navigation and emit a diagnostic report."""
from __future__ import annotations
import os, subprocess, time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path("fynx-runtime-screenshots"); ROOT.mkdir(parents=True,exist_ok=True)
PACKAGE="com.fynx.app"

def run(*args:str):
    return subprocess.run(args,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)

def dump_ui(path:Path)->str:
    run("adb","shell","uiautomator","dump","/sdcard/fynx-runtime.xml")
    pulled=run("adb","pull","/sdcard/fynx-runtime.xml",str(path))
    return path.read_text(encoding="utf-8",errors="replace") if pulled.returncode==0 and path.exists() else ""

def screenshot(path:Path)->None:
    with path.open("wb") as out: subprocess.run(["adb","exec-out","screencap","-p"],stdout=out,check=False)

def launch(uri:str,png:Path,xml:Path)->str:
    run("adb","shell","am","force-stop",PACKAGE)
    result=run("adb","shell","am","start","-W","-a","android.intent.action.VIEW","-d",uri,PACKAGE)
    time.sleep(2.5); screenshot(png)
    return dump_ui(xml) if result.returncode==0 else ""

def nodes(xml_text:str):
    if not xml_text: return []
    try: return list(ET.fromstring(xml_text).iter("node"))
    except ET.ParseError: return []

def find_control(xml_text:str,labels:list[str]):
    wanted=[x.lower() for x in labels]
    for node in nodes(xml_text):
        text=(node.attrib.get("text") or "").strip()
        desc=(node.attrib.get("content-desc") or "").strip()
        rid=(node.attrib.get("resource-id") or "").strip()
        hay=" | ".join((text,desc,rid)).lower()
        if any(label in hay for label in wanted):
            bounds=node.attrib.get("bounds","")
            try:
                left_top,right_bottom=bounds.split("][",1)
                left,top=map(int,left_top.strip("[]").split(","))
                right,bottom=map(int,right_bottom.strip("[]").split(","))
                return text or desc or rid,(left+right)//2,(top+bottom)//2
            except (ValueError,IndexError):
                pass
    return None

def tap_and_capture(name,xml_text,labels):
    control=find_control(xml_text,labels)
    if not control: return False,"control not found: "+", ".join(labels)
    label,x,y=control
    run("adb","shell","input","tap",str(x),str(y)); time.sleep(2)
    screenshot(ROOT/f"journey-{name}.png")
    dump_ui(ROOT/f"journey-{name}.xml")
    return True,f"tapped {label!r} at ({x},{y})"

report=["# FYNX Runtime Navigation Diagnostic","",
        f"- Commit: {os.environ.get('GITHUB_SHA','local')}",
        f"- Run: {os.environ.get('GITHUB_RUN_ID','local')}","",
        "Observed behavior from the built APK only. No fake application data is created.",""]
failures=[]

for name,uri in {"home":"fynx://home","stories":"fynx://stories","money":"fynx://money"}.items():
    xml=launch(uri,ROOT/f"route-{name}.png",ROOT/f"route-{name}.xml")
    ok=bool(xml) and (ROOT/f"route-{name}.png").stat().st_size>1000
    report.append(f"- {'PASS' if ok else 'FAIL'} static route {uri} -> screenshot/UI hierarchy captured")
    if not ok: failures.append("static route "+uri)

report+=["","## Visible UI journey"]
home_xml=launch("fynx://home",ROOT/"journey-home.png",ROOT/"journey-home.xml")
for name,labels in (("chat",["Chat"]),("stories",["Stories","Status"]),("money",["Money Tools","Money"])):
    ok,detail=tap_and_capture(name,home_xml,labels)
    report.append(f"- {'PASS' if ok else 'FAIL'} Home -> {name}: {detail}")
    if not ok: failures.append("Home -> "+name)
    home_xml=launch("fynx://home",ROOT/"journey-home-reset.png",ROOT/"journey-home-reset.xml")

report+=["","## Parameterized routes",
         "- Profile, Chat, Group and Call deep links require a real identifier; this diagnostic does not invent one.",
         "- Their parser/manifest contracts remain covered by the existing Android deep-link tests and manifest declarations.","",
         f"## Result: {'GREEN' if not failures else 'RED'}"]
if failures:
    report+=["","Broken connections observed:"]+["- "+x for x in failures]
(ROOT/"FYNX-runtime-diagnostic.md").write_text("\n".join(report)+"\n",encoding="utf-8")
print("\n".join(report))
raise SystemExit(1 if failures else 0)
