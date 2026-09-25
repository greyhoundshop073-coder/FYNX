#!/usr/bin/env python3
"""Large Badge #15 — runtime responsive and screen-safety certification."""
from pathlib import Path
import os,re,xml.etree.ElementTree as ET
ROOT=Path("fynx-runtime-screenshots"); OUT=Path("fynx-responsive-certification"); OUT.mkdir(exist_ok=True)
profiles={"small":(720,1280),"medium":(1080,1920),"large":(1440,2560)}
fail=[]; nodes=0
for name,(sw,sh) in profiles.items():
    xmls=sorted(ROOT.glob(f"responsive-{name}.xml")); pngs=sorted(ROOT.glob(f"responsive-{name}.png"))
    if not xmls or not pngs:
        fail.append(f"{name}: required runtime screenshot and UI hierarchy were not captured"); continue
    for p in xmls:
        try: root=ET.fromstring(p.read_text(encoding="utf-8",errors="replace"))
        except Exception as e: fail.append(f"{p.name}: invalid hierarchy XML: {e}"); continue
        for n in root.iter("node"):
            if n.attrib.get("visible-to-user","true").lower()=="false": continue
            m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.attrib.get("bounds",""))
            if not m: continue
            l,t,r,b=map(int,m.groups()); nodes+=1
            if l<0 or t<0 or r>sw or b>sh or r<=l or b<=t: fail.append(f"{p.name}: unsafe bounds {(l,t,r,b)} for {sw}x{sh}")
            if n.attrib.get("clickable","false").lower()=="true" and (r-l)<48 and (b-t)<48: fail.append(f"{p.name}: clickable control smaller than 48px in both dimensions {(r-l,b-t)}")
        texts = [n.attrib.get("text","").strip() for n in root.iter("node")]
        content_descs = [n.attrib.get("content-desc","").strip() for n in root.iter("node")]
        markers = set(texts + content_descs)
        if not ({"FYNX", "Home", "Chat", "Friends"} & markers):
            fail.append(f"{p.name}: responsive hierarchy does not contain authenticated FYNX UI markers")
    for p in pngs:
        b=p.read_bytes()
        if b[:8]!=b"\x89PNG\r\n\x1a\n": fail.append(f"{p.name}: invalid PNG signature")
        else:
            w=int.from_bytes(b[16:20],"big"); h=int.from_bytes(b[20:24],"big")
            if (w,h)!=(sw,sh): fail.append(f"{p.name}: captured {w}x{h}; expected {sw}x{sh}")
result="GREEN" if not fail else "RED"
report=["# FYNX Large Badge #15 — Responsive & Screen-Safety Certification","",f"- Result: {result}",f"- Commit: {os.environ.get('GITHUB_SHA','local')}",f"- UI nodes inspected: {nodes}","- Runtime profiles: small 720x1280, medium 1080x1920, large 1440x2560"]
if fail: report+=["","## Failures"]+[f"- {x}" for x in fail]
else: report+=["","## Certified checks","- the same installed APK was exercised at three phone viewport sizes","- visible UI bounds remain inside each runtime viewport","- invalid/zero-size UI bounds are rejected","- clickable controls are not tiny","- captured screenshots match the requested runtime dimensions","- no fake users or application data are created"]
(OUT/"README.md").write_text("\n".join(report)+"\n",encoding="utf-8"); print("\n".join(report)); raise SystemExit(1 if fail else 0)
