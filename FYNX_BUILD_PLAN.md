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

## Stage 3 checklist
- [x] Chat list and conversation entry
- [x] One-to-one conversation screen
- [x] Text message composer and send
- [x] Voice-note recording foundation
- [x] Voice-note playback foundation
- [x] Copy message text
- [x] Text selection / copy and paste support
- [x] Reply to message foundation
- [x] Edit sent text foundation
- [x] Delete sent message foundation
- [x] Message search foundation
- [x] Attachment picker foundation
- [x] Local text-message persistence
- [x] Safe empty/error handling foundations
- [x] FYNX dark theme and reusable rounded surfaces
- [ ] Production realtime messaging backend
- [ ] Server-side message synchronization
- [ ] End-to-end encryption
- [ ] Production media/voice upload service

## Stage 10 checklist
- [x] Home, Chats, Friends, Marketplace and Money Tools visual consistency
- [x] Features hub and Extra Tools visual consistency
- [x] Calendar and To-Do visual consistency
- [x] Notifications visual consistency
- [x] Profile visual polish and local profile persistence foundation
- [x] Settings & privacy visual polish and local preference persistence foundation
- [x] Global personalization/accent preference connection
- [x] Full navigation/screen consistency audit
- [x] Final UI/UX polish
- [x] Full integration build and completion audit

## ACTIVE PHASE — FYNX REALITY / FUNCTIONALITY FIX PASS
This phase does not replace the old work. It is an integrated continuation of it. Every newly discovered defect, missing professional behavior, weak interaction, backend gap or security/privacy problem is added here and also mapped back to the original stage/backlog item. The objective is to make existing FYNX features actually work reliably, not merely appear on screen.

### Order of execution for speed and stability
1. Foundation/runtime/navigation blockers
2. Authentication/session/account identity
3. People discovery + complete profiles + privacy
4. Chat/realtime/media/message reliability
5. Friends/groups/social interactions
6. Marketplace + payment protection
7. Camera/media/display/upload reliability
8. Notifications/sharing/preferences
9. AI integrations and tool flows
10. Admin/trust/safety/anti-scam
11. Cross-feature integration, performance, security and regression verification

### Current first fix batch — People Discovery → Complete Profile
- [x] Search result can resolve the selected user through the authenticated backend search contract instead of relying only on the device-local friend store
- [x] Shared Other User Profile surface remains the single profile destination; no duplicate profile implementation
- [ ] Add a dedicated authenticated backend profile-detail contract returning only fields the viewer is authorized to see
- [ ] Profile photo, display name, @username and bio/about from authoritative backend profile data
- [ ] Country/region only where legitimately available and permitted
- [ ] Verification indicator where applicable
- [ ] Privacy-aware online/activity state
- [ ] Mutual friends/connections where permitted
- [ ] Public posts/status/content and profile media where permitted
- [ ] Add Friend / Accept / Remove / Follow / Unfollow state driven by backend relationship state
- [ ] Message, voice and video actions connected to supported existing pipelines
- [ ] Share profile, block and report actions with backend enforcement
- [ ] Proper loading, empty, permission-denied and retry states
- [ ] Backend must enforce profile visibility; private fields must not be fetched and hidden only in the UI
- [ ] Reuse this profile system from People search, Friends, Chat, Marketplace seller, Groups and Social content

### Professional product-behavior audit findings to carry through the old work
- [ ] Privacy controls must be real server-enforced controls, not device-only presentation switches
- [ ] Presence/last-seen/activity must respect per-user privacy rules
- [ ] Unknown-user discovery must have clear identity/context and abuse controls
- [ ] Block/report must consistently stop or restrict the relevant interactions across search, chat, calls, social and marketplace
- [ ] Messaging must have dependable delivery/read/error/retry/offline behavior before production-ready status
- [ ] Media must have reliable upload, download, preview, playback, retry and failure states
- [ ] Social profiles/content must use ownership checks for edit/delete/privacy operations
- [ ] Every sensitive operation must be authorized by the backend, never trusted because a button is hidden in Android UI
- [ ] Realtime and background work must recover cleanly from network loss, app restart and token/session changes
- [ ] Every major surface needs empty/loading/error states, accessibility, responsive layout and safe repeated-tap behavior
- [ ] Existing functionality must be tested as user journeys, not only by checking that screens compile/open

## Future development backlog — saved for later
These are planned product features, not part of the current green build batch. Build them when their development phase is reached, without restarting or replacing the existing FYNX project.

