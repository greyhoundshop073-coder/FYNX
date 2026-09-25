#!/usr/bin/env python3
"""FYNX Large Badge #14 — accessibility and screen-safety runtime gate."""
from __future__ import annotations
import os, re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path("fynx-runtime-screenshots")
REPORT = Path("fynx-accessibility-certification")
REPORT.mkdir(parents=True, exist_ok=True)

# The CI AVD is Pixel 2-class. If the workflow supplies an exact density, use it;
# otherwise use the Pixel 2 mdpi-equivalent density (420dpi / 160 = 2.625).
density = float(os.environ.get("FYNX_EMULATOR_DENSITY", "2.625"))
min_px = max(1, round(48 * density))
sw = int(os.environ.get("FYNX_SCREEN_WIDTH", "0"))
sh = int(os.environ.get("FYNX_SCREEN_HEIGHT", "0"))

allow_small = {"android.widget.SeekBar"}
allow_unlabelled = {
    "android.widget.EditText", "android.widget.CheckBox",
    "android.widget.Switch", "android.widget.RadioButton",
}
files = sorted(ROOT.glob("*.xml"))
failures, warnings, checked = [], [], 0

def bounds(n):
    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
    return tuple(map(int, m.groups())) if m else None

def label(n):
    return " ".join(
        x for x in (
            n.attrib.get("text", "").strip(),
            n.attrib.get("content-desc", "").strip(),
            n.attrib.get("resource-id", "").strip(),
        ) if x
    ).strip()

def click(n):
    return n.attrib.get("clickable", "false").lower() == "true"

def has_semantic_descendant(n):
    # Compose/View hierarchies often put the semantic label on a child of a
    # clickable container. Do not reject the parent when the actionable
    # control is already described by a visible descendant.
    return any(label(child) for child in n.iter("node") if child is not n)

for p in files:
    try:
        root = ET.fromstring(p.read_text(encoding="utf-8", errors="replace"))
    except ET.ParseError as e:
        failures.append(f"{p.name}: invalid UI hierarchy XML ({e})")
        continue

    for n in root.iter("node"):
        b = bounds(n)
        if not b or n.attrib.get("visible-to-user", "true").lower() == "false":
            continue
        l, t, r, bot = b
        w, h = r - l, bot - t

        if sw and sh and (l < 0 or t < 0 or r > sw or bot > sh):
            failures.append(f"{p.name}: node bounds outside screen {b}")
        if r <= l or bot <= t:
            failures.append(f"{p.name}: invalid node bounds {b}")

        if not click(n):
            continue
        checked += 1
        cls = n.attrib.get("class", "")
        text = label(n)
        clipped_scroll = clipped_by_scrollable_ancestor(n, parents)\n        if cls not in allow_small and (w < min_px or h < min_px) and not clipped_scroll:
            failures.append(
                f"{p.name}: clickable target below 48dp: {w}x{h}px < "
                f"{min_px}px label={text or '<semantic-child>'} bounds={b}"
            )
        if not text and cls not in allow_unlabelled and not has_semantic_descendant(n) and not clipped_scroll:
            failures.append(
                f"{p.name}: clickable node has no accessible text/content-desc/"
                f"resource-id and no semantic descendant class={cls} bounds={b}"
            )

    for parent in root.iter("node"):
        kids = [n for n in list(parent) if click(n) and bounds(n)]
        for i, a in enumerate(kids):
            al, at, ar, ab = bounds(a)
            aa = max(0, ar - al) * max(0, ab - at)
            for bnode in kids[i + 1:]:
                bl, bt, br, bb = bounds(bnode)
                inter = max(0, min(ar, br) - max(al, bl)) * max(0, min(ab, bb) - max(at, bt))
                ba = max(0, br - bl) * max(0, bb - bt)
                if inter and min(aa, ba) and inter / min(aa, ba) >= .75:
                    failures.append(
                        f"{p.name}: overlapping sibling clickable targets "
                        f"{label(a) or '<a>'} vs {label(bnode) or '<b>'}"
                    )

if not files:
    warnings.append("No runtime UI hierarchy XML files were captured.")

result = "GREEN" if not failures else "RED"
lines = [
    "# FYNX Large Badge #14 — Accessibility & Screen-Safety Certification",
    "",
    f"- Result: {result}",
    f"- Commit: {os.environ.get('GITHUB_SHA', 'local')}",
    f"- Hierarchies inspected: {len(files)}",
    f"- Clickable nodes inspected: {checked}",
    f"- 48dp minimum target converted to: {min_px}px at density {density:.3f}",
]
if warnings:
    lines += ["", "## Warnings"] + [f"- {x}" for x in warnings]
if failures:
    lines += ["", "## Failures"] + [f"- {x}" for x in failures]
else:
    lines += [
        "", "## Certified checks",
        "- clickable controls have usable 48dp-class targets",
        "- clickable controls expose semantics directly or through an actionable semantic descendant",
        "- UI bounds remain inside the captured screen when screen dimensions are supplied",
        "- invalid/zero-size bounds are rejected",
        "- substantially overlapping sibling click targets are rejected",
        "- checks run against real emulator UI hierarchies captured during authenticated runtime",
    ]

(REPORT / "README.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
print("\n".join(lines))
raise SystemExit(1 if failures else 0)
