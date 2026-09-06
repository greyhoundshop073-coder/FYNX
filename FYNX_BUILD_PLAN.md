# FYNX BUILD PLAN

## Rule
Build one major stage at a time. Inspect existing code first. Keep each stage consolidated. Build Android after every stage. 🟢 Green = continue. 🔴 Red = fix before continuing. Never duplicate existing functions. Never put API secrets in the APK. Full integration verification is performed as one consolidated pass, not as separate feature-by-feature builds.

## Ten-stage product build
1. Foundation & Design System — DONE 🟢
2. Authentication & User Identity — DONE 🟢 (UI/local foundation)
3. Chat & Messaging — IN PROGRESS
4. Groups & Social
5. Marketplace
6. Voice & Video Calls
7. Money Tools
8. Notifications & Sharing
9. FYNX Extra Tools — DONE 🟢
10. Final Integration & Polish — COMPLETE 🏁

## ACTIVE PHASE — FYNX REALITY / FUNCTIONALITY FIX PASS
This phase continues the existing work. The current priority order is: foundation/runtime/navigation, authentication/session identity, people discovery + complete profiles + privacy, chat/realtime/media reliability, friends/groups/social interactions, marketplace/payment protection, camera/media reliability, notifications/sharing/preferences, AI integrations, admin/trust/safety, then cross-feature integration/performance/security/regression verification.

### People Discovery → Complete Profile
- [x] Search results resolve selected users through the authenticated backend search contract
- [x] Shared Other User Profile surface remains the single profile destination
- [x] Dedicated authenticated backend profile-detail contract
- [x] Backend profile visibility enforcement before profile data is returned
- [x] Authoritative display name and @username
- [x] Backend-backed bio/about, country/region and verification fields when available
- [x] Privacy-aware activity visibility
- [x] Mutual connection and post counts where permitted
- [x] Backend-driven friend relationship/request state on profile
- [x] Message, block and remove-friend actions use existing authenticated pipelines
- [x] Loading, empty, denied and retry states
- [ ] Backend-backed profile photo editing/upload
- [ ] Backend-backed follow/unfollow state
- [ ] Backend-backed report case submission and status
- [ ] Profile content/media feed reused from the existing social pipeline

### Professional product-behavior audit
- [x] Privacy controls are server-enforced for the protected systems completed so far
- [x] Presence/activity privacy and blocking protections
- [x] Unknown-user discovery and abuse controls
- [x] Block restrictions across relevant messaging/call/media/marketplace paths
- [x] Delivery/read/error/retry foundations for production messaging
- [x] Media upload/download/privacy/cache protections
- [x] Sensitive operations use backend authorization
- [x] Realtime/background recovery foundations
- [ ] Full real-device user-journey audit across every major surface

## Future development backlog
- [ ] Personal Status/Timeline: text, photo, video, link, audience, edit/delete, reactions, comments and view counts
- [ ] FYNX Official Updates/Announcements with secure admin authorization
- [ ] Per-user notification preferences with server-backed settings
- [ ] Secure FYNX Owner/Admin Control Center
- [ ] Anti-Scam, Account Safety & Appeals: detection, risk levels, restrictions, evidence, freeze/appeal/review/unfreeze

## Stage 12 — AI Creation Layer follow-up backlog
- [ ] AI Creation stays connected to the existing media/posting pipeline
- [ ] AI-assisted captions, rewrites, ideas and marketplace descriptions use the existing authenticated AI backend
- [x] AI Photo Editor remains integrated with the existing media flow
- [ ] AI Money Coach uses the same FYNX AI system and never executes transactions
- [ ] Original FYNX chat conversation wallpaper
- [ ] Improved chat profile information, including legitimate country/region
- [ ] Consistent full media display across statuses, social posts and other media surfaces

## Chat & Group Chat UX requirement
- [x] Private conversation camera entry and shared CameraX foundation
- [ ] Group conversation camera entry using the same shared camera foundation
- [ ] Group media sent through the real group messaging pipeline
- [ ] Consistent FYNX conversation background/wallpaper on private and group conversations
- [ ] Full real-device call journey: request, ringing, accept/reject, signaling, ICE, PeerConnection, remote media, reconnect/failure

## Important
Stage 3 UI and local persistence are foundations. Production messaging requires authenticated backend APIs, realtime transport, secure media storage and encryption design.

Profile and settings local foundations must be connected to secure backend behavior before launch.

## Removed permanently
AI image generation and AI video generation are NOT part of the FYNX roadmap. Do not add them back.
