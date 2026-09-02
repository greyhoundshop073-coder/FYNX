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
The current FYNX repository build scope is complete after the consolidated final audit and successful Android build verification. No additional feature-development batch is required for this completion cycle. Future product work should be treated as a new development phase rather than another completion step.

## Important
Stage 3 UI and local persistence are foundations. Production messaging requires a backend, realtime transport, authenticated server APIs, secure media storage, and encryption design. Those should be connected before calling the messaging system production-ready.

Profile and settings currently remain local foundations. Production account recovery, export, deletion, privacy enforcement, and server-backed preferences must be connected to the secure backend before launch.

## Removed permanently
AI image generation and AI video generation are NOT part of the FYNX project roadmap. Do not add them back.
