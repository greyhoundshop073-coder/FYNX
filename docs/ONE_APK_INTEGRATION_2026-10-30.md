# FYNX single-APK integration record — 2026-10-30

## Canonical source

All Android work for this release belongs in `integration/fynx-single-apk-20261013`.
The feature changes below are merged into that one source branch; they are not separate APK projects.

## Feature integrations

- **Status creator and Stories — PR #399**, squash merge `06801d22f4e2537914913b30bae790ac635dfc7e`. Includes the Home Status creation route, immersive creator, photo/video/gallery/text/voice/music/layout options, Music Status catalogue handling, owner/story navigation, and Status video duration/size safeguards.
- **Camera controls and effects browsing — PR #398**, squash merge `0d17b173f9ae746a8c72d7ac908e9b8ca2b70787`. Includes the right-side camera quick-tools rail, Effects shortcut, horizontally scrollable effect options, and the revised shutter/gallery layout.
- Existing canonical work remains in the same app source: Marketplace/My Shop presentation and seller-image upgrades (PRs #390, #391, #393, #396), Home AI card finish (PR #392), and the earlier camera effects layout foundation (PR #394).

## Preserved requirements

- Keep the debug/local testing login independent of live backend availability. Do not add OTP or require backend health merely to open the APK's local UI.
- Keep Marketplace, Status, camera, Home, and the other modules inside the same canonical Android application.
- Do not change Chat/Groups or Home Avatar/Profile as a side effect of these feature integrations.

## Verification evidence before merge

**PR #399 head `3a154dffddb1d70397a8cf9699a68602f6bafda4`:**
- Android Build run #9146 passed the configured fast build, unit-test, lint and APK steps; the APK was checked against that exact commit.
- Chat/Groups parity run #2252 passed.
- Backend CI run #4198 passed.
- The PR run skipped the longer emulator/runtime stage. Do not describe it as runtime-certified.

**PR #398 head `9a4ecbf9ccf24b41ffca2aed457c1dc12a5fbbd7`:**
- Android Build run #9130 passed.
- Chat/Groups parity run #2244 passed.
- Backend CI run #4184 passed.
- The run produced a debug APK for that PR head, not for the combined canonical merge.

## Remaining release gate

A fresh build and runtime verification must be attached to the final combined canonical source before that APK is described as the release candidate. The backend reachability probe is separate and was not required for these offline-capable UI/build checks.

The older Camera Stages 1–5 draft (PR #389) is deliberately not included here: it is significantly behind the canonical branch and its previous emulator run did not complete the video-recording journey. Runtime evidence showed the camera visually entering recording while the accessibility tree still exposed the old “Record video note” label instead of “Stop video note”; this requires repair and revalidation before those additional effects are integrated.
