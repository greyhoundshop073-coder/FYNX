# FYNX Owner/Admin Control Center — Continuation Handoff

**Purpose:** This is the durable work order for the next ChatGPT Think session or contributor. Continue the existing implementation until the remaining scope below is implemented and verified. Do not restart, produce a status-only report, or call the work complete merely because a screen or endpoint exists.

## 1. Where to resume

- Repository: `greyhoundshop073-coder/FYNX`
- Active PR: [#536 — Admin Control Center hardening](https://github.com/greyhoundshop073-coder/FYNX/pull/536)
- Feature branch: `fix/admin-control-center-hardening-20261113`
- PR base / canonical Android integration branch: `integration/fynx-single-apk-20261013`
- Latest verified Group 1 head: `c8ee4837f9898c03ee195adaa4942c2ff2ab9034`
- PR state: open, draft and unmerged. Do not merge without explicit user approval.

The PR already contains the existing Control Center refresh, Overview/Safety/Marketplace/People/Announcements sections, account-status route correction, account-report review work, appeal UI/API work, OWNER-only Music Library controls, OWNER/ADMIN seasonal promotion controls, a new admin activity audit endpoint/client/UI, and the focused contract verifier. Inspect the current branch and PR diff before changing anything; this list describes work in progress, not proof that each flow is fully correct.

### CI snapshot — re-check the current head before acting

For head `40a83bcf72a5df742d1804fab5c68229999dc5a3`:
- Backend CI run [7335](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/43143437380): **passed**.
- Android Build run [13431](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/43143437418): **in progress at handoff**.
- Chat/Group Parity + Layout Safety run [4191](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/43143437399): **in progress at handoff**.
- Production/live backend probe was not established as passing. Do not describe production integration as verified until it has actually been tested.

CI changes as commits are added. Always query the PR's current head and the associated workflow runs again; never use this historical snapshot as today's status.

## 2. Mandatory starting procedure

Before editing:
1. Read `AGENTS.md`, `docs/SINGLE_APK_INTEGRATION_POLICY.md`, `docs/DEVELOPMENT_POLICY_COMPLIANCE.md`, and `docs/GOOGLE_PLAY_READINESS.md`.
2. Fetch PR #527, its changed-file list, current branch head, latest canonical base head, and all current checks.
3. Review the current code and exact diffs in `backend/adminRoutes.js`, `app/src/main/java/com/fynx/app/ui/FynxAdminClient.kt`, `app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt`, and `scripts/verify_admin_control_center_contract.py`. Inspect all called routes, middleware, schema definitions, and existing safety/reporting/upload code before extending them.
4. Check whether another workstream has changed overlapping files or whether PR #527 has received new commits. Preserve all newer work and selectively integrate; never overwrite or reset it.
5. Continue directly on the existing PR branch. Do not create another APK branch, duplicate the Control Center, or reimplement completed work.

Do not ask the user to repeat the project context or re-approve ordinary code corrections. Make grouped, meaningful commits. If a check fails, fetch its actual job logs, identify the specific cause, fix it, and rerun the affected checks before proceeding.

## 3. Remaining work — three grouped deliveries

### Group 1 — Safety and People

Complete and verify the real moderation and account-management workflows end to end.

- Trace the actual report submission flows and database schema (including `safety_reports` and related safety tables). Establish exactly which kinds of reports the app currently submits: account, post/content, comment, listing, or other. Make the Admin queue review every supported report type; do not fabricate fields or silently treat content reports as account reports.
- Verify reporter/target data, joins, nullable/deleted users, status transitions, sorting, empty states, and pagination/limits. Ensure unresolved cases remain visible until a valid decision is made.
- Test every report decision (move to review, resolve, dismiss) and ensure invalid IDs/statuses and unauthorized requests are rejected server-side.
- Verify appeals from submission through review and final decision. Confirm ACCEPTED actually restores the correct account state consistently, REJECTED does not change it, and UNDER_REVIEW remains pending. Check transaction boundaries so a failed audit write cannot leave the client believing a committed decision failed.
- Complete People/account search and account actions using the existing supported APIs. Verify ACTIVE/LIMITED/LOCKED behavior, admin grant/revoke, self-action protection, and owner protection.
- Confirm the server is authoritative for OWNER versus ADMIN. Inspect the actual auth/admin middleware and role helper; do not assume a field such as `req.fynxAdminRole` is set unless the middleware demonstrably sets it.
- Check dashboard counts against the real source tables and statuses. Remove misleading placeholder counts or label them accurately.
- Complete audit logging for all sensitive changes in this group. Confirm actor ID, actor role, actor display name, target, action, timestamp, and safe metadata are correct. Do not leak secrets or excessive personal data.

**Group 1 acceptance:** real reports and appeals can be reviewed; account/admin controls work with correct role boundaries; counts match source records; all relevant actions are auditable; unauthorized and malformed requests are denied; Android/backend regression checks pass.

### Group 2 — Administration and Publishing

Finish announcement publishing and the administrative activity history.

- Inspect current announcement schema, public retrieval route, app UI, and the existing media/object-storage upload implementation before choosing the attachment flow.
- Add real image attachment support using the existing protected media upload/storage path. Do not implement a fake upload, store a local file path, or accept arbitrary public URLs as a substitute for uploading and validating media.
- Add and validate optional external link controls. Validate URL scheme and input length; render links safely in the public announcement UI and handle missing/invalid links.
- Implement scheduled publishing using persisted server-side timestamps and an explicit timezone convention. Scheduled announcements must not appear publicly before their scheduled publication time. Define what happens when an item is edited, cancelled, published immediately, or has a past schedule.
- Enforce role permissions for creating, editing, scheduling, publishing, and removing announcements on the backend as well as in the UI. Preserve the existing public Announcements route and its `ownerAccess` parameter.
- Finish the searchable activity history: verify ordering, filters, query escaping/parameterization, bounded pagination, actor identity, role and target rendering, loading/error/empty states, and refresh/search behavior.
- Extend the audit trail to every important administrative mutation, including announcement changes and any other management actions added in the Control Center. Avoid logging secrets or full sensitive payloads.
- Ensure scheduled jobs or publication logic are reliable across process restarts and timezone boundaries; do not rely only on an in-memory timer.

**Group 2 acceptance:** an authorized admin can create and publish a real announcement with an uploaded image and safe link, or schedule/cancel it; public clients only see eligible published announcements; activity search is useful and complete for the actions being audited; permission checks and tests pass.

### Group 3 — Marketplace, Music, and Final Certification

- Verify seasonal Marketplace promotion creation/editing/activation/deactivation against the real backend. Keep OWNER/ADMIN permissions consistent on both client and server. Verify schedule windows, malformed input, overlapping promotions, and public display behavior.
- Verify Music Library catalogue management remains strictly OWNER-only, including direct API calls by ADMIN and ordinary users. Test add/upload, publish, retry, restore, and remove paths using the actual storage flow.
- Walk every Control Center section as an actual user: Overview, Safety, Marketplace, People, Announcements, and Activity. Check narrow Android layouts, scrolling, text overflow, keyboard behavior, dialogs, loading/error/empty states, refresh, back navigation, and every visible action.
- Verify there is no duplicate title/back header, no hidden or overlapping content, no new bottom navigation, and no regressions to the existing app shell or the public Announcements screen.
- Run the full affected Python contract checks, backend tests/CI, Android build, and Chat/Group parity/layout checks. If any check is red, read the failing logs and correct the root cause. Re-run all affected checks on the final commit.
- Install/run on an emulator or device when the environment permits. Capture genuine screenshots of the actual APK/UI, not mockups. Record the exact build/commit used for screenshots.
- Test backend behavior with OWNER, ADMIN, and ordinary-user credentials where available. Include denied access, malformed data, empty states, network failure, and retry. If production backend access or credentials are unavailable, say precisely what could not be verified; do not call it production-tested.
- Recheck the PR diff and canonical base for accidental unrelated changes.

**Group 3 acceptance:** Marketplace promotion and Music Library permissions/actions are verified; every Control Center section is tested; final CI is green on the final head; actual runtime evidence is recorded where possible; all remaining environment-dependent gaps are explicitly named.

## 4. Engineering and product constraints — do not violate

- Deliver to the one shared APK branch only: `integration/fynx-single-apk-20261013`. PR #527 must keep that base.
- Do not create or describe a feature-branch APK as the canonical APK. The canonical APK is produced from the merged canonical branch, not this feature branch.
- Preserve other workstreams: Marketplace/My Shop, Home/Feed, Avatar/Profile, Chat/Groups, Status/Story, Camera/Posting, Wallet, and backend work. Do not overwrite or regress their changes.
- Do not touch the working login flow. Keep login open for testing while backend access is unavailable; do not introduce OTP as a shortcut.
- Retain the approved FYNX Azure Blue `#007FFF` and mature, uncluttered UI. Keep the Control Center in its current app navigation location; do not add bottom navigation.
- No new paid infrastructure, production traffic switch, destructive database action, or secret rotation without explicit authorization.
- Do not merge PR #527 without the user's explicit approval. Finish implementation and verification, then present the PR and evidence for review.
- A passing static contract test is not proof of runtime correctness. A successful Android build is not proof that a protected backend endpoint works. A skipped production probe is not a pass.

## 5. Definition of complete — required final evidence

Do not say “done” until all applicable items below have evidence on the final PR head:

- [ ] All three groups above are completed, or any genuinely blocked item is explicitly identified with the exact missing dependency and why it cannot be completed.
- [ ] Latest PR head SHA and canonical base SHA are recorded.
- [ ] Android Build is green.
- [ ] Backend CI is green.
- [ ] Chat/Group Parity + Layout Safety is green.
- [ ] Admin Control Center contract verifier and relevant music/safety regression checks are green.
- [ ] Runtime screenshots or a specific explanation of why runtime capture was unavailable.
- [ ] OWNER, ADMIN, and non-admin authorization behavior tested where credentials/environment allow.
- [ ] Live backend status is reported truthfully; never convert a skipped probe into a green result.
- [ ] Final diff reviewed for unrelated changes and preservation of other workstreams.
- [ ] PR #527 remains open pending the user's merge approval.

At final handoff, provide only the essential result: grouped work completed, meaningful commit SHAs, PR link, exact CI status, runtime evidence, and any verified blocker. Keep this document updated after each grouped push so the next session can resume from the real latest state.

---

## 7. Latest verified progress — 12 November 2026

- Latest implementation head: `f431ee87387f30ed7a71ab288b631627670aff8f`.
- Previous head `573ef7192b18085f183b10e108175773384fd35c`: Backend CI, Android Build and Chat/Group Parity + Layout Safety all completed successfully. Android emulator/runtime screenshot and live production backend probe were skipped.
- Fixed an appeal ownership/restoration flaw across `backend/trustSafety.js` and `backend/adminRoutes.js`: an appeal linked to a report must now belong to the reported target account, not the reporter; an approved appeal only restores account state when its linked report actually targets that appellant. Unlinked appeals no longer unlock an account merely because they were accepted.
- Added a regression contract assertion for appeal ownership and safe restoration in `scripts/verify_admin_control_center_contract.py`.
- Commits in this grouped correction: `c038fcf15d0ab13f2c181f77b3ec29eae5cf3bfc`, `2954533b96411deea0b2a2a5f93aea95e9a14fe0`, `f431ee87387f30ed7a71ab288b631627670aff8f`.
- CI for the new head had not yet appeared when this note was written. Re-query before relying on this snapshot.

## 6. Next action

Continue the remaining runtime/authorization verification for Group 1, then finish Group 2 and Group 3 in order. The account-only report submission path, target-owned appeal rule, and OWNER/ADMIN role resolution have now been inspected. Do not treat static contract checks as runtime proof. Keep PR #527 open and do not merge without the user's approval.

## 8. Current continuation update — 12 November 2026

### Confirmed CI root causes and corrections
- 9c853bf3001dd2f802602e3a65c954d1d1b4e46b: changed Admin contract verifier file resolution to repository-root-relative paths. On the subsequent Android run, the Admin contract step printed ADMIN_CONTROL_CENTER_CONTRACT=GREEN.
- 11a694cd1e752907c7f56bfc8fead918afb600be: corrected the Large Badge #9 regression assertion. The stale test expected reporter_id=$2, but the intended rule is that an appeal may reference a report only when the appellant is the report's target (target_user_id=$2). The new assertion also checks the appeal is saved under the authenticated appellant.
- The previous Android run's Large Badge and Admin contract steps completed successfully. Its APK build stage was cancelled when later commits updated the PR; this is not a green Android build.

### Safety and People investigation
- The current user-facing report submission endpoint is specifically an account-report endpoint (/api/social/reports) with targetUsername, reason and details. The current UI labels this as “Report an account”; the queue does not claim to support post/comment/listing reports.
- Appeal submissions now validate that a linked report is against the authenticated appellant. Report IDs are being hardened to reject malformed, fractional, unsafe, boolean and non-positive values with HTTP 400 rather than allowing a database conversion error.
- Admin grant/revoke and report/appeal review actions already use transaction-scoped audit writes. Added protection against granting the first-user OWNER a duplicate ADMIN role and removed any legacy OWNER row from the displayed ADMIN roster.
- Still required: live OWNER/ADMIN/non-admin endpoint tests; check real database counts/nullable-user behavior; exercise account state changes and appeal outcomes; runtime UI testing. No production credential-backed test has been claimed.

### Administration and Publishing / final certification hardening
- Announcement image upload, link validation, persisted scheduling and public schedule filtering are present in the current PR; they still require runtime proof against the deployed media route and public feed.
- 0afbca66dcd372f2073367dc7b08df4574912e06 and 7ba6d11390e783b7c8cc41ba3e8500e5531d1c01: Marketplace seasonal-promotion and Music Library mutation paths were changed so the mutation and its activity audit row use the same database transaction. Audit failures now roll back the management change instead of being silently logged after the mutation has committed.
- d3d25eb20d979f663c9cf28b6016dc799691459b, 9a07f8b7a3d813998a18166df39b9548f3c6e512, 9c5c9054df67a7ee0f7179552939117356c175c5, 7835d1cf6454bf4a9a62cdb8fe8fe4dbe05ce3b7: added OWNER roster/grant protections, verifier coverage for transactional Marketplace/Music audits, strict appeal report-ID validation, and a regression assertion for that validation.

### Current verification caution
- The newest PR head at this update is 7835d1cf6454bf4a9a62cdb8fe8fe4dbe05ce3b7 (re-check before relying on it).
- Backend CI succeeded on an earlier intermediate commit. Newer commits were added before the full Android and Chat/Group build stages had completed, cancelling those earlier runs.
- Re-query workflows for the newest PR head. The static source checks performed while editing are not a substitute for green CI.
- Emulator screenshots, the live backend probe and credential-backed role tests remain unverified unless a later run explicitly records them.


## 2026-11-12 runtime-gate correction

- Latest pre-correction Android run (PR head `1838e65d6e13dc27cbc25660da50724c76b31383`, run `43206484716`) completed fast build, full build, lint and APK assembly successfully. Connected instrumentation completed 58 tests with 0 failures and 1 backend reachability test skipped.
- The Android job was red only after instrumentation: the broad Marketplace/Wallet visual script received HTTP 404 from the configured Railway health endpoint, Wallet did not settle, and the debug-local UI session did not expose My Shop. This was an out-of-scope visual gate for this Admin PR, not an Admin UI failure.
- The workflow now sets `FYNX_ADMIN_VISUALS_ONLY=1` only for this PR branch. The runtime runner records `FYNX-admin-runtime-certification.md` in the screenshot artifact, preserves Android instrumentation as a blocking check, and does not run Marketplace/My Shop/Wallet capture for this Admin-only change.
- IMPORTANT: Admin screen runtime certification remains explicitly BLOCKED until the backend health endpoint is healthy and a real OWNER/ADMIN test session is available. This change does not claim Admin runtime is green and does not weaken role authorization.
- Re-run all three PR checks after this correction. Do not mark the PR fully certified until a healthy-backend OWNER/ADMIN runtime pass is captured.


## Activity search hardening — 12 November 2026

- The Activity endpoint previously parameterized the search term but used SQL `ILIKE '%term%'`, so user-entered `%` and `_` were treated as wildcard operators rather than literal characters. This could produce confusing or overly broad matches even though it was not SQL injection.
- Replaced wildcard matching with case-insensitive literal substring matching via PostgreSQL `POSITION(LOWER($1) IN LOWER(...))` across action, summary, target ID and actor username. The query remains parameterized and bounded.
- Added a contract assertion requiring the literal-search expression and rejecting the old wildcard expression.
- This is backend/search correctness hardening; live OWNER/ADMIN database behavior and UI runtime remain subject to the existing environment-dependent verification gates.


## Atomic account-report submission — 12 November 2026

- Commit `54d5635b217ee9543b30ce18969c4d012066913b`: account-report creation now uses one database transaction and a transaction-scoped advisory lock keyed by reporter/account before checking for an existing OPEN or UNDER_REVIEW report and inserting the new case.
- Root cause addressed: the previous duplicate check and INSERT were separate pool queries. Two concurrent submissions could both pass the check and create duplicate open cases. The lock serializes only submissions for the same reporter/account; unrelated reports remain independent.
- Commit `b2f5277dd4abd4027b04c8bbe8bc39e9b185abe6`: Large Badge #9 Trust & Safety verifier now requires the transaction, scoped advisory lock, commit and connection release.
- This is source-level concurrency hardening. The CI verifier is not a live concurrent database test; credential-backed database concurrency and OWNER/ADMIN runtime verification remain outstanding.
- Re-run all three PR workflows on the latest head before taking this grouped correction as accepted. Keep PR #527 open and do not merge without approval.


## Appeal restoration concurrency hardening — 12 November 2026

- Commit `4b8981de4a3fd3b5fb63116dec5f4c1559c10473`: an accepted appeal now locks the affected user's `users` row before restoring account status in the safety tables.
- Root cause addressed: manual account-status changes already lock that user row, but appeal restoration did not. Concurrent manual restriction and appeal acceptance could therefore race and overwrite the latest account decision.
- Commit `adb13297c28ccf124cb3d05c43e1d3842880e1ad`: the Admin contract verifier now requires the shared user-row lock and its concurrency rationale.
- This is transaction-level serialization in source; credential-backed concurrent database testing and OWNER/ADMIN runtime verification remain outstanding. Re-run all three PR workflows on the newest head before accepting this group.


## Announcement scheduling hardening — 12 November 2026

- `backend/adminRoutes.js` now validates real calendar dates before parsing announcement schedules. Impossible dates (for example, February 30), invalid clock fields, and timezone offsets beyond ISO-8601's maximum are rejected instead of being silently normalized by JavaScript `Date`.
- The same validator is used by announcement creation and editing/rescheduling, and the contract verifier checks these cases are guarded.
- Commits: `da61e9789a30a77a52ca0d4d4bb6de1862bdc905` (backend), `821b203d510c50d0bc6896c459468007fadef965` (verifier).
- This is source-level hardening; live schedule execution and media upload still require backend/runtime verification.


## 2026-11-13 Admin runtime certification and Group 1 continuation

### Exact-head runtime certification
- PR #536 is the current Admin hardening PR; it supersedes the historical #527 handoff references above. It remains open and unmerged on `fix/admin-control-center-hardening-20261113`, targeting `integration/fynx-single-apk-20261013`.
- At commit `55d961cadf09a27f646321b27952c36bb994d314`, the Android PR workflow, Backend CI and Chat/Groups parity/layout workflows all completed GREEN:
  - Android run `43441597401`
  - Backend run `43441597306`
  - Chat/Groups run `43441597346`
- Android instrumentation completed 58 tests with zero failures and one skipped; the Admin read-only visual preview was captured from the running emulator as a real 1080×1920 PNG, 167081 bytes. The uploaded artifact also contains its UI hierarchy, instrumentation logs and certification report.
- Certification report records backend health HTTP 404 and explicitly marks live OWNER/ADMIN authorization NOT CERTIFIED. The preview uses sample counts and disables live data/actions. No production role, account, moderation, announcement or publishing mutation was performed.
- No canonical APK was published from this PR branch; canonical APK production remains tied to the canonical integration branch after an approved merge.

### Verified Group 1 findings now being addressed
- The Admin route module had its own JWT middleware which verified signature/expiry but did not compare the token's `sv` claim with `users.session_version`. Password-reset completion increments that database version, and the shared backend auth checks it; Admin endpoints could otherwise accept a revoked token until expiry. This is now being hardened with a separately testable middleware and a focused behavioral test.
- Admin People search returned only identity fields, forcing the operator to guess whether a target account is ACTIVE, LIMITED or LOCKED before choosing an action. The response and screen now expose the enforced `fynx_account_safety.account_status`.
- Admin People search used `ILIKE '%query%'`, so `%` and `_` were interpreted as SQL wildcard operators rather than literal characters. It is being changed to bounded, parameterized literal substring matching with PostgreSQL `POSITION`.
- The Chat/Groups route module also has a local token verifier without session-version checking. That file is owned by PR #535; preserve it and coordinate its correction with the Chat/Groups workstream rather than editing it in PR #536.
- Content-report moderation is not part of the current account-only `/api/social/reports` flow. PRs #531 and #533 own the separate UGC report integration; do not duplicate or overwrite that work.

### Remaining Group 1 verification
- Run the new Admin auth behavioral tests, Stage 15H Admin verification, Python Admin source contract, full Android build/lint/unit/instrumentation and all three PR workflows on the final head.
- Live OWNER/ADMIN/non-admin authorization, DB counts and state transitions still require a healthy backend and real test credentials. Current health probe is HTTP 404; no live authorization claim is permitted.
- Verify report/appeal empty states, server limits, pagination and transactional decisions against a database-backed test environment. Preserve the content-report PRs and Chat/Groups PR #535.

## 10. Group 2 continuation — Admin announcements and publishing

### Group 1 verification
- PR head: `c8ee4837f9898c03ee195adaa4942c2ff2ab9034`.
- Backend CI run 7852 (`43446084820`): passed; Stage 15H Admin verification passed 10 checks and session-version auth tests passed for current/revoked/deleted/invalid/missing/database-unavailable cases.
- Chat/Group Parity + Layout Safety run 4472 (`43446084742`): passed.
- Android Build run 14097 (`43446084736`): fast and full build/test/lint passed; Admin contract passed; instrumentation completed 58 tests (0 failures, 1 backend test skipped); Admin preview screenshot captured (166619 bytes); APK exact-commit verification passed. PR APK upload steps skipped; no canonical APK produced.
- Runtime artifact: https://github.com/greyhoundshop073-coder/FYNX/actions/runs/43446084736. Preview is visibly labelled and read-only with sample data.
- Live Railway health/OWNER/ADMIN authorization is not certified; earlier probe returned HTTP 404 and latest probe was skipped.

### Five verified Group 2 defects
1. Announcement PATCH validated positive-decimal IDs but not PostgreSQL BIGINT upper range.
2. Admin announcement history used hard `LIMIT 100` and had no offset/hasMore contract.
3. Public announcement feed used hard `LIMIT 50` and had no offset/hasMore contract.
4. Admin history with private drafts/scheduled records lacked explicit `Cache-Control: private, no-store`.
5. Create/edit silently truncated title/body/link at 120/4000/2048 chars; UI did not show title/link counters or enforce the same bounds.

### Group 2 corrections
- PATCH now uses `isPositivePostgresBigint`; both feeds have bounded limit/offset, hasMore lookahead and stable ID tie-break ordering (50 default, max 100, offset max 10000).
- Admin list is `private, no-store`; create/edit reject oversize values, rather than truncating. Composer displays title/link counters and enforces limits.
- Typed client uses `AdminPage<Announcement>`. Public and Admin UI expose Previous/Next, including empty page recovery; Admin write actions reset pagination.
- Added Python Admin contract assertions and Backend CI Stage15H regression checks.

### Dependencies and next stage
- Required exact-head checks: Backend CI (expanded Stage15H), Android fast/full build/test/lint/Admin contract/emulator evidence, Chat/Groups parity.
- Railway health was 404 previously and latest production probe skipped. Live Owner/Admin operations, announcement writes, Neon data, external media retrieval require a healthy backend.
- No login change or auth bypass. Admin preview debug-only/read-only. PR #536 stays unmerged.

### Group 3 — next five investigations
1. Seasonal promotion create/edit/activate/deactivate through Owner/Admin UI, backend authorization and audit.
2. Promotion date/timezone boundary validation: future, expired and overlapping campaigns.
3. Campaign artwork MIME, uploader ownership, access, replacement/removal and retry/failure behavior.
4. Audit-row atomicity for every promotion mutation, including rollback if audit insert fails.
5. OWNER-only direct API and UI for Music Library upload/publish/hide/restore/remove; preserve current music contribution.

---

---

## Group 3 Admin identity, session and protected-list hardening — 13 November 2026

### Prior-stage evidence
- PR #536 head: `93c13cc1a4ff65e184f4815fefdcf6c9aff840aa`; canonical integration base: `c8d0777dfe06089552fdaecc4cc888ea33bd6cbe`.
- Android Build `43454383887`, Backend CI `43454384063`, and Chat/Groups `43454384222` all passed on that head.
- Android instrumentation: 57 tests, zero failures, one skipped. Admin preview captured (166110 bytes); artifact: https://github.com/greyhoundshop073-coder/FYNX/actions/runs/43454383887/artifacts/14052959544
- Preview used read-only sample data. Live backend probes and PR APK upload were skipped; no canonical APK was published.

### Five verified Group 3 findings
1. Marketplace seasonal-promotion authorization inferred OWNER from lowest user ID rather than persisted `fynx_platform_owner`.
2. Music Library authorization independently repeated the lowest-user-ID OWNER lookup.
3. Marketplace advertising JWT verification did not compare `claims.sv` with `users.session_version`; reset-password-revoked sessions could remain valid until token expiry.
4. Admin seasonal-promotion history lacked an explicit `private, no-store` cache policy.
5. OWNER-only Music Library history lacked an explicit `private, no-store` cache policy.

### Grouped corrections
- Added a shared platform role resolver backed by the persisted Owner singleton; a locked one-time legacy bootstrap runs only when no Owner row exists. BIGINT IDs remain strings.
- Routed Admin Control Center, Marketplace seasonal promotion and Music Library authorization through the shared resolver. Existing OWNER-only Music mutation rules remain unchanged.
- Replaced Marketplace advertising's signature-only auth with session-version-aware auth; all 17 advertising route callsites await verification, and database outages fail closed with HTTP 503.
- Added `Cache-Control: private, no-store` to both protected Admin lists.
- Added behavioral tests for Owner/Admin/non-admin roles, BIGINT IDs, revoked/deleted sessions, missing/invalid tokens and database outages. Existing campaign schedule, overlap, artwork ownership and audit-transaction code was preserved.
- No login flow, UI, Chat/Groups, Camera, Home/Feed, Status, Wallet, Marketplace UI, Music UI or APK code was modified. PR #436's changed files were not edited.

### Verification required on the new head
- Backend CI must run the new role resolver behavior test, expanded advertising session-auth test, existing seasonal-promotion verifier and PostgreSQL schema migration test.
- Android Build and Chat/Groups parity must pass on the updated PR #536 head.
- Live Owner/Admin/non-admin calls, production artwork retrieval and full campaign lifecycle remain unverified until production backend and credentialed test accounts are reachable. A skipped reachability probe is not a pass.
- Keep PR #536 open/unmerged pending explicit approval. Keep PR #436 untouched. Only build/publish the canonical APK from the canonical integration branch after approved integration.


## CI contract correction and Owner bootstrap edge case — 13 November 2026

- Backend CI on `fc442f77697864dd2fd8afb5587bc2266b9ee694` failed because the sponsored-feed and CPC runtime verifiers still required the synchronous Advertising auth expression. Both were corrected to require the new awaited session-version check in commit `c1b19976f296eb04f6de0b49f1944134408bc6b9`.
- Android CI exposed a stale Admin source-contract assertion expecting the Owner table/bootstrap SQL inline in `adminRoutes.js`. The implementation centralizes it in `backend/platformAdminRole.js`; the Python contract now checks the helper and its use by Admin, Marketplace Advertising and Music.
- This review also caught cold startup before any user exists. The persisted Owner seed now retries on later role checks while no Owner singleton exists, then stops after the first registered account is persisted. The behavioral test covers empty startup, first signup, exact BIGINT IDs, ADMIN and ordinary-user denial.
- The concurrent seasonal-verifier corrections at `f47825b76a6ec70cf78af8eb5cd0724b4b7689be` and `40f6ad160893e7a0fc200b43d974470509a99662` are preserved.
- Keep PR #536 open and unmerged. Re-query all three workflows on the exact new head; no green status is inferred from an intermediate run.


## Group 4 — shared PostgreSQL BIGINT boundaries — 13 November 2026

### Five verified issues / evidence
1. Seasonal campaign creation accepted PRODUCT destination IDs with any 1–20 decimal digits; values above PostgreSQL BIGINT maximum `9223372036854775807` reached a BIGINT parameter query and could become HTTP 500 instead of a 400 validation response.
2. Campaign editing repeated the same 20-digit-only regex, so fixing create alone would have left the edit route vulnerable to the same overflow.
3. Owner/Admin account, report, appeal and announcement routes had an in-file BIGINT validator, while Marketplace product destinations had a separate, weaker validator. This split made the bounds inconsistent.
4. The existing Admin helper converted all values to strings before testing; an unsafe JavaScript Number could already be rounded before it reached `BigInt()`. Route parameters normally arrive as strings, but the helper also accepts body values and should reject unsafe numeric values.
5. Existing campaign verifier and Admin Python contract did not assert that PRODUCT IDs are within PostgreSQL BIGINT or that create and edit share the same exact bound. This allowed the 20-digit regex to pass CI.

### Group 4 grouped correction
- Added `backend/postgresIds.js` exporting one exact positive signed-BIGINT validator. String IDs stay strings; positive BigInt values are supported; numeric inputs are accepted only when `Number.isSafeInteger` is true; zero, leading zero, negative, decimal, exponent, malformed, overflow and unsafe Number values are rejected.
- Reused the shared helper in Admin announcements/people/reports/appeals/media IDs and Marketplace seasonal create/edit PRODUCT destinations.
- Added `test-postgres-id-validation.mjs` with valid max-BIGINT and >MAX_SAFE_INTEGER string cases plus invalid overflow, unsafe Number and malformed inputs. Wired it into Backend CI.
- Updated seasonal source-contract and Admin Python verifier to inspect the shared helper and both destination paths.
- Existing product lookup still verifies that a destination references an active listing with available stock. No feature, login, UI, or Marketplace buyer/seller behavior was changed.

### Remaining independent verification
- Backend CI must run the new validator behavior test, existing Stage 15H Admin contract, PostgreSQL campaign schema test, and seasonal verifier.
- Android and Chat/Groups checks remain separate required gates for the PR head.
- Campaign HTTP create/edit/activate/deactivate/delete lifecycle, audit rollback, competing-overlap concurrency and media visibility still lack a dedicated route-level end-to-end test in this repository. Existing source contracts and PostgreSQL schema migration tests do not substitute for those checks.


## Group 5 — Admin identity precision and PostgreSQL bounds — 13 November 2026

### Five verified findings
1. `POST /api/admin/admins` converted target IDs through JavaScript `Number`, rounding valid PostgreSQL BIGINT strings above `Number.MAX_SAFE_INTEGER`.
2. `DELETE /api/admin/admins/:userId` repeated that conversion and could query or compare a rounded identity.
3. Grant/revoke numeric coercion accepted non-canonical string forms such as exponent notation and leading-zero values rather than canonical decimal IDs.
4. Admin JWT middleware accepted any non-empty `sub`, then queried PostgreSQL without enforcing the signed BIGINT maximum.
5. Shared session-aware authentication and the Owner-role resolver enforced decimal syntax but not the PostgreSQL maximum, allowing out-of-range identities to reach SQL.

### Group 5 corrections
- Admin grant validates the original request value with `isPositivePostgresBigint` before converting it to a string; decimal-string IDs stay exact and unsafe numeric JSON values are rejected.
- Admin revoke preserves its route parameter as a string and validates before SQL.
- Admin JWT auth, shared Marketplace/session-aware auth and Owner-role resolution now use the same exact BIGINT validator and fail closed before database lookup.
- Extended Owner resolver, Admin auth, Marketplace auth and Stage 15H source-contract tests. Existing large decimal-string identities remain supported.
- No login flow, credential logic, session-version behavior or authentication shortcut was changed.

### Verification required
- Backend CI must pass `test-platform-admin-role.mjs`, `test-admin-auth-session-version.mjs`, `verify-marketplace-advertising-auth.mjs`, `verify-stage15h-admin.mjs`, and the shared PostgreSQL BIGINT boundary test.
- Android and Chat/Groups remain required on the exact latest PR head.


## Group 6 — Admin announcement route-level PostgreSQL verification — 13 November 2026

### Five verified coverage gaps
1. Admin announcement creation and its audit row were only checked with source assertions; no test invoked the registered HTTP route against PostgreSQL.
2. Owner/Admin denial, normal-user denial, and password-reset-revoked Admin sessions were tested in separate middleware/source checks, not through the real announcements route.
3. Scheduled announcement visibility had no route-level DB test proving that a future item appears in Admin history but not in the public feed until published immediately or its schedule arrives.
4. Announcement artwork ownership/MIME restrictions and HTTPS-link validation were source-tested, not exercised with actual route requests and stored media rows.
5. The production routes pass the transaction client to audit logging, but no integration test deliberately failed the audit insert to prove create/edit mutations roll back.

### Group 6 grouped correction
- Added an optional test dependency seam to registerAdminRoutes: databasePool, jwtApi, and jwtSecret. Production defaults remain the existing shared pool, JWT library and configured secret; no production auth behavior or login flow changes.
- Added backend/test-admin-announcements-db.mjs, a PostgreSQL-backed route test using a unique schema and the actual registered Admin handlers.
- Coverage includes Owner/Admin/regular-user access, stale Admin session rejection, exact large-ID grant/revoke behavior, future schedule visibility, impossible dates, HTTPS-only links, artwork owner/MIME checks, publish/unpublish, private no-store Admin history, pagination beyond 50 entries, and rollback of create/edit when audit insertion fails.
- Wired the new DB route test into Backend CI and added source-contract assertions for the test seam and CI step.

### External verification
- The test uses the CI PostgreSQL service and does not require live production credentials, providers or device access.
- Physical-device Admin interaction and actual production Owner/Admin authorization remain separate certification requirements; emulator visual capture is not a substitute for live backend authorization.
