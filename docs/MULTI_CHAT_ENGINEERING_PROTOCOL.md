# FYNX Multi-Chat Engineering Protocol

**Applies to:** every ChatGPT conversation, coding agent, and human contributor working in this repository.
**Canonical Android integration branch:** `integration/fynx-single-apk-20261013`
**Purpose:** allow multiple independent sessions to make progress without creating competing implementations, losing context, overwriting work, or fragmenting the APK.

This protocol supplements (and does not replace) `AGENTS.md`, `docs/SINGLE_APK_INTEGRATION_POLICY.md`, `docs/DEVELOPMENT_POLICY_COMPLIANCE.md`, and `docs/BACKEND_READY_DEVELOPMENT_POLICY.md`. If instructions conflict, follow the stricter repository policy and ask the project owner before an irreversible or scope-changing action.

## 1. Parallel chats are allowed and expected

The project owner may use multiple ChatGPT conversations because of usage limits, latency, context limits, or to work on separate areas in parallel. **Do not require the owner to use one chat.** Treat each conversation as a contributor in one coordinated engineering team.

Parallelism is useful when workstreams are bounded and file/API ownership is clear. It is unsafe when sessions edit the same files or contracts without coordination.

## 2. One shared destination; isolated work

- The only canonical Android delivery destination is `integration/fynx-single-apk-20261013`.
- Each independent workstream uses its own feature branch, created from the latest canonical head.
- Each feature PR targets the canonical branch. Never use `main`, another feature branch, or a competing integration branch as the delivery destination.
- Do not commit feature work directly to the canonical branch.
- Do not publish a feature-branch APK as the shared/latest FYNX APK. The shared APK is built from the latest merged canonical commit.
- A local/PR APK may be used for diagnostics only and must be labelled with its source branch and commit.

## 3. Mandatory check-in before coding

Every new or returning chat must:

1. Read `AGENTS.md` and the four core policies/handoffs listed above.
2. Read this protocol and the current `docs/MULTI_CHAT_WORKSTREAM_REGISTER.md`.
3. Fetch the latest canonical branch head and current status of relevant open PRs, branches, and CI checks. Do not rely on screenshots, copied summaries, or old handoffs as current status.
4. Inspect the target feature's implementation, callers, shared interfaces, tests, migrations, and relevant previous commits.
5. Identify files, APIs, database tables, navigation routes, shared components, and configuration that the work may touch.
6. Check the register for active owners and overlapping work. Claim or renew the workstream before editing.

If current GitHub state cannot be retrieved, say so and do not claim it was inspected. Do not guess the latest commit, PR state, CI result, or runtime status.

## 4. Workstream ownership and overlap control

- Give each active workstream a clear label and one accountable session/owner at a time (for example, `Video Calls — Chat C`). The label is coordination metadata, not a permanent assignment.
- Record scope, branch, PR, base commit, current head commit, files/contracts touched, status, next action, and last update in the shared register.
- One active owner per workstream. A replacement chat takes over explicitly and verifies the branch/PR before editing.
- Separate workstreams may proceed concurrently when their files and contracts do not conflict.
- If two workstreams need the same file, shared API model, navigation graph, database schema, backend route, build configuration, or design-system component, pause the overlapping edit and coordinate through the register/PR discussion. Agree on one owner for the shared change or split it into a stable interface change followed by independent work.
- Do not create parallel implementations of the same feature, duplicate API clients, duplicate repositories, or competing routes.
- Before merging, rebase/update against the latest canonical base as appropriate, inspect the complete diff and preserve all newer work. Never blindly merge a divergent branch or force-push over another contributor's commits.

## 5. Define a bounded assignment

Each workstream must state:
- **Goal:** user-visible outcome, not a vague activity.
- **In scope:** exact feature flows and files/contracts likely involved.
- **Out of scope:** neighboring features that must remain untouched.
- **Acceptance criteria:** observable behavior and failure cases that prove completion.
- **Dependencies:** backend, storage, realtime, payment, permissions, credentials, device/emulator, or another workstream.
- **Verification plan:** relevant unit, integration, contract, UI, regression, and runtime checks.

Do not expand scope just because adjacent improvements are noticed. Record unrelated findings as follow-up work unless they are necessary to prevent a defect or security issue.

## 6. Implementation and quality rules

