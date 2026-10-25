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

    ("Soft Cloud White token is exact and wired to light background",
     "val SoftCloudWhite = Color(0xFFF5F7FA)" in design and "val LightBackground = SoftCloudWhite" in design and "background = FynxDesign.LightBackground" in design),
    ("Midnight Slate token is exact and wired to raised surfaces",
     "val MidnightSlate = Color(0xFF1C2434)" in design and "val SurfaceRaised = MidnightSlate" in design),
    ("Slate Grey token is exact and wired to secondary light-theme text",
     "val SlateGrey = Color(0xFF64748B)" in design and "val LightTextSecondary = SlateGrey" in design and "onSurfaceVariant = FynxDesign.LightTextSecondary" in design),
    ("Premium Gold is a named, exact semantic token",
     "val PremiumGold = Color(0xFFD4AF37)" in design),
    ("Pale Cobalt is exact and wired to light selected containers",
     "val PaleCobalt = Color(0xFFE8EEFA)" in design and "val LightSelectedContainer = PaleCobalt" in design),
    ("light theme uses shared neutral tokens rather than duplicate hard-coded colors",
     "background = FynxDesign.LightBackground" in design and "surface = FynxDesign.LightSurface" in design and "onSurface = FynxDesign.LightTextPrimary" in design),
    ("supporting palette usage rules are documented",
     all(token in guidelines for token in ("#F5F7FA", "#1C2434", "#64748B", "#D4AF37", "#E8EEFA"))),
]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit(f"{len(failed)} FYNX palette check(s) failed")
print(f"FYNX approved brand palette: {len(checks)} checks passed")
