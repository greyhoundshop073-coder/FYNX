#!/usr/bin/env python3
"""Guard the Friends screen against accidental destructive relationship actions."""
from pathlib import Path
import sys

source = Path("app/src/main/java/com/fynx/app/ui/FriendsPanel.kt")
text = source.read_text(encoding="utf-8")

checks = {
    "friend removal is staged for confirmation": 'pendingRelationshipAction = person.username to "remove"' in text,
    "friend blocking is staged for confirmation from friend options": 'onBlock = { pendingRelationshipAction = person.username to "block" }' in text,
    "discovery blocking is staged for confirmation": 'onSecondary = { pendingRelationshipAction = person.username to "block" }' in text,
    "confirmation dialog is shown": "pendingRelationshipAction?.let" in text and "AlertDialog(" in text,
    "blocking warning explains contact and discovery impact": "will no longer be able to contact you" in text,
    "removal warning explains friendship impact": "This will end your FYNX friendship" in text,
    "confirm action calls server-backed block/remove methods": "FynxSocialClient.block(context, username)" in text and "FynxSocialClient.removeFriend(context, username)" in text,
    "cancel action dismisses without mutating relationship": 'Text("Cancel")' in text and "onDismissRequest = { pendingRelationshipAction = null }" in text,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    sys.exit("Friends safety contract failed: " + "; ".join(failed))
print(f"FRIENDS_DESTRUCTIVE_ACTION_CONFIRMATION=GREEN ({len(checks)} checks)")