- Inspect before editing; extend the existing architecture and preserve working behavior.
- Every visible control must perform its advertised action or be clearly disabled with an honest explanation. No inert buttons or navigation dead ends.
- Verify loading, success, empty, validation, permission-denied, offline, timeout, server-error, retry, and cancellation states where relevant.
- Keep UI provider-independent and use the existing shared API/configuration/storage abstractions.
- No secrets, credentials, tokens, private keys, or payment data in source, logs, screenshots, APK resources, or AI prompts.
- Never fake live connectivity, users, balances, orders, reports, uploads, messages, call connections, payments, or admin actions. Test doubles must be explicitly isolated from production.
- Preserve the existing working login flow. Do not introduce OTP or weaken authorization as a shortcut.
- Follow the backend-ready policy: finish safe app-side, contracts, backend logic, migrations, tests, and reconnection instructions even when production services are offline. Mark only genuinely live-dependent actions as blocked.
- Follow platform, privacy, safety, security, and Google Play requirements. Update compliance inventories when data, permissions, SDKs, moderation, payments, AI, ads, or other regulated behavior changes.
- UI changes require layout checks for alignment, spacing, scrolling, overlap, text overflow, keyboard behavior, accessibility, touch targets, and relevant screen sizes. Prefer actual APK/device evidence over source-only assumptions.

## 7. Test, diagnose, and merge

Before requesting review:
1. Run the focused tests for the changed behavior.
2. Run relevant Android build/lint/tests, backend tests, API/contract checks, and cross-feature regression checks.
3. Test unavailable-service and failure paths; verify no false success, crash, data loss, duplicate mutation, or unsafe retry.
4. Inspect the final diff and confirm no unrelated or other-workstream changes were included.
5. Fetch current CI checks. For every red check, inspect the actual failing job/log, identify root cause, fix it, and rerun affected checks. A skipped check is not a pass.
6. Request review/merge only when required checks and evidence are available, or explicitly identify any owner-approved exception. Do not merge around a failing required check.

A successful compile proves compilation only. It does not prove a complete user journey, live backend connectivity, or release readiness.

## 8. Handoff and chat replacement

A conversation may become slow, reach a usage limit, or be replaced. This must not cause work to restart.

At each meaningful checkpoint and before handing off, update the workstream register and the feature-specific handoff with:

- Workstream and bounded scope.
- Branch, PR URL/number, PR base, base commit, and latest feature commit.
- Exact files and interfaces changed.
- What works and what remains, with status per capability.
- Commands/tests run and exact pass/fail/skip results.
- Current CI links and latest observed state.
- Runtime/device/emulator evidence and exact APK commit, if any.
- Backend/provider dependencies and precise live-only blockers.
- Known risks, overlapping files, and files another session should not edit without coordination.
- One concrete next action.

A replacement chat must verify current GitHub state and continue from the existing branch/PR. It must not restart, recreate the branch, duplicate the implementation, or trust a stale status snapshot.

## 9. Shared status vocabulary

Use these labels consistently:

- **Planned:** not implemented.
- **In progress:** active implementation; not yet accepted.
- **Implemented — unverified:** code exists, but required behavior has not been demonstrated.
- **Locally verified:** specified local tests passed; live service not necessarily tested.
- **Contract-ready — awaiting live service:** client/server contracts and safe local tests are ready; a named live dependency remains.
- **Live verified:** the real configured service and end-to-end user journey passed with evidence.
- **Blocked:** work cannot proceed; state the exact blocker and owner/action needed.
- **Merged — not release-certified:** merged to canonical, but full app/release verification remains.
- **Complete:** acceptance criteria and required tests passed, integration is confirmed, and evidence is recorded.

Never upgrade a status based on optimism, a successful build alone, or an old CI run.

## 10. Integration and release authority

Feature chats own their bounded feature delivery; they do not certify the entire app. Integration/release verification checks the combined canonical application, cross-feature regressions, actual APK artifact, source commit, artifact SHA-256, and relevant runtime/backend evidence.

The release report must distinguish:
- merged code;
- successful Android build;
- successful backend/contract checks;
- tested runtime behavior;
- live backend/provider verification;
- release/policy blockers.

Only an APK built from the latest canonical merged commit may be called the shared canonical APK. Never invent an artifact link, screenshot, checksum, test result, or deployed commit.

## 11. Escalation

Pause and ask the project owner before:
- changing the canonical branch or package/application ID;
- replacing or removing a working feature outside the assignment;
- changing login/authentication behavior;
- altering production data or production traffic;
- introducing paid infrastructure or a new paid provider;
- enabling real-money wallet/payment/escrow behavior;
- making an irreversible migration or destructive data operation;
- accepting a security, privacy, or platform-policy exception.

For ordinary, reversible bug fixes within the assigned scope, investigate and proceed without repeatedly asking the owner to restate known project context.
