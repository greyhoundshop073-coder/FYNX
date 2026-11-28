# FYNX Live Shopping — Approved Future Project Plan

**Status:** OWNER-APPROVED FUTURE PROJECT — PLANNING ONLY  
**Implementation status:** NOT STARTED  
**Approval recorded:** 25 November 2026  
**Canonical integration target:** `integration/fynx-single-apk-20261013`  
**Repository:** `greyhoundshop073-coder/FYNX`

> This document is the handoff source of truth for any future ChatGPT session, engineer, or team asked to plan or implement FYNX Live Shopping. Read it before proposing code. The owner has approved the project and its preparation, not immediate implementation, provider spending, production deployment, or changes to the current app.

## 1. Product vision

Build **FYNX Live Shopping**: an optional live-commerce channel connected to the existing FYNX Marketplace. An approved seller can broadcast a product demonstration from My Shop. Buyers can watch, ask questions, react, view/pin products, add products to their existing cart, and complete purchases through the existing Marketplace checkout.

The live broadcast is a discovery and product-demonstration surface, not a second marketplace.

### Core buyer and seller journeys

**Seller**
1. Open My Shop and choose Start Live (future feature).
2. Enter a title, choose eligible products, set visibility, and review camera/microphone permissions.
3. Start a broadcast using a short-lived server-issued streaming credential.
4. Pin/unpin one of their own active products, answer questions, and see basic live metrics.
5. End the broadcast; the session closes and any recording follows the seller's explicit recording choice and retention policy.

**Buyer**
1. Discover a LIVE NOW card in Marketplace.
2. Join as a viewer without publishing camera/microphone.
3. Watch, comment, react, follow/share, and open a pinned product.
4. Add the product to the existing cart and use existing checkout/payment/order flows.
5. Return to the broadcast if the app's navigation/checkout design supports it.

### Product principles
- My Shop is the main home for professional seller broadcasts. A future personal-account sharing path may be considered later, but it is not part of the first pilot.
- One canonical listing/product identity is used across Marketplace, Home, shop, live product pins, cart, orders, and analytics. Do not duplicate product, stock, price, seller or payment records.
- Live streaming is optional and lazy-loaded. Ordinary Marketplace browsing must not initialize camera, microphone, streaming SDK, or a live connection.
- No automatic recording by default. Clear consent, notice, reporting, moderation and retention rules are required.
- Never promise that a product is available or reserve stock merely because it is pinned. Existing inventory and checkout rules remain authoritative.

## 2. Repository investigation — existing assets to reuse

The canonical integration branch was inspected while preparing this plan. Re-check the exact current canonical head and relevant PRs when implementation begins; repository state can change.

### Existing Android and Marketplace foundations
- `app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt`: current Marketplace Compose entry/panel. Extend its existing discovery/navigation surfaces; do not build a parallel Marketplace.
- `app/src/main/java/com/fynx/app/ui/FynxCallsPanel.kt`: existing Calls UI and lifecycle.
- `app/src/main/java/com/fynx/app/ui/FynxCallMediaEngine.kt`: media-engine contract.
- `app/src/main/java/com/fynx/app/ui/FynxWebRtcCallEngine.kt`: current one-to-one/group call WebRTC engine.
- Existing realtime signaling and call session models should be inspected and reused only where semantics genuinely fit. A broadcast is not a mesh call.

### Important existing limitation
The current group-call path is a peer-to-peer mesh capped at four participants total. It is not a scalable one-to-many broadcast/SFU architecture. The current call-engine notes also identify that production TURN relay is not configured/certified. **Do not expand the group mesh to serve live shopping audiences.** Do not claim current video calls equal a production livestream service.

### Existing Marketplace contract
Read and preserve `docs/FYNX_MARKETPLACE_MASTER_ARCHITECTURE.md`. It defines the canonical product/listing identity, buyer checkout, protected order, ledger, dispute, payout, inventory and seller flows. Live Shopping must hand off to those existing systems; it must not create new checkout, escrow, wallet, refund, inventory or payout logic.

### Existing media/backend foundations
Inspect the current canonical backend and media-storage integration before design or code. The repository has documented Neon-backed media work and a private `fynx-media` bucket in the existing development setup; confirm its current branch, credentials, limits and deployed status at implementation time. Do not assume a development bucket or staged environment is production-ready. Keep streaming media delivery separate from ordinary object storage unless a tested provider design explicitly requires recording/export into object storage.

