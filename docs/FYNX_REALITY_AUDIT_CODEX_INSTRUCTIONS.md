# FYNX — Full Reality Audit + Fix Instructions

Repository: greyhoundshop073-coder/FYNX
Branch: main

## Mission

Continue the existing FYNX Android social + marketplace app. Do not rebuild it, redesign it from zero, replace working implementations, or create duplicate systems.

The objective is not merely to make CI GREEN. The objective is for the features to actually work in the downloaded Android application.

## Non-negotiable rules

- Inspect the actual repository before changing code.
- Preserve the strongest existing implementation.
- Integrate into existing camera, chat, marketplace, AI, theme, realtime and backend systems.
- Do not create duplicate implementations when an existing one can be strengthened.
- Do not put API secrets in the Android APK.
- Group compatible work into sensible batches.
- Build Android after each major batch.
- Run backend verification after backend changes.
- Test real user journeys, including real-device testing where hardware/realtime behavior is involved.
- A verifier marker or successful compilation is not enough to call a feature GREEN.
- RED means fix before moving forward.

## Reality status

Use exactly these states:

- GREEN: complete UI + integration + backend/storage where needed + real-device user journey.
- YELLOW: exists but incomplete.
- RED: visible but broken, disconnected, black/blank, dead, or functionally incorrect.
- MISSING: capability does not exist.

## Phase 1 — Full audit before feature work

Audit:

1. Home/Social
2. Private Chat
3. Group Chat
4. Camera
5. Status/Stories
6. Voice/Video Calls
7. Marketplace
8. AI Assistant
9. AI Creation
10. Global Theme
11. Contacts/People
12. Notifications
13. Security
14. Backend/API

For every system trace:

USER ACTION -> UI -> STATE -> CLIENT -> API/REALTIME -> BACKEND -> DATABASE/STORAGE -> RESPONSE -> UI

Record the exact missing or broken link. Do not assume that code existing means the feature works.

## Phase 2 — Fix highest-priority broken systems

### A. Shared camera

Use the existing CameraX foundation. Do not create separate cameras for Home, Chat, Groups, Marketplace and Status.

Investigate black preview deeply:

- camera permission
- microphone permission where required
- lifecycle
- CameraProvider initialization
- PreviewView
- surface provider
- camera selector
- binding/unbinding
- ImageCapture
- VideoCapture/Recorder
- Compose lifecycle
- navigation lifecycle
- returning to camera after leaving
- front/back switching
- device-specific CameraX errors
- actual preview frames on a physical device

The result must be a real live preview, not a hidden black screen.

### B. Private chat camera

Required flow:

Chat -> Camera -> live preview -> photo/video capture -> preview -> retake/edit -> caption -> send -> existing message transport -> recipient receives media.

Reuse existing media/message infrastructure.

### C. Group chat camera

Add the camera entry point using the shared camera foundation.

Required:

Group Chat -> Camera -> live preview -> capture -> preview/edit -> caption -> send -> group message transport -> members receive media.

Do not create a second group messaging backend.

### D. Voice/video calls

Inspect the existing WebRTC/media engine and realtime infrastructure.

A complete call requires:

Caller -> call request -> callee notification/ringing -> accept/reject -> signaling -> offer/answer -> ICE -> PeerConnection -> remote media -> connected call.

Voice:
- ring
- accept/reject
- mute
- speaker
- end
- failure/reconnect handling

Video:
- all above
- camera on/off
- front/back
- local preview
- remote video

Do not declare calls GREEN if the UI only changes call state without a real device-to-device media connection.

### E. Status/Stories

Support photo, video and text.

The status ring must reflect the actual number of active status items. Do not fake segment counts.

Viewer should support:
- photo/video/text
- next/previous
- viewer tracking
- likes/reactions
- comments/replies
- ownership/privacy
- expiration where applicable

Counts must come from real stored data, not placeholder local values.

### F. Marketplace

Do not rebuild the marketplace.

Audit the complete buyer/seller path.

Seller:
- create listing
- camera/gallery media
- multiple images
- video
- preview
- edit
- product details
- price
- quantity
- category
- condition
- location
- delivery/pickup
- publish/edit/pause/delete
- orders
- shipping/tracking
- protected payout state

Buyer:
- browse/search/filter
- product details
- seller/reputation
- contact seller
- cart
- checkout
- protected payment
- order tracking
- delivery
- confirmation
- dispute
- evidence
- refund
- review

Protection:
buyer payment -> protected/held -> seller ships -> delivery -> buyer confirms match -> payout release.

Dispute:
dispute -> payout blocked -> evidence -> resolution -> idempotent refund or payout.

Verify webhooks, authorization, transaction/audit records, retries and idempotency.

### G. Marketplace camera

Reuse the shared FYNX camera. Keep the existing gallery/document picker.

Seller must be able to take product photos/video directly, preview, retake, edit and attach multiple media items to the listing.

