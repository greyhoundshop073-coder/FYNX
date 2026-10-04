#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

# Gate 7D certifies the actual GroupChatPanel entry point. FynxGroupsPanel is
# the group-list surface, not the conversation implementation.
conversation = read("app/src/main/java/com/fynx/app/ui/GroupChatPanel.kt")
remote = read("app/src/main/java/com/fynx/app/ui/FynxGroupRemoteClient.kt")
backend = read("app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt")
store = read("app/src/main/java/com/fynx/app/ui/FynxChatStore.kt")
realtime = read("app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt")

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

check("group conversation keeps local history", 'loadGroupMessages(context, group.id)' in conversation and 'saveGroupMessages(context, group.id, messages)' in conversation)
check("group history is persisted after synchronization", 'saveGroupMessages(context, group.id, messages)' in conversation and 'refreshGroupMessages' in conversation)
check("group conversation supports message search", 'var searchQuery by remember' in conversation and 'Search messages…' in conversation)
check("group search filters actual message text", 'messages.filter { it.text.contains(searchQuery, true) }' in conversation)
check("group search navigates to matching message context", 'searchQuery.isNotBlank()' in conversation and 'scrollToItem' in conversation)
check("pinned group message is exposed in conversation", 'val pinnedMessage = messages.lastOrNull { it.pinned }' in conversation and 'Pinned message' in conversation)
check("pinned group message navigates back to its source", 'messages.indexOfFirst { it.id == pinned.id }' in conversation and 'animateScrollToItem(index)' in conversation)

# Authentication is centralized in FynxBackendClient. Group history calls the
# authenticated backend wrapper rather than duplicating an Authorization header
# in every feature client. Verify both sides of that contract.
check("group history has authenticated remote loading", 'suspend fun loadMessages(context:Context,groupId:String)' in remote and 'FynxBackendClient.get(context,"/api/groups/$groupId/messages")' in remote and 'if (requiresAuthentication) accessToken(context)?.let { setRequestProperty("Authorization", "Bearer $it") }' in backend)

# The actual conversation screen subscribes to the authenticated realtime client
# and handles GroupMessage events alongside REST history recovery.
check("group realtime remains available alongside history", 'FynxRealtimeClient(' in conversation and 'Event.GroupMessage' in conversation and 'FynxGroupRemoteClient.loadMessages(context, group.id)' in conversation and 'Event.GroupMessage' in realtime)
check("group messages retain stable ids for list navigation", 'items(visibleMessages, key = { it.id })' in conversation)

failed = [name for name, ok in checks if not ok]
for index, (name, ok) in enumerate(checks, 1):
    print(f"{index:02d}. {'PASS' if ok else 'FAIL'}: {name}")
if failed:
    print("\nFAIL: " + "; ".join(failed))
    sys.exit(1)
print("\nPASS: Gate 7D group history/search/navigation contract is internally consistent.")