### Coordination with existing calls work
PR #599 is the existing coordinated Calls/Marketplace Live Proof/group calling workstream:
https://github.com/greyhoundshop073-coder/FYNX/pull/599

Review its final merged/current state and the canonical implementation before starting. Live Proof is a buyer↔seller call about a particular listing; Live Shopping is one seller publishing one stream to many viewers. Share reusable identity, permission, signaling conventions and lifecycle patterns where appropriate, but do not conflate the products or duplicate the Calls UI.

## 3. Recommended architecture

Use a **managed broadcast/SFU service** for video distribution. FYNX's backend remains authoritative for users, shop permissions, live-session metadata, product pins, comments authorization, moderation and commerce. The streaming provider carries audio/video packets and scales viewer delivery.

Conceptual flow:

```
FYNX Android app
  Seller: My Shop -> Start Live -> camera/mic publisher
  Buyer: Marketplace -> LIVE NOW -> viewer
                 |
                 v
FYNX backend (session authority)
  - seller/shop authorization
  - short-lived publish / subscribe token issuance
  - session lifecycle and metadata
  - product pin ownership checks
  - chat/moderation/report authorization
  - rate limits, audit events and cost guardrails
                 |
        +--------+---------+
        |                  |
        v                  v
Managed live-video     Existing FYNX commerce
provider (SFU/CDN)     and Marketplace systems
- ingest/publish       - canonical listing IDs
- fan-out to viewers   - cart and checkout
- adaptive layers      - payment and order state
- reconnect/quality    - wallet/ledger/protection
        |
        v
Viewer devices
```

### Hard architectural boundaries
- Never send video frames through the ordinary Node/Neon application API or store continuous video in Postgres.
- Never use peer-to-peer full-mesh connections for public audiences.
- Provider API secrets stay server-side. Android receives only short-lived, scoped, revocable session credentials.
- The backend authorizes the seller and selected listing IDs. A client-supplied product title/price is never authoritative.
- Keep the video provider replaceable behind a small FYNX interface/adapter; avoid scattering provider-specific SDK calls through screens and domain code.
- Keep live comments/events distinct from the media transport. Use the existing supported realtime backend path where appropriate, with bounded messages and persistence policy.
- Do not make video recording mandatory. Recording adds cost, privacy and storage obligations.
- Use adaptive bitrate/simulcast or provider-managed quality layers, conservative default resolution, and viewer quality adaptation for weak/mobile networks.
- Provide explicit network fallback/retry, app background/foreground handling, screen lock behavior, camera/microphone release, and stream termination on account/session invalidation.

## 4. Provider evaluation — decision not yet made

Evaluate at least these two providers with a small proof of concept and current written quotes/pricing before selecting one:

### Candidate A: LiveKit Cloud
- Supports WebRTC-based realtime rooms, ingress and egress/recording workflows.
- Potential fit where FYNX wants interactive low-latency streaming and a portable open-source server option.
- Pricing includes usage components such as participant connection minutes, bandwidth and transcoding/egress depending on selected plan and feature use. Model real viewer traffic, not just host time.
- Official references:
  - https://livekit.com/pricing
  - https://docs.livekit.io/transport/media/ingress-egress/ingress/
  - https://kb.livekit.io/articles/3947254704-understanding-livekit-cloud-pricing

### Candidate B: Agora Broadcast Streaming
- Provides a broadcast audience mode designed for one-to-many live streaming.
- Its published pricing distinguishes host and audience minutes and video quality. The pricing page currently lists HD host usage at USD $3.99/1,000 minutes and HD broadcast audience usage at USD $1.99/1,000 minutes; confirm current terms, free allowance, billing mode, regional availability and any minimums before purchase.
- Its pricing documentation explains that usage is participant-minute based and resolution affects the rate.
- Official references:
  - https://www.agora.io/en/pricing/broadcast-streaming/
  - https://docs.agora.io/en/interactive-live-streaming/introduction/pricing