### H. AI Creation

Inspect the existing AI architecture. Do not create another AI system.

The current photo enhancement must not be represented as advanced AI merely because it changes brightness/contrast/saturation.

Improve the existing system toward useful capabilities such as:
- intelligent enhancement
- composition/crop assistance
- product photo improvement
- caption generation
- marketplace description generation
- social post improvement

Do not add AI image/video generation if it conflicts with the repository roadmap.

### I. AI voice conversation

This is NOT speech-to-text.

Required experience:

User taps voice mode -> FYNX Assistant speaks: “I am FYNX Assistant. What do you want to know?” -> user speaks -> FYNX processes -> assistant responds with voice -> conversation continues.

Required:
- microphone permission
- speech input
- AI processing through existing authenticated backend
- text-to-speech
- loading/error states
- interruption/stop
- conversation state
- no API secret in APK

### J. AI keyboard behavior

When keyboard opens, the input composer must move above the keyboard and remain usable.

### K. Global FYNX theme

Use the existing FYNX theme/preferences architecture.

Dark mode must be genuinely dark/black.

Accent color must propagate consistently throughout the app.

Use theme color roles and readable on-color roles. Audit hardcoded colors that bypass the theme.

Every major screen must look like it belongs to FYNX rather than a different application.

### L. Contacts/People

Required:
- Android contacts permission
- phone contacts discovery
- search/filter
- distinguish FYNX users/non-users
- Chat for FYNX users
- Invite for non-users
- FYNX-branded invite experience

Home People shortcut:
- smaller
- above the main camera/create button
- not awkwardly beside it
- globally themed

Do not expose raw phone numbers unnecessarily.

### M. Notifications

Trace:

EVENT -> backend -> notification -> device -> tap -> correct FYNX destination.

Cover chat, groups, likes, comments, follows, marketplace orders/payment/delivery/disputes, calls and status interactions.

### N. Security

Audit:
- API secrets
- authentication
- authorization
- ownership checks
- webhook verification
- payment state validation
- idempotency
- rate limits
- media access
- file handling
- session/logout
- sensitive-data exposure
- audit trails

Backend authorization is authoritative.

## Phase 3 — Build and verify

After each major compatible batch:

1. Build Android.
2. Run relevant backend tests.
3. Run relevant verifiers.
4. Check for regressions.
5. Install the APK.
6. Test the changed user journey on a real device.
7. Test failure/retry paths where applicable.
8. For two-user features, test device/user A against B.
9. Only then mark GREEN.

If CI is GREEN but the downloaded APK is broken, the feature is RED and must be fixed.

## Phase 4 — Consolidated integration audit

After the fixes, verify all major areas together:

Social feed, posts, media, voice posts, likes, comments, shares;
private chat and group chat;
camera in Home/Chat/Groups/Marketplace/Status;
voice/video calls;
Status/Stories;
marketplace buyer/seller/protection;
AI text, creation and voice;
contacts/search/invites;
notifications;
theme;
security.

## Acceptance gate

Do not call FYNX complete until the applicable answers are YES:

- social posting works
- camera preview is live
- photo/video capture works
- private-chat camera works
- group-chat camera works
- real voice calls work
- real video calls work
- Status supports photo/video/text and real item counts
- status viewers/reactions/comments work
- marketplace listing and purchase work
- protected payment/settlement works
- disputes/refunds/payout protection work
- AI creation is genuinely useful
- AI voice conversation works
- AI composer stays above keyboard
- contacts can be searched
- FYNX users can be chatted
- non-users can be invited
- People shortcut has correct position
- theme is consistent
- dark mode is genuinely dark
- accent color propagates
- notifications reach the correct destination
- no secrets are shipped in APK

## Development discipline

Do not optimize for the number of commits or verifier markers. Optimize for working FYNX.

Before every change identify:
- existing implementation
- root cause
- smallest correct fix
- affected files
- required backend/API changes
- tests
- real-device test

Do not make unrelated changes.

## Required developer report

At the end of every batch report:

### 🟢 COMPLETED
Only genuinely verified features.

### 🟡 INCOMPLETE
Existing but unfinished features.

### 🔴 FAILED — FIXING
Broken features and root causes.

### ❌ MISSING
Not-yet-implemented capabilities.

### 🔧 CHANGES MADE
Files/components and integration changes.

### 🧪 TESTS
Android build, backend tests, integration tests, real-device/two-user tests.

### 🚀 NEXT BATCH
Highest-priority remaining work.

## START HERE

1. Inspect current main branch and latest commit.
2. Inspect recent commits and existing Stage 15/16 work.
3. Inspect current CI workflows and verifier scripts.
4. Perform the Reality Audit above.
5. Do not restart FYNX.
6. Do not duplicate existing implementations.
7. Fix the highest-priority RED/MISSING issue.
8. Build and verify it.
9. Do not call it GREEN until the real user journey works.
