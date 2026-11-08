# FYNX Module Completion Register

> **Purpose:** The single source of truth for deciding whether a FYNX feature is unfinished, implemented, verified, certified, or reopened for a justified defect.
>
> **Canonical project:** `greyhoundshop073-coder/FYNX`  
> **Canonical Android integration branch:** `integration/fynx-single-apk-20261013`  
> **Release rule:** Only the canonical integration branch produces the official single FYNX APK. Feature-branch APKs and validation artifacts are not release APKs.

Last reviewed: 2026-11-08  
Register status: **INITIAL BASELINE — module-by-module audits still required**

---

## 1. Status vocabulary (use exactly these states)

| Status | Meaning | Can we call the module complete? |
|---|---|---|
| **NOT ASSESSED** | No current, complete audit has established its state. | No |
| **OPEN** | Requirements, implementation, or known defects remain. | No |
| **IN PROGRESS** | Work is actively being implemented or corrected. | No |
| **IMPLEMENTED — UNVERIFIED** | Code is present, but required tests or runtime evidence are missing. | No |
| **CONDITIONAL — BLOCKED** | Source-level work may be ready, but an external dependency or required runtime check prevents full verification. | No |
| **CERTIFIED — CLOSED** | All acceptance criteria passed on the integrated canonical build, evidence is recorded, and closure is approved. | **Yes** |
| **REOPENED** | A verified defect or approved change has reopened a previously closed module. | No, until recertified |

A green CI check alone is not certification. A merged PR alone is not certification. A screenshot alone is not certification. Certification requires the complete evidence set below.

## 2. Definition of Done — mandatory closure gate

A module may be marked **CERTIFIED — CLOSED** only when every applicable gate is satisfied and evidence is linked in its record.

### A. Scope and requirements
- [ ] The module's purpose, boundaries, and user-facing journeys are documented.
- [ ] Every agreed requirement has a stable checklist item or acceptance criterion.
- [ ] Out-of-scope behavior and known API/backend limitations are stated honestly.
- [ ] Loading, empty, error, offline, permission-denied, and recovery states are specified where applicable.
- [ ] Security, privacy, role/ownership, and data-retention expectations are documented where applicable.

### B. Implementation
- [ ] Every in-scope acceptance criterion is implemented.
- [ ] Controls perform their stated actions; no dead buttons, placeholder success states, or misleading labels remain.
- [ ] Navigation, back behavior, state restoration, and lifecycle behavior are correct.
- [ ] UI layout, spacing, typography, media sizing, keyboard behavior, and accessibility are reviewed against the agreed design.
- [ ] No temporary test fixtures, debug-only shortcuts, secrets, or unintended sample data leak into production behavior.

### C. Quality and reliability
- [ ] Relevant unit tests pass.
- [ ] Relevant integration/contract tests pass.
- [ ] Static analysis, lint, and build checks pass.
- [ ] Relevant Android emulator/instrumentation journeys pass.
- [ ] Failure/retry, slow or absent network, process recreation, and permission paths are tested where applicable.
- [ ] Security/privacy checks pass; unauthorized users cannot access protected data or actions.
- [ ] No known release-blocking defect remains.
- [ ] Any accepted lower-severity defect is listed with impact, workaround, owner, and follow-up issue.

### D. Regression and integration
- [ ] The exact candidate commit SHA is recorded.
- [ ] The feature is integrated into the canonical branch; the tested source is not an unrelated feature branch.
- [ ] Required neighboring-module regression suites pass (for example, Chat/Groups parity when camera, Home, or shared UI code changes).
- [ ] No protected module or user flow regressed.
- [ ] Backend-dependent behavior is verified against a healthy, correctly configured test environment. If unavailable, it remains CONDITIONAL — BLOCKED; it must not be represented as live-certified.
- [ ] The canonical Android build succeeds for the exact integrated commit.

### E. Evidence and approval
- [ ] Actual application screenshots are attached and labelled by journey/state; mockups do not count as runtime evidence.
- [ ] Test run URLs, workflow names, results, and exact commit SHA are recorded.
- [ ] Relevant PRs, merge commits, and issue/defect links are recorded.
- [ ] A concise manual walkthrough is recorded for important user journeys.
- [ ] Remaining limitations and external dependencies are disclosed.
- [ ] Product owner/user acceptance is recorded.
- [ ] Register row and module record are updated to CERTIFIED — CLOSED.

