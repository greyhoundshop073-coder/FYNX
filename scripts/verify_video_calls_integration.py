#!/usr/bin/env python3
"""Regression contract for the three distinct FYNX video-call entry journeys.

This is a source-level integration guard. It deliberately does not claim live
WebRTC success; Android instrumentation and authenticated two-account runtime
checks remain separate acceptance gates.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/fynx/app/ui"
BACKEND = ROOT / "backend"

def read(path: Path) -> str:
    if not path.is_file():
        raise AssertionError(f"required source file is missing: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")

app = {
    name: read(APP / file)
    for name, file in {
        "marketplace": "FynxMarketplacePanel.kt",
        "live_proof": "FynxMarketplaceLiveProof.kt",
        "shell": "FynxApp.kt",
        "calls": "FynxCallsPanel.kt",
        "active_call": "FynxActiveCallPanel.kt",
        "realtime": "FynxRealtimeClient.kt",
        "private_chat": "ConversationPanel.kt",
        "groups": "FynxGroupsPanel.kt",
        "group_call": "FynxGroupCallPanel.kt",
        "group_engine": "FynxGroupWebRtcCallEngine.kt",
        "transport": "FynxCallTransportHardening.kt",
    }.items()
}
backend = {
    name: read(BACKEND / file)
    for name, file in {
        "marketplace_context": "marketplaceCallContext.js",
        "group_calls": "groupCallRealtime.js",
        "node_server": "server.js",
        "neon_bridge": "neon-realtime-bridge.js",
    }.items()
}

workflow = read('.github/workflows/android-build.yml')

checks = {
    # Marketplace: listing identity remains connected from product panel to
    # the outgoing invite, through server validation, and into the recipient UI.
    "Marketplace Live Proof forwards listing ID, title and seller":
        "onRequestProof(listing.id, listing.title, listing.sellerUsername)" in app["live_proof"]
        and "onRequestProof = { listingId, productTitle, sellerUsername -> onLiveProof(listingId, productTitle, sellerUsername) }" in app["marketplace"],
    "App routes Marketplace proof with all listing context":
        "callListingId = listingId" in app["shell"]
        and "callProductTitle = productTitle" in app["shell"]
        and "initialListingId = pendingIncomingCall?.listingId ?: callListingId" in app["shell"],
    "Calls sends listing ID in authenticated invite":
        "sendCallInvite(current.id, peerId, video, callListingId)" in app["calls"]
        and 'put("listingId", it)' in app["realtime"],
    "Incoming calls parse product ID, title and seller":
        all(token in app["realtime"] for token in ('"listingId"', '"productTitle"', '"sellerUsername"')),
    "Active call displays product inspection context":
        all(token in app["calls"] for token in ("MARKETPLACE LIVE PROOF", "Listing #", "callProductTitle")),
    "Server validates active listing ownership before relaying proof calls":
        all(token in backend["marketplace_context"] for token in (
            "marketplace_listings", "active=TRUE", "sellerId", "targetUserId",
            "blocks", "reserved_quantity", "quantity"
        )),
    "Both Railway and Neon call handlers apply Marketplace context":
        "resolveFynxMarketplaceCallContext" in backend["node_server"]
        and "resolveFynxMarketplaceCallContext" in backend["neon_bridge"]
        and "listingId" in backend["neon_bridge"],

    # Private Chat remains connected to the shared direct-call engine.
    "Private Chat retains voice and video call entry points":
        "onVoiceCall" in app["private_chat"] and "onVideoCall" in app["private_chat"],
    "Private Chat call actions enter the shared Calls route":
        'onVoiceCall = { callTarget = openChat!!.username; callVideo = false' in app["shell"]
        and 'onVideoCall = { callTarget = openChat!!.username; callVideo = true' in app["shell"],
    "Direct-call invites and accepts wait for a connected realtime socket":
        "pendingOutgoingInvite" in app["calls"]
        and "pendingIncomingAcceptSignal" in app["calls"]
        and "realtimeState != FynxRealtimeClient.State.CONNECTED" in app["calls"]
        and "sendCallInvite(current.id, peerId, video, callListingId)" in app["calls"],
    "Backend direct-call signaling errors are surfaced to the caller":
        '"error" -> if (session?.id == event.callId)' in app["calls"]
        and 'event.error ?: "The call could not be started."' in app["calls"],
    "Shared one-to-one call uses authenticated direct signaling":
        "sendCallInvite" in app["calls"] and "sendCallOffer" in app["calls"]
        and "sendCallAnswer" in app["calls"] and "sendCallIce" in app["calls"],

    # Group Chat has a separate group signaling contract and per-peer media.
    "Current Group Chat exposes a group video-call action":
        "Start group video call" in app["groups"]
        and "FynxGroupCallPanel(" in app["groups"],
    "App routes group invitations into the existing group conversation":
        "pendingIncomingGroupCall" in app["shell"]
        and "initialIncomingGroupCall = pendingIncomingGroupCall?.takeIf" in app["shell"],
    "Group call UI exposes participant and call controls":
        all(token in app["group_call"] for token in (
            "Start group video call", "Participants", "Mute", "Camera", "Speaker",
            "Leave", "Decline", "Join call"
        )),
    "Group-call starter sees an explicit End call action while invitees see Leave":
        'if (isStarter) "End call" else "Leave"' in app["group_call"],
    "Group call signaling events are queued so burst ICE candidates are not overwritten":
        "Channel<FynxRealtimeClient.Event.GroupCall>(Channel.UNLIMITED)" in app["group_call"]
        and "groupEvents.trySend(event)" in app["group_call"]
        and "for (event in groupEvents)" in app["group_call"],
    "Realtime durable read and acknowledgement queue remains account-scoped and bounded":
        'type != "read" && type != "message_ack"' in app["realtime"]
        and "pendingAccountKey = accountKey" in app["realtime"]
        and "pendingPayloads.addLast(value)" in app["realtime"],
    "Group schema setup is cached rather than run on every signaling frame":
        "let schemaReady = null" in backend["group_calls"]
        and "await ensureSchema()" in backend["group_calls"],
    "Group invitations use the runtime delivery adapter without Neon-only connection-table assumptions":
        "for (const row of members.rows)" in backend["group_calls"]
        and "fynx_realtime_connections" not in backend["group_calls"],
    "Group engine uses independent peer connections":
        "linkedMapOf<String, PeerState>()" in app["group_engine"]
        and "createPeerConnection" in app["group_engine"]
        and "addTrack" in app["group_engine"]
        and "acceptOfferAndCreateAnswer" in app["group_engine"]
        and "addRemoteIceCandidate" in app["group_engine"]
        and "disconnect()" in app["group_engine"],
    "Backend checks group membership, participant limit and peer targets":
        all(token in backend["group_calls"] for token in (
            "fynx_group_members", "MAX_GROUP_CALL_PARTICIPANTS = 4",
            "group_offer", "group_answer", "group_ice",
            "status='JOINED'", "Only the group-call starter"
        )),
    "Both backend runtimes route group-call signaling":
        "createFynxGroupCallSignalHandler" in backend["node_server"]
        and "handleNodeGroupCallSignal" in backend["node_server"]
        and "createFynxGroupCallSignalHandler" in backend["neon_bridge"]
        and "handleGroupCallSignal" in backend["neon_bridge"],
    "Group create and join actions survive socket reconnection after permission prompts":
        "LaunchedEffect(realtimeState, startAction, waitingForPermission)" in app["group_call"]
        and "if (realtimeState != FynxRealtimeClient.State.CONNECTED || waitingForPermission)" in app["group_call"]
        and "sendGroupCallCreate" in app["group_call"]
        and "sendGroupCallJoin" in app["group_call"],
    "Call transport accepts only the bounded group-call signal vocabulary":
        all(f'"{signal}"' in app["realtime"] for signal in (
            "group_create", "group_join", "group_leave", "group_reject",
            "group_end", "group_offer", "group_answer", "group_ice"
        )),
}

checks["Android CI runs the dedicated unified voice/video call contract verifier"] = "python3 scripts/verify_video_calls_integration.py" in workflow
checks["The [full-runtime] PR title marker enables build, emulator and screenshot certification"] = workflow.count("contains(github.event.pull_request.title, '[full-runtime]')") >= 6

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}  {name}")
if failed:
    print(f"\n{len(failed)} of {len(checks)} video-call source contracts failed.", file=sys.stderr)
    raise SystemExit(1)
print(f"\nAll {len(checks)} unified video-call source contracts passed.")