### Social Updates & Personal Timeline
- [ ] Personal Status/Timeline for every FYNX user
- [ ] User-created text, photo, video and link updates
- [ ] Per-post audience/privacy controls
- [ ] User-owned edit/delete controls where supported
- [ ] Likes, reactions, comments/replies and view counts
- [ ] Comment/reply controls, mute and block controls
- [ ] Optional disappearing status/updates
- [ ] Server-side ownership and privacy enforcement for every user's content

### FYNX Official Updates
- [ ] Official FYNX Updates/Announcements area separate from private chats
- [ ] Authorized FYNX admin/owner publishing controls
- [ ] Create, edit and remove official announcements
- [ ] Important/security/emergency announcements
- [ ] Delivery/view metrics where appropriate
- [ ] Secure server-side admin authorization; ordinary users must not be able to impersonate admin

### Personal Notification & Privacy Controls
- [ ] Per-user notification preferences for messages, likes, comments, friend requests, announcements and channels
- [ ] Security-critical notifications protected from ordinary notification toggles where necessary
- [ ] Per-user privacy controls for profile, posts/status and interactions
- [ ] Server-backed preferences rather than device-only settings for production behavior

### FYNX Owner/Admin Control Center
- [ ] Secure admin dashboard/control center
- [ ] Platform announcements and system controls
- [ ] Trust & Safety review queue
- [ ] Scam reports, account restrictions and appeals
- [ ] Audit trail for sensitive administrative actions
- [ ] Strong role/permission checks enforced on the backend

### Anti-Scam, Account Safety & Appeals
- [ ] Scam/risk detection across messages, profiles, posts and marketplace activity
- [ ] Risk levels: Normal, Watch, Restricted, Frozen
- [ ] Behavioral signals, reports, rate limits and anomaly detection
- [ ] Protective restrictions for high-risk behavior
- [ ] Evidence-preserving moderation actions
- [ ] Account freeze that can be appealed
- [ ] Appeal submission and case status for affected users
- [ ] Review evidence before final action
- [ ] Restore/unfreeze account when review shows the user was innocent/right
- [ ] Keep restriction or remove account when malicious behavior is confirmed
- [ ] Audit trail recording reason, action, review decision and timestamp
- [ ] Abuse-resistant appeal/review controls

## Final completion state
The previous consolidated completion audit is preserved as historical status. The current development phase is the Reality / Functionality Fix Pass above; it must reach GREEN through actual build and verification before a new completion claim is made.

## Important
Stage 3 UI and local persistence are foundations. Production messaging requires a backend, realtime transport, authenticated server APIs, secure media storage, and encryption design. Those should be connected before calling the messaging system production-ready.

Profile and settings currently remain local foundations. Production account recovery, export, deletion, privacy enforcement, and server-backed preferences must be connected to the secure backend before launch.

## Removed permanently
AI image generation and AI video generation are NOT part of the FYNX project roadmap. Do not add them back.

## Stage 12 — AI Creation Layer follow-up backlog
- [ ] AI Creation remains connected to the existing FYNX media and posting pipeline; no duplicate posting system
- [ ] AI-assisted captions, rewrites, ideas and marketplace descriptions use the existing authenticated FYNX AI backend
- [ ] AI Photo Editor remains integrated with the existing media flow
- [ ] AI Money Coach in Money Center uses the same FYNX AI system and must never execute transactions
- [ ] Chat conversation wallpaper: add an original, subtle FYNX patterned background to the existing chat conversation screen
- [ ] Chat profile information: improve the existing conversation header/profile details, including country/region where legitimately available
- [ ] Full media display: remove unnecessary black/empty side areas around videos and stories while preserving aspect ratio and important content
- [ ] Apply the media-display correction consistently across status/stories, social posts and other existing media presentation surfaces

## Chat & Group Chat UX requirement — screenshot-inspired, FYNX-native
- Use the user's supplied chat screenshots as UX reference for familiar conversation structure and discoverability, without copying another service's branding/assets.
- Apply one consistent FYNX conversation design language to both private chats and group chats.
- Conversation list: recognizable avatar, name, latest-message preview, time, unread state and clear online/activity state where available.
- Conversation screen: polished header, profile/avatar access, online/typing state, voice/video actions where supported, message search and useful conversation actions.
- Composer: text, media/photo/video, camera, voice message and send actions connected to the existing FYNX messaging/media pipeline.
- Message interactions: replies, reactions, edit/delete where already supported, delivery/read state, media previews and voice playback.
- Group conversation: same visual language as private chat, plus member identity, group info/members, admin controls and group-specific actions.
- Keep chat and group chat responsive, accessible, familiar and uncluttered; do not create a second messaging system.
- Add a subtle original FYNX conversation background/wallpaper to the actual conversation surfaces, including group conversations, while preserving readability and accessibility.
