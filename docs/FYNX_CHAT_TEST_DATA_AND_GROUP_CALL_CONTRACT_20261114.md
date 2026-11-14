# FYNX Chat & Groups — Test Data, Offline Evidence and Group-call Contract

Date: 14 November 2026  
PR: #535  
Working branch: `fix/fynx-chat-groups-offline-completion-20261113`

## 1. What test data is available

The repository already has backend integration tests that provision disposable PostgreSQL data in CI. The backend workflow starts a PostgreSQL 16 service for the job; test scripts create and remove their own users, groups, memberships, messages and related records. These tests do not require the production Railway service or its production data.

Relevant existing tests and coverage:

| Test | Coverage exercised with disposable data |
|---|---|
| `backend/test-private-message-history.mjs` | Private conversation history, participant-pair isolation, bounded search, stable cursor paging, blocked/unread handling and recent-conversation index |
| `backend/test-group-content-routes.mjs` | Group message creation, sender identity, idempotency, conflicting IDs, reply authorization, read receipts, edit/delete ownership, admin pinning, reactions, outsider rejection, pagination, history-visibility cutoff, polls and event authorization |
| `backend/test-group-message-search.mjs` | Group-scoped message search and cursor pagination |
| `backend/test-group-membership-requests.mjs` | Admin-controlled new-member history visibility, invites, disabled invite links, join requests, approvals, reporting, legacy join handling and member-capacity/failure-atomicity |
| Backend CI Chat Gate 2 | Message-action contracts |
| Backend CI Chat Gate 3 | Media contract and protected-media checks |
| Backend CI Chat Gate 4 | Group organization/content contracts |

These tests exercise backend route/database behavior against a temporary database. They are valid simulated-backend integration evidence, not evidence that Android has connected to Railway or that production data has been read or written.

## 2. Android offline fixture evidence

The focused Android runtime path uses the installed debug APK on an emulator with Wi-Fi and mobile data disabled before local group creation. It exercises the real Compose screens and local persistence:

- Chat list and Groups list navigation.
- Groups manager -> actual create-group dialog.
- Blank-form validation (Create remains disabled).
- Valid name/description entry and local group creation.
- Account-scoped group persistence, creator ADMIN membership and pending-sync marker.
- Opening the new group, composing a message, leaving and reopening the conversation.
- The local outbox displays retry state while no server can be reached.

The disposable group and its message are local to the CI emulator. They are not inserted in the production backend and do not prove delivery to another member.

The Android offline attachment instrumentation test covers app-owned media staging, byte-for-byte persistence, outbox reload, account isolation, invalid-source failure and cleanup.

A separate runtime private-chat fixture seeds a peer and owner message into the account-scoped local chat store. Its owner namespace must match the username the debug-local login enters; the CI runtime script passes that username through the Android instrumentation arguments. The visual check must require both seeded message texts in the actual private ConversationPanel and must not accept an empty self-chat as a substitute.

## 3. What this does and does not prove

**Verified by PostgreSQL-backed test data:** backend route contracts listed in Section 1, including private/group authorization, membership, group history visibility, search, pagination, messages, polls/events, media and selected moderation operations.

**Verified by Android-local fixtures when the focused runtime job is green:** real app UI navigation, local group creation, local group message persistence/reopen, local private-chat UI using seeded local history, local attachment outbox persistence/account isolation, and the explicit offline/error/retry UI.

**Still requires restored live backend and authenticated test identities:** Android-to-backend authenticated request/response flow against the deployed environment; two real accounts on separate devices; live WebSocket delivery; server membership/invite changes; read receipts as observed by another account; private/group media upload and protected retrieval; notification/FCM delivery; reconnect synchronization and duplicate prevention against the deployed service.

Never describe a fixture-only emulator flow as live server delivery.

## 4. Current calling architecture inspected

The current call feature is one-to-one:

