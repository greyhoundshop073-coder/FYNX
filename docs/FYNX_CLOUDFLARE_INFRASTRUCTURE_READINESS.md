# FYNX — Cloudflare Security, Media and Video Infrastructure Readiness

**Workstream owner:** Chat — Cloudflare Infrastructure  
**Stage:** 2 — Cloudflare Security, Media and Video  
**Audit date:** 2026-11-26  
**Canonical branch inspected:** `integration/fynx-single-apk-20261013`  
**Canonical base SHA:** `4382023be9dfd51d43728231504ce8fe9950e125`  
**Feature branch:** `docs/cloudflare-infra-readiness-20261126`  
**Scope:** Read-only infrastructure/source audit and implementation plan. No Cloudflare resource, secret, DNS record, WAF rule, storage bucket, deployment, provider switch, or media object was changed.

## 1. Executive result

**Status: CONDITIONAL — BLOCKED for live Cloudflare verification; source-level preparation is documented.**

The Railway testing environment confirms that the variable names `CLOUDFLARE_SFU_APP_ID` and `CLOUDFLARE_SFU_APP_SECRET` are present in a **staged** environment patch. Railway redacts their values. This proves only that encrypted variable entries have been staged; it does not prove that the values are valid, that the referenced Cloudflare Calls SFU application exists, or that Cloudflare has enabled the relevant product on the owner's account.

The current canonical Android call engine is peer-to-peer WebRTC. It creates a peer connection using public STUN servers and has no configured TURN credential flow or SFU media transport. The canonical Neon realtime bridge relays call signaling (offer, answer and ICE candidate) between users; it does not relay media. The open call integration PR #599 explicitly documents four-person mesh calling and no production TURN relay. It is not an SFU implementation.

The repository contains a Cloudflare R2-compatible S3 adapter, but the active media provider remains configuration-driven and defaults to PostgreSQL. The existing Railway testing patch stages Neon storage variables, not R2 variables. No Cloudflare account-level inventory was available in this session, so account products, R2 buckets/keys, Calls SFU apps, DNS, WAF, DDoS settings and zone configuration remain **not verified**.

**Recommendation:** do not migrate media to R2 or switch the call engine to SFU yet. Keep the existing Neon-first storage path as the current target, complete live Neon media verification, and separately validate the Cloudflare account and credentials. Cloudflare TURN is the lowest-risk first network improvement for one-to-one calls; introduce SFU for group fan-out only after a bounded proof-of-concept and cost/quality tests.

## 2. Connection inventory

