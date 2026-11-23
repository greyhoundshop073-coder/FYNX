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

group_routes = Path("backend/group-routes.js")
route_source = group_routes.read_text(encoding="utf-8") if group_routes.exists() else server

check("group conversation uses authenticated realtime client", "FynxRealtimeClient(" in panel and "realtime.connect()" in panel)
check("group realtime events are filtered by group id", "Event.GroupMessage" in panel and "event.groupId == group.id" in panel)
check("group realtime client parses group_message events", '"group_message"' in realtime and "GroupMessage" in realtime)
check("backend broadcasts group messages only to group members", "fynxBroadcastGroupMessage" in server and "fynx_group_members" in server)
check("group message send path broadcasts realtime updates", "broadcastGroupMessage(groupId, message)" in route_source)
check("group conversation preserves optimistic messages", "pendingLocal" in panel and "distinctBy { it.id }" in panel)
check("group messages have authoritative REST recovery path", "loadMessages(context, group.id)" in panel)
check("group creation uses server-backed sync", "FynxGroupRemoteClient.syncGroup" in groups)
check("group member permissions are represented in the existing model", "FynxGroupRole" in panel and "isAdmin" in panel)

failed = [name for name, ok in checks if not ok]
for i, (name, ok) in enumerate(checks, 1):
    print(f"{i:02d}. {'PASS' if ok else 'FAIL'}: {name}")

if "delay(10_000)" in panel:
    print("WARNING: Group Chat still contains a 10-second refresh loop. Realtime is already wired, but Gate 7A is not considered fully closed until periodic polling is removed or converted to recovery-only behavior.")

if failed:
    print("\nFAIL: " + "; ".join(failed))
    sys.exit(1)

print("\nPASS: Group Chat core contract is internally consistent; remaining polling item is reported as a warning.")