- `FynxCallsPanel.kt` maintains one active call session and one target user.
- `FynxWebRtcCallEngine.kt` creates one WebRTC `PeerConnection` and exposes a single remote audio/video track.
- `FynxRealtimeClient.kt` sends offer, answer, ICE, accept, reject and end signals to one `targetUserId`.
- `backend/server.js` stores calls in an in-memory `activeCalls` map with only `callerId` and `calleeId`; `callPeer` resolves the opposite endpoint and relays signals between those two users.
- `participantUsernames` on the Android session is a display/model list; it does not add multiparty signaling or media capability.
- The current Android WebRTC ICE configuration contains public STUN entries but no TURN credential/configuration path in this engine.

No multiparty group-call room currently exists. Do not present the one-to-one call screen as a group call and do not add a decorative group-call button until the whole path is implemented.

## 5. Proposed multiparty room contract — NOT IMPLEMENTED

The following is a design contract for future implementation, not a description of existing routes.

### Persistent room state

A room record should have:
- `room_id` (opaque, high-entropy identifier)
- `group_id` (the FYNX group that authorizes membership)
- `created_by_user_id`
- `media_type` (`voice` or `video`)
- `status` (`ringing`, `active`, `ended`)
- `created_at`, `started_at`, `ended_at`

Participant records should have:
- `room_id`, `user_id`
- `role` and invitation/acceptance state
- `joined_at`, `left_at`
- current connection/presence state and last heartbeat

Only current group members with the necessary group-call permission may create or join a room. Every join and signal must revalidate membership; a user removed from the group must be rejected immediately. The server must not trust a client-supplied participant roster.

### HTTP control plane (proposed)

- `POST /api/groups/:groupId/call-rooms` — create a room after authorization, capacity checks and idempotency validation.
- `GET /api/call-rooms/:roomId` — return the authorized room state and participant roster.
- `POST /api/call-rooms/:roomId/join` — join after fresh group membership/role verification.
- `POST /api/call-rooms/:roomId/leave` — leave and update room lifecycle.
- `POST /api/call-rooms/:roomId/end` — end a room only for an authorized owner/host or when the last participant leaves.

These paths are proposed and must not be treated as live endpoints until implemented and tested.

### Realtime signaling plane (proposed)

Use a room-scoped event envelope containing:
- `roomId`
- `eventId` / idempotency key
- `senderUserId` derived from the authenticated socket (never trusted from payload)
- `targetUserId` only when sending a participant-specific signal
- `signalType` such as `participant-joined`, `participant-left`, `offer`, `answer`, `ice`, `mute-state`, `camera-state`, `host-ended`
- validated SDP/ICE payload or constrained participant-state payload

Every sender and recipient must be an authorized room participant. Validate size, schema, event ordering and room status. Do not relay opaque messages to all sockets without checking room membership.

### Media plane

Use an SFU (Selective Forwarding Unit) for production group voice/video rather than creating a full mesh of peer connections. The app will need a participant media roster, track lifecycle, reconnect/resubscribe, mute/camera state and a grid/speaker presentation. The backend must issue short-lived TURN credentials or provider tokens; never ship static TURN secrets in the APK.

Provider/SFU selection, deployment, cost and recording policy require an explicit product decision before implementation.

### Required group-call test matrix

- Owner/admin creates a group room; unauthorized member and nonmember are denied.
- Concurrent room creation is idempotent and respects per-group capacity.
- Invite, accept, decline, busy, leave and host-end flows work for 3+ authenticated users.
- Removed member loses room and signaling access immediately.
- Duplicate, stale, malformed and cross-room SDP/ICE signals are rejected.
- Participant reconnect, network handoff, app background/foreground and permission denial recover safely.
- Camera/microphone state, audio route, speaker selection, foreground service and notification lifecycle are correct.
- TURN relay works when direct peer connectivity is blocked.
- SFU allocation, cleanup and billing/usage metrics are observable.
- Multi-device Android runtime tests verify actual audio/video for at least three participants.

## 6. Delivery and verification rule

Keep source implementation, backend fixture verification, Android-local UI verification and deployed live-backend verification as separate evidence categories. A PR can be marked complete only for the category whose evidence exists.