| Area | Verified evidence | Status / conclusion |
|---|---|---|
| Cloudflare account and enabled products | No Cloudflare account connector or authenticated Cloudflare API session was available. | **Not verified.** Cannot list account products, zones, billing, R2, Calls apps, TURN keys, DNS or security settings. |
| Railway SFU variables | Railway project `FYNX-Isolated-Testing`, environment `testing`, service `fynx-backend-testing` lists `CLOUDFLARE_SFU_APP_ID` and `CLOUDFLARE_SFU_APP_SECRET`. Both are additions in staged patch `0e560e21-bc20-4134-bd0a-0781b3fb1eea`; values are redacted. | **Names staged; values and validity unverified.** They are not evidence of a live Cloudflare application or live backend configuration. |
| Railway deployment | Service source points at the canonical integration branch. The service is `live-with-staged-changes`; 11 staged changes remain. Its latest deployment is `FAILED` (2026-11-03). No deploy was triggered by this audit. | **Not live-verified.** Do not treat staged variables or source as active runtime configuration. |
| R2 bucket and credentials | No Cloudflare account access. Railway's current staged patch includes Neon S3 variables and `FYNX_MEDIA_STORAGE_PROVIDER`, but not `FYNX_R2_ACCOUNT_ID`, `FYNX_R2_ACCESS_KEY_ID`, `FYNX_R2_SECRET_ACCESS_KEY` or `FYNX_R2_BUCKET`. | **No live R2 bucket/key verified.** Do not infer a bucket from test fixtures. |
| R2 adapter | `backend/mediaStorage.js` supports `neon` and `r2`, validates provider configuration, uses S3-compatible clients, signs short-lived upload/download URLs, validates object keys and bounds object size to 12 MB. | **Implemented in source; live R2 unverified.** |
| Active media provider | `FYNX_MEDIA_STORAGE_PROVIDER` defaults to `postgres` in the adapter. The staged Railway config contains Neon storage variables; it does not prove a provider switch has deployed. | **No R2 cutover.** Neon is the preferred existing object-storage path pending live verification. |
| Android WebRTC | `FynxWebRtcCallEngine.kt` defaults to Google public STUN servers and `stun.cloudflare.com:3478`. It constructs a peer connection for the call and exchanges SDP/ICE through FYNX signaling. No TURN username/credential configuration or SFU session transport is present in this canonical class. | **P2P mesh with STUN discovery only.** Cloudflare STUN does not provide TURN relay or SFU forwarding. |
| Backend call signaling | `backend/neon-realtime-bridge.js` persists call state and relays authenticated participant signaling; its call signal types include offer, answer and ICE. It forwards signaling messages, not RTP media. | **Signaling relay, not media SFU.** |
| Existing Cloudflare migration notes | `backend/CLOUDFLARE_MIGRATION_ENGINEERING_RECORD.md` labels its assessment historical, says Cloudflare Worker scaffolding was removed, and records that no Worker, Durable Object, R2 bucket or Cloudflare deployment was created. It also explains that Workers' Node HTTP adapter cannot pass through the existing WebSocket upgrade transport. | **No active Cloudflare backend migration.** Neon Functions is the selected runtime in that record. |
| DNS / CDN / WAF / DDoS | Not readable without the Cloudflare account/zone inventory. | **Not verified.** No DNS or security configuration was changed. |

### Railway staged configuration caution

The Railway testing service currently has 11 staged changes, including the two SFU variable names, Neon media configuration, database URL, branch and builder changes. The service's most recent deployment is failed. The staged patch is not live until explicitly committed and successfully deployed. This workstream did not accept the patch, reveal variable values, or restart/redeploy the service.

## 3. Why the current calls remain mesh + public STUN

There are three distinct layers:

1. **Signaling:** FYNX's backend tells the other participant about the call and forwards SDP offers/answers and ICE candidates.
2. **ICE connectivity:** STUN helps a device discover its public-facing address and attempt a direct peer-to-peer path. It does not relay the media when direct connectivity fails.
3. **Media transport:** The Android WebRTC engine currently creates peer connections between participants. In group calls, each participant therefore has multiple peer connections and sends/receives separate media streams: mesh.

The current architecture does not become an SFU merely because an SFU App ID/Secret variable name exists or because the backend relays signaling. An SFU requires the Android client to publish tracks to a Cloudflare SFU session and subscribe to server-forwarded remote tracks. The application backend must create/authorize sessions, validate call membership and listing/group context, and securely deliver short-lived session credentials/track metadata. Those media-publishing/subscription steps are not implemented in the canonical Android engine inspected.

The open PR #599 (head `109884d7417742098d005e5291a7c9f431069fde`) states that group video is mesh-capped at four participants and has no production TURN relay. It is still open. Its Backend CI, Chat Final Closure and Chat Group Parity checks passed on the observed head; Android Build failed because `FynxMarketplaceScreensComposeTest.kt` has unresolved `onAllNodesWithContentDescription` references at lines 128 and 174 during `compileDebugAndroidTestKotlin`. This is a separate call workstream; do not duplicate or overwrite it. PR #589 also remains open and is not mergeable at the last inspected state. No call PR is being certified or merged by this documentation work.

## 4. Recommended TURN / SFU connection plan

### Phase A — prove TURN before changing media topology

