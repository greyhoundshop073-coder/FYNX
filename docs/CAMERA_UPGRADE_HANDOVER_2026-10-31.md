# FYNX Camera Upgrade — Investigation, Implementation Map & Handover
**Updated:** 2026-10-31  
**Repository:** greyhoundshop073-coder/FYNX  
**Working PR:** #403 — Stabilize camera/editor and certify Status + My Shop journeys  
**Working branch:** `fix/camera-contrast-and-expanded-runtime-cert-20261031`  
**Base:** `integration/fynx-single-apk-20261013`  
**Known PR head at investigation start:** `4f1522492ae935cefb964ec8f0864122602f78b5`

## 1. Non-negotiable scope: upgrade, do not rebuild
This is a focused upgrade of the existing FYNX camera and post journey. Keep the existing CameraX capture pipeline, existing effects and effect processing, photo/video editor, media storage/URI handling, camera permissions, Gallery picker, Retake/Rotate, video export, and existing feed-post integration. Do not add a second camera implementation or replace working capture/effect engines merely to change presentation.

Preserve unrelated app work. Do not modify backend, payments, database, authentication/login, Home feed behavior outside the camera entry/hand-off, Avatar/Profile, Chat/Groups, Status/Stories, or Marketplace/My Shop feature logic unless a separately evidenced camera integration defect requires a narrowly scoped change. Do not merge this PR or publish test content. Camera Send/Next tests must stop at an unpublished draft/details screen; never tap Post automatically.

## 2. User-approved target experience
The user supplied two reference screenshots. Match the interaction pattern, not a pixel-for-pixel copy of another brand:
1. **Capture / edit preview screen:** large live camera or captured-media preview; clean right-side editing tool rail; music chip when selected; capture/edit controls; bottom sharing/Next actions. All overlay cards, chips, trays and control backplates that cover the image should use a genuine translucent/frosted-glass treatment (background blur where supported, restrained tint, subtle border/highlight and sufficient contrast). Do not use opaque near-black rectangles as the default treatment. Text and icons must remain legible over bright and dark camera scenes.
2. **Next → post details screen:** a separate, clearly navigated details step matching the second reference's information architecture: caption/description, media preview and Edit cover, Hashtags, Mention, Content disclosure and ads, Location, Add link, audience/privacy, More options, Share to, with Drafts and Post actions anchored at the bottom. Keep keyboard, scrolling, system bars and bottom navigation/safe areas correct.
3. **Feed completion:** only after a real user taps Post should the normal existing publish path run. Runtime automation must never publish a test post.
4. Keep FYNX brand styling and existing features. TikTok-like organization is a layout/interaction reference, not permission to copy protected branding/assets.

## 3. Verified starting point and known evidence
- PR #403 is open and unmerged. Its purpose and scope are camera/editor stability plus Status and My Shop runtime certification.
- PR #403 already changes the existing `FynxCameraCapturePanel.kt`, `FynxHomeSocialHubPanel.kt`, `CameraVideoEffects.kt`, camera preview drawable assets and runtime verification scripts.
- Existing capture path includes photo/video capture, effect tray, photo Retake/Rotate/Send, video editing/export, and photo Send into the existing unpublished New post draft.
- The current UI uses CameraX `PreviewView`; current PR changes it to `ImplementationMode.PERFORMANCE`. It also adds fallback when a requested lens is unavailable.
- The camera dialog previously used a dark outer Surface, but that alone did not darken the top system-bar strip in the emulator screenshots. This push now resolves the actual Compose `DialogWindowProvider` Window, applies a dark decor/status/navigation backdrop and white system-icon appearance, disables contrast enforcement where applicable, and restores prior window state on disposal. Runtime screenshots must still prove this correction.
- A read-only comparison found that the existing `FynxMatureStatusComposerPanel.kt` already has a scoped `DisposableEffect` that saves/restores system-bar state, disables contrast enforcement, sets dark system-icon appearance and keeps edge-to-edge enabled. The camera dialog/panel does not show an equivalent camera-scoped Window treatment in the inspected source. This is a strong implementation lead, not yet a proven root cause: camera uses a Compose Dialog, so the correct Dialog Window (not blindly the Activity Window) must be identified and restored safely. Reuse the established save/restore principles, then verify the actual window with runtime screenshots.
- Workflow run **41206823912** completed with the overall runtime job failed. Its Android build/unit/lint/instrumentation-related build steps and APK upload succeeded; the runtime report itself was RED.
- Runtime report recorded three repeated failures: front camera, rear camera and Gallery-return camera all measured status-bar mean luminance 98.9 with dark ratio 0.00, making the backdrop too light for white system icons. This is the immediate known camera certification blocker. Do not claim the precise Window/insets root cause until source + runtime tracing confirms it.
- Camera-to-post journey source check passed. The runtime artifact showed photo capture → editor → Send → unpublished New post caption draft; the test did not tap Post.
- Status creator runtime report was GREEN in that run. Marketplace/My Shop had real-data/environment blockers (no visible priced live listing; seller orders and driver deliveries showed recoverable connection/approval states). Those are not camera UI defects and must not be “fixed” with fake records.
- The run also reported emulator limitations: no front-facing emulator camera available, no flash capability, and on-device speech caption generation unsupported for that test media. Treat those as environment limits, not verified production-device failures.
- Run job 124553489191 had successful fast/full build/test/lint steps and the APK upload, but its emulator visual-certification step failed on the three system-bar contrast assertions. Keep these outcomes separate.

