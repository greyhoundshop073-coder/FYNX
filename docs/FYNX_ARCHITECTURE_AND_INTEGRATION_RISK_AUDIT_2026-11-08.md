# FYNX Architecture and Integration Risk Audit

**Audit date:** 2026-11-08  
**Scope:** Read-only inspection of the canonical Android integration branch `integration/fynx-single-apk-20261013`.  
**Repository:** `greyhoundshop073-coder/FYNX`

## Executive summary

The immediate professional priority is not a broad rewrite or a forced Gradle modularization. It is to reduce integration risk around shared navigation/state, close already-identified reliability gaps, and make CI results easier to trust and maintain. Preserve the existing single canonical APK and existing feature implementations.

This audit did not modify Android application source code, backend code, login, payment flows, or runtime configuration. It is a source/configuration inspection, not a device certification.

## Confirmed observations

| Area | Evidence | Assessment |
|---|---|---|
| Gradle structure | `settings.gradle.kts` includes only `:app`. | Confirmed single application module. This is not itself a defect; modularize only when clear boundaries and test value justify it. |
| App entry/navigation | `app/src/main/java/com/fynx/app/ui/FynxApp.kt` is 874 lines (about 60 KB). It owns top-level navigation, many screen-routing values, lifecycle effects, account/session state and multiple feature-specific transient states. | High change-blast-radius risk. Avoid broad edits here; extract behavior in small, behavior-preserving steps after tests exist. |
| Local state concentration | The inspected `FynxApp.kt` contains 42 textual `remember` occurrences and 39 mutable-state declarations/usages by simple source count. | Indicates concentrated state ownership; counts are a signal for review, not a quality metric or proof of a bug. |
| Android CI workflow | `.github/workflows/android-build.yml` is 350 lines and includes many product-specific verification scripts. | Existing contract checks are valuable; retain them while improving grouping and diagnostics. |
| Duplicate workflow step | The workflow contains two consecutive steps both named “Verify Google Cloud authentication” with identical commands. | Confirmed redundant step. Remove only after a workflow change is reviewed and the remaining authentication gate is verified. |
| Documentation triggers | The workflow path filters include `docs/**` for push and pull-request events. | Documentation-only commits can trigger the full Android workflow. Separate documentation validation from Android build/runtime jobs without losing coverage. |
| Chat offline-send backlog | GitHub issue #47 describes the composer as bypassing the outbox. Current canonical source shows ordinary text and ordinary media messages call `FynxProductionMessaging.sendText`, whose default `allowOfflineQueue = true` enqueues ambiguous transport failures in the account-scoped outbox with a stable request ID. `FynxOfflineSync` drains with queueing disabled and replaces the pending local message after acceptance. However, the composer’s structured-message branch calls `sendStructuredMessage`, which posts directly and has no corresponding offline queue/reconciliation path. | The issue is partly stale, not a reason to duplicate the existing queue. The remaining confirmed source-level gap is structured messages (location/contact/poll/sticker/GIF) and their attached media path. Test the existing ordinary-message flow, then design a compatible structured-message outbox with idempotency, local pending rendering, persistence and retry semantics before changing it. |
| Email launch backlog | GitHub issue #131 records email delivery as incomplete and calls for test-environment delivery and recovery/rate-limit/expiry tests. | Launch blocker until the required email flows are defined and real delivery is verified. A visible UI alone is not evidence of delivery. |

## Verification limitations

- The branch name was confirmed to exist and the files above were fetched from that ref.
- The inspection did not establish the exact current branch-head commit SHA, branch protection/ruleset configuration, or a complete current open-PR inventory. Those must be checked with repository settings and PR-specific data before any merge policy or PR status is asserted.
- The fetched historical engineering-standards commit had no workflow runs/status entries returned by the available commit-run/status queries. This is not evidence that current application CI is green or red.
- No APK was built or installed in this audit. No emulator/device screenshots or live backend checks were performed.
- Backend-dependent flows remain unverified while the backend is unavailable. UI/source checks must not be reported as live-service certification.

## Risk register and priority

### P0 — Protect the canonical integration baseline

