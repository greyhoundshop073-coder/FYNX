# FYNX BUILD PLAN

## Rule
Build one major stage at a time. Inspect existing code first. Keep each stage consolidated. Build Android after every stage. 🟢 Green = continue. 🔴 Red = fix before continuing. Never duplicate existing functions. Never put API secrets in the APK. Full integration verification is performed as one consolidated pass, not as separate feature-by-feature builds.

## LOCKED FYNX APK / UX INTEGRATION RULE
Whenever a feature is intended for users, it is not complete merely because code exists or a verifier passes. It must be correctly positioned in the APK, have the expected entry point and controls, provide the complete user interaction and states, connect to the real backend/storage where required, handle navigation/keyboard/insets/errors/retry appropriately, and be verified in the applicable real user journey. Established social/messaging/camera products may be used as UX references for familiar interaction patterns, but FYNX's existing visual language and architecture must be preserved. GREEN means this complete standard is met. Once GREEN, keep it locked and do not reopen it for theoretical concerns; reopen only for an actual regression or failed test. This rule is canonical for future FYNX chats and must be followed before moving to another workstream.

## ACTIVE PHASE — FYNX REALITY / FUNCTIONALITY FIX PASS
Continue the existing FYNX Android social + marketplace app. Do not rebuild or replace working systems. The current execution order is runtime/navigation, identity, people/profiles/privacy, chat/realtime/media, groups/social, marketplace/payment protection, camera/media reliability, notifications/preferences, AI flows, trust/safety, then final integration/performance/security/regression.

### People Discovery → Complete Profile
- [x] Authenticated backend profile-detail contract
- [x] Backend profile visibility enforcement
- [x] Authoritative display name and username
- [x] Backend-backed bio, country and verification fields when available
- [x] Privacy-aware activity visibility
- [x] Mutual connection and post counts where permitted
- [x] Backend-driven friend/request state
- [x] Message, block and remove-friend actions use existing pipelines
- [x] Loading, empty, denied and retry states
- [x] Backend-backed profile photo editing/upload
- [x] Backend-backed follow/unfollow
- [x] Backend-backed report case submission/status
- [x] Profile content/media feed reuse
- [x] Remote profile photo display with privacy-aware media fetch

### Chat & Group Chat
- [x] Private conversation camera entry and shared CameraX foundation
- [x] Group conversation camera entry using the shared camera
- [x] Server-authoritative group creation/membership foundation
- [x] Authenticated group message send/load pipeline
- [x] Group media message ownership validation
- [x] Original FYNX conversation wallpaper/background
- [x] Full real-device voice/video call journey foundation restored; runtime device verification remains in final audit

### Professional behavior
- [x] Server-enforced privacy/blocking protections for completed systems
- [x] Message/media reliability foundations
- [x] Realtime/background recovery foundations
- [x] Full real-device user-journey audit across every major surface

### Large Badge #4 — Full Real-Device User-Journey Certification
- [x] Emulator instrumentation is executed against the production-shaped app
- [x] Home, Chat, Friends, Stories, Marketplace, Calls, Notifications, Privacy, Money and AI routes are wired
- [x] Runtime screenshots and UI hierarchy are captured
- [x] Existing smoke, deep-link, recovery, call and account-isolation instrumentation remains present
- [x] No fake application data is introduced during verification
- [x] Consolidated workflow is GREEN

### Large Badge #5 — Camera + Media Reliability & Full Media Display Certification
- [ ] Shared CameraX photo/video capture remains production-wired, including permission, lens, flash, zoom, exposure, timer, preview, retake and send states
- [ ] Remote image/video/audio media uses authenticated backend download, account-scoped caching, bounded media size, retry/error states and resource cleanup
- [ ] Social posts and Status/Stories reuse the same real remote-media path with correct aspect-ratio presentation
- [ ] Profile avatars use real backend media and privacy-aware fallback behavior
- [ ] Existing media/post/camera verification gates remain GREEN
- [ ] Android unit/instrumentation coverage validates media lifecycle and camera-to-post behavior
- [ ] No fake media records or fabricated application data are introduced
- [ ] Badge is not complete until the consolidated workflow is GREEN

## Future backlog
- [x] Personal status/timeline expansion — backend-first timeline now surfaces authenticated Status media/text/audio while retaining Stories creation/viewing
- [ ] FYNX official announcements/admin controls
- [ ] Owner/Admin control center
- [ ] Anti-scam, account safety and appeals
- [x] Server-backed notification preferences foundation

## Stage 12 follow-up
- [x] AI Creation stays connected to the existing media/posting pipeline
- [x] AI-assisted captions, rewrites, ideas and marketplace descriptions
- [x] AI Photo Editor integration
- [x] AI Money Coach
- [ ] Chat profile information improvements
- [x] Consistent profile media display foundation
- [ ] Consistent full media display across status/social/media surfaces

## Removed permanently
AI image generation and AI video generation are NOT part of the FYNX roadmap. Do not add them back.


### Large Badge #6 — Marketplace Protected Transaction Full-Lifecycle Certification
- [ ] Buyer checkout creates a protected order before payment and preserves the canonical order ID
- [ ] Payment is verified server-side before protected-order progression
- [ ] Seller shipping, buyer receipt confirmation and post-inspection completion use the real backend lifecycle
- [ ] Disputes lock the canonical order and preserve prior order/escrow state
- [ ] Refund/payout resolution is mutually exclusive and idempotent
- [ ] Escrow and financial operations expose explicit pending, disputed, released/refunded states with unique idempotency/provider references
- [ ] Order evidence and inventory reservation remain server-authoritative
- [ ] Existing marketplace security gates plus real Android instrumentation remain GREEN
- [ ] No fabricated buyers, sellers, listings, orders, payments or balances are introduced
- [ ] Badge is not complete until the consolidated workflow is GREEN


### Large Badge #7 — Notifications + Preferences + Delivery Certification
- [ ] Server-backed notification feed, read/unread state and account isolation
- [ ] Server-backed per-category notification preferences and quiet mode
- [ ] Authenticated device registration/unregistration with unique device ownership
- [ ] Server-side FCM delivery honors user preferences and handles retry/invalid tokens
- [ ] Real event wiring covers messaging, groups and social activity without fabricated records
- [ ] Existing notification/security gates plus real Android instrumentation remain GREEN
- [ ] Badge is not complete until the consolidated workflow is GREEN



### Large Badge #8 — FYNX AI Flows & Intelligence Certification
- [ ] Real FYNX Assistant entry point, text composer, send, retry/error and conversation controls are production-wired
- [ ] Persistent authenticated AI conversations and real media attachments remain user-scoped
- [ ] AI context, tool execution, confirmation-gated messaging and sensitive-action restrictions remain server-authoritative
- [ ] Voice/realtime AI uses authenticated FYNX session transport with bounded tool processing
- [ ] Provider credentials remain server-side and abuse/security controls remain active
- [ ] Existing AI extension and security gates remain GREEN
- [ ] No fabricated AI application data or secrets are introduced
- [ ] Badge is not complete until the consolidated workflow is GREEN
