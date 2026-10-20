# FYNX shared APK release gate

## Source of truth

`integration/fynx-single-apk-20261013` is the canonical integration branch and the only release line for the FYNX Android APK. Feature branches are for development and CI only; they must not be presented as the shared release APK.

## Required release checks

1. Merge completed work from all feature chats into the canonical integration branch.
2. Build the debug APK from the latest canonical commit, not a feature-branch head or an older artifact.
3. Confirm the successful workflow run's checked-out commit SHA matches the canonical commit being released (or a newer canonical commit with all the same merged work).
4. Confirm the artifact name includes that exact commit SHA and the APK upload step succeeded.
5. Report source SHA, workflow run, artifact link and build/check status together. Never call an APK the latest shared APK without this evidence.
6. Keep this branch open for future work; every subsequent shared APK supersedes the prior artifact and contains all changes merged before its source commit.

## Push 30 checkpoint (2026-10-20)

Marketplace product-detail hierarchy changes were merged by PR #294 at merge commit `27958aa48d3f26f40de20b515f313c05ebef622d`. The known green Android CI artifact was built from feature commit `b2d9a2ca21a70f0985b1583a5b691e9430a909a6`, so it is not certified as the shared integration APK. A canonical-branch build must pass before the next artifact is announced as the shared APK.

## APK workflow

`.github/workflows/android-build.yml` runs on pushes that touch `docs/**` and uploads an exact-commit debug APK named `FYNX-debug-apk-<commit-sha>`. This release-gate document intentionally triggers a canonical push build so the resulting artifact can be checked against the actual integrated branch head.
