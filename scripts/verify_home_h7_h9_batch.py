#!/usr/bin/env python3
"""Home Batch #3 (H7-H9) source contract guard.

This guard verifies that Home's existing create/post, AI, and cross-surface
integration pieces remain present before the Android build is considered a
valid Home Batch #3 candidate. It intentionally checks source contracts only;
it does not mutate application code.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = {
    "H7 create/post": [
        ("app/src/main/java/com/fynx/app/ui/FynxHomeCreateMenu.kt", "FynxHomeCreateMenu"),
        ("scripts/verify_batch9_create_post_integration.py", "Create Post"),
        ("scripts/verify_batch10_create_post_hardening.py", "Create Post"),
    ],
    "H8 AI/Home": [
        ("app/src/main/java/com/fynx/app/ui/FynxAiAssistantPanel.kt", "FynxAiAssistantPanel"),
        ("app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt", "FynxHomeSocialHubPanel"),
        ("scripts/verify_ai_extension_contract.py", "AI"),
    ],
    "H9 cross-surface": [
        ("app/src/main/java/com/fynx/app/ui/FynxNavigation.kt", "FynxNavigation"),
        ("app/src/main/java/com/fynx/app/ui/FynxHomeDiscoverySection.kt", "FynxHomeDiscoverySection"),
        ("scripts/verify_fynx_journey.py", "journey"),
        ("scripts/verify_home_interactions.py", "Home"),
    ],
}

failures = []
for badge, checks in REQUIRED.items():
    for relative, needle in checks:
        path = ROOT / relative
        if not path.is_file():
            failures.append(f"{badge}: missing {relative}")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        if needle.lower() not in text.lower():
            failures.append(f"{badge}: contract '{needle}' missing from {relative}")

if failures:
    print("HOME H7-H9 GATE RED")
    for failure in failures:
        print(f" - {failure}")
    raise SystemExit(1)

print("HOME H7-H9 GATE GREEN: create/post, AI/Home, and cross-surface contracts are present")
