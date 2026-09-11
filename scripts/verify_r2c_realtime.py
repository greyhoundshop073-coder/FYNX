from pathlib import Path

SOURCE = Path("app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt")
text = SOURCE.read_text(encoding="utf-8")

checks = {
    "reconnect timers are cleared before scheduling": "reconnectHandler.removeCallbacksAndMessages(null)" in text,
    "reconnect requires current authorization": "isSocketStillAuthorized(expectedAccountKey)" in text,
    "reconnect uses bounded exponential backoff": "coerceAtMost(6)" in text and "coerceAtMost(30_000L)" in text,
    "network recovery resets reconnect state": "reconnectAttempt = 0" in text,
    "network loss cancels active socket": "socket?.cancel()" in text,
    "network loss cancels in-flight socket creation": "socketBeingCreated?.cancel()" in text,
    "socket creation is serialized": "socketCreationInProgress" in text and "synchronized(socketCreationLock)" in text,
    "socket creation synchronous failure resets state": "} catch (_: Throwable)" in text and "socketCreationInProgress = false" in text,
    "stale socket callbacks are rejected": "socket === webSocket" in text and "FYNX socket replaced" in text,
    "closed sockets retry only when policy allows": "shouldRetrySocket(code)" in text,
    "auth websocket failure clears session": "isAuthFailure(response?.code)" in text and "FynxAuthStore.clear(context)" in text,
    "realtime sends are account-bound": "socketAccountKey != accountKey" in text,
    "realtime sends require an access token": "!FynxBackendClient.hasAccessToken(context)" in text,
    "durable queue is restricted to read and ack": 'type != "read" && type != "message_ack"' in text,
    "pending queue is account-bound": "pendingAccountKey" in text,
    "pending flush checks active socket identity": "socket === webSocket" in text,
    "pending flush checks current account": "currentAccountKey() == accountKey" in text,
    "pending flush checks validated network": "hasUsableNetwork()" in text,
    "pending items are only removed after successful send": "if (!sent) return" in text and "pendingPayloads.removeFirst()" in text,
    "call signal input is validated": "isValidCallSignal(signalType)" in text and "isValidCallType(callType)" in text,
}

failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {name}")

if failed:
    raise SystemExit("R2-C realtime invariant gate failed: " + "; ".join(failed))

print(f"R2-C realtime invariant gate: GREEN ({len(checks)} checks)")
