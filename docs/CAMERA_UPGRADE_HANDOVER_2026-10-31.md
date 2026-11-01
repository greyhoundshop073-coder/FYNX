# FYNX Camera Upgrade — Investigation, Implementation Map & Handover
**Updated:** 2026-10-31  
**Repository:** greyhoundshop073-coder/FYNX  
**Working PR:** #403 — Stabilize camera/editor and certify Status + My Shop journeys  
**Working branch:** `fix/camera-contrast-and-expanded-runtime-cert-20261031`  
**Base:** `integration/fynx-single-apk-20261013`  
**Known PR head at investigation start:** `4f1522492ae935cefb964ec8f0864122602f78b5`

## 1. Non-negotiable scope: upgrade, do not rebuild
This is a focused upgrade of the existing FYNX camera and post journey. Keep the existing CameraX capture pipeline, existing effects and effect processing, photo/video editor, media storage/URI handling, camera permissions, Gallery picker, Retake/Rotate, video export, and existing feed-post integration. Do not add a second camera implementation or replace working capture/effect engines merely to change presentation.

Preserve unrelated app work. Do not modify backend, payments, database, authentication/login, Home feed behavior outside the camera entry/hand-off, Avatar/Profile, Chat/Groups, or Marketplace/My Shop feature logic. The only allowed Status/Stories integration is passing the already captured URI into the existing Status composer via its optional initial-media input; do not change Status editing, audience, upload or publishing behavior. Do not merge this PR or publish test content. Camera Send/Next tests must stop at an unpublished draft/details screen; never tap Post automatically.

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
- Establish a clear final preview step with a glass top media chip, vertical right-side tool rail (Looks, Face FX, Background, AI Studio and Rotate for photos; captions for video), compact selected-effect panel, and a Retake / Next footer.
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
- [x] Confirm live PR #403 head, base branch, changed files and current Actions runs before the implementation changes.
- [x] Add camera-scoped Dialog Window system-bar ownership, dark backdrop, white system icons and restoration of the previous Window state; runtime proof pending.
- [x] Replace near-opaque camera sheets/rails/backplates with translucent blue-grey mirror/glass tint and subtle light borders; runtime legibility proof pending.
- [x] Reuse the existing New post composer as the Next details step, with side-by-side media preview, description field, hashtag/mention insertion helpers, privacy entry and a keyboard-aware sticky Post action; runtime proof pending.
- [x] Preserve existing effect catalogue/processing; current PR already has twelve portrait preview assets shared by live creator and editor. Runtime still must confirm the visual quality on-device.
- [x] Extend runtime navigation to enter camera via More options → Add photo/video and verify the reference-style post-detail rows and local Drafts save/open/delete without tapping Post. Fresh emulator evidence is still pending.
- [ ] Re-run exact-head CI and obtain GREEN camera runtime evidence.
- [ ] Physical-device verification for front camera/flash and supported speech-caption path.
- [ ] Final handover with final commit, screenshots and one canonical APK artifact.

## 9. Current implementation state (2026-10-31)
- Camera live/editor: edge-to-edge Dialog backdrop, scoped Window state restoration, translucent blue-grey overlay surfaces, compact right-side tool rail, existing effect categories/thumbnails, and connected music chip. The captured-media footer now has a profile-photo **Your Story** action plus **Next**; Your Story passes the captured URI into the existing Status composer and Next preserves the original post callback.
- Post details: original post composer is reused. The current order is caption and attached-media preview with Preview/Edit cover overlays, Hashtags/Mention, Content disclosure and ads, Location, Add link, audience/privacy, More options, Share to; Drafts and Post are anchored in the bottom footer.
- Draft storage: private account-scoped metadata plus copied media files under app-private `filesDir/post_drafts`; list/open/update/delete actions are wired into the composer. This is local device storage, not cloud sync.
- Add link: only a validated HTTP(S) URL is appended to the caption as text; rich link metadata is not part of the existing publishing API.
- Share to: uses Android's native text chooser for the caption; it does not imply an in-app Status/Group target or share attached media.
- Content disclosure and custom cover: informational dialogs explain the existing API cannot persist paid-partnership labels or a custom cover. Do not present these as enabled publish features until model/API support is implemented.
- Music: live/editor chip opens the existing catalogue; More options retains a catalogue shortcut; selected track displays the actual catalogue preview player and can be removed. No fake tracks.
- Camera test flow now enters through More options → Add photo/video. The automated path checks upper details labels and the lower options, saves/reopens/deletes its local test draft and never presses Post.

### Failure ledger / prevention work
- Run 41223581929: source guard failed because Batch 5 Location still expected the old quick-chip after the row was moved into the reference-style list. The guard now checks the actual Location row, permission-aware callback and current-place resolver; the feature was not removed.
- Run 41223759196: Batch 7 Music guard failed because the selected catalogue preview and original Music shortcut had been removed from the composer’s old quick-action row. Restored the selected-track preview player under the caption helper row and a Music shortcut in More options; the live-camera music chip remains connected to the same catalogue.
- The last successful/failed runtime outcomes are not evidence for the newest commits. Fresh exact-head CI and emulator screenshots are required before marking the camera flow green.