1. Refresh the canonical branch head SHA and compare it with every active feature PR's base/head.
2. Identify overlapping file changes in shared navigation, theme, messaging, media and app-level state before integration.
3. Verify required checks and branch protection/rulesets in GitHub Settings. Do not invent or assume protection is enabled.
4. Preserve the last known-good canonical APK and record its source SHA, workflow run, artifact checksum and device test evidence.

**Exit evidence:** exact branch SHA; PR dependency/overlap map; actual ruleset/check names; reproducible APK provenance.

### P1 — Complete the remaining structured-message offline path

1. Preserve the existing ordinary text/media implementation: `sendText` already queues ambiguous transport failures by default, uses an account-scoped durable outbox and stable request ID, and `FynxOfflineSync` retries without recursively queueing.
2. Add focused verification for ordinary text/media: outbox persistence, stable idempotency key, reconnect drain, local pending-message replacement, account isolation and duplicate retry reconciliation.
3. Trace `sendStructuredMessage` and all callers (location, contact, poll, sticker and GIF). Its current implementation posts directly and has no offline queue/reconciliation counterpart.
4. Design a structured outbox item that preserves message type, payload, reply ID, media ID/type and stable request ID; ensure the backend idempotency contract supports these structured requests before implementing.
5. Render and persist a pending local structured message, reconcile it on reconnect, and remove it only after server acceptance or authoritative history reconciliation.
6. Add tests for each structured type, upload-success/send-failure, reconnect flush, process restart, duplicate retry, account switch and permanent failure. Run Chat/Groups parity and messaging regression checks on the final integration SHA.

**Guardrail:** do not rebuild or replace the existing ordinary-message queue, and do not change login/backend configuration as part of this task.

### P1 — Audit and gradually reduce navigation coupling

1. Map navigation destinations, deep links, back-stack rules, account/session state and feature-owned transient state.
2. Add focused tests around back navigation, deep links, account switching and screen transitions.
3. Extract one isolated navigation/state responsibility at a time, keeping the public behavior unchanged.
4. Run all relevant feature regression tests after each extraction.

**Guardrail:** no wholesale rewrite of `FynxApp.kt`, no forced module split, and no simultaneous UI redesign.

### P1 — Make CI clearer without weakening it

1. Remove the duplicated Google Cloud authentication verification step after confirming one identical gate remains.
2. Split fast static/contract checks, Android compile/unit/lint, emulator/runtime certification, and optional live-backend probes into clearly named jobs.
3. Keep all existing verification scripts and their failure visibility.
4. Prevent docs-only changes from running the full Android build while retaining markdown/link/document validation.
5. Use stable artifact names and attach commit SHA, run URL and checksum to release artifacts.

**Guardrail:** do not delete or silently skip a product-specific verification script to make CI green.

### P1 — Close launch-critical email and account recovery

Follow issue #131. Define required email journeys and provider/backend contract first. Test real delivery, invalid addresses, resend limits, token expiry/reuse, reset security, and failure/retry behavior in a controlled test environment. Keep secrets in deployment configuration.

### P2 — Continue feature-by-feature production journey certification

Use the module completion register. For each feature, certify one complete user journey including normal, failure, recovery, privacy/security and regression paths. Mark backend-dependent behavior conditional until tested against a live service. Capture screenshots from the exact final integrated APK, not a stale branch build.

## Recommended execution order

1. Refresh exact canonical SHA, active PRs, PR overlap and CI status.
2. Fix and certify the Chat offline-send path (issue #47) as a bounded reliability change.
3. Map and test navigation/back/deep-link/account-switch behavior before any extraction from `FynxApp.kt`.
4. Make the minimal CI cleanup and split jobs only with all existing checks preserved.
5. Complete the email/auth launch blocker (issue #131) in its own bounded workstream.
6. Continue module certification and release hardening against one canonical APK.

## Completion standard for each change

A change is not complete because code compiles or a source-level script passes. Record:
- exact repository, branch, base SHA and final commit SHA;
- bounded scope and acceptance criteria;
- tests/build/lint and relevant feature regression results;
- emulator/device journey evidence and current screenshots where UI is affected;
- backend status separately from APK status;
- known limitations and rollback path.

Only mark a module `CERTIFIED — CLOSED` when its agreed acceptance criteria and evidence are satisfied. Reopen it only for a reproducible regression, new requirement, or newly discovered security/reliability defect.
