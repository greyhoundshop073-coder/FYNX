#!/usr/bin/env python3
"""Regression gate for the approved FYNX brand palette and appearance choices."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
design = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxDesignSystem.kt").read_text(encoding="utf-8")
picker = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxChatWallpaper.kt").read_text(encoding="utf-8")
guidelines = (ROOT / "docs/FYNX_MASTER_UI_GUIDELINES.md").read_text(encoding="utf-8")

checks = [
    ("default blue accent is the approved Cobalt Blue", "Blue(Color(0xFF0047AB), Color(0xFF3C73BD), Color(0xFF008000))" in design),
    ("success token is the exact Classic Green", "val ClassicGreen = Color(0xFF008000)" in design),
    ("brand error token is the exact Crimson Red", "val CrimsonRed = Color(0xFFDC143C)" in design),
    ("dark mode uses a contrast-safe red for error text", "error = FynxDesign.DarkModeError" in design and "val DarkModeError = Color(0xFFFF7086)" in design),
    ("light mode uses the exact Crimson Red for error text", "error = FynxDesign.CrimsonRed" in design),
    ("error foreground is explicitly readable in both schemes", design.count("onError = Color.White") >= 2),
    ("accent picker labels the selected blue Cobalt Blue", 'FynxAccent.Blue -> "Cobalt Blue"' in picker),
    ("Light, Dark, and System appearance choices are preserved",
     all(f'Triple("{mode}"' in picker for mode in ("System", "Light", "Dark"))),
    ("master UI guidelines document the approved three-color palette",
     all(token in guidelines for token in ("#0047AB", "#008000", "#DC143C"))),
]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit(f"{len(failed)} FYNX palette check(s) failed")
print(f"FYNX approved brand palette: {len(checks)} checks passed")
