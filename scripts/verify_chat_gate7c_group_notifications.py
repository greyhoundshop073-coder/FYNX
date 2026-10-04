#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

conversation = read("app/src/main/java/com/fynx/app/ui/FynxGroupsPanel.kt")
prefs = read("app/src/main/java/com/fynx/app/ui/FynxConversationPreferences.kt")
realtime = read("app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt")
backend = read("backend/server.js")
group_panel = read("app/src/main/java/com/fynx/app/ui/GroupChatPanel.kt")

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

check("group notification preference is account/group scoped", 'group(context, groupId).getBoolean("notifications", true)' in prefs)
check("group notification preference can be changed", 'fun setGroupNotifications' in prefs and 'putBoolean("notifications", enabled)' in prefs)
check("group chat exposes mute/enable notification action", 'Mute notifications' in conversation and 'Turn on notifications' in conversation and 'setGroupNotifications(context, groupId, groupNotificationsEnabled)' in conversation)
check("group realtime transport defines group message events", 'data class GroupMessage' in realtime and '"group_message"' in realtime)
check("backend broadcasts group messages to group members", 'app.locals.fynxBroadcastGroupMessage' in backend and 'SELECT user_id FROM fynx_group_members' in backend and 'type: "group_message"' in backend)
check("group conversation has realtime event handling", 'FynxRealtimeClient(' in group_panel and 'Event.GroupMessage' in group_panel)
check("group conversation keeps REST recovery path", 'FynxGroupRemoteClient.loadMessages' in conversation and 'delay(10_000)' in conversation)
check("group notification control is not global-only", 'group_$groupId' in conversation and 'groupNotifications(context, groupId)' in conversation)

failed = [name for name, ok in checks if not ok]
for index, (name, ok) in enumerate(checks, 1):
    print(f"{index:02d}. {'PASS' if ok else 'FAIL'}: {name}")
if failed:
    print("\nFAIL: " + "; ".join(failed))
    sys.exit(1)
print("\nPASS: Gate 7C group notification/realtime contract is internally consistent.")
