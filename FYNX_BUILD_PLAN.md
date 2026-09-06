# FYNX BUILD PLAN

## Rule
Build one major stage at a time. Inspect existing code first. Keep each stage consolidated. Build Android after every stage. 🟢 Green = continue. 🔴 Red = fix before continuing. Never duplicate existing functions. Never put API secrets in the APK. Full integration verification is performed as one consolidated pass, not as separate feature-by-feature builds.

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
- [ ] Group conversation camera entry using the shared camera
- [ ] Group media through the real group messaging pipeline
- [ ] Original FYNX conversation wallpaper/background
- [ ] Full real-device voice/video call journey

### Professional behavior still to finish
- [x] Server-enforced privacy/blocking protections for completed systems
- [x] Message/media reliability foundations
- [x] Realtime/background recovery foundations
- [ ] Full real-device user-journey audit across every major surface

## Future backlog
- [ ] Personal status/timeline expansion
- [ ] FYNX official announcements/admin controls
- [ ] Server-backed notification preferences
- [ ] Owner/Admin control center
- [ ] Anti-scam, account safety and appeals

## Stage 12 follow-up
- [ ] AI Creation stays connected to the existing media/posting pipeline
- [ ] AI-assisted captions, rewrites, ideas and marketplace descriptions
- [x] AI Photo Editor integration
- [ ] AI Money Coach
- [ ] Chat profile information improvements
- [x] Consistent profile media display foundation
- [ ] Consistent full media display across status/social/media surfaces

## Removed permanently
AI image generation and AI video generation are NOT part of the FYNX roadmap. Do not add them back.
