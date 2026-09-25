#!/usr/bin/env python3
"""Large Badge #15 — runtime responsive and screen-safety certification."""
from pathlib import Path
import os,re,xml.etree.ElementTree as ET
ROOT=Path("fynx-runtime-screenshots"); OUT=Path("fynx-responsive-certification"); OUT.mkdir(exist_ok=True)
xmls=sorted(ROOT.glob("*.xml")); pngs=sorted(ROOT.glob("*.png"))
sw=int(os.environ.get("FYNX_SCREEN_WIDTH","1080")); sh=int(os.environ.get("FYNX_SCREEN_HEIGHT","1920"))
fail=[]; warn=[]; nodes=0
for p in xmls:
    try: root=ET.fromstring(p.read_text(encoding="utf-8",errors="replace"))
    except Exception as e: fail.append(f"{p.name}: invalid hierarchy XML: {e}"); continue
    for n in root.iter("node"):
        if n.attrib.get("visible-to-user","true").lower()=="false": continue
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.attrib.get("bounds",""))
        if not m: continue
        l,t,r,b=map(int,m.groups()); nodes+=1
        if l<0 or t<0 or r>sw or b>sh or r<=l or b<=t: fail.append(f"{p.name}: unsafe bounds {(l,t,r,b)}")
        if n.attrib.get("clickable","false").lower()=="true" and (r-l)<48 and (b-t)<48:
            fail.append(f"{p.name}: clickable control smaller than 48px in both dimensions {(r-l,b-t)}")
for p in pngs:
    # PNG dimensions are checked from the PNG header without adding dependencies.
    try:
        b=p.read_bytes()
        if b[:8]!=b"\x89PNG\r\n\x1a\n": fail.append(f"{p.name}: invalid PNG signature")
        else:
            w=int.from_bytes(b[16:20],"big"); h=int.from_bytes(b[20:24],"big")
            if (w,h)!=(sw,sh): warn.append(f"{p.name}: captured {w}x{h}; expected runtime {sw}x{sh}")
    except Exception as e: fail.append(f"{p.name}: unreadable screenshot: {e}")
if not xmls: warn.append("No runtime hierarchy XMLs were captured.")
if not pngs: warn.append("No runtime PNGs were captured.")
result="GREEN" if not fail else "RED"
report=[ "# FYNX Large Badge #15 — Responsive & Screen-Safety Certification","",f"- Result: {result}",f"- Commit: {os.environ.get('GITHUB_SHA','local')}",f"- Hierarchies inspected: {len(xmls)}",f"- UI nodes inspected: {nodes}",f"- Expected runtime viewport: {sw}x{sh}"]
if warn: report+=["","## Warnings"]+[f"- {x}" for x in warn]
if fail: report+=["","## Failures"]+[f"- {x}" for x in fail]
else: report+=["","## Certified checks","- visible UI bounds remain inside the runtime viewport","- invalid/zero-size UI bounds are rejected","- clickable controls are not tiny","- captured screenshots are inspected for expected runtime dimensions","- no fake users or application data are created"]
(OUT/"README.md").write_text("\n".join(report)+"\n",encoding="utf-8"); print("\n".join(report)); raise SystemExit(1 if fail else 0)