1. Verify the Cloudflare account and Calls product availability, then list the existing Calls SFU apps and TURN keys through the account API. Read-only first; do not create or rotate credentials during inventory.
2. Confirm whether the staged `CLOUDFLARE_SFU_APP_ID` refers to an existing app. Verify the secret server-side without printing it. If the app or secret is absent/invalid, report that before provisioning anything.
3. Create a dedicated, least-privilege TURN key only after owner approval. The backend should mint short-lived TURN credentials and return only the ICE server URLs, username and credential to the authenticated caller. Never embed the long-lived app secret in Android, logs, crash reports or repository files.
4. Extend the existing call configuration/repository to load ICE servers from the authenticated backend call setup. Retain STUN as a candidate path; add TURN UDP and TCP/TLS fallbacks for restrictive networks. Do not hardcode permanent TURN credentials in the APK.
5. Test direct candidate selection and forced relay (`iceTransportPolicy=relay`) separately. Verify UDP, TCP and TLS fallback, credential expiry, denied/invalid credentials, Wi-Fi↔mobile handover, restrictive NAT, call reconnect and media teardown.
6. Keep one-to-one calls P2P with TURN fallback initially. TURN solves connectivity; it does not create an SFU.

Cloudflare's documented TURN endpoints include `turn.cloudflare.com` on UDP/TCP 3478, UDP 443, TCP 80, and TLS 5349/443. The documented STUN endpoint is `stun.cloudflare.com:3478`. Confirm the currently supported endpoint/ports and credential-generation API against the account's current Cloudflare documentation before implementation.

### Phase B — add SFU for group/video fan-out

1. Keep the existing FYNX signaling contract as the authority for user identity, call state, permissions, blocking and Marketplace listing/seller validation.
2. Add a server-side SFU session adapter using the verified Cloudflare Calls app credentials. Backend creates a session only after validating the caller, call participants, group membership or exact Marketplace listing/seller relationship.
3. Return a minimal, short-lived session token/connection descriptor to each authorized participant. Never return the account app secret.
4. Replace per-peer media fan-out for the SFU-enabled call type with one publisher connection per participant and explicit subscribe/unsubscribe operations. Handle remote track lifecycle, mute/camera changes, reconnect, session expiry and participant departure.
5. Keep P2P/TURN as a controlled fallback only if product policy allows it and the user is told which mode is active. Never silently downgrade into an unbounded mesh.
6. Load-test 2, 3, 4, 8 and 16 participants with realistic video bitrate, mobile CPU, packet loss, data consumption and latency. Record per-participant uplink/downlink and server egress. Decide the supported group size from measured results, not assumptions.

Cloudflare's current product guidance distinguishes Realtime SFU (custom native/WebRTC transport and application-managed signaling/permissions) from RealtimeKit (prebuilt meeting SDKs/UI). FYNX already has a custom native WebRTC engine, so Realtime SFU is the more direct architectural fit; adopting RealtimeKit would be a separate SDK/UI integration decision, not a drop-in server switch.

## 5. CDN, DNS, WAF and DDoS plan

No zone configuration has been inspected, so these are target controls, not claims about current account settings.

- **DNS:** Keep the authoritative FYNX app/API hostnames explicit. Proxy only HTTP(S) API/media hostnames supported by Cloudflare; do not proxy arbitrary realtime/WebSocket or TURN media endpoints without validating protocol support and latency. Maintain DNSSEC where registrar/zone support allows. Keep origin addresses and management interfaces private where possible.
- **WAF:** Start with Cloudflare managed rules and conservative API-specific rate limits. Apply stricter limits to login/session, call invite/session creation, media upload-intent/finalize, public profile and comment endpoints. Exempt authenticated WebSocket upgrade paths from generic body/rate rules only where required and replace with connection, message-size and per-user quotas. Never use WAF as a substitute for backend authentication/authorization.
- **DDoS:** Confirm L3/L4 and HTTP DDoS protections are enabled for proxied zones, verify origin protection and alerting, and run only approved low-rate tests. Do not run a stress/flood test against production.
- **CDN:** Use cache rules only for deliberately public, immutable/versioned media derivatives. Cache key must include the immutable media version. Do not cache authenticated API responses, private chat/group attachments, private statuses, unlisted media, seller-private media, signed URLs, or any response whose authorization depends on the caller.
- **Origin controls:** Keep R2 buckets private by default. Use backend authorization and short-lived signed access for private media. A public media domain should expose only an explicit public-media namespace/derivative with publication visibility enforced before the object is made public. Avoid a public bucket that can reveal raw/private object keys.
- **Observability:** Record request IDs, status class, cache hit/miss, WAF action and media object IDs only; never log bearer tokens, signed URL query strings, TURN credentials, SDP bodies or secret values.

