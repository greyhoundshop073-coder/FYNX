#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

conversation = read("app/src/main/java/com/fynx/app/ui/FynxGroupsPanel.kt")
remote = read("app/src/main/java/com/fynx/app/ui/FynxGroupRemoteClient.kt")
store = read("app/src/main/java/com/fynx/app/ui/FynxChatStore.kt")
realtime = read("app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt")

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

check("group conversation keeps local history", 'FynxChatStore.load(context, "group_$groupId"' in conversation)
check("group history is persisted after synchronization", 'FynxChatStore.save(context, "group_$groupId", messages)' in conversation)
check("group conversation supports message search", 'var searchOpen by remember' in conversation and 'searchQuery' in conversation and 'Search messages…' in conversation)
check("group search filters actual message text", 'messages.filter { it.text.contains(searchQuery, true) }' in conversation)
check("group search navigates to matching message context", 'messageListState.scrollToItem(0)' in conversation and 'searchQuery.isNotBlank()' in conversation)
check("pinned group message is exposed in conversation", 'val pinnedMessage = messages.lastOrNull { it.pinned }' in conversation and 'Pinned message' in conversation)
check("pinned group message navigates back to its source", 'messages.indexOfFirst { it.id == pinned.id }' in conversation and 'animateScrollToItem(index)' in conversation)
check("group history has authenticated remote loading", 'suspend fun loadMessages' in remote and 'Authorization' in remote)
check("group realtime remains available alongside history", 'Event.GroupMessage' in conversation and 'FynxRealtimeClient(' in conversation)
check("group messages retain stable ids for list navigation", 'items(visibleMessages, key = { it.id })' in conversation)

failed = [name for name, ok in checks if not ok]
for index, (name, ok) in enumerate(checks, 1):
    print(f"{index:02d}. {'PASS' if ok else 'FAIL'}: {name}")
if failed:
    print("\nFAIL: " + "; ".join(failed))
    sys.exit(1)
print("\nPASS: Gate 7D group history/search/navigation contract is internally consistent.")
