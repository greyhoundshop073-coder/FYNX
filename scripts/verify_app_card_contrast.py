#!/usr/bin/env python3
"""Regression gate for FYNX custom-surface card text contrast and Home empty state."""
from pathlib import Path
import re
ROOT = Path(__file__).resolve().parents[1]
ui = ROOT / "app/src/main/java/com/fynx/app/ui"
home = (ui / "FynxRemoteHomeSocialPanel.kt").read_text(encoding="utf-8")
assert "containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary" in home, "Home empty-feed card must use the shared primary blue and its paired foreground"
assert "containerColor = MaterialTheme.colorScheme.onPrimary, contentColor = MaterialTheme.colorScheme.primary" in home, "Create Post action must remain distinct against the blue card"
assert "border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))" in home, "Find People action must retain a visible contrasting outline"
assert 'Text("Your feed is ready"' in home, "Home empty-feed state is missing"
notifications = (ui / "NotificationPanel.kt").read_text(encoding="utf-8")
assert "CardDefaults.cardColors(containerColor = containerColor, contentColor = FynxDesign.TextPrimary)" in notifications, "Notification cards with dynamic dark backgrounds must set a readable foreground"
checked = [
    "FynxRemoteHomeSocialPanel.kt", "TodoPanel.kt", "CalendarPanel.kt",
    "FynxSharePanel.kt", "MoneyToolsPanel.kt", "MoneyCenterPanel.kt",
    "FynxExtraToolsPanel.kt", "NotificationPanel.kt",
]
for filename in checked:
    source = (ui / filename).read_text(encoding="utf-8")
    for match in re.finditer(r"CardDefaults\.cardColors\(([^)]*)\)", source):
        args = match.group(1)
        if "FynxDesign.Surface" in args:
            assert "contentColor = FynxDesign.TextPrimary" in args, (
                f"{filename}: custom dark FynxDesign.Surface card lacks explicit readable foreground"
            )
print(f"PASS: Home uses primary blue; checked {len(checked)} UI files for explicit text colors on custom dark cards.")
