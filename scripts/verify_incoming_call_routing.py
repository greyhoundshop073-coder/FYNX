from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
app = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text(encoding="utf-8")
calls = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxCallsPanel.kt").read_text(encoding="utf-8")
routing = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxIncomingCallRouting.kt").read_text(encoding="utf-8")
tests = (ROOT / "app/src/test/java/com/fynx/app/ui/FynxIncomingCallRoutingTest.kt").read_text(encoding="utf-8")

checks = {
    "app shell owns a call signaling listener while authenticated": "DisposableEffect(authSession.state, authSession.username)" in app and "incomingCallListener.connect()" in app and "incomingCallListener.close()" in app,
    "incoming invites route from screens outside Calls": "FynxIncomingCallRouting.shouldRouteInvite" in app and 'selected = "Calls"' in app,
    "incoming calls clear chat, group, and profile overlays before routing": all(x in app for x in ["openChat = null", "openGroup = null", "profileUser = null"]),
    "incoming call identity and type are handed into the existing Calls panel": all(x in app for x in ["initialIncomingCallId = pendingIncomingCall?.callId", "initialIncomingUserId = pendingIncomingCall?.fromUserId", "pendingIncomingCall?.callType"]),
    "Calls panel seeds a ringing session from a routed invite": "initialIncomingCallId?.let { callId ->" in calls and "state = FynxCallState.RINGING" in calls,
    "incoming invite is recorded once and consumed after panel initialization": "LaunchedEffect(initialIncomingCallId)" in calls and "onIncomingCallConsumed(callId)" in calls,
    "active Calls panel retains its existing realtime and WebRTC handler": "realtimeClient.connect()" in calls and '"offer" -> if (session?.id == event.callId' in calls and "mediaEngine.acceptOfferAndCreateAnswer(event.sdp)" in calls,
    "routing helper only routes invites outside Calls when no invite is pending": "signalType.equals("invite", ignoreCase = true)" in routing and 'currentScreen != "Calls"' in routing and "!hasPendingIncomingCall" in routing,
    "unit tests cover screen routing, duplicate pending invites, and other signals": all(x in tests for x in ["routesAnInviteFromAnyNonCallsScreen", "leavesInvitesToTheActiveCallsScreen", "doesNotReplaceAnAlreadyPendingInvite", "ignoresNonInviteCallSignals"]),
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("INCOMING CALL ROUTING CONTRACT RED: " + "; ".join(failed))
print(f"INCOMING CALL ROUTING CONTRACT GREEN ({len(checks)} checks)")