**If any required gate is unchecked, the module is not certified.** Mark it OPEN, IN PROGRESS, IMPLEMENTED — UNVERIFIED, or CONDITIONAL — BLOCKED as appropriate.

## 3. Module register — initial evidence-based baseline

This is a starting inventory, not a declaration that every module is unfinished. **NOT ASSESSED** means the team must audit the actual integrated implementation before making a claim.

| Module | Initial status | Current evidence / next required action |
|---|---|---|
| Home feed, video, comments and reposts | **IN PROGRESS** | PR #486 remains open. Its own merge conditions require exact-head Android build, instrumentation, visual evidence, Home journeys, and regressions. Re-audit current PR and checks before closure. |
| Camera, media editor and New Post | **IN PROGRESS** | PR #490 remains open. At the recorded head `e24ceb7aadd338ecdeeeb488ca4ad7cc0765669b`, Backend CI and Chat/Group Parity passed while Android Build was still in progress. Do not certify until exact-head Android/runtime/screenshot checks finish and the integrated canonical build is verified. |
| Chat | **NOT ASSESSED** | Audit all personal messaging journeys, delivery/read state, attachments, voice notes, calls if in scope, notifications, recovery, privacy and regression evidence. |
| Groups | **NOT ASSESSED** | Audit creation/join/invites/roles, group messaging, media retry, membership changes, notifications, permissions and all runtime journeys. PR #343 describes a focused group-media reliability correction; it is not proof that all Groups work is complete. |
| Status and Stories | **NOT ASSESSED** | Audit photo/video/voice creation, preview, edit, publish, expiry, viewers, privacy, deletion and runtime recovery. |
| Marketplace buyer experience | **NOT ASSESSED** | Audit discovery, search, product details, saved items, cart, checkout validation, orders, receipts, disputes and unavailable-backend behavior. |
| My Shop / seller storefront | **IN PROGRESS** | PRs #460 and #461 are open for public storefront access and earnings breakdown. Verify the complete seller lifecycle and API/privacy contract before closure. |
| Wallet and payments | **NOT ASSESSED** | Audit ledger correctness, pending/reserved/available balances, funding, payment verification, receipts, refunds, PIN/security and test/live separation. Never claim live payment certification without live-safe verification. |
| FYNX AI | **IN PROGRESS** | PRs #487 and #488 are open for financial privacy test hardening and read-aloud UI. Audit all AI entry points, text/voice behavior, failure states and explicit exclusion of private financial data. |
| Discovery | **NOT ASSESSED** | Audit discovery entry points, feed/content behavior, navigation, pagination, empty/error states and regression with Home. |
| Notifications | **NOT ASSESSED** | Audit delivery, deep links, unread/read state, preferences, lifecycle/background behavior and permission denial. |
| Profile / Avatar | **NOT ASSESSED** | Audit identity/photo update, visibility/privacy, consistency across modules, loading/failure and account ownership. |
| Help & Support | **IN PROGRESS** | PR #440 remains open and explicitly requires CI, real role-based walkthroughs, screenshots and canonical APK verification. |
| Admin / Owner controls | **NOT ASSESSED** | Audit role enforcement, server authorization, sensitive actions, audit history, and Owner/Admin separation. |
| App-wide navigation, appearance and accessibility | **NOT ASSESSED** | Audit all destinations, back stack, light/dark/AMOLED modes, shared controls, text scaling and accessibility. |

**Baseline note:** statuses above reflect the PR and workflow information inspected on 2026-11-08. They must be refreshed from GitHub before each certification decision. Do not infer that a PR is merged or a check passed unless its current GitHub record confirms it.

## 4. Required module record (copy for every module)

Create or update one record per module in this register, or link a dedicated `docs/modules/<module-name>.md` record.