### Safe public/private media caching

Current `mediaStorage.js` writes object metadata with `Cache-Control: private, max-age=60`; signed download URLs default to 120 seconds and are bounded to 30–300 seconds. That is a private-first posture. The authenticated `/api/media/:id` authorization route must remain uncached/private. For public media, add a separate explicit public-serving path only after checking publication status, audience, blocks, ownership and deletion state. Store public derivatives under immutable versioned keys and set a public cache lifetime; purge or version-bump on takedown. Never turn all existing media into public R2 URLs.

## 6. Neon Object Storage vs Cloudflare R2 cost comparison

Public list pricing checked 2026-11-26. Prices below are USD, exclude taxes and any ancillary service charges, and do not reflect FYNX's actual usage or negotiated plan.

| Item | Neon Object Storage | Cloudflare R2 Standard |
|---|---:|---:|
| Storage | $0.023 / GB-month on paid plans | $0.015 / GB-month |
| Operation charges | No per-operation charge stated in Neon docs | Class A $4.50 / million; Class B $0.36 / million |
| Included usage | Depends on Neon plan; object storage available across plans | 10 GB-month, 1m Class A, 10m Class B per month |
| Egress | Counts against Neon's shared public network transfer allowance; paid plans include 500 GB/month from June 2026 | No R2 egress charge |
| Integration | Existing FYNX Neon-first deployment and branch-aligned data model | Existing S3-compatible R2 adapter, but no live R2 bucket/credentials verified |

Illustrative storage-only comparison:

- 100 GB average stored: R2 approximately **$1.35/month** after its 10 GB free storage allowance; Neon approximately **$2.30/month** at the published paid-plan rate.
- 1,000 GB average stored: R2 approximately **$14.85/month** after its 10 GB allowance; Neon approximately **$23.00/month** at the published paid-plan rate.

At R2's standard tier, the first 1 million Class A and 10 million Class B operations per month are included. Above those allowances, writes/listing and reads add cost. R2's free egress is its major advantage for media-heavy public delivery. Neon has no per-operation object-storage fee, and its paid-plan 500 GB public data-transfer allowance may be sufficient for early traffic. The actual Neon plan and current account usage must be checked before forecasting overage.

**Decision:** keep Neon as the active storage target for now. It is already the selected FYNX backend path and existing project/bucket configuration is staged for Neon, while no live R2 setup has been verified. R2 is a credible later choice if measured media egress becomes the dominant cost. Do not migrate or dual-write until the owner approves a measured cost case and a rollback-safe migration plan.

## 7. Implementation plan and ownership boundaries

