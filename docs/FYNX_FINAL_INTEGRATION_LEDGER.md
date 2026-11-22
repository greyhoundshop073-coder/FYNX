# FYNX final integration completion ledger

**Scope frozen:** PRs #478, #555, #556, #558, #493, #494, #506, #534, #535, #468, #469, #518, #418, #419, #531, #533, #536, #436, #438.  
**Consolidation destination:** `integration/fynx-single-apk-consolidation-20261117`  
**Consolidation PR:** #563 → `integration/fynx-single-apk-20261013`  
**Observed candidate SHA:** `c3854dfe3ea61c7854ac464733f8f3854a7c8771`  
**Ledger rule:** A candidate file or passing static check is not runtime certification. A live external dependency remains blocked separately from independently testable app/backend work.



## Live reconciliation checkpoint — 2026-11-20 (supersedes stale candidate notes above)

**This section is the current operational truth.** The older rows below are historical evidence and must not be repeated as current status without checking this checkpoint.

- Current consolidation PR: [#563](https://github.com/greyhoundshop073-coder/FYNX/pull/563), head `7c2e5a806b79cb86782dede19d82c1c33c169f48`, base `b075bf5c8990b0034eac07f2d35c6b3531ea0e1c`. GitHub currently reports `mergeable=false`, `mergeable_state=dirty`. **Do not merge until conflicts are resolved and exact-head checks pass.**
- The 19 agreed assignments are not 19 still-open PRs. Current PR-state reconciliation:
  - **#418 — INTEGRATED / CLOSED:** merged to canonical on 31 Oct 2026; keep closed and do not reopen.
  - **#558 — SUPERSEDED / CLOSED:** zero commits ahead of the current consolidation candidate and no unique files in the current compare; do not reopen or count it as a separate port. Its Marketplace runtime acceptance evidence remains part of Marketplace certification, not a second #558 coding task.
  - **#478, #555, #556, #493, #494, #506 — CLOSED, UNIQUE DELTAS STILL TO RECONCILE:** these PRs were closed without merging. Current compare against the candidate still shows respectively 9, 6, 10, 13, 18 and 21 commits ahead. Treat those counts as branch divergence, not as a promise to cherry-pick every commit. Review only the unique intended file changes and port compatible deltas once into #563.
  - **#419 — CLOSED, UNIQUE WALLET WORK NOT INTEGRATED:** current compare still shows 9 commits ahead and 16 changed files. Reconcile the safe app/backend implementation selectively; real-money Paystack execution remains blocked until credentials, webhook configuration and provider eligibility are verified. Do not fake successful funding or payouts.
  - **#534, #535, #468, #469, #518, #531, #533, #536, #436, #438 — OPEN, UNIQUE DELTAS REQUIRE REVIEW:** they are not to be merged blindly. Compare the current PR head to the candidate and port only changes still absent. In #534, explicitly exclude all changes to `FynxAuthPanel.kt` and any sign-in/login behavior.
- **Count for the 19 assignments:** 1 merged/closed (#418); 1 superseded/closed with no unique port (#558); 17 assignments still require selective reconciliation and/or their stated external/runtime acceptance gate. Do not report “19 unfinished” again.
- Current candidate comparison is against `integration/fynx-single-apk-consolidation-20261117`, not against an old baseline SHA. Branches are heavily divergent; “ahead by N commits” is not a safe cherry-pick list. Use per-file diffs and caller/test tracing.
- New infrastructure PRs outside the original 19:
  - [#570 Remote Config](https://github.com/greyhoundshop073-coder/FYNX/pull/570), head `799debda0f652cd5bc17d650f63e8600f271355e`: Backend CI passed. Android build/lint/source verifiers passed; the Android instrumentation/UI-capture job ended with exit 1 after emulator/ADB startup errors. No test assertion failure was recorded in the visible progress output. Failed job rerun attempt 2 is in progress: [run #44724478048](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/44724478048).
  - [#572 Neon direct media uploads](https://github.com/greyhoundshop073-coder/FYNX/pull/572), head `847d27e66c2c4c897475b4bcc1ae8cf7b337b69a`: Backend CI passed. Android build/lint/source verifiers passed; Android instrumentation/UI-capture job ended with exit 1 after emulator/ADB startup errors. No test assertion failure was recorded in the visible progress output. Failed job rerun attempt 2 is in progress: [run #44722273114](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/44722273114).
- Firebase IAM permission has been granted by the owner. Remote Config template publication succeeded in workflow [#44714028510](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/44714028510); its dedicated live Android Remote Config test passed. The full Android instrumentation/UI-capture lane still needs a clean exact-head result.
- The failed instrumentation/UI-capture step is not yet attributed to a product defect: both #570 and #572 logs show emulator/ADB startup failures, and the Gradle test summary did not show failed test assertions before the wrapper exited. Rerun results decide whether this is transient CI infrastructure or a reproducible app/test-harness failure. Do not downgrade or remove tests to make CI green.
- **Current next integration sequence:** (1) finish reruns #44724478048 and #44722273114; (2) resolve #563's dirty merge state; (3) port the responsive/layout chain (#478 → #493 → #494 → #506) and Marketplace fixtures (#555/#556) selectively; (4) port non-login design work from #534; (5) reconcile Chat/Groups (#535), Camera/New Post (#518), storage (#468/#469/#572), Wallet (#419), Trust/Safety/Admin (#531/#533/#536), Music (#436), Support (#438), and Remote Config (#570) into the same candidate; (6) run exact-head Android, backend and parity workflows; (7) merge to the protected canonical branch only after the candidate is clean and verified.
- **Closure discipline:** when a unique delta is ported, tested and accepted, update its row to `INTEGRATED — VERIFIED`, record the canonical merge SHA and evidence, then close the source PR if it is still open. Once an assignment is `CERTIFIED — CLOSED`, future chats must not list it as unfinished unless a reproducible regression or approved new requirement reopens it. A PR being closed is not proof that its work is integrated.

## Fixed assignment tracker

| PR | Classification | Unique work / presence in candidate | Integration commit / evidence | Remaining work or blocker |
|---|---|---|---|---|
| #478 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Responsive Marketplace, My Shop, Wallet and adaptive content sizing. Marketplace/Wallet files and some layout work are present in #563; this PR still reports a conflict and must be reconciled selectively. | Candidate inspected at `c3854dfe3ea61c7854ac464733f8f3854a7c8771`; #563 Android CI is rerunning. | Reconcile the remaining adaptive-width changes without replacing the newer Marketplace runtime implementation; certify small phone/tablet/large-font/landscape. |
| #555 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Marketplace Compose runtime fixture stabilization. Related isolated scenario and Marketplace screen test files are present in #563. | Candidate inspected at `c3854dfe3ea61c7854ac464733f8f3854a7c8771`; earlier #563 candidate reported 74 instrumentation tests, zero failures (not this latest SHA). | Compare all six PR files against the candidate and retain any unique fixture fixes; rerun exact-head instrumentation. |
| #556 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Marketplace/Wallet fixture rendering and workflow/test fixture adjustments. Related fixture tests and Marketplace/Wallet UI files are present in #563. | Candidate inspected at `c3854dfe3ea61c7854ac464733b19cf9728be5e` (superseded); current candidate `c3854dfe3ea61c7854ac464733f8f3854a7c8771`. | Reconcile remaining fixture-only changes and ensure no workflow condition weakens mandatory checks. |
| #558 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Marketplace runtime certification repairs. Its core test/UI files are represented in #563 and its runtime-capture script is present. | Earlier candidate validation recorded 74 instrumentation tests with zero failures; latest candidate Android job failed at Admin contract step before later runtime stages. | Fix/re-run latest CI, then certify real Marketplace listings, details, seller publishing, My Shop and Wallet data screens; visual capture was previously blocked. |
| #493 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Floating glass bottom navigation. `FynxGlassBottomBar.kt` and app-shell integration are present in #563. | Candidate inspected at `c3854dfe3ea61c7854ac464733f8f3854a7c8771`; Chat/Groups parity workflow rerunning. | Reconcile unique visual/interaction deltas and test navigation on supported screen sizes. |
| #494 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Glass headers and title/back-button treatment. `FynxGlassHeader.kt` and app-shell changes are present in #563. | Candidate inspected at `c3854dfe3ea61c7854ac464733f8f3854a7c8771`. | Verify all header callers, back navigation, long titles and no duplicate Marketplace title/back controls. |
| #506 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Content scrolling beneath floating bottom navigation across primary tabs. Relevant app-shell, Marketplace, Chat and Friends files are present in #563. | Candidate inspected at `c3854dfe3ea61c7854ac464733f8f3854a7c8771`. | Reconcile unique insets/padding changes and verify bottom actions remain reachable without content obstruction. |
| #534 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Design polish for Chats, Seller Center, Status and Home. Several non-login surfaces are present in #563; the PR also modifies `FynxAuthPanel.kt`. | Candidate inspected at `c3854dfe3ea61c7854ac464733f8f3854a7c8771`. | Preserve the existing login flow: do not port the sign-in changes. Selectively reconcile only non-login changes and verify them. |
| #535 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Offline-ready Chat/Groups reliability. Chat, Friends, Groups, backend group clients and offline outbox instrumentation are present in #563; Chat/Groups parity workflow passed on the previous candidate and is rerunning on this SHA. | Candidate inspected at `c3854dfe3ea61c7854ac464733f8f3854a7c8771`; workflow run #44151112972 pending at ledger creation. | Compare remaining unique changes from the 64-file PR; complete offline/reconnect, group permission and runtime journeys on the exact final SHA. |
| #468 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Backend secure direct-to-R2 upload intent/signing and completion verification. `backend/mediaStorage.js` and media verification scripts are present in #563. | Candidate inspected at `c3854dfe3ea61c7854ac464733f8f3854a7c8771`; Backend CI passed on previous candidate and is rerunning. | Reconcile the backend PR selectively; live R2 upload/complete verification requires valid R2 account/bucket credentials and configuration. |
| #469 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Android client direct signed-media upload. `FynxBackendClient.kt` and `FynxProductionMessaging.kt` include signed upload flow in #563. | Candidate inspected at `c3854dfe3ea61c7854ac464733f8f3854a7c8771`. | Verify all media call sites, expiry/error/retry behavior and upload retrieval; live R2 verification remains dependent on #468 configuration. |
| #518 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Camera and unified New Post composer journey, video overlays/effects and publishing. Camera capture, overlay, local draft and status/home publishing files plus instrumentation are present in #563. | Candidate inspected at `c3854dfe3ea61c7854ac464733f8f3854a7c8771`; previous candidate camera-related contract checks passed, but latest Android job stopped at Admin static contract. | Reconcile remaining PR-only changes and complete actual capture → edit → caption → publish runtime journey with real device evidence. |
| #418 | INTEGRATED AND VERIFIED | Wallet preparation screens and fee-mode default correction (5% seller-paid default), retaining safe no-fake-account/no-live-money boundary. Already merged; do not reopen. | Merge commit `a97d48c4232e06dccb5e02f82a9c53178a69f421`; PR #418 merged 31 Oct 2026. | Real deposits/withdrawals remain a separate provider/backend release gate, not unfinished #418 work. |
| #419 | NOT INTEGRATED — WORK STILL REQUIRED | Paystack-backed wallet funding, safeguarded bank payout and associated backend settlement/client work. PR remains open and its unique backend files are not in the #563 changed-file set. | No integration SHA recorded. | Selectively port onto the existing consolidation branch; sandbox/live money movement requires approved Paystack credentials, webhook configuration and provider eligibility. Never simulate a successful transaction. |
| #531 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Backend content reporting, moderation audit and reporting schema/tests. `backend/trustSafety.js`, admin routes and content-reporting tests are present in #563. | Candidate inspected at `c3854dfe3ea61c7854ac464733f8f3854a7c8771`; Backend CI passed on previous candidate and is rerunning. | Reconcile any PR-only backend tests/schema changes; verify database-backed reporting and audit flows with safe fixtures. |
| #533 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Android reporting/moderation UI and protected admin/report clients. Related Admin, Home comments, Marketplace and safety client files are present in #563. | Candidate inspected at `c3854dfe3ea61c7854ac464733f8f3854a7c8771`. | Verify report submission, separate account/content queues, review/audit and permission enforcement end to end; keep runtime evidence distinct from static checks. |
| #536 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Owner identity, admin roster/search and Admin Centre hardening. `platformAdminRole.js`, `adminRoutes.js`, PostgreSQL ID validation and Admin verifier are present in #563. | Candidate inspected at `c3854dfe3ea61c7854ac464733f8f3854a7c8771`. CI failure traced to a stale verifier assertion expecting the legacy lowest-user-ID owner query; corrected in commit `c3854dfe3ea61c7854ac464733f8f3854a7c8771`; rerun pending. | Confirm the updated verifier passes; complete real OWNER/ADMIN access and sensitive-action runtime verification. |
| #436 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Official announcement/music administration with owner-only catalogue changes. Announcement panel and music-related backend/admin work are represented in #563. | Candidate inspected at `c3854dfe3ea61c7854ac464733f8f3854a7c8771`. | Verify the music admin component is present from the canonical base or selectively port any missing unique files; test OWNER-only restrictions and published catalogue access. |
| #438 | PARTIALLY INTEGRATED — SPECIFIC WORK REMAINS | Shared Owner/Admin Support Centre, support API, privacy entry point and support notifications. Support hub/client/routes/tests are present in #563. | Candidate inspected at `c3854dfe3ea61c7854ac464733f8f3854a7c8771`. | Complete backend-backed support case creation, staff access, replies and permission/runtime checks. |

## Latest CI evidence

At the time this ledger was created, commit `557fa13003a08c883b35ad34bebd19cf9728be5e` had:
- Backend CI: success.
- Chat/Groups parity: success.
- Android Build: failure at `scripts/verify_admin_control_center_contract.py`, specifically the obsolete assertion that the Owner must be excluded using the first user ID.

The failure was traced to a verifier that still expected the retired query `u.id<>(SELECT id FROM users ORDER BY id ASC LIMIT 1)`. The actual implementation uses the persisted singleton owner record: `u.id<>(SELECT owner_user_id FROM fynx_platform_owner WHERE singleton=TRUE)`. The verifier was updated to assert the actual persisted-owner schema/query, without changing application authorization logic.

The correction commit is `c3854dfe3ea61c7854ac464733f8f3854a7c8771`. Android, Backend and Chat/Groups workflows were triggered for that commit; their result was **in progress** at the time of ledger creation. No green result is claimed until those exact-SHA runs finish.

## Release boundary

The consolidation PR is not a final APK certification. Its prior description records Marketplace/My Shop/Wallet live screens and the production backend probe as unverified/blocked. The official APK must still be built from the approved canonical integration source after approval; this ledger does not authorize merging PR #563 or changing the canonical branch.


## Current operational checkpoint — 2026-11-22 (PR #580; supersedes 2026-11-20 status for this workstream)

**Canonical branch:** `integration/fynx-single-apk-20261013`  
**Canonical base SHA used by candidate:** `dfb9b83d420b41512224ad7180eab797b9664f6d`  
**Active candidate PR:** [#580](https://github.com/greyhoundshop073-coder/FYNX/pull/580), OPEN and UNMERGED  
**Candidate branch:** `fix/canonical-local-runtime-visual-modes-20261122`  
**Candidate head at checkpoint:** `e1c3bcf149dff2246ca7123867910958323b0037`

### Grouped work added to the candidate

- Home Feed runtime detection no longer treats normal post interactions as modal routes.
- The disposable local Discovery fixture explicitly persists and verifies `FOR_YOU` before its local videos are scanned.
- Groups tab uses a unique `Open Groups` accessibility description and the runtime verifier requires that exact control.
- Chat/Groups account/group read-receipt capability state is scoped and the authoritative final message snapshot is persisted with stale-session guards.
- Added focused receipt tests, two-account isolation instrumentation and offline message persistence/cold-restart coverage.
- Backend group-history capability tests and Chat/Groups parity/closure contracts were updated.
- Added/updated the runtime/workflow handoff and permanent engineering standard (see #581 port recorded on the next candidate head).

### Exact-head evidence at time of checkpoint

- Android source/contract gates: PASSED on #45058308486, including camera script syntax, Home video/Discovery, Home lifecycle, R5A Groups security, Status source/runtime contract, Marketplace/Wallet and H12 gates.
- Backend CI: PASSED on #45058308499; production reachability was SKIPPED and remains unverified.
- Chat/Groups parity/layout static contracts: PASSED on #45058308496; Android regression build was still running.
- Android fast build/test/lint: still running in #45058308486.
- Installed-app runtime capture and final screenshots/hierarchy: NOT YET VERIFIED on this head.
- Earlier Chat Closure Android unit/lint/debug APK workflow #45057973421 passed on ancestor #781da8b; the Chat source files were unchanged afterward, but this is not a substitute for the latest full exact-head checks.
- No feature-branch APK is the shared canonical APK. No official APK is published.

### Related PR disposition

- #574 Chat/Groups source changes were selectively ported to #580; keep #574 OPEN until the candidate has passed its checks and the final integration disposition is confirmed.
- #581 global engineering standard is being ported into the same candidate; do not treat documentation or a feature-branch PR as canonical integration.
- #155 currently has a head SHA identical to the canonical branch and targets `main`; it contains no unique change relative to canonical and should be closed as superseded rather than merged into the delivery path.
- Divergent #535/#573/#171 and old consolidation-base PRs must be reviewed per-file; do not wholesale merge them.

### Next acceptance gates

1. Complete #45058308486 Android build, unit tests, lint, instrumentation and installed-app runtime capture.
2. Complete #45058308496 Android regression build.
3. Verify exact-head Chat/Groups closure and collect local UI evidence.
4. Review Home/Discovery, Camera, Status and Marketplace/My Shop runtime reports/screenshots, keeping live-service blockers separate.
5. Reconcile the remaining unique PR work into one candidate branch, then build the shared APK only from the approved canonical commit after authorization.

**Release boundary:** This candidate is not merged into canonical, no production configuration/data was changed, and no official APK was published.

### 22 November 2026 — Runtime evidence correction and next grouped repair

- PR #580 exact-head run #45059210784: Android instrumentation passed (83 tests, 0 failed, 1 skipped); installed-app runtime capture remained RED.
- Actual Groups screen screenshot exists; the runtime selector expected `Create group` but the rendered button was `＋ New group`. The button is being given a stable accessibility label.
- Offline private message was still in the composer after the old tap. The send control now has an accessibility click action; the verifier uses IME Send first and confirms the message appears outside the EditText before declaring success.
- Discovery fixtures were seeded but, with only three feed posts, the insertion was after the last post. Short test feeds now place Discovery after the first post to make it reachable.
- Home ratio/Discovery viewer and Home final navigation remain unresolved; Status capture reported no running app process after Camera. No FATAL/ANR marker was found in the available logcat, so this is not yet evidence of an app crash.
- Next gate: rerun exact-head build, instrumentation and emulator screenshots; keep backend, multi-account delivery, Wallet/Paystack and official APK certification explicitly separate until their prerequisites are available.

**Release boundary:** PR #580 remains a feature candidate. No canonical merge, production change or official APK publication has occurred.

### 22 November 2026 — Neon Object Storage selectively integrated into candidate

- PR #572 head `494be03a025955825ef75579fae1e4e574f224fe` was integrated into the PR #580 feature candidate, not canonical. Its backend CI passed; Android build and instrumentation passed (83 tests, 0 failed, 1 skipped), while its installed-app visual runtime remained RED on separate Chat/Groups, Home, Camera, and authenticated Marketplace/Wallet journeys.
- Candidate now includes Neon/R2 direct signed media uploads, provider-pinned upload intent completion/cleanup, strict trusted-host checks, no bearer-token forwarding to object storage, PostgreSQL fallback, and backend/Android storage tests.
- Live Neon upload, HEAD validation, authorized retrieval, expiry and cleanup remain UNVERIFIED until a healthy test service and branch-scoped credentials are available. No production credentials, bucket, configuration or data were changed.

**Release boundary:** feature candidate only; no canonical merge or official APK publication.

### 22 November 2026 — Selective Chat reliability port from #535

- Reviewed the divergent #535 branch per-file and selectively ported its unique private-history fallback, per-chat alert policy and deterministic group reconnect-drain tests into the #580 candidate.
- Private chat now distinguishes saved-history refresh failure from an empty first-load failure. In-app alert sound/vibration now respects per-chat notification and mute settings. Group reconnect tests verify successful server-ID replacement and preservation of pending group/message work after a simulated failure.
- All added tests use local fixtures/injected transports. They do not certify live delivery, FCM or backend readiness.

**Release boundary:** feature candidate only; no canonical merge, production change or official APK publication.

### 22 November 2026 — Camera video editor accessibility correction

- Real Camera evidence showed the Text rail action and Video FX tiles on screen but without stable accessible names. Added merged accessibility labels to the shared rail-action and effect-tile clickable surfaces, and added harness contracts for both.
- Next exact-head runtime must demonstrate video text entry, Cinematic/Warm Film selection, and a completed edited MP4 export. This source repair is not yet runtime-certified.

### 22 November 2026 — Groups accessibility and large-history fixture

- Groups list cards now expose merged `Open group <name>` accessibility labels on their actual clickable surfaces.
- The disposable Chat runtime fixture now verifies 1,002 persisted account-scoped messages, including the two visible conversation messages at the end of the history. Added a harness contract for this stress case.
- Preserved the candidate's newer group read-receipt final-snapshot persistence logic.

**Release boundary:** feature candidate only; no canonical merge or official APK publication.

### 22 November 2026 — Instrumentation fixture assertion correction

- Run #45067248420: APK build and unit tests passed; Android instrumentation reported 86 tests, 1 failed, 1 skipped. The only failure was two stale fixture assertions expecting 2 messages after the same test now seeds and verifies 1,002 messages. Both stale assertions now expect 1,002.
- Runtime navigation was not attempted because the instrumentation gate correctly stopped the sequence. The next exact-head run must repeat instrumentation before runtime certification.