## 10. Change log
- 2026-10-31: Captured-media preview now uses a vertical translucent right-side tool rail for photo categories and Rotate, a glass Photo/Video preview chip, a shorter scrollable effect panel and a two-action Retake / Next footer. The Stage 2 verifier asserts the rail controls and safe-area layout; runtime screenshots must still prove the result.\n- 2026-10-31: Meaningful camera UI push: system-bar presentation on the actual Compose camera Dialog Window; translucent camera/editor panels, right control rail, effects tray and self-timer surface; Retake/Rotate/Next hand-off; redesigned existing post-details composer with attached-media preview, caption helpers, audience entry and sticky Post action; runtime selectors updated for Next. Follow-up root-cause correction removes parent-level `safeDrawing` padding from the camera Dialog canvas so its dark backdrop draws behind the status bar; controls keep their individual safe-area insets. Added a regression guard against reintroducing parent padding. CI and genuine emulator proof remain required. The existing composer/API does not yet provide persistent Drafts storage, content-disclosure fields, external-link fields or share-target routing, so those reference controls were not added as nonfunctional UI.
- 2026-10-31: Initial investigation and handover map created from PR #403 changed-file list, camera source patches, workflow job logs and runtime report. Compared existing Status composer system-bar lifecycle as a camera fix lead.
- 2026-10-31: Camera reference-style details flow, local account-scoped draft store, caption URL action, native caption sharing, honest unsupported disclosure/cover states, camera music chip and runtime/static guards implemented on PR #403. Source-guard compatibility fixes were added after Batch 5 and Batch 7 checks exposed expectations tied to the previous quick-action row. Do not treat these as green until fresh exact-head Actions and runtime evidence complete.
- 2026-10-31: Added the reference's left **Your Story** CTA with the current profile image and wired it through the Home → existing StatusHubPanel → FynxMatureStatusComposerPanel initial-media path. The Status composer and Send/publish behavior are unchanged; runtime verification opens and closes the prefilled composer without pressing Send.

- 2026-10-31: Latest Android runtime failed in the Python visual-capture harness after the instrumentation suite completed 51 tests with 0 failures (1 skipped). Root cause: `capture_camera_post_journey()` could reach `return capture_camera_video_journey(home, prefix)` on the successful post-details branch without assigning the local `home` variable. Initialized a safe Home fallback and assign the actual recovered Home after dismissing the unpublished details page; added a static regression guard. Exact-head rerun: Android run 41233409190 (in progress at handover); Chat/Groups run 41233409144 (in progress); Backend run 41233409142 (in progress). Do not mark runtime green until the visual capture script completes and its report passes.


### 2026-11-01 corrective pass
- Replaced the camera's AndroidX Photo Picker launch with `OpenDocument` for `image/*` and `video/*`. The previous API-35 runtime captured an ANR inside Android's `PhotoPickerActivity`; this path avoids that specific provider activity while preserving the same local cache-copy and edit flow.
- Gallery cancellation now increments a camera rebind key, causing the existing CameraX binding effect to rebuild the live preview on return. Media-copy failures also request recovery rather than leaving a stale camera surface.
- Added an in-content status-bar scrim sized from `WindowInsets.statusBars`. Android 15 transparent edge-to-edge bars can ignore a configured `Window.statusBarColor`; the runtime screenshot must still confirm the resulting strip is dark.
- Updated Gallery runtime detection and source guards to accept DocumentsUI or Photo Picker fallback, while preserving same-Activity, live-preview, control-safe-area and no-publish assertions. The correction is not certified until the full Android runtime workflow passes on its exact head SHA.

 
### 2026-11-01 runtime failure follow-up
- The latest API 35 runtime confirmed Gallery cancellation, photo capture, Retake, the Status hand-off and Next-to-post details. The camera status bar still measured too bright because the full-screen Dialog was dimming the host Activity window behind the transparent system status bar. The camera now clears `FLAG_DIM_BEHIND` and sets a zero dim amount while open, then restores the Dialog's original dim state on disposal.
- The video recording action did start; the emulator screenshot showed an active recording timer and Stop icon, but the accessibility hierarchy initially retained the pre-recording shutter label. The runtime check previously treated that stale label as a failed start and pressed Back while recording was active, which correctly did not dismiss the camera and cascaded into Marketplace navigation failure. The check now accepts the visible recording timer as proof, stops the same shutter control even when its label has not refreshed, verifies the Retake/Next editor, and attempts to stop any active recording before recovery navigation.
- The shutter accessibility labels now distinguish normal Feed video (`Record video`) from video notes and use a generic `Stop recording` label while recording.
- Exact-head emulator certification is still required after these corrections; do not declare the camera/Marketplace journey green from source checks alone.


## 11. Grouped camera reliability correction — 2026-11-01
- Centered the live Add music chip to match the captured-media editor while reserving side clearance from the right camera rail.
- Reworked the self-timer around one cancellable coroutine job. The countdown now exposes a visible Cancel timer action; Back cancels an active countdown before dismissing the camera; disposal cancels pending timer work.
- Caption audio extraction now decodes the actual encoded audio track through Android MediaCodec and writes 16-bit PCM WAV for the on-device speech recognizer. This removes the old AAC-only rejection that failed against the emulator's AMR-NB camera recording. The user-facing timing note remains explicit: captions are chunk-timed estimates because the current whole-clip recognition response does not provide word timestamps.
- Added an instrumentation regression test which records AMR-NB audio and validates the decoded WAV header and PCM payload.
- Expanded runtime certification to check timer cancel/no-late-capture, timer completion, real post caption typing with the keyboard open, sticky footer reachability, caption persistence after keyboard dismissal, and to fail if the old unsupported-audio message returns.
- Still not claimed as covered until exact-head CI finishes: selecting an actual image/video in the Android system Gallery, exhaustive hashtag/mention/location/link/audience/share interaction, Android hardware camera/flash variants, and on-device speech model availability on a production phone.
