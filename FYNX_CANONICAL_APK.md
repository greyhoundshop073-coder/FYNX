# FYNX Canonical APK — Cross-Chat Integration Contract

**Purpose:** Every FYNX chat/workstream must integrate compatible finished work into one shared Android release candidate instead of leaving useful changes stranded on separate feature branches.

## Canonical target

- **Canonical integration branch (current):** `integration/fynx-single-apk-20261013`
- **Canonical APK role/name:** `FYNX-CANONICAL-DEBUG-APK`
- **Actual immutable artifact name:** `FYNX-debug-apk-<FULL_40_CHARACTER_COMMIT_SHA>`
- **Repository:** `greyhoundshop073-coder/FYNX`
- **Permanent release gate:** `docs/FYNX_RELEASE_GATE.md`

The role name identifies *which APK is the shared candidate*; it is not a claim that every APK with that label is the same binary. Every candidate must be tied to its exact full commit SHA, GitHub Actions run, artifact ID/link, and SHA-256 checksum.

## Rules for every chat/workstream

1. Read this file and `docs/FYNX_RELEASE_GATE.md` before changing or handing off FYNX work.
2. Work on a focused feature branch. Do not push unrelated changes straight to the canonical integration branch.
3. When the work is ready, compare it against the current canonical branch and open a pull request **targeting `integration/fynx-single-apk-20261013`**, not `main`. The PR must list the feature, commits, changed files, tests, backend dependencies, and any runtime proof still missing.
4. Before integration, check whether the changes are already present, duplicate existing behavior, conflict with other branches, or depend on another unfinished change. Preserve compatible work; do not blindly cherry-pick a whole branch.
5. After integration, build a fresh candidate from the resulting integration commit. Never claim a feature is in the canonical APK just because it exists on its feature branch or its separate APK passed CI.
6. The candidate is **GREEN / release-ready** only after the applicable CI checks, real runtime journey, and APK-visible behavior pass on the exact artifact. If a required check is unavailable, mark it **YELLOW / partially verified** and say precisely what is blocked. A build-only APK is not certified.
7. For every candidate, record:
   - Full source commit SHA
   - Workflow run URL and result
   - Artifact name, artifact ID, and download URL
   - SHA-256 checksum
   - Build/lint/unit/instrumentation results
   - Actual device/runtime journeys verified and any gaps
8. Do not delete or overwrite older artifacts used as evidence. Keep the last known-good candidate identifiable while testing a newer one.
9. Do not merge to `main`, deploy production, or change unrelated work without the owner's explicit authorization.

## Current status — do not misread this as a release approval

The October 13 integrated commit `e1238d8a5cf65843698dbfb8fdbc7e7c88ffcd7d` has stronger recorded Android runtime-test evidence among the candidates previously audited, but it predates the October 15 login work. The October 15 APK from `b73fae60f0d2123591ccf2a6e766b0eb8141a08f` is newer but its full runtime checks were skipped; a real login attempt returned HTTP 401. Therefore **neither artifact is declared the final canonical release APK**. The branch above is the shared integration target; the release-ready APK must be freshly built from the integrated commit after the release gate passes.

## Login/backend work: preserve progress and verify the exact artifact

The login/backend work is tracked in [PR #170](https://github.com/greyhoundshop073-coder/FYNX/pull/170). Do not discard or reimplement it. Reconcile its latest source commits and backend deployment against the canonical integration candidate, then build and test an APK that actually contains those commits. CI success and backend health are useful evidence but are not proof of successful on-device authentication. Preserve the owner's approved test/offline login contract; do not change authentication/session behavior beyond the explicitly authorized login scope.

## Required handoff line

Every chat ending a FYNX batch must report:

`Feature/branch → PR into canonical integration branch → integrated commit → APK artifact/run → CI status → real runtime status → remaining blockers`

If the feature is not integrated into the canonical branch and present in the exact candidate artifact, it must be reported as **not yet in the canonical APK**.
