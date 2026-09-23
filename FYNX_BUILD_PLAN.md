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
- [x] FYNX official announcements/admin controls — server-backed announcements are exposed in the APK with authenticated retry/error handling
- [x] Owner/Admin control center — server-authoritative dashboard, admin grants/revocation, account status and marketplace protection controls
- [x] Anti-scam, account safety and appeals — server-backed safety/report/appeal flows certified by Large Badge #9
- [x] Server-backed notification preferences foundation

## Stage 12 follow-up
- [x] AI Creation stays connected to the existing media/posting pipeline
- [x] AI-assisted captions, rewrites, ideas and marketplace descriptions
- [x] AI Photo Editor integration
- [x] AI Money Coach
- [x] Chat profile information improvements — conversation header uses real remote display name, username, joined/country data and remote profile identity
- [x] Consistent profile media display foundation
- [x] Consistent full media display across status/social/media surfaces — shared authenticated remote media path is reused across Status, social/profile content and chat/group media

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



### Large Badge #9 — Trust, Safety & Account Protection Certification
- [ ] Server-backed report creation, account-scoped report history and abuse-case status
- [ ] Real block/unblock enforcement remains server-authoritative across social/profile/media flows
- [ ] Account safety controls persist server-side and are authenticated/account-isolated
- [ ] Appeals are authenticated, tied to the submitting account and cannot access another user's report
- [ ] Anti-scam content inspection remains server-side and protected by the existing abuse guard
- [ ] Privacy & Safety APK surface exposes real report submission, report history and appeals states
- [ ] Android and backend verification cover the complete trust/safety contract with no fabricated safety records
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


### Large Badge #10 — Final Integration & Release Certification
- [x] Major social, messaging, marketplace, media, notifications, AI and trust/safety certification gates remain consolidated in CI
- [x] Production journey and security certification remain wired before Android build/test
- [x] Android lint, unit tests and APK assembly remain mandatory
- [x] Real Android instrumentation executes before release artifact publication
- [x] Runtime screenshots and UI hierarchies are captured from the built APK
- [x] Exact-commit debug APK artifact is published only after successful build/test/instrumentation
- [x] No common API-secret patterns are permitted in critical client files
- [x] Final certification is complete — consolidated workflow is GREEN


### Large Badge #11 — Final Product Completeness & UX Integration
- [x] Official FYNX announcements are reachable in the APK and backed by the authenticated backend
- [x] Owner/Admin control center uses server-authoritative dashboard, role protection, account controls and marketplace protection review
- [x] Chat profile information displays real remote identity details without fabricated user data
- [x] Full media display is consistent across Chat, Groups, Status/Stories, social/profile and marketplace surfaces using the shared remote-media path
- [x] Trust/safety, appeals and account-protection continuity remains connected to the product surfaces
- [x] Dedicated product-completeness verifier is wired into the Android release workflow
- [x] Badge is complete when the consolidated workflow remains GREEN