```text
Module:
Product owner:
Module lead:
Status: NOT ASSESSED | OPEN | IN PROGRESS | IMPLEMENTED — UNVERIFIED |
        CONDITIONAL — BLOCKED | CERTIFIED — CLOSED | REOPENED
Last reviewed:
Requirements version:
Scope / explicit exclusions:

ACCEPTANCE CRITERIA
[ ] AC-01:
[ ] AC-02:
[ ] AC-03:

IMPLEMENTATION
- Screens/components:
- APIs/data contracts:
- PR(s):
- Merge commit(s):

VERIFICATION
- Exact canonical commit SHA:
- Build:
- Unit tests:
- Integration/contract tests:
- Lint/static analysis:
- Emulator/instrumentation:
- Manual walkthrough:
- Regression suites:
- Backend/live dependency state:

EVIDENCE
- Screenshot/artifact links:
- Workflow run links:
- Test report links:
- Known limitations:
- Open defects / issue links:

APPROVAL
- Product owner acceptance:
- Certified by:
- Certification date:
- Closure decision and notes:
```

## 5. Working rules — prevent endless development

1. **Lock scope before implementation.** New ideas go into a follow-up backlog unless they are required to satisfy an agreed acceptance criterion or fix a defect.
2. **One active closure target at a time.** A module has one named current objective, a finite checklist, and an explicit exit gate.
3. **No silent scope expansion.** Any new requirement is logged, impact-assessed, and either added with owner approval or scheduled for a later version.
4. **No false completion.** A push, PR, merge, successful compile, or passing subset of tests is not the same as module certification.
5. **Fix red checks before moving the gate.** Inspect logs, identify the cause, fix it, and rerun the failed check on the new exact head.
6. **Protect closed modules.** Once certified, freeze its scope. Reopen only for a reproducible defect, security issue, compatibility issue, or explicitly approved change.
7. **Regression is mandatory.** A change to a shared component or cross-module contract must rerun affected neighboring-module suites.
8. **Keep the canonical APK lane single.** No competing integration branch or feature-branch release APK.
9. **Backend unavailable means conditional, not complete.** Source/static checks may continue, but live-dependent behavior stays clearly blocked until it can be verified.
10. **Every handoff uses three headings:** COMPLETED (verified only), CURRENT TASK, REMAINING TO CERTIFY.
11. **Every work report names the exact commit and evidence.** State what was tested, what passed/failed/blocked, and where the artifacts are.
12. **Do not reopen for cosmetic preference alone** after sign-off; collect it for a planned improvement unless it violates an agreed design criterion.

## 6. Defect severity and reopening

- **Release blocker:** crash, data loss, security/privacy breach, unauthorized action, broken critical journey, incorrect financial state, or a required build/test failure. Certification is blocked.
- **Major:** core journey unusable or materially incorrect with no reasonable workaround. Certification is blocked.
- **Minor:** limited issue with a safe workaround and no security/data/financial risk. May be accepted only with explicit product-owner approval and a linked issue.
- **Polish:** non-blocking visual or interaction refinement that does not violate the approved acceptance criteria. Record it for a later iteration.

When a certified module is reopened, record the defect, affected version/commit, severity, reproduction steps, fix commit, regression results, and recertification date. Preserve the original certification history.

## 7. Standard progress report

Every FYNX handoff and progress report must use:

### COMPLETED
Only items that passed their stated verification. Include commit SHA and evidence.

### CURRENT TASK
One clearly named module/objective, its remaining acceptance criteria, and the exact branch/PR/head SHA.

### REMAINING TO CERTIFY
A numbered list of the failed, untested, blocked, or awaiting-approval gates. State who/what can unblock each item.

**The report must end with one honest status:** CERTIFIED — CLOSED, IN PROGRESS, IMPLEMENTED — UNVERIFIED, or CONDITIONAL — BLOCKED.

---

## 8. First operational sequence

1. Keep this register on the canonical integration branch.
2. Audit Chat and Groups against the actual current source and all relevant PRs; do not assume a focused parity test certifies every user journey.
3. Finish and certify the active Camera/New Post and Home work only after exact-head checks and actual runtime evidence pass.
4. Then close modules one by one using the same Definition of Done.
5. Refresh this register after every merge, failed check, accepted limitation, or certification decision.

**A module is finished when its agreed requirements are met and verified—not when we get tired of working on it.**
