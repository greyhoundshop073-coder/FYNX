#!/usr/bin/env python3
"""Repository-wide regression gate for FYNX custom-surface card text contrast."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/fynx/app/ui"


def call_arguments(source: str, opening_paren: int) -> str:
    """Return a Kotlin call's arguments, balancing nested parentheses."""
    depth = 0
    in_string = False
    escaped = False
    for index in range(opening_paren, len(source)):
        char = source[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return source[opening_paren + 1:index]
    raise AssertionError("Unbalanced Kotlin call parentheses in cardColors")


home = (UI / "FynxRemoteHomeSocialPanel.kt").read_text(encoding="utf-8")
assert "containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary" in home, "Home empty-feed card must use primary blue with its paired readable foreground"
assert "containerColor = MaterialTheme.colorScheme.onPrimary, contentColor = MaterialTheme.colorScheme.primary" in home, "Create Post action must remain distinct against the blue card"
assert "border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))" in home, "Find People action must retain a visible contrasting outline"
assert 'Text("Your feed is ready"' in home, "Home empty-feed state is missing"

scanned_files = 0
custom_surface_cards = 0
problems = []
for path in sorted(UI.rglob("*.kt")):
    source = path.read_text(encoding="utf-8")
    scanned_files += 1
    cursor = 0
    token = "CardDefaults.cardColors("
    while True:
        call = source.find(token, cursor)
        if call < 0:
            break
        opening = call + len(token) - 1
        args = call_arguments(source, opening)
        if "FynxDesign.Surface" in args:
            custom_surface_cards += 1
            if "contentColor = FynxDesign.TextPrimary" not in args:
                line = source.count("\n", 0, call) + 1
                problems.append(f"{path.relative_to(ROOT)}:{line}: custom FynxDesign.Surface card lacks explicit readable contentColor")
        cursor = opening + len(args) + 2

# Notification cards can receive a dynamic custom background, so their
# foreground must be explicit even though the background is a variable.
notifications = (UI / "NotificationPanel.kt").read_text(encoding="utf-8")
assert "CardDefaults.cardColors(containerColor = containerColor, contentColor = FynxDesign.TextPrimary)" in notifications, "Notification cards with dynamic backgrounds must set a readable foreground"

assert not problems, "Found card contrast regressions:\n" + "\n".join(problems)
print(f"PASS: Home blue/foreground pairing; scanned {scanned_files} Kotlin UI files and {custom_surface_cards} custom-surface card declarations.")
