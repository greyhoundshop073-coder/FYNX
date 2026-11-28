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

def read(path: str | Path) -> str:
    # Callers intentionally pass both ROOT-based Path objects and repository-
    # relative strings. Normalize both forms before checking or reading them.
    resolved = Path(path)
    if not resolved.is_absolute():
        resolved = ROOT / resolved
    if not resolved.is_file():
        try:
            display_path = resolved.relative_to(ROOT)
        except ValueError:
            display_path = resolved
        raise AssertionError(f"required source file is missing: {display_path}")
    return resolved.read_text(encoding="utf-8")

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
        "direct_engine": "FynxWebRtcCallEngine.kt",
        "group_engine": "FynxGroupWebRtcCallEngine.kt",
        "group_offer_policy": "FynxGroupCallOfferPolicy.kt",
        "group_media_policy": "FynxGroupCallMediaPolicy.kt",
        "transport": "FynxCallTransportHardening.kt",
    }.items()
}
backend = {
    name: read(BACKEND / file)
    for name, file in {
        "marketplace_context": "marketplaceCallContext.js",
        "call_safety": "callSafety.js",
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
    "Private voice/video calls enforce bilateral blocks in both backend runtimes":
        "isFynxCallBlocked" in backend["node_server"]
        and "isFynxCallBlocked" in backend["neon_bridge"]
        and "blocker_id=$1 AND blocked_id=$2" in backend["call_safety"]
        and "blocker_id=$2 AND blocked_id=$1" in backend["call_safety"],

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
    "Direct-call engine receives Unified Plan remote audio and video tracks":
        "override fun onAddTrack(receiver: org.webrtc.RtpReceiver" in app["direct_engine"]
        and "is AudioTrack -> callbacks.onRemoteAudioTrack(track)" in app["direct_engine"]
        and "is VideoTrack -> callbacks.onRemoteVideoTrack(track)" in app["direct_engine"]
        and "override fun onAddStream(stream: MediaStream)" in app["direct_engine"],

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
    "Group mesh assigns one deterministic SDP offerer per peer pair":
        "FynxGroupCallOfferPolicy.shouldCreateOffer(localUserId, peerId)" in app["group_call"]
        and "if (initiateOffer &&" in app["group_call"]
        and "offerStartedFor.add(peerId)" in app["group_call"]
        and "engine.createOffer(peerId)" in app["group_call"]
        and "ensurePeerAndNegotiate(peer.userId, initiateOffer = createOffers)" in app["group_call"],
    "Group engine uses independent peer connections":
        "linkedMapOf<String, PeerState>()" in app["group_engine"]
        and "createPeerConnection" in app["group_engine"]
        and "addTrack" in app["group_engine"]
        and "acceptOfferAndCreateAnswer" in app["group_engine"]
        and "addRemoteIceCandidate" in app["group_engine"]
        and "disconnect()" in app["group_engine"],
    "Existing participants initiate the new member's mesh offer":
        "ensurePeerAndNegotiate(participant.userId, initiateOffer = true)" in app["group_call"]
        and "FynxGroupCallOfferPolicy.shouldCreateOffer(localUserId, peerId)" in app["group_call"]
        and "engine.createOffer(peerId)" in app["group_call"],
    "Group-call offer ownership is deterministic and tested":
        "local < remote" in app["group_offer_policy"]
        and "exactlyOneEndpointOwnsTheInitialOfferForEachPair" in read("app/src/test/java/com/fynx/app/ui/FynxGroupCallOfferPolicyTest.kt"),
    "Group peer creation is idempotent for repeated roster events":
        "peers.containsKey(peerId) || peers.size >= 3) return false" in app["group_engine"]
        and "offerStartedFor.add(peerId)" in app["group_call"],
    "Group camera mute preserves video for peers joining later":
        "private var videoCallEnabled = false" in app["group_engine"]
        and "FynxGroupCallMediaPolicy.shouldAttachVideoTrack(videoCallEnabled, video != null)" in app["group_engine"]
        and "fun setCameraEnabled(enabled: Boolean) {\n        // Camera mute controls the existing track only." in app["group_engine"]
        and "mutedCameraDoesNotRemoveVideoTrackFromFuturePeerConnections" in read("app/src/test/java/com/fynx/app/ui/FynxGroupCallMediaPolicyTest.kt"),
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

# Disconnect cleanup must run only after the final socket for a user closes,
# and the same durable group-call state machine must serve Node and Neon.
checks["Private-call disposal uses the latest session and peer for end or reject"] = (
    "val latestSession by rememberUpdatedState(session)" in app["calls"]
    and "val latestTargetUserId by rememberUpdatedState(targetUserId)" in app["calls"]
    and "val active = latestSession" in app["calls"]
    and "val peerId = latestTargetUserId" in app["calls"]
    and "realtimeClient.sendCallReject(active.id, peerId, isVideo)" in app["calls"]
    and "realtimeClient.sendCallEnd(active.id, peerId, isVideo)" in app["calls"]
)

checks["Group-call disposal uses the latest call state for end, leave and reject signals"] = (
    "val latestIsStarter by rememberUpdatedState(isStarter)" in app["group_call"]
    and "val latestIsIncoming by rememberUpdatedState(isIncoming)" in app["group_call"]
    and "val latestIsJoined by rememberUpdatedState(isJoined)" in app["group_call"]
    and "val latestCallEnded by rememberUpdatedState(callEnded)" in app["group_call"]
    and "val activeId = latestCallId" in app["group_call"]
    and "if (latestIsStarter) realtimeClient.sendGroupCallEnd" in app["group_call"]
    and "realtimeClient.sendGroupCallLeave(groupId, activeId)" in app["group_call"]
    and "realtimeClient.sendGroupCallReject(groupId, activeId)" in app["group_call"]
)

checks["Group-call disconnect cleanup is implemented and directly tested"] = (
    "disconnectFynxGroupCallsForUser" in backend["group_calls"]
    and "creator_disconnected" in backend["group_calls"]
    and "no_participants_remaining" in backend["group_calls"]
    and "disconnectFynxGroupCallsForUser" in backend["node_server"]
    and "disconnectFynxGroupCallsForUser" in backend["neon_bridge"]
)
checks["Backend CI executes group-call disconnect lifecycle tests"] = (
    "node test-group-call-disconnect.mjs" in read(".github/workflows/fynx-backend-ci.yml")
)
checks["Disconnect recovery initializes group-call schema once per database pool"] = (
    "const groupCallSchemaPromises = new WeakMap()" in backend["group_calls"]
    and "await ensureFynxGroupCallSchema(pool)" in backend["group_calls"]
)
checks["Marketplace Live Proof authorization has direct backend tests"] = (
    "test-marketplace-call-context.mjs" in read(".github/workflows/fynx-backend-ci.yml")
    and "resolveFynxMarketplaceCallContext" in backend["marketplace_context"]
)

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}  {name}")
if failed:
    print(f"\n{len(failed)} of {len(checks)} video-call source contracts failed.", file=sys.stderr)
    raise SystemExit(1)
print(f"\nAll {len(checks)} unified video-call source contracts passed.")
