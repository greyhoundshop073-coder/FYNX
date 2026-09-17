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
- **APK-visible integration is mandatory:** whenever a feature is added or changed for users, complete its actual placement, entry point, controls, navigation, states, feedback, and end-to-end interaction. Do not merely add code or place a UI element on a screen and call the feature integrated.
- **Use established product patterns as UX references:** inspect mature social/messaging/camera products when deciding placement and interaction conventions, then apply the useful patterns in FYNX's existing visual language. Do not copy another product's branding or redesign FYNX.
- **Complete the whole user interaction for every surfaced feature:** for example, a camera feature includes the live preview, expected controls/settings, capture, review, retake/edit, confirm/send/save behavior, permissions, errors and return/navigation behavior as applicable. The same completeness standard applies to AI, media, voice/video, notifications, marketplace, money tools, Status, groups, search and settings.
- **No hidden or awkward placement:** a feature must be positioned where a normal user would reasonably find it, with appropriate touch targets, hierarchy, keyboard/inset handling and navigation continuity.
- **GREEN is locked only after integration:** GREEN means implemented + correctly positioned in the APK + complete user interaction + backend/storage integration where required + verification/build success + applicable real-device journey. Once GREEN, do not reopen it for theoretical concerns; only reopen it for an actual regression or failed test.
- **Future chats must follow this file:** this document is the canonical continuation rule for FYNX reality/integration work. Do not weaken or reinterpret these rules in a later chat.

## 🔒 Locked engineering discipline — learned from actual FYNX failures

These rules are permanently locked because these failure patterns have repeatedly caused wasted work, false GREEN states, regressions, or misleading progress during FYNX development.

### 1. Investigate BEFORE every push

Never push because a change "looks right" or because a verifier can be made to pass.

Before every push:
1. Inspect the current `main` HEAD, not an old handoff SHA.
2. Inspect the existing implementation and all relevant callers/routes.
3. Trace the actual UI entry point and destination.
4. Trace client state and backend/API/storage paths when applicable.
5. Inspect relevant tests, verifiers and CI workflows.
6. Identify the concrete root cause.
7. Define the smallest correct fix.
8. Check that the fix does not duplicate or weaken a stronger existing implementation.
9. Run relevant local/static verification available.
10. Only then push a meaningful batch.

### 2. Investigate AGAIN AFTER every push

A successful push is not the end of investigation.

After every push:
1. Inspect the resulting commit/diff.
2. Confirm the intended files changed and unrelated files did not.
3. Re-trace the affected implementation from entry point through completion.
4. Check Android CI/build/test/lint status.
5. Check backend CI whenever backend/API behavior is affected.
6. Check relevant verifier gates.
7. Check regressions against previous working behavior.
8. Where applicable, install/use the resulting APK and test the real journey.
9. Only after this second investigation may the batch be called GREEN.

If post-push evidence exposes a defect, the state is RED and the next action is investigation and correction — not moving to another feature.

### 3. Never confuse source existence with user functionality

A class, route, endpoint, database table, verifier string, or UI button existing does not prove the feature works.

Every feature must be traced as:

USER ACTION -> VISIBLE CONTROL -> CLIENT STATE -> API/REALTIME -> BACKEND AUTHORITY -> DATABASE/STORAGE -> RESPONSE -> UPDATED UI -> COMPLETION -> BACK/RETURN STATE

If one link is missing, disconnected, fake, local-only, stale, unreachable, or incorrect, the feature is not GREEN.

### 4. Never use a verifier as a substitute for reality

A verifier is evidence, not the product. Do not weaken a verifier merely to obtain GREEN. Do not add a marker solely to satisfy a verifier without proving the underlying behavior. When verifier expectations and the real implementation disagree, investigate both sides and correct the real contract.

### 5. Fix root causes, not symptoms

When CI, a verifier, an APK journey, or a user interaction fails, identify the first broken link in the actual flow.

Do not paper over failures with extra UI, fake success states, placeholder data, duplicated routes/models/services, verifier-only strings, local state pretending to be server state, or disabling tests without evidence.

### 6. Backend authority always wins over stale client state

For membership, ownership, permissions, payments, orders, posts, reactions, comments, media access and other authoritative state, the client must reconcile with the backend.

Do not allow stale snapshots, optimistic state, cached lists, or navigation state to create a false representation of server truth. Optimistic UI must have success reconciliation and failure rollback.