| Work item | Owner / dependency | Exit evidence |
|---|---|---|
| Cloudflare account, product, zone, R2 bucket and Calls app inventory | Owner must provide an authenticated Cloudflare connection or perform guided account access | Account ID/zone/app/bucket identifiers recorded safely; secret values never displayed |
| Verify SFU app ID/secret | Cloudflare account access + Railway staged secret metadata | Cloudflare API confirms app exists; server-side authenticated test passes without exposing secret |
| Verify R2 credentials/bucket | Cloudflare account access; no provisioning until approved | Bucket metadata and least-privilege S3 access confirmed by non-destructive HEAD/list test |
| TURN credential API and Android ICE configuration | Calls/backend owner + Android calls owner | Authenticated short-lived credentials; forced-relay and fallback tests on device |
| SFU session adapter and native media integration | Existing Calls owner (#599/#589); coordinate before touching call engine, signaling, backend call handlers or CI workflow | Publish/subscribe proof, group tests, failure/reconnect/security tests and physical-device verification |
| CDN/DNS/WAF/DDoS audit | Cloudflare zone/account access | Read-only configuration export/checklist and approved changes only |
| Storage cost and migration decision | Owner + storage owner | Measured GB, operations, egress and retrieval profile; written approval before migration |

This workstream does not own the call engine or shared call PR files. PR #599 owns unified call integration and PR #589 owns camera/call signaling hardening; coordinate with those owners rather than creating a parallel call implementation. Storage adapter code is also shared with the existing Storage / Media / Realtime workstream; no adapter changes were made here.

## 8. Runtime acceptance checklist

### Cloudflare account and secrets
- [ ] Cloudflare account and intended FYNX zone confirmed.
- [ ] R2 enabled; intended private bucket exists in the correct account.
- [ ] R2 credentials are least-privilege and limited to the intended bucket.
- [ ] SFU app exists; App ID matches Railway configuration.
- [ ] SFU secret authenticates successfully in a server-side test without printing it.
- [ ] TURN key exists and backend can mint short-lived credentials.
- [ ] Billing alerts/limits and owner approval are set before any billable activation.

### Media storage
- [ ] Non-destructive R2 bucket HEAD/list and signed PUT/HEAD/GET/DELETE tests pass.
- [ ] Upload URL expiry, content type, object size and key validation pass.
- [ ] Unauthorized account cannot retrieve private media; owner and intended recipients can.
- [ ] Private chat, group, Status, Marketplace and seller-private media remain private.
- [ ] Public derivatives are separately addressed, immutable, purgeable and visibility-gated.
- [ ] Mixed Neon/R2 rows remain readable and rollback path is proven before migration.
- [ ] Existing Neon/Postgres fallback works during provider outage.

### Calls and realtime
- [ ] TURN-only forced relay succeeds over UDP.
- [ ] TURN TCP/TLS fallback succeeds on a restrictive network.
- [ ] Invalid/expired credentials fail closed and can be refreshed.
- [ ] One-to-one P2P direct and TURN-relayed paths both work.
- [ ] SFU publisher/subscriber flow works with two, three and four authenticated test accounts.
- [ ] Group participant authorization is server-enforced; arbitrary participant injection is rejected.
- [ ] Marketplace Live Proof binds the actual listing ID and seller identity to the call.
- [ ] Camera/microphone permission denial, background/foreground, network handover, ICE restart, reconnect, leave/end and cleanup pass.
- [ ] Physical-device tests cover at least two different mobile networks.
- [ ] No call success is shown until signaling and media connectivity are actually confirmed.

### Security and operations
- [ ] DNS/origin and TLS configuration verified.
- [ ] WAF managed rules and API-specific rate limits reviewed.
- [ ] DDoS protections and alerting verified.
- [ ] Authenticated APIs and private media are never cached publicly.
- [ ] No secrets, tokens, SDP, signed URL query strings or TURN credentials appear in logs.
- [ ] Usage, egress, latency, packet loss, failed call rate and storage errors are observable.
- [ ] Owner approves any paid product activation, production deployment or media migration.

## 9. Evidence links

- Canonical branch head at audit: https://github.com/greyhoundshop073-coder/FYNX/commit/4382023be9dfd51d43728231504ce8fe9950e125
- Open unified call PR #599: https://github.com/greyhoundshop073-coder/FYNX/pull/599
- Open camera/call hardening PR #589: https://github.com/greyhoundshop073-coder/FYNX/pull/589
- Existing Neon-first media adapter PR #492 (merged historically): https://github.com/greyhoundshop073-coder/FYNX/pull/492
- Cloudflare Realtime SFU overview: https://developers.cloudflare.com/realtime/sfu/
- Cloudflare TURN service and ports: https://developers.cloudflare.com/realtime/turn/
- Cloudflare TURN FAQ / TURN vs SFU: https://developers.cloudflare.com/realtime/turn/faq/
- Cloudflare Realtime SFU/TURN pricing: https://developers.cloudflare.com/realtime/sfu/pricing/
- Cloudflare R2 pricing: https://developers.cloudflare.com/r2/pricing/
- Neon plans and Object Storage pricing: https://neon.com/docs/introduction/plans

## 10. What is verified vs unverified vs blocked

**Verified from repository/Railway read-only inspection**
- Canonical integration branch head is `4382023be9dfd51d43728231504ce8fe9950e125`.
- Railway testing service has the two SFU variable names in a staged patch; values remain redacted.
- Railway service has 11 staged changes and its latest deployment is failed; staged changes were not applied.
- R2 adapter source and deterministic test fixtures exist; adapter supports Neon/R2 configuration.
- Canonical Android WebRTC source defaults to public STUN and peer connections.
- Canonical Neon realtime bridge relays signaling, not media.
- Historical Cloudflare migration record states no Worker/Durable Object/R2/Cloudflare deployment was created.
- PR #599 documents four-person mesh and no production TURN; its observed Android Build check failed at Android test-source compilation.

**Implemented but not live-verified**
- R2 S3-compatible storage adapter and provider selection.
- Neon/R2 dual-provider read abstraction and signed URL machinery.
- FYNX call signaling and Android P2P WebRTC engine.

**Blocked / requires owner access or coordinated engineering**
- Cloudflare account/product/zone inventory, SFU app existence and secret validity.
- R2 bucket and credential existence/validity.
- Live TURN credential generation and forced-relay tests.
- SFU Android publish/subscribe integration.
- DNS/WAF/DDoS/CDN configuration review.
- Any paid activation, R2 migration or production cutover.

**Final status: CONDITIONAL — BLOCKED.** No production infrastructure was changed and no live Cloudflare capability is certified.


---

## 11. 2026-11-27 authenticated Cloudflare follow-up (supersedes prior access limitation)

**Inspection method:** authenticated, read-only Cloudflare dashboard inspection through the owner's saved TinyFish session. No resource or account setting was changed. Secret values and payment details were not copied into this report.

### Account inventory now visible

| Area | 2026-11-27 evidence | Engineering conclusion |
|---|---|---|
| Realtime Serverless SFU | One app is listed as **FYNX Video Calling**. The UI's app label was inconsistently rendered once as “FYXN”; confirm the intended app by its stable app ID before connecting production traffic. | App existence is confirmed; deployment state, environment, endpoint URL, bindings, app-secret validity and runtime usage were not exposed by the dashboard pages reached. |
| Realtime TURN keys | The Realtime TURN Server product page shows no TURN Server apps. It does not expose the separate Calls TURN-key inventory. No TURN key UID, label or created/modified timestamp was visible in the inspected dashboard UI. | TURN key existence is **not verified**, not proven absent. Do not create a key until the owner approves provisioning and confirms the previously exposed SFU secret has been rotated. |
| R2 | R2 opens on subscription/onboarding setup; no bucket is listed and no usage dashboard was visible. The displayed Standard free allowance is 10 GB-month, 1 million Class A operations and 10 million Class B operations. | R2 is not configured for FYNX in the inspected account view. Do not switch the existing Neon-first provider or create a bucket without approval. |
| DNS | No DNS zones/domains were listed in the inspected account view. | No FYNX domain/zone security can be certified from this account view. |
| Zero Trust | Overview is an onboarding/default-account page. Operational threat findings, device counts, access policies and logs were not displayed. | Security posture remains unverified; sidebar navigation is not proof that a control is configured. |
| Observability | SFU analytics/usage destinations showed no populated data. | No real SFU media traffic or call runtime is certified. |

### Official Cloudflare TURN contract cross-check

The isolated route in PR #615 was checked against Cloudflare's current public documentation:

- Credential generation uses `POST https://rtc.live.cloudflare.com/v1/turn/keys/{TURN_KEY_ID}/credentials/generate-ice-servers`, with `Authorization: Bearer {TURN_KEY_API_TOKEN}` and JSON `{ "ttl": ... }`.
- Cloudflare's API schema specifies the TURN key UID as 32 characters and the returned bearer key as exactly 64 characters; the route's 32-hex UID / 64-hex secret checks match that schema.
- Cloudflare documents the relay URLs used by the route. Port 53 is a known browser timeout path and is filtered by the route.
- The documented client refresh mechanism is WebRTC `RTCPeerConnection.setConfiguration()`; Android call engines must still implement credential fetch and refresh.
- Cloudflare says TURN is free when used together with Realtime SFU; standalone TURN is billed at $0.05 per real-time GB outbound. R2 Standard free allowances are monthly and do not remove charges after the allowance is exceeded.

Sources:
- https://developers.cloudflare.com/realtime/turn/generate-credentials/
- https://developers.cloudflare.com/api/resources/calls/subresources/turn/methods/create/
- https://developers.cloudflare.com/realtime/sfu/
- https://developers.cloudflare.com/r2/pricing/

### Repository/CI status rechecked 2026-11-27

- Canonical branch remains `integration/fynx-single-apk-20261013`; PR #612 is the latest verified merge and its merge commit is `9b2401759dfb7e15293ec898685fe86b4ba08a48`.
- PR #615 is based on that canonical SHA. Its exact head `de36002dfb30efecf0f0ef02344dbeade1588a15` has successful checks for Cloudflare TURN Contract, Backend CI, Android Build, Chat Final Closure and Chat Group Parity/Layout Safety.
- PR #615 is still an isolated backend route module. It is not registered in the active server and is not called by Android yet.
- PR #599 remains open. Backend CI, Chat Final Closure and Chat Group Parity passed on observed head `109884d7417742098d005e5291a7c9f431069fde`; Android Build failed in `FynxMarketplaceScreensComposeTest.kt` at unresolved `onAllNodesWithContentDescription` references. Do not treat its call integration as certified.
- PR #589 remains open and is marked non-mergeable in the observed metadata. Its latest observed checks passed, but it overlaps the calling files owned by #599 and must be coordinated rather than duplicated.

### Updated status and safe next steps

**Status: CONDITIONAL — Cloudflare account access achieved; live infrastructure still not ready/certified.**

1. Keep Neon as the active media-storage target until the existing Neon bucket and protected upload lifecycle are verified. R2 is an optional future provider, not a prerequisite to finish app-side media work.
2. Confirm the SFU app identity and server-side secret validity without displaying secrets. Because a previous setup handoff warned that an SFU secret had been exposed in a downloaded page, rotate that secret before any production deployment; record only the rotation completion/date, never the value.
3. If the owner approves, create one least-privilege TURN key in the intended Cloudflare account, store its 32-character UID and 64-character key only in the Railway testing secret store, and never put it in Android or Git.
4. Have the #599 call owner register the PR #615 route with shared auth/rate limiting and add the route test to backend CI. Android must fetch short-lived credentials per active call and refresh before expiry with `setConfiguration()`.
5. Validate SFU session publish/subscribe and remote cleanup separately from TURN. TURN relay does not implement SFU media routing.
6. Create/activate an R2 bucket only after an explicit owner decision and a private-bucket/access-policy plan. The current free allowance is bounded; usage alerts and cost controls must be reviewed before growth.
7. Obtain a FYNX-controlled domain/zone before DNS, WAF, CDN and DDoS controls can be audited and configured.

No Cloudflare resources, TURN keys, R2 buckets, DNS records, WAF policies, paid products, secrets or deployments were created or modified by this follow-up.
