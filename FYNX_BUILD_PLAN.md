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
10. Final Integration & Polish — IN PROGRESS

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
- [ ] Full integration build and completion audit

## Important
Stage 3 UI and local persistence are foundations. Production messaging requires a backend, realtime transport, authenticated server APIs, secure media storage, and encryption design. Those should be connected before calling the messaging system production-ready.

Profile and settings currently remain local foundations. Production account recovery, export, deletion, privacy enforcement, and server-backed preferences must be connected to the secure backend before launch.

## Removed permanently
AI image generation and AI video generation are NOT part of the FYNX project roadmap. Do not add them back.