### Provider selection scorecard
Score each candidate on:
1. Real one-to-many broadcast/SFU architecture and audience scaling.
2. Quality and resilience on Nigerian mobile networks and higher-latency connections.
3. Android SDK stability, app size impact, Compose/lifecycle compatibility and release maintenance.
4. Host-to-viewer latency appropriate for shopping interaction.
5. Adaptive bitrate, simulcast/layers, reconnect and audio-only/low-data fallback.
6. Data residency, privacy, encryption, abuse controls, moderation hooks and token security.
7. Actual total cost: host minutes + viewer minutes + bandwidth/egress + transcoding + recording + storage + taxes/FX + any minimum commitment.
8. Provider lock-in and feasibility of self-hosting/migrating later.
9. Operational support, uptime/SLA and incident visibility.

Do not select a provider based on headline free minutes alone. Do not enable paid usage until the owner approves a measured cost ceiling and billing alerts.

## 5. Cost-control model before public launch

Video cost grows with audience viewing time. Example: 100 viewers watching for 30 minutes creates 3,000 audience video-minutes, plus the host's publishing usage. At the currently published Agora HD broadcast audience rate of USD $1.99 per 1,000 minutes, that audience component alone would be approximately USD $5.97, before host usage, any other fees, taxes or currency conversion. This is an illustration, not a quote or guaranteed invoice.

Required controls:
- Start with an invite-only pilot, low concurrent-viewer cap and short session limit.
- Default to 360p/480p or provider-adaptive low/medium layers; offer higher quality only where justified.
- No auto-recording or replay by default.
- Enforce per-seller session limits, concurrent broadcast limits, viewer caps and global monthly spend caps server-side.
- Monitor actual provider usage and cost per live session/viewer-minute.
- Automatically stop or reject new sessions at configured limits; alert administrators before limits are reached.
- Provide a kill switch to disable new broadcasts without breaking ordinary Marketplace.
- Estimate costs using realistic local network profiles and target user counts before expanding.
- Keep a free/low-cost development environment separate from production credentials and budgets.

## 6. Proposed data model and API responsibilities

This is a design starting point; confirm existing schema conventions and avoid duplicate tables/routes before implementation.

Potential concepts:
- `live_sessions`: session ID, seller/user ID, shop ID, title, status, provider, provider room/stream reference, visibility, start/end timestamps, viewer-count snapshot, moderation state, created/updated timestamps.
- `live_session_products`: session ID, canonical listing ID, seller ownership snapshot/reference, pinned order/state and timestamps. Listing price/stock remain sourced from the canonical listing.
- `live_chat_messages`: session ID, sender ID, bounded text, moderation status and timestamps; media attachments are out of first-pilot scope.
- `live_session_events`: audit events such as start/end, pin/unpin, moderator action, report and provider callback. Avoid recording sensitive tokens or unnecessary personal data.
- Optional aggregate analytics: viewer-minute totals, product opens, add-to-cart attribution and completed order attribution. Keep analytics separate from financial ledger truth.

Potential backend operations (names illustrative; check route naming conventions first):
- Create/start session: authenticated seller; verify shop status and listing ownership; create provider room/stream; issue short-lived publish token.
- Join session: verify session is live and viewer is allowed; issue scoped subscribe token.
- End session: seller or authorized moderator; revoke/close provider session and update state idempotently.
- Pin/unpin product: verify seller owns the active listing and listing is eligible; broadcast a bounded event.
- Send/read comments: enforce authentication/guest policy, rate limits, length limits, moderation and report controls.
- Provider webhooks: signature verification, replay protection, idempotency, event reconciliation and safe state transitions.

Do not implement these APIs until the existing backend route registration, Neon schema migration pattern, authentication and realtime implementation have been inspected.

## 7. Android integration and app-performance rules

- Add a separate, lazy-loaded Live Shopping feature module/package; do not initialize streaming SDKs from app startup or Marketplace's ordinary browse path.
- Add LIVE NOW as a lightweight list of session metadata/thumbnails, not a preconnected set of video players.
- Join and initialize the media SDK only after a buyer explicitly enters a live session.
- Start camera/microphone only after a seller explicitly chooses Start Live and grants contextual permission.
- Release tracks, camera, audio focus, sockets, timers and provider resources on end, leave, navigation, background policy or error.
- Keep one active playback session per device in the initial release.
- Bound chat rendering and event history; use paging/windowing rather than unbounded in-memory lists.
- Use thumbnails/posters before joining. Do not preload multiple live video streams in Marketplace cards.
- Test low-memory Android devices, thermal behavior, battery drain, mobile data use, orientation, interruptions, incoming calls and process recreation.
- Measure APK size before/after SDK addition. Use dependency analysis and shrink/ABI strategy where safe; never compromise provider SDK functionality.
- Existing Marketplace browsing, product detail, checkout and ordinary app navigation must remain usable when the streaming provider is down or disabled.

