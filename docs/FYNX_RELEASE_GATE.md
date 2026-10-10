# FYNX Release Gate — Permanent Development Standard

Repository: greyhoundshop073-coder/FYNX

This is the permanent release/continuation standard for every FYNX workstream and every future ChatGPT development session. Read this before starting work, before pushing, and after pushing.

## Core rule

Never move to the next workstream merely because code was pushed or CI is green.

A feature is complete only when its implementation, integration, backend/storage path where applicable, CI, real user journey, regression behavior, and APK-visible behavior have been verified.

## Required sequence

1. INVESTIGATE FIRST
- Inspect the live repository and current main.
- Inspect the existing implementation and all relevant callers/usages.
- Trace UI entry, navigation, state, API/realtime, backend, database/storage, response, and UI completion.
- Inspect relevant tests, verifiers, and workflows.
- Identify the real root cause and define the smallest correct change.
- Reuse existing systems; do not create duplicate cameras, chat transports, AI paths, marketplace systems, models, routes, or storage systems.

2. IMPLEMENT ONE COHERENT BATCH
- Preserve known-good behavior.
- Do not mix unrelated work.
- Do not fabricate application data.
- Do not put secrets in the APK.
- Every commit created during continuation work must begin with FYNX-THIS-CHAT —.

3. VERIFY AFTER THE PUSH
- Inspect the resulting diff/commit.
- Confirm only intended files changed.
- Run relevant Android/backend/static/security verification.
- Verify the complete user journey, not just source existence or a verifier marker.
- Confirm navigation, back behavior, lifecycle, keyboard/insets, touch/z-order, loading, empty, error, retry, and return states where applicable.

4. CI GATE
- Relevant workflows must finish GREEN on the exact commit.
- Any RED result stops the workstream. Investigate the actual failure before making another feature change.
- Never weaken or rewrite a verifier just to obtain GREEN.

5. REAL RUNTIME GATE
- Where authenticated/runtime testing exists, run it.
- Check real APK behavior: navigation, process crashes, authenticated data, backend calls, media/realtime behavior, notifications/deep links, permissions, and completion states.
- A static build or verifier alone is not a runtime certification.

6. APK GATE
- The APK must correspond to the exact verified commit.
- Verify the published artifact exists and is the artifact tested.
- Do not hand off an APK as release-ready while required runtime/integration gates remain unverified.

## Status meanings

- GREEN — RELEASE READY: required implementation + integration + CI + applicable real runtime/APK verification all passed.
- YELLOW — PARTIALLY VERIFIED: implementation/build/integration evidence exists, but a required runtime or other release gate is still outstanding.
- RED — NOT READY: a required check failed or a real defect is present; fix before moving on.
- MISSING: the capability does not yet exist.

## Mandatory end-of-batch checklist

Before saying done or starting another area, answer:
- What exact commit was verified?
- What files actually changed?
- Did Android build/test/lint pass?
- Did backend/security checks pass when relevant?
- Did the integration journey pass from user action to backend/storage and back to UI?
- Did applicable authenticated/runtime testing pass?
- Did the APK-visible journey pass?
- Were nearby existing behaviors regression-checked?
- Is the exact APK artifact tied to the verified commit?
- If any answer is no, the work is NOT GREEN.

## Permanent discipline

- Source existence != functionality.
- Verifier success != product success.
- Old handoffs != current repository truth.
- A green unrelated workflow != a green affected workflow.
- Do not guess.
- Do not paper over failures.
- Fix root causes.
- Do not move goalposts after a failure.
- Do not reopen a GREEN system for theoretical concerns; reopen only for a real regression, failed test, changed dependency, or concrete integration issue.
- Finish the current RED/YELLOW workstream before starting another.
- Keep an evidence-based audit trail in commits and completion reports.

## Canonical references

This standard works together with:
- FYNX_BUILD_PLAN.md
- docs/FYNX_REALITY_AUDIT_CODEX_INSTRUCTIONS.md
- docs/FYNX_MARKETPLACE_MASTER_ARCHITECTURE.md

