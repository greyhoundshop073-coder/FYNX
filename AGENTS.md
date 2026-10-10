# FYNX AI Development Rules

**Applies to every AI coding session and developer working in this repository.**

Before editing, read `docs/FYNX_RELEASE_GATE.md`, inspect the live branch/commit, relevant implementation, callers, tests, workflows, and previous changes. Never rely on a prior chat's completion claims without checking repository and runtime evidence.

## Investigate before changing
- Trace the full affected path: UI, navigation, state, API/backend, persistence/storage, and returned UI state.
- Identify the root cause and smallest safe complete fix. Reuse the existing architecture.
- Research official documentation, reputable technical sources, and relevant established implementations when needed.
- Compare relevant FYNX user journeys with established social platforms when designing or auditing social features. Separate verified facts from assumptions; do not copy blindly or add unnecessary complexity.
- Do not touch unrelated features or replace working systems without evidence and a clear reason.

## Own blockers through resolution
When a build, test, workflow, integration, or runtime journey fails, the AI owns the investigation and repair. Do not stop at the first error or ask the user to diagnose a routine engineering problem.
1. Inspect the exact failing commit, job, step, logs, source, configuration, dependency versions, callers, and test environment.
2. Determine the root cause; research reliable documentation if useful.
3. Make the smallest safe correction and assess effects on shared code, data, APIs, security, authentication, payments, and existing features.
4. Run the relevant checks, inspect the results, and repeat diagnosis/correction until required checks pass.
5. Monitor required GitHub Actions runs to completion. Do not call pending, skipped, unrelated, or red checks green.

Never bypass a required test, weaken a verifier, suppress an error, fabricate data, or disable security/payment protections just to obtain a green result. If progress genuinely requires an external action only the user can take (such as unavailable credentials, permission, paid service approval, or inaccessible hardware), first exhaust available investigation and safely complete independent work; then state the precise external dependency without pretending it is resolved.

## Every push must prove its purpose
Before pushing, know the intended user-visible result, files expected to change, behavior to preserve, and checks that will prove success. After pushing:
- Inspect the exact commit SHA and complete diff; confirm no unintended files changed.
- Run relevant build, unit, lint, integration, backend/security and workflow checks.
- Diagnose and fix required failures before moving on. Do not make speculative repeated pushes.
- Test the actual user journey, including applicable loading, success, empty, offline/error, retry, back/return, lifecycle, keyboard/insets, and touch/z-order behavior.
- Check adjacent existing functionality for regressions.

## APK and visual proof are mandatory for user-visible Android work
- Tie the APK/artifact to the exact commit under verification.
- Install/run that APK on a device or emulator when the required runtime environment is available.
- Exercise the affected screen and interaction; capture and inspect genuine runtime screenshots/recordings when visual behavior is involved.
- Old APKs, mockups, previews, source-code presence, screenshots from unrelated runs, or static checks do not prove the current feature works.
- If a required runtime check cannot run, mark it NOT VERIFIED and name the specific blocker. Never claim the feature is fully verified.

For reference-image work, compare the actual running screen with the reference: hierarchy, section order, alignment, sizing, spacing, typography, scrolling, touch targets, clipping, empty/offline states, and working controls. Verify that the intended change appears in the tested APK.

## Status and reporting
- **GREEN — VERIFIED:** Required implementation, relevant CI/integration, and applicable APK/runtime checks passed on the exact commit.
- **YELLOW — PARTIALLY VERIFIED:** Evidence exists but one or more required checks remain unverified.
- **RED — FAILED / NOT READY:** A required check failed or a defect/regression exists.
- **MISSING:** Required capability is not implemented.

A commit or green build alone does not mean the feature is done. Never claim “fixed,” “complete,” “all in the APK,” or “release-ready” without supporting evidence. Every push handoff must state: purpose; branch and SHA; changed files; CI/test status; user journey tested; APK/artifact identity; runtime evidence; regression checks; blockers/remaining work; final status.

Do not move to the next dependent task while the current required release gate is red or unverified. Do not merge/release until the repository release gate passes. The goal is not more pushes; every push must be meaningful, traceable, tested, and visible to the user.
