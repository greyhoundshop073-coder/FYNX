#!/usr/bin/env python3
"""Build an evidence-driven Home lifecycle certification matrix."""
from pathlib import Path
import sys
root=Path("fynx-runtime-screenshots")
report=root/"FYNX-authenticated-runtime.md"
if not report.exists():
    print("HOME LIFECYCLE CERTIFICATION RED: authenticated runtime report is missing")
    raise SystemExit(1)
text=report.read_text(encoding="utf-8",errors="replace")
checks=[
("1","cold open","PASS real account authenticated through the FYNX login flow"),
("2","cached/offline open",None),("3","online refresh",None),("4","long scroll/pagination",None),
("5","new-post arrival without scroll jump",None),("6","image and video playback",None),
("7","post interaction",None),("8","failed interaction and rollback",None),("9","leave/return",None),
("10","app restart",None),("11","offline while browsing",None),("12","reconnect and reconciliation",None),
("13","Status open/return","PASS authenticated Home -> stories screenshot/UI hierarchy"),
("14","Discovery open/return",None),("15","Marketplace open/return",None),("16","Profile/navigation open/return",None),
("17","repeated Home navigation",None),("18","privacy/block/delete reconciliation",None),
("19","account isolation",None),("20","authenticated runtime APK verification","PASS real account authenticated through the FYNX login flow")]
out=["# FYNX Home Lifecycle Certification Evidence","",
     "Missing runtime evidence is YELLOW; it is never promoted to GREEN by static checks.",""]
yellow=[]
for n,name,needle in checks:
    ok=bool(needle and needle in text)
    status="GREEN" if ok else "YELLOW"
    out.append(f"- {n}. {name}: **{status}**")
    if not ok: yellow.append(name)
if "## Result: GREEN" in text:
    out.append("- Authenticated runtime harness: **GREEN**")
elif "## Result: RED" in text:
    out.append("- Authenticated runtime harness: **RED**"); yellow.append("runtime harness")
else:
    out.append("- Authenticated runtime harness: **UNKNOWN**"); yellow.append("runtime harness")
out += ["","Certification rule: all applicable items must be GREEN before HOME COMPLETE / CERTIFIED.",
        f"Current certification state: **{'YELLOW — runtime evidence remains incomplete' if yellow else 'GREEN'}**"]
(root/"FYNX-home-lifecycle-certification.md").write_text("\n".join(out)+"\n",encoding="utf-8")
print("\n".join(out))
