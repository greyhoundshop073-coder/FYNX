# More Tools Reliability Release Gate

## Scope ownership
This work is isolated to More/Features, Extra Tools, Calendar, To-Do, Calls entry points, and local Money Center records. Do not edit Home or Marketplace feature implementations in this workstream; those areas have a separate active owner. Do not merge this branch into `main` without explicit approval.

## Current branch
- Branch: `fix/more-tools-professional-reliability-20261013`
- Pull request: #151 (draft)
- Backend: intentionally not reconnected; Render is unavailable and backend-dependent journeys are not certified.

## Required checks before calling this complete
1. Run `python3 scripts/verify_more_tools_reliability.py`.
2. Run `./gradlew lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon --build-cache`.
3. Run connected Android instrumentation and authenticated navigation verification in the emulator.
4. Confirm the workflow uploads an APK from the exact final commit and records the SHA.
5. Inspect runtime screenshots/UI hierarchy for navigation and changed More/Money Center screens.
6. Verify on a device that the APK actually installs and that Quick Notes persist/delete, Calendar validation rejects invalid input, overdue tasks remain visible, local money records survive reopening/account switching, and visible/system Back both follow the route history.
7. Confirm the existing Home interaction verifier checks the history-aware header back action without dropping the Saved Posts return-to-Features behavior.
7. Keep the PR draft and unmerged until the user explicitly authorizes a merge.

## Known limits
- Quick Notes and listed Money Center records are local account-scoped records, not cloud-synced data.
- The Money Lock/Vault is only a UI demo; it does not encrypt or secure sensitive records.
- Money transaction entries are local records and do not transfer money.
- Calls, AI, business/advertising, seller tools, and other live backend-dependent features cannot be certified while the backend is unavailable.
- A green build or static source contract is not proof of real-device runtime correctness.

---

## 2026-11-24 — More feature directory redesign

**Feature branch:** `feat/fynx-more-feature-grid-20261124`  
**Canonical base:** `c1b06c4ee187f23aacada0b8d1a18fa33cefb055`  
**Delivery:** pull request to `integration/fynx-single-apk-20261013`

### Product scope

- Replace the More screen's single-column feature rows with a two-column, rounded-card directory inspired by the supplied reference.
- Every retained feature card has its own coloured icon tile, title and short description.
- Standard accounts receive the complete 14-card directory. The authenticated OWNER or ADMIN receives one additional Owner / Admin Control Center card.
- Role visibility continues to come from the existing server-authoritative `FynxAdminClient.dashboard` result. The feature directory does not fetch roles or call backend services during composition; the existing Admin screen and server authorization remain unchanged.
- Search filters the in-memory feature registry only. The grid uses lazy composition, stable route keys, item content types and a remembered scroll state. Icons are bundled vector assets; no remote icon requests, image decoding or feature-screen initialization occurs while More is displayed.

### Consolidation decisions

The following More shortcuts are intentionally omitted because their existing entry points remain available:
- Marketplace — permanent bottom-navigation tab.
- Notifications and Privacy & Safety — Profile > Settings.
- Saved Products and Seller Center / My Shop — Marketplace's existing navigation actions.
- Events — removed as a duplicate Calendar shortcut from Extra Tools; Calendar remains a single More card.

Their destination screens and underlying functionality are not deleted. Wallet, Calls, public Announcements, Business & Creator Profile, Advertising, Advertising Analytics, AI, Saved Posts, Money Center, To-Do, Gifts, Share & Invite, Calendar and Extra Tools remain distinct destinations. Admin-only publishing and management stay in the existing Control Center rather than being reimplemented in More.

### Required verification

1. Run `python3 scripts/verify_more_tools_reliability.py`.
2. Run the new `FynxFeaturesPanelComposeTest` instrumentation suite: ordinary-account card inventory/deduplication, OWNER and ADMIN role visibility, rejection of unknown roles, two-column placement, route callback and local search.
3. Run Android lint, unit tests, debug build and Android instrumentation using the `[full-runtime]` PR path. Inspect the exact final-head job logs; a skipped emulator/screenshot stage is not a pass.
4. Review real Owner and ordinary-user screenshots from the exact feature PR head. Where authenticated Owner credentials are unavailable, the role fixture verifies rendering only and must not be described as live account verification.
5. Compare scrolling, search response and frame/jank behavior against the previous More screen on the same emulator/device profile. Do not claim a measured speed improvement from source structure alone.
6. Confirm More does not trigger network work and that Home, Chats, Friends and Marketplace navigation remain unchanged.
7. Preserve overlapping active PRs: #599 owns private/group/Marketplace call integration in `FynxApp.kt`; this work only changes the More route invocation and the extracted More composable at the end of that file. Do not overwrite or cherry-pick call changes from #599. PR #582 owns Admin publishing changes; reuse the existing Control Center without editing its implementation.

**Status at handoff:** implementation and focused test source added; CI/runtime performance evidence pending. This section is an acceptance gate, not a declaration of completion.