### 7. Never create a second system because the first system is inconvenient

Before adding a camera, chat transport, media pipeline, AI path, marketplace transaction path, theme system, navigation route, model or backend endpoint, search the repository for the existing implementation and reuse/strengthen it.

### 8. Navigation is part of the feature

For every new or changed destination verify:

ENTRY -> CORRECT DESTINATION -> USE -> BACK BUTTON -> SYSTEM BACK -> GESTURE/PREDICTIVE BACK -> EXACT PREVIOUS DESTINATION -> PREVIOUS STATE PRESERVED

Never accept a route merely because it opens. Watch for unexpected Home jumps, lost scroll position, lost composer text, duplicate destinations, dead ends, loops, reloads and abandoned work.

### 9. Visual position is functional behavior

A button that is technically present but covered, clipped, misplaced, too large, touch-stealing, hidden by the keyboard, behind a camera preview, or unreachable is broken.

Always inspect z-order, touch interception, insets, keyboard behavior, scrolling under fixed controls, dialog/sheet layering, accessibility/touch targets, theme roles, hardcoded colors and camera preview/control overlap.

### 10. One workstream must finish before another is opened

Do not abandon an incomplete investigation because another feature looks easier or more interesting. Finish the current RED/YELLOW work through root-cause fix, verification and applicable real-device journey before moving forward.

Large batches are encouraged, but they must be coherent and fully verified rather than collections of unfinished changes.

### 11. Preserve known-good behavior

Before changing a GREEN system, identify what made it GREEN and protect it. Reopen it only for an actual regression, failed test, changed dependency, or concrete integration issue.

### 12. Do not trust an old handoff over the live repository

Handoffs are context, not proof of current state. At the start of a continuation, inspect live `main`, latest commits, current files and current CI.

### 13. No fake data to make a journey look complete

Never fabricate users, posts, comments, likes, followers, messages, products, orders, payments, transactions, notifications, statistics, media or marketplace activity.

Empty state means empty state. Loading means loading. Failure means failure with retry. Real data must come from the correct backend/database/storage path.

### 14. Different failures require different investigation

A RED build, RED verifier, backend failure, instrumentation failure, emulator failure and real-device failure each require investigation of their own evidence. Do not assume an unrelated failure is the application root cause, and do not dismiss an application failure because CI happened to be GREEN elsewhere.

### 15. Android APK is the final product surface

The repository is not the product by itself. A feature is not finished until APK-visible behavior is correct. When possible, verify on the actual target Android environment, including permissions, lifecycle, keyboard, camera, media, realtime and navigation behavior.

### 16. Large pushes must still be disciplined

Move fast by grouping **related, investigated, compatible fixes** into larger batches — not by skipping investigation. A large push may contain multiple fixes only when they share the same audited workstream and can be verified together. Never combine unrelated risky changes merely to increase commit size.

### 17. Every push must leave a clear audit trail

Every commit created in this continuation must start with:

`FYNX-THIS-CHAT —`

Commit messages must describe the actual batch. Never claim GREEN, production-ready, real-device verified, or complete unless the evidence supports it.

### 18. Do not move the goalposts after a failure

When a test exposes a real defect, fix the implementation instead of changing the definition of success. Acceptance criteria may become stricter when investigation discovers a missing real-world requirement, but must never be weakened simply to obtain GREEN.

### 19. Security and privacy cannot be traded for speed

No API secrets in APK. No client-only authorization. No exposing private data merely to simplify UI. No bypassing ownership checks, payment protection, webhook verification, authentication or backend authorization for testing convenience.

### 20. Completion reports must be evidence-based

Only report a feature under 🟢 COMPLETED when the applicable implementation, integration, tests and user journey have actually been verified. Otherwise report it under 🟡 INCOMPLETE, 🔴 FAILED — FIXING, or ❌ MISSING.

**LOCK:** These rules are part of the canonical FYNX continuation contract. Future development instructions must follow them unless a later repository decision explicitly and deliberately replaces this document.

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

Also verify the APK-visible layer:

ENTRY POINT -> POSITION -> CONTROLS -> FEEDBACK -> NAVIGATION -> COMPLETION -> ERROR/RETRY -> RETURN STATE

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
- expected camera controls/settings and their placement
- capture -> review -> retake/edit -> confirm/send/save flow

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
