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