## 8. Safety, trust and commerce requirements

Before any public pilot:
- Seller must be authenticated, eligible and authorized for the shop.
- Only the seller's own eligible listings may be pinned.
- Buyers must see clear seller identity and the exact canonical listing being promoted.
- Comments require rate limits, block/report tools, spam controls and moderator handling.
- Provide report-live-session and report-comment paths.
- Define prohibited content, counterfeit/misleading listing response, seller suspension, stream termination and appeals.
- Protect minors and prevent inappropriate or exploitative broadcasts; apply age and content rules appropriate to the platform and jurisdiction.
- Explicitly disclose if a stream is recorded; define consent, retention and deletion.
- Protect provider credentials and private stream URLs; use expiring tokens and server authorization.
- Do not expose buyer personal data in public comments or overlays.
- A pinned product does not guarantee stock. Checkout revalidates current inventory and price server-side.
- A stream must never alter payment, escrow, dispute, refund or payout state.
- Preserve the existing FYNX buyer-protection and ledger invariants.
- Review Nigerian privacy, consumer-protection, tax and payment-provider obligations before production launch; obtain appropriate professional review where needed.

## 9. Staged delivery plan and acceptance gates

### Stage 0 — Revalidate and design (no feature code)
- Read this document, `FYNX_CANONICAL_APK.md`, `docs/FYNX_RELEASE_GATE.md`, `docs/FYNX_MARKETPLACE_MASTER_ARCHITECTURE.md`, and the current engineering/reference-to-implementation standards.
- Inspect exact canonical branch SHA, open PRs, merged state of #599, Calls/media implementation, backend bootstrap, Neon schema, storage and realtime routes.
- Map existing capabilities and missing pieces in a short architecture decision record (ADR).
- Compare LiveKit and Agora using a real Android proof-of-concept branch isolated from the canonical APK.
- Produce a cost model for 1, 10, 50, 100 and 500 concurrent viewers at 10, 30 and 60 minutes, using current vendor pricing.
- Exit gate: owner approves provider, monthly spend cap, pilot limits, privacy/recording policy and implementation scope.

### Stage 1 — Technical spike (isolated prototype, not production)
- Prove one Android publisher and a small number of viewers on physical devices.
- Test low-bandwidth/reconnect and foreground/background behavior.
- Measure app size, memory, CPU, heat, battery, latency and actual billed usage.
- No production users, live payment, recording or broad rollout.
- Exit gate: repeatable successful tests and measured cost within owner-approved limits.

### Stage 2 — Private seller pilot
- Add backend-authorized session lifecycle and provider token issuance.
- Add seller Start/End Live and viewer join/leave UI behind a feature flag.
- Start with one host and a deliberately small audience cap.
- Add basic LIVE NOW discovery, moderation/reporting, metrics and emergency kill switch.
- Exit gate: security, abuse, weak-network, cost and real-device tests pass; owner explicitly approves expansion.

### Stage 3 — Commerce integration
- Add canonical product pinning and live product detail entry.
- Add cart handoff and existing checkout; verify stock and price at checkout.
- Add order attribution to the stream without changing financial truth.
- Test completed order, failed payment, out-of-stock, seller suspension and stream termination.
- Exit gate: existing Marketplace payment/order/dispute tests remain green and live buyer/seller journey is demonstrated.

### Stage 4 — Controlled public expansion
- Expand viewer/seller caps gradually based on measured reliability and cost.
- Add optional replay/recording only after separate consent, retention and cost approval.
- Add seller analytics and promoted live sessions only after abuse, advertising and billing controls are ready.
- Review provider performance and unit economics before each expansion.

## 10. Required international specialist review

Use a skills-based distributed review, not assumptions about nationality. If recruiting across countries, these are useful sourcing pools and perspectives, not country stereotypes or mandatory nationalities:
- **Streaming/SFU architect** — seek experienced realtime-media/SFU engineers (for example, US/EU/global open-source LiveKit/WebRTC communities).
- **Android performance engineer** — seek Kotlin, CameraX, audio focus, lifecycle and low-memory device expertise (India, Europe, East Asia or global).
- **Backend/realtime engineer** — seek PostgreSQL, Neon, WebSocket/realtime, token security and idempotent API expertise (Nigeria, India, Europe or global).
- **Security/privacy reviewer** — seek application security, privacy engineering and trust-and-safety expertise (global).
- **Nigeria commerce/network reviewer** — seek Nigerian Android users, sellers, mobile-network testing and local payments/consumer-protection experience.
- **Independent QA lead** — seek hands-on physical Android device, poor-network, load and accessibility testing experience.

