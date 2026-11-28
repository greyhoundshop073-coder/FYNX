# FYNX Calling — Railway Go-Live Handoff

> **Purpose:** This is the handover checklist for any ChatGPT session or engineer who resumes FYNX unified calling after Railway billing/service access is restored. Read this file before changing code, Railway, Cloudflare, or the canonical Android branch.

## 1. Project identity and non-negotiable rules

- Repository: `greyhoundshop073-coder/FYNX`
- Current coordinated calling PR: [#599](https://github.com/greyhoundshop073-coder/FYNX/pull/599)
- Working branch: `fix/fynx-unified-video-calls-20261123`
- Canonical Android integration branch: `integration/fynx-single-apk-20261013`
- Do not create a second calling implementation, a competing APK, or a new integration branch.
- Do not merge PR #599 or change the canonical integration branch until exact-head CI, Android tests, runtime evidence, and the acceptance checks below are complete.
- Do not change login/authentication UX as a shortcut. Use the existing authenticated FYNX backend identity and authorization.
- Never put Cloudflare App Secret, Railway credentials, database URLs, or tokens in Android resources, `index.html`, source control, screenshots, logs, or chat.
- Do not deploy unrelated staged Railway changes just to activate the two Cloudflare variables. Inspect every pending change first.

## 2. Current known state (verify again before acting)

- Cloudflare Realtime **Serverless SFU** application was created with the name `FYNX Video Calling`.
- The App ID was supplied by the owner and entered in Railway as `CLOUDFLARE_SFU_APP_ID`.
- The owner also entered `CLOUDFLARE_SFU_APP_SECRET` in the Railway testing backend variables.
- The App Secret was exposed in a downloaded `index.html` file during setup. The owner was advised to rotate/regenerate it. **Before live deployment, verify that the exposed secret has been revoked and that Railway contains only the replacement secret. Never request or print the secret.**
- Railway service previously identified: project `FYNX`, service `fynx-backend-testing`, environment `production` (verify actual project/service/environment names and IDs in Railway; do not assume the selected environment is correct).
- Railway trial was expired at the last inspection. The owner intends to reactivate/subscribe when financially able, with December 1, 2026 as a possible target. Engineering and local/CI verification must continue before then.
- Railway had a large batch of pending staged changes (last observed 11 changes, including unrelated Neon/storage settings, branch/build configuration changes, and the Cloudflare variables). **Re-read the current staged-change list. Do not blindly apply or deploy the batch.**
- PR #599 was open and unmerged at last check. Last known head after the initial Cloudflare backend foundation commits: `b340ee75534cb1fd7262ee683d5f90f1e47f7580`. Always fetch the current PR head before editing; this SHA is historical, not authority to overwrite newer work.
- Commits added to PR #599:
  - `bb59271` — initial authenticated Cloudflare SFU backend route module.
  - `e5b3845` — registered those routes in the existing backend.
  - `b340ee7` — added a backend startup contract check.
- Backend CI passed on that head; Android Build, Chat Final Closure, and Group Parity were still running at the last observed check. Re-check exact-head workflow status.

## 3. What must be completed before Railway is live

This is engineering work, not a reason to wait for billing:

1. Finish the native Android WebRTC ↔ Cloudflare SFU endpoint implementation. The Android client must create/apply SDP offers and answers, publish microphone/camera tracks, subscribe to authorized remote tracks, render remote media, and handle mute/camera/speaker/route controls.
2. Implement correct Cloudflare SFU negotiation for every operation. Follow the current official Connection API and negotiation guides linked below. Do not assume an HTTP 200 means all requested tracks succeeded.
3. Serialize all mutations per SFU session (track creation/update/close and renegotiation) through one queue. Handle simultaneous joins, partial track results, timeouts, lost responses, retries, and replacement sessions without duplicate subscriptions.
4. Complete backend authorization for all supported call types:
   - private voice/video calls: only the two active call participants;
   - Marketplace Live Proof: buyer and the seller verified from the actual listing record, listing active/available, block rules respected;
   - group calls: only authenticated members of the group, server-owned participant roster, invites/join/decline/leave/end, maximum supported room size explicitly enforced.
5. Decide and document the SFU topology: one bidirectional session or separate publisher/receiver sessions per participant. Use the simplest topology compatible with the Android WebRTC library and ensure every session's mutations are serialized.
6. Complete resource lifecycle: close tracks, close peer connections, mark publications unavailable, end/expire sessions, clean up on app background/foreground, call end, participant departure, network loss, and backend errors.
7. Add tests for authorization denial, invalid IDs/SDP, wrong call/group/listing, cross-user session access, partial track failures, simultaneous subscription races, duplicate join/leave, reconnection, and teardown.
8. Keep the old four-participant mesh limit only where fallback is deliberately retained. When SFU group calling is implemented and certified, update the product limit based on tested capacity; do not claim unlimited group size.
9. Review and correct the initial SFU route module against the current Cloudflare OpenAPI schema before enabling it. In particular, verify exact response shapes, publication track-name identifiers, subscription payloads, session close/track-close behavior, and SDP offer/answer ownership. The current module is an initial foundation, **not live-certified production integration**.
10. Ensure the existing Node backend and Neon realtime bridge have matching call authorization and event semantics where both are used. Avoid two competing sources of truth for session ownership/publications.

## 4. When Railway billing/access is restored — deployment runbook

### A. Verify the target before touching anything

- Open Railway and select the FYNX project.
- Select the intended backend service, expected to be `fynx-backend-testing`.
- Confirm the correct environment and currently deployed commit/branch.
- Confirm this is the testing service, not a production service or another project.
- Inspect service status, latest deployment, logs, and current variables.
- Check whether the service is on the expected working branch and build method. Do not change branch/build settings unless the repo's current deployment configuration proves the change is required.

### B. Review pending Railway changes

- Open the pending changes list.
- Compare each item with the intended FYNX backend configuration.
- Keep the two Cloudflare variables, but separately assess every Neon/database/storage variable and any branch/build configuration changes.
- Do not apply unrelated changes as a bundle. Remove/revert unrelated staged edits or split them into a separately reviewed change set.
- Confirm database migrations are backward-compatible and have a backup/rollback plan before deploying.
- Confirm the selected environment's database URL points to the intended testing database, not production data.

### C. Verify secrets and configuration

Required backend variables:
- `CLOUDFLARE_SFU_APP_ID` — Cloudflare SFU App ID (not secret).
- `CLOUDFLARE_SFU_APP_SECRET` — newly rotated Cloudflare SFU App Secret (secret).

Also verify the existing FYNX backend variables required by the current server and database configuration, without replacing them with guesses. Never print values in logs or handoff notes. Verify the backend can connect to its database and its existing authentication/signalling dependencies.

The owner previously supplied the App ID; use the value already stored in the correct Railway service rather than asking them to paste it into chat. If the App Secret was not rotated after exposure, stop and have the owner rotate it in Cloudflare, then update Railway securely.

### D. Deploy safely

- Deploy only after the pending-change review and code review are complete.
- Deploy the intended PR #599 head to the testing backend first.
- Watch build output and startup logs.
- Confirm backend startup checks and database schema initialization succeed.
- Confirm the service is healthy and the deployed commit SHA is the intended SHA.
- Verify SFU configuration is detected without logging the secret.
- Exercise authenticated health/config checks; do not expose a public endpoint that returns the secret or unrestricted Cloudflare proxy operations.
- If deploy fails, capture the exact failing step and logs, diagnose it, and fix the cause. Do not repeatedly redeploy unchanged code or weaken checks.

## 5. Cloudflare SFU API requirements

Official docs:
- [Realtime SFU overview](https://developers.cloudflare.com/realtime/sfu/)
- [Connection API](https://developers.cloudflare.com/realtime/sfu/api/)
- [Sessions and tracks](https://developers.cloudflare.com/realtime/sfu/concepts/sessions-tracks/)
- [Negotiation and session lifecycle](https://developers.cloudflare.com/realtime/sfu/concepts/negotiation/)
- [Connection patterns](https://developers.cloudflare.com/realtime/sfu/get-started/connection-patterns/)
- [Application architecture](https://developers.cloudflare.com/realtime/sfu/concepts/architecture/)
- [Best practices and security](https://developers.cloudflare.com/realtime/sfu/best-practices/)
- [Limits and quotas](https://developers.cloudflare.com/realtime/sfu/platform/limits/)
- [Observability](https://developers.cloudflare.com/realtime/sfu/observability/)

Important API contract:
- Base URL: `https://rtc.live.cloudflare.com/v1`
- Backend authorization: `Authorization: Bearer <APP_SECRET>`; send JSON content type.
- Session creation: `POST /apps/{appId}/sessions/new`
- Add/publish/subscribe tracks: `POST /apps/{appId}/sessions/{sessionId}/tracks/new`
- Track update: `PUT /apps/{appId}/sessions/{sessionId}/tracks/update`
- Renegotiation answer: `PUT /apps/{appId}/sessions/{sessionId}/renegotiate`
- Close tracks: `PUT /apps/{appId}/sessions/{sessionId}/tracks/close`
- Session inspection: `GET /apps/{appId}/sessions/{sessionId}`
- Follow current API schema for all JSON fields and response fields; don't infer a close-session API if the docs specify track closure and endpoint teardown separately.
- Backend must independently authenticate the FYNX user and authorize the exact call, group, listing, participant, session and publication. Cloudflare App Secret authenticates the backend to Cloudflare; it does not replace FYNX user authorization.
- A session has one PeerConnection and one SDP offer/answer state machine. Complete the entire SDP exchange before starting another mutation on that same session.
- If an operation response is lost, do not blindly repeat it. Inspect/recover where supported or discard and create a replacement connection according to Cloudflare's recovery guidance.
- Never log App Secret, SDP, ICE credentials, media payloads, or user tokens.

## 6. Live acceptance tests after deployment

Use at least three separately authenticated test accounts and physical devices where possible. Use Wi-Fi and mobile data, including different networks. Test each case and record date, app build, backend commit, device/network, result, and evidence.

### Private calls
- Account A calls B; B receives and accepts.
- Confirm two-way audio, two-way video, mute/unmute, camera on/off, speaker/earpiece route, and call end.
- Repeat with B calling A.
- Verify a non-participant cannot join, subscribe, inspect, or end the session.

### Marketplace Live Proof
- Buyer opens a real available listing and starts Live Proof with its actual seller.
- Confirm correct listing ID, title, and seller are shown to both parties.
- Confirm only the buyer and verified listing owner can join.
- Test unavailable/sold listing, wrong seller, blocked user, and unrelated account; all must be denied.
- End the call and confirm the listing/order state is unchanged unless a separately authorized purchase action occurs.

### Group calls
- Test 3 and 4 participants, including simultaneous joins.
- Verify each participant can hear/see the intended participants.
- Test invite, accept, decline, leave, host/end, member removal, app background/foreground, and reconnect.
- Verify non-members cannot join or subscribe to group publications.
- Confirm room-size limits and behavior above the supported maximum.
- Monitor device CPU, memory, heat, battery and data usage.

### Network and lifecycle
- Test Wi-Fi ↔ mobile data transitions, weak signal, restrictive NAT/firewall, temporary internet loss, app process death, permission denial, and re-entry.
- Confirm failures are clearly surfaced and never show a false “connected” state.
- Confirm no stale camera/microphone capture, orphan sessions, ghost participants, or duplicate tracks remain after ending calls.
- Verify repeated calls work without restarting the app.

### Billing and observability
- Review Cloudflare usage and Railway usage after controlled tests.
- Configure usage alerts/limits where available; the owner must approve any paid-plan or spending changes.
- Keep logs sanitized and correlate issues using timestamps, operation names, session IDs and Cloudflare request identifiers (never secrets or SDP).
- Confirm costs and data usage are acceptable for Nigerian mobile networks before wider rollout.

## 7. CI, APK and release acceptance

- Re-fetch PR #599 and exact current head before every edit or CI conclusion.
- Require backend tests, syntax checks, route/security tests, Android full build, unit tests, lint, group/Marketplace source contracts, and full emulator instrumentation.
- Investigate every red check to root cause; distinguish code regression from external dependency rate limiting or runner failure. Rerun only after documenting/fixing the cause.
- Capture actual emulator screenshots and UI hierarchies for private calls, Marketplace Live Proof, and group calls.
- Do not treat screenshots, source checks, or green CI as proof of real multi-account media.
- Build and verify the one canonical APK through the canonical integration branch only after PR review/acceptance and owner authorization.
- No PR merge, canonical branch update, or APK replacement until the acceptance evidence is complete and the owner has been told exactly what is being merged.

## 8. Final closure checklist

A future takeover chat may mark calling complete only when all are true:

- [ ] Cloudflare App Secret rotated after prior exposure and securely stored in Railway.
- [ ] Railway pending changes reviewed; only intended changes deployed to the correct testing service/environment.
- [ ] Backend deployment healthy at a recorded commit SHA.
- [ ] Native Android SFU publish/subscribe/SDP flow implemented and verified against Cloudflare's current API schema.
- [ ] Private call tests pass on separate accounts/devices.
- [ ] Marketplace Live Proof validates the real listing and seller and passes positive/negative tests.
- [ ] Group calls pass simultaneous join, membership, media, reconnect and cleanup tests.
- [ ] Network resilience and resource cleanup pass.
- [ ] CI is green on exact PR head; any external failures are resolved or explicitly documented.
- [ ] Real emulator evidence and physical-device live-call evidence captured.
- [ ] Canonical APK is built from the approved integration branch and verified.
- [ ] Owner approves merge/release and receives the final evidence and remaining limitations.

**Current status:** this handoff is a plan and record of known state, not a certification. Do not claim live calling works until the checklist has been completed with evidence.


## 9. 2026-11-28 Android group-call mesh negotiation correction

A source-level review of the group-call join event sequence found an Android-side gap independent of Railway availability: the backend sends the updated roster to the joining user, but sends existing participants a group_participant_joined event. The existing Android handler created a peer connection for that event but did not start the deterministic SDP offer. The newcomer could therefore appear in the participant roster without the existing participant initiating media negotiation.

The coordinated call branch now handles that event through the same offer-election policy used for roster reconciliation. The lower opaque user ID initiates the offer, the opposite side answers, and a per-call set prevents duplicate offers when a roster and participant event describe the same peer. Peer creation now returns false for an already-existing peer, so its return value means a newly created connection.

Added JVM tests cover one-offer-per-pair election, opaque string ordering, blank IDs, and self-pairs. The source verifier also checks that the participant-joined path calls the shared negotiation helper.

This corrects the Android mesh edge setup only. It does not prove live two-way audio/video, TURN relay, Cloudflare SFU publishing, offline invite delivery, or physical-device network behavior. Those remain separate live acceptance gates.


## 10. 2026-11-28 Group camera-mute / late-join correction

A second Android-only group-media defect was found: the engine used one boolean both for the call's video mode and the camera's mute state. Muting the camera set that boolean to false, so a peer created afterward could skip adding the existing video track. Unmuting then would not give that late participant a video stream.

The engine now retains a separate video-call mode flag, while the camera control only toggles the existing VideoTrack enabled state. New peer connections attach the track whenever the call is a video call and a local track is available—even if it is currently disabled. The track therefore becomes visible when the user unmutes, without requiring another SDP track negotiation.

Added JVM policy tests for muted-camera late join, voice-call no-video, and missing-camera behavior. This is a local media attachment correction; remote video still requires working signaling/connectivity and must be separately verified.


## 12. 2026-11-28 direct-call remote media receive-path verification

Rechecked the exact unified calling branch after comparing it with the camera/signaling branch #589. The current #599 head already contains both the legacy `onAddStream` callback and the Unified Plan `onAddTrack` callback in `FynxWebRtcCallEngine`; the latter forwards both remote AudioTrack and VideoTrack to the existing active-call callbacks. No duplicate callback was added.

The dedicated video-call source verifier now explicitly guards this receive path so a future branch reconciliation cannot silently drop it. The callback's presence is source evidence only; remote-track delivery still needs real authenticated two-account runtime verification.


## 13. 2026-11-28 Direct call transport failure handling

Ported the call-transport failure behavior from the coordinated camera/signaling hardening work into the unified call branch while preserving the Marketplace listing-ID invite parameter. Direct WebRTC offer, answer and ICE callbacks now check whether the authenticated realtime client accepted each frame. If it did not, the active call is stopped, local media is released, pending invite/answer flags are cleared, call history is marked unavailable, and a retryable error is shown rather than leaving a false connecting screen.

The Android direct-call send helper now validates call ID, target, media type and signal name, and bounds outgoing SDP and ICE candidate payloads before handing them to the authenticated socket. Remote signaling errors now use the same cleanup path. Hang-up timeout and manual end report when the local socket did not accept the terminal signal.

Marketplace listing context remains attached to the outgoing invite; the added failure checks cover offer/answer/ICE only. Group signaling remains separately managed by its existing group event queue and per-peer state.

This is app-side transport correctness. It does not certify that a live Railway/Neon realtime endpoint is healthy or that two physical devices can complete the call.
