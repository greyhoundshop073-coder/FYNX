#!/usr/bin/env python3
"""Badge B — bottom/keyboard safety certification."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

checks = [
    ("Home feed reserves bottom scroll space and system navigation inset",
     "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt",
     ("navigationBarsPadding()", "bottom = 96.dp")),
    ("Create Post composer is IME-aware",
     "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt",
     ("imePadding()",)),
    ("Conversation composer stays above keyboard and navigation",
     "app/src/main/java/com/fynx/app/ui/ConversationPanel.kt",
     ("navigationBarsPadding().imePadding()",)),
    ("Group chat composer stays above keyboard and navigation",
     "app/src/main/java/com/fynx/app/ui/GroupChatPanel.kt",
     ("navigationBarsPadding().imePadding()",)),
    ("Status reply composer stays above keyboard and navigation",
     "app/src/main/java/com/fynx/app/ui/FynxStatusTimelinePanel.kt",
     ("navigationBarsPadding()", "imePadding()")),
    ("Marketplace sell action stays above keyboard and navigation",
     "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt",
     ("navigationBarsPadding().imePadding()",)),
    ("Home hides floating navigation while the keyboard is visible",
     "app/src/main/java/com/fynx/app/ui/FynxApp.kt",
     ("val isKeyboardVisible =", "if (!isKeyboardVisible")),
]

failures = []
for label, path, needles in checks:
    source = read(path)
    missing = [n for n in needles if n not in source]
    if missing:
        failures.append(f"{label}: missing {', '.join(missing)}")
    else:
        print(f"PASS: {label}")

comments = read("app/src/main/java/com/fynx/app/ui/FynxHomeCommentsPanel.kt")
if "Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()" in comments:
    failures.append("Home comments: known double-inset composer pattern returned")
else:
    print("PASS: Home comments avoids the known double-IME-padding pattern")

for path in (
    "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt",
    "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt",
    "app/src/main/java/com/fynx/app/ui/ConversationPanel.kt",
    "app/src/main/java/com/fynx/app/ui/GroupChatPanel.kt",
    "app/src/main/java/com/fynx/app/ui/FynxStatusTimelinePanel.kt",
    "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt",
):
    source = read(path)
    if "imePadding().imePadding()" in source or "navigationBarsPadding().navigationBarsPadding()" in source:
        failures.append(f"{path}: duplicate consecutive inset modifier chain detected")

if not failures:
    print("PASS: no duplicate consecutive IME/navigation inset modifier chains in critical surfaces")

if failures:
    print("\nBADGE B RED")
    for failure in failures:
        print(f" - {failure}")
    raise SystemExit(1)

print("\nBADGE B GREEN — bottom/keyboard safety source audit passed")
