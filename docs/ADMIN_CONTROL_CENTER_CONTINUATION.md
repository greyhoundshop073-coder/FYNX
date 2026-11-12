# FYNX Owner/Admin Control Center — Continuation Handoff

**Purpose:** This is the durable work order for the next ChatGPT Think session or contributor. Continue the existing implementation until the remaining scope below is implemented and verified. Do not restart, produce a status-only report, or call the work complete merely because a screen or endpoint exists.

## 1. Where to resume

- Repository: `greyhoundshop073-coder/FYNX`
- Active PR: [#527 — Integrate Owner/Admin Control Center refresh on canonical branch](https://github.com/greyhoundshop073-coder/FYNX/pull/527)
- Feature branch: `admin/control-center-complete-20261112`
- PR base / canonical Android integration branch: `integration/fynx-single-apk-20261013`
- Code head when this handoff was prepared: `40a83bcf72a5df742d1804fab5c68229999dc5a3`
- PR state at handoff: open and unmerged.

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
