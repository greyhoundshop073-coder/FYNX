#!/usr/bin/env python3
from pathlib import Path
import sys


def read(path):
    return Path(path).read_text(encoding="utf-8")

checks = []

def check(name, ok):
    checks.append((name, bool(ok)))

panel = read("app/src/main/java/com/fynx/app/ui/GroupChatPanel.kt")
realtime = read("app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt")
groups = read("app/src/main/java/com/fynx/app/ui/FynxGroupsPanel.kt")
server = read("backend/server.js")

check("group conversation uses authenticated realtime client", "FynxRealtimeClient(" in panel and "realtime.connect()" in panel)
check("group realtime events are filtered by group id", "Event.GroupMessage" in panel and "event.groupId == group.id" in panel)
check("group realtime client parses group_message events", '"group_message"' in realtime and "GroupMessage" in realtime)
check("backend broadcasts group messages only to group members", "fynxBroadcastGroupMessage" in server and "fynx_group_members" in server)
check("group message send path broadcasts realtime updates", "broadcastGroupMessage(groupId, message)" in read("backend/group-routes.js") or "broadcastGroupMessage(groupId, message)" in server)
check("group conversation preserves optimistic messages", "pendingLocal" in panel and "distinctBy { it.id }" in panel)
check("group messages have authoritative REST recovery path", "loadMessages(context, group.id)" in panel)
check("group creation uses server-backed sync", "FynxGroupRemoteClient.syncGroup" in groups)
check("group member permissions are represented in the existing model", "FynxGroupRole" in panel and "isAdmin" in panel)

# This is intentionally a warning-level gap, not a passing claim. The current
# implementation still contains a 10-second polling loop alongside realtime.
# Gate 7A will only be considered fully closed after that loop is removed or
# converted to an explicit recovery-only mechanism.
check("group chat does not use periodic polling as primary delivery", "delay(10_000)" not in panel)

failed = [name for name, ok in checks if not ok]
for i, (name, ok) in enumerate(checks, 1):
    print(f"{i:02d}. {'PASS' if ok else 'FAIL'}: {name}")

if failed:
    print("\nFAIL: " + "; ".join(failed))
    sys.exit(1)

print("\nPASS: Group Chat Gate 7A core contract is internally consistent.")
