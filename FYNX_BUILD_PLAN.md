# FYNX BUILD PLAN

## Rule
Build one major stage at a time. Inspect existing code first. Keep each stage consolidated. Build Android after every stage. 🟢 Green = continue. 🔴 Red = fix before continuing. Never duplicate existing functions. Never put API secrets in the APK.

## Ten-stage product build
1. Foundation & Design System — DONE 🟢
2. Authentication & User Identity — IN PROGRESS
3. Chat & Messaging
4. Groups & Social
5. Marketplace
6. Voice & Video Calls
7. Money Tools
8. Notifications & Sharing
9. FYNX Extra Tools
10. Final Integration & Polish

## Stage 2 checklist
- [x] First-launch authentication gate
- [x] Welcome screen
- [x] Create-account flow
- [x] Display name
- [x] Username
- [x] Phone number field
- [x] Verification-code screen foundation
- [x] Local account/session persistence
- [x] Returning-user login flow
- [x] Sign-out keeps the local account but ends the session
- [x] Validation and safe error states
- [ ] Production OTP/backend authentication
- [ ] Secure server-side account storage
- [ ] Production account recovery

## Important
Stage 2 is a UI/local authentication foundation. It does not pretend that local verification is production-grade identity verification. Production OTP, backend accounts, recovery, and server security are connected when the production backend is ready.

## Removed permanently
AI image generation and AI video generation are NOT part of the FYNX project roadmap. Do not add them back.