If a future ChatGPT session continues FYNX work, it must read these documents and inspect the live repository before making changes.

LOCKED: This file is the permanent FYNX release-gate contract. It must be followed automatically after every completed push without requiring the user to remind the assistant.

## AI blocker ownership and comparative product research

Every AI coding session must also follow the root `AGENTS.md` and `.github/copilot-instructions.md`.

### Own diagnosis and repair
When a required build, test, workflow, integration, or runtime check fails, the AI must investigate and own the repair rather than stopping at the first error or asking the user to solve routine engineering problems. Inspect the exact commit, job/step logs, source, callers, configuration, dependencies, and environment; determine root cause; consult official documentation or reputable technical sources where useful; implement the smallest safe correction; and rerun and monitor the relevant checks to completion. Fix follow-on failures exposed by the correction before declaring the work complete.

Do not make speculative repeated pushes, hide failures, weaken verifiers, bypass required tests, fabricate data, or disable security, authentication, or payment protections to obtain green CI. If a genuine external action is indispensable and only the user can perform it (for example, unavailable credentials, permissions, paid-service approval, or inaccessible hardware), first exhaust available investigation and complete independent safe work; then state the exact dependency and mark the affected gate NOT VERIFIED.

### Compare relevant user journeys against established platforms
When designing or auditing a social feature, research relevant established platforms and reliable sources, including official product/help/developer documentation where available. Compare real user goals, interaction flows, UI patterns, accessibility, performance, privacy, and safety. Separate verified facts from assumptions; account for regional/version differences; identify what FYNX already does, actual gaps, and worthwhile improvements. Do not copy competitors blindly or add needless complexity. Verify any resulting change in FYNX itself.

### Exact-commit APK and visual evidence
For every user-visible Android change, tie the APK/artifact to the exact commit, install/run it on a device or emulator when the environment permits, exercise the affected journey, and inspect genuine runtime screenshots/recordings for visual work. A mockup, preview, old APK, unrelated screenshot, source-code presence, or green compilation does not prove the current change works. If a required runtime check cannot run, record the precise reason and mark it NOT VERIFIED.

### No false completion
A commit being pushed is not completion. A green build is not by itself proof of correct behavior. Do not call work done while required checks are red, pending, skipped, or unverified. After every push report the exact branch/SHA, intended purpose, changed files, relevant CI/tests, actual journey tested, APK/artifact identity, runtime evidence, regression checks, blockers, and GREEN/YELLOW/RED/MISSING status. Do not proceed to dependent work or release until required gates pass.


## Substantial, APK-outcome-based pushes

The objective is a working product, not a high number of commits. Avoid a sequence of tiny pushes for pieces of one feature. Before starting, define a coherent delivery scope and combine related, safe work—UI and correct screen placement, interactions, state/data/backend or persistence wiring, relevant empty/loading/offline/error handling, and tests—into one substantial, reviewable push wherever practical. Do not combine unrelated work or make speculative edits just to increase the size of a push.

A separate small fix is justified when it is the minimal safe way to unblock a red required gate or prevent a regression; record why it is separate. Otherwise, finish the coherent feature batch before pushing. Afterward, verify the exact commit, complete diff, required CI, and the exact APK built from that commit. For UI work, inspect genuine runtime screenshots and exercise the relevant journey to confirm the feature appears in the intended position and works. A push, green CI, source presence, or artifact upload alone is not proof that the feature reached the APK.

Keep an evidence-based work ledger distinguishing: (1) implemented and runtime-verified, (2) pushed but not runtime-verified, (3) missing/not implemented, and (4) blocked by a specific external dependency. Re-investigate claims from old handoffs against the live repository and APK; do not repeat work without evidence of a real gap. Report each delivery's intended outcome, branch/SHA, changed files, CI results, exact APK identity, runtime/screenshots evidence, regression checks, remaining gaps, and status.