## 4. Investigation before implementation
Before changing UI code, inspect the current branch (not a stale branch or default-branch copy) and document:
- Camera entry and lifecycle: Home camera action → dialog/surface → CameraX bind/unbind → lens switching → permission and failure states → dismiss/Back.
- Capture lifecycle: photo/video capture → pending URI/type → editor → Retake/Rotate/effects/export → Send callback → existing New post state.
- Every overlay/backplate/chip/tray/button in the camera and editor, including all places using opaque black/near-black backgrounds. Classify which should become glass, which must remain solid for accessibility, and which are full-screen surfaces.
- The actual system-bar owner and edge-to-edge/insets configuration on camera entry, Gallery hand-off/return and exit. Trace Window status/navigation bar colors, light/dark icon appearance, decorFitsSystemWindows, Compose dialog window flags, safeDrawing/inset padding and any activity-level side effects. The previous Surface color change is proven insufficient.
- Existing New post screen state/model and route. Determine whether it already contains the caption/details controls shown in the reference; reuse and extend it rather than creating a duplicate post composer.
- Existing camera effects catalogue and thumbnail asset coverage. Preserve effect IDs, ordering/state and processors; only improve grouping, thumbnail variety and presentation where required.
- Existing instrumentation selectors and screenshot capture points. Make automation state-aware, wait for UI readiness and capture evidence at each transition rather than depending on fragile fixed sleeps/coordinates.
- Compare other camera branches only as read-only references. Do not merge/cherry-pick unreviewed branches or overwrite work from another chat.

## 5. Staged implementation map
### Stage 0 — Baseline and ownership audit
- Confirm current PR head, base SHA, changed files, workflow status and latest artifact.
- Trace all camera entry/capture/editor/post callback callers.
- Build a transition/state diagram and identify existing reusable components.
- Record baseline screenshots for camera idle, effects tray, photo editor, video editor and New post details.
- No unrelated source changes.

### Stage 1 — Glass/mirror overlay system
- Create/reuse one small camera-scoped glass surface/style helper rather than hand-tuning each control differently.
- Use platform blur where reliably supported; provide a translucent tint/border fallback on devices where blur is unavailable or expensive.
- Apply consistently to right-rail control backplates, music chip, tool trays, AutoCut/effect chips, and floating controls over media.
- Avoid dark opaque cards unless the control genuinely needs a solid accessible surface.
- Check legibility against both bright and dark preview content and on the user's Android device class.
- Do not add blur to the full camera preview or cause a second camera surface.

### Stage 2 — Capture preview → Next transition
- Keep the current photo/video capture and editing processors.
- Establish a clear final preview step with the reference's hierarchy and a prominent Next action.
- Preserve selected music/effects/caption/cover metadata through navigation.
- Back returns to the editor/preview without losing media; retake returns to camera; cancellation releases temporary media safely.
- Verify no duplicate camera binding or stale URI is created.

### Stage 3 — Post details page
- Reuse the existing New post model/route and controls wherever possible.
- Present caption, media preview/Edit cover, hashtags, mentions, disclosure, location, link, audience, more options and Share to in a tidy scrollable layout.
- Anchor Drafts and Post at the bottom while respecting IME and system navigation insets.
- Draft saves only when explicitly selected; Post invokes the existing publish flow only after explicit user action.
- Do not invent new backend fields. If an option is not supported by the existing model/API, disclose that and avoid a decorative nonfunctional control.

### Stage 4 — Effects and thumbnail presentation audit
- Audit every existing effect category, effect ID, preview source and processor.
- Improve variety and relevance of thumbnails where the catalogue is currently too small; keep thumbnail-to-effect mapping truthful.
- Arrange effects in compact, discoverable groups and horizontally scrollable rows/sheets where appropriate.
- Keep camera effects responsive: avoid synchronous heavy work on the main thread; verify cancellation, loading, error and retry states.
- No removal or silent replacement of existing effects.