One technical owner must coordinate interfaces and approve architecture. Contributors must not independently create parallel Marketplace, Calls, chat, payment or media implementations.

## 11. Mandatory FYNX engineering and handoff rules

Every future chat/developer must:
1. Inspect the current canonical integration branch and all relevant PRs before changing code.
2. Read `FYNX_CANONICAL_APK.md` and `docs/FYNX_RELEASE_GATE.md`; target only `integration/fynx-single-apk-20261013` through a focused feature branch and PR.
3. Preserve existing work. Do not restart, duplicate or wholesale merge unrelated branches.
4. Keep Live Shopping behind a feature flag until the pilot is explicitly approved.
5. Do not change login, Wallet, Marketplace financial flows, Home, Status, Chat/Groups or Camera outside a reviewed dependency.
6. Group meaningful related work into coherent PRs; avoid tiny fragmented pushes.
7. If CI turns red, investigate the exact failing job/log and root cause, distinguish regression from pre-existing/environment failure, fix it and rerun. Never guess or declare success without evidence.
8. Verify Android build, unit tests, lint, backend tests, security contracts, emulator and physical-device journeys as applicable.
9. Capture actual runtime screenshots/video evidence from the exact tested build; do not substitute mockups or claim screenshots prove untested backend behavior.
10. Do not publish another competing APK. Any candidate must follow the canonical APK contract and exact commit/artifact/checksum evidence.
11. Do not merge to main, deploy production, create paid provider resources or incur usage charges without the owner's explicit authorization.
12. Report: branch → PR → integrated canonical commit → exact APK artifact/run → CI → real runtime evidence → unresolved blockers.

## 12. First actions when the owner says “start FYNX Live Shopping”

The next chat must do these steps in order:
1. Confirm the owner has explicitly started implementation and confirm current spending/provider authorization; this planning approval alone is not authorization to incur charges.
2. Re-read this plan and the required canonical/release/Marketplace standards.
3. Inspect the exact current canonical SHA, PR #599 status, active Calls/media code, backend bootstrap, Neon schema/realtime and media storage.
4. Produce a concise current-state gap matrix: already present / reusable with changes / missing / blocked.
5. Prepare provider comparison and a cost estimate using current published pricing and the owner's viewer/session assumptions.
6. Present the Stage 0 ADR and seek approval for provider, budget cap and pilot limits before any paid provisioning.
7. Implement only the approved Stage 1 prototype in an isolated branch; no production rollout until all gates pass.

## 13. Reference documents and provider sources

FYNX repository:
- `FYNX_CANONICAL_APK.md`
- `docs/FYNX_RELEASE_GATE.md`
- `docs/FYNX_MARKETPLACE_MASTER_ARCHITECTURE.md`
- `REFERENCE_TO_IMPLEMENTATION_STANDARD.md`
- `app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt`
- `app/src/main/java/com/fynx/app/ui/FynxCallsPanel.kt`
- `app/src/main/java/com/fynx/app/ui/FynxCallMediaEngine.kt`
- `app/src/main/java/com/fynx/app/ui/FynxWebRtcCallEngine.kt`
- PR #599: https://github.com/greyhoundshop073-coder/FYNX/pull/599

Provider documentation and pricing (re-check at implementation time):
- LiveKit pricing: https://livekit.com/pricing
- LiveKit ingress: https://docs.livekit.io/transport/media/ingress-egress/ingress/
- LiveKit livestream cost guide: https://kb.livekit.io/articles/3947254704-understanding-livekit-cloud-pricing
- Agora broadcast pricing: https://www.agora.io/en/pricing/broadcast-streaming/
- Agora interactive live streaming pricing: https://docs.agora.io/en/interactive-live-streaming/introduction/pricing

---
**Owner decision:** FYNX Live Shopping is approved as a future project and this plan is the handoff baseline. It is not permission to start implementation, merge code, deploy, or spend money until the owner explicitly says the project is ready to begin.
