from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt").read_text()
group_panel = (ROOT / "app/src/main/java/com/fynx/app/ui/GroupChatPanel.kt").read_text()
group_client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupRemoteClient.kt").read_text()
server = (ROOT / "backend/server.js").read_text()

checks = []
def check(name, condition):
    checks.append((name, bool(condition)))

check("realtime reconnect is authorization-bound", "isSocketStillAuthorized" in client and "FynxAuthStore.hasAccount" in client)
check("network loss cancels in-flight socket creation", "socketBeingCreated?.cancel()" in client)
check("stale socket callbacks are rejected", "belongsToCurrentAccount" in client and "socket === webSocket" in client)
check("durable queue is limited to read/ack", 'body.optString("type")' not in client or ('"read"' in client and '"message_ack"' in client))
check("pending flush keeps items until send succeeds", "socket.send(payload)" in client and "pending.removeAt(0)" in client)
check("401/403 websocket failure clears session", "FynxAuthStore.clear(context)" in client and "401" in client and "403" in client)
check("HTTP group sends use authenticated backend client", "FynxBackendClient.postJson" in group_client)
check("HTTP group loads use authenticated backend client", "FynxBackendClient.get" in group_client)
check("group UI reloads authoritative history", "FynxGroupRemoteClient.loadMessages" in group_panel)
check("backend realtime server authenticates websocket", "jwt.verify" in server and 'path: "/realtime"' in server)
check("backend realtime relays private messages", "broadcastMessage" in server and 'type: "message"' in server)
check("backend realtime tears down calls on peer disconnect", '"peer_disconnected"' in server)

# R2-D deliberately gates the known group-realtime loophole instead of hiding it.
# Group messages currently use HTTP only, so this must stay RED until the group
# transport is connected to the authenticated realtime channel.
check("group messages have realtime transport", "sendGroupMessage" in client and "group_message" in server and "subscribe_group" in client)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    print(f"R2-D recovery gate: FAIL ({len(failed)} checks)")
    raise SystemExit(1)
print(f"R2-D recovery gate: PASS ({len(checks)} checks)")