### Stage 5 — Runtime certification and regression
Capture genuine emulator/device screenshots and UI hierarchy for:
1. Camera open/idle.
2. Each camera tool rail/tray and music chip.
3. Photo capture → edit → glass overlays → Retake and Rotate.
4. Photo → Next → complete post details screen with attached media.
5. Back navigation preserving draft media and metadata.
6. Video capture → effects/style/export → Next → post details.
7. Gallery open → return to same live camera session.
8. Camera dismiss → Home with no stale camera binding.
9. Bright and dark background contrast, system bars, keyboard and navigation safe areas.
10. A deliberate manual publish is out of scope for automated certification; never tap Post in CI.
- Re-run fast build, full build, unit tests, lint, instrumentation and all required PR checks against the exact same head SHA.
- Overall certification is GREEN only when every required camera assertion passes. APK build success alone is not runtime certification.
- Record any emulator-only gaps separately and arrange physical-device verification for front camera/flash/speech features as needed.

## 6. Prevent the previous delay from recurring
- One canonical working branch/PR for this camera upgrade. Do not split UI implementation across competing camera branches.
- One stage per meaningful push; every push must include its relevant source guard/tests and a short changelog in this document.
- Before each push: inspect the exact diff and confirm only camera/editor/post-flow files plus relevant tests/docs are changed.
- After each push: wait for Actions; inspect the failed step/log/artifact; fix the root cause before starting the next stage. Do not stack unverified UI changes on a red runtime.
- Use deterministic UI readiness conditions and screenshots. Avoid fixed-coordinate tapping when UI hierarchy or state-based selectors are available.
- Maintain a failure ledger with exact run ID, commit SHA, failing assertion, root cause, fix commit and rerun result.
- Never claim green based only on static source checks, successful APK assembly, or instrumentation test count if the final visual certification report is red.
- Keep PR unmerged and do not announce a release APK until required checks are green on the exact same PR head.
- Always report branch, PR, commit SHA, run ID, artifact name/link, check status, screenshot evidence and remaining blockers.

## 7. Ownership boundaries / handover rules
- This document is the camera upgrade source of truth for any chat or developer taking over.
- Read this document and inspect the current PR head before editing.
- Do not revert or overwrite commits from another chat. Ask/coordinate before touching shared navigation, backend, authentication, Home feed, Profile/Avatar, Chat/Groups, Status/Stories or Marketplace/My Shop.
- Camera-specific callback wiring into the existing post composer is allowed; changing the broader feed publishing architecture is not.
- Keep all test media local and unpublished. Delete temporary files only after confirming no retained draft depends on them.

## 8. Current checklist
- [x] User-approved scope: incremental upgrade, not rebuild.
- [x] Reference target documented: glass preview controls + separate Next/details page.
- [x] Existing camera-to-unpublished-draft path identified.
- [x] Latest known runtime failure narrowed to three camera system-bar contrast assertions.
- [ ] Confirm current live PR head and latest required-check state before the next source edit.
- [x] Add camera-scoped Dialog Window system-bar ownership, dark backdrop, white system icons and restoration of the previous Window state; runtime proof pending.
- [x] Replace near-opaque camera sheets/rails/backplates with translucent blue-grey mirror/glass tint and subtle light borders; runtime legibility proof pending.
- [x] Reuse the existing New post composer as the Next details step, with side-by-side media preview, description field, hashtag/mention insertion helpers, privacy entry and a keyboard-aware sticky Post action; runtime proof pending.
- [ ] Audit effect categories and thumbnail coverage/performance.
- [ ] Add robust end-to-end state-aware runtime assertions and screenshots.
- [ ] Re-run exact-head CI and obtain GREEN camera runtime evidence.
- [ ] Physical-device verification for front camera/flash and supported speech-caption path.
- [ ] Final handover with final commit, screenshots and one canonical APK artifact.

## 9. Change log
- 2026-10-31: Meaningful camera UI push: system-bar presentation on the actual Compose camera Dialog Window; translucent camera/editor panels, right control rail, effects tray and self-timer surface; Retake/Rotate/Next hand-off; redesigned existing post-details composer with attached-media preview, caption helpers, audience entry and sticky Post action; runtime selectors updated for Next. CI and genuine emulator proof remain required. The existing composer/API does not yet provide persistent Drafts storage, content-disclosure fields, external-link fields or share-target routing, so those reference controls were not added as nonfunctional UI.
- 2026-10-31: Initial investigation and handover map created from PR #403 changed-file list, camera source patches, workflow job logs and runtime report. Compared existing Status composer system-bar lifecycle as a camera fix lead. No camera source implementation changes are claimed by this documentation update.
