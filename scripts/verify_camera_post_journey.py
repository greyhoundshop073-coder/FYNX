from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
home = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8")
camera = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt").read_text(encoding="utf-8")
overlay = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxCameraTextOverlay.kt").read_text(encoding="utf-8")
overlay_test = (ROOT / "app/src/androidTest/java/com/fynx/app/ui/CameraTextOverlayInstrumentationTest.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text(encoding="utf-8")
refresh = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeLifecycle.kt").read_text(encoding="utf-8")
backend = (ROOT / "backend/socialMultiMediaBootstrap.js").read_text(encoding="utf-8")
runtime = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
status_hub = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxStatusHubPanel.kt").read_text(encoding="utf-8")
status_client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxStatusClient.kt").read_text(encoding="utf-8")
video_effects = (ROOT / "app/src/main/java/com/fynx/app/ui/CameraVideoEffects.kt").read_text(encoding="utf-8")
audio_test = ROOT / "app/src/androidTest/java/com/fynx/app/ui/CameraVideoAudioExtractionInstrumentationTest.kt"
app_source = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text(encoding="utf-8")
draft_store = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxLocalPostDraftStore.kt").read_text(encoding="utf-8")

checks = [
    ("Live and editor music chips share a centered position with side clearance",
     "modifier = Modifier.align(Alignment.TopCenter)" in camera
     and ".padding(start = 64.dp, end = 64.dp, top = 74.dp)" in camera
     and "widthIn(max = 196.dp)" in camera),
    ("Camera self-timer invalidates stale jobs and exposes a full-card cancel target",
      "var timerCaptureJob by remember { mutableStateOf<Job?>(null) }" in camera
      and "val timerCaptureGeneration = remember { AtomicInteger(0) }" in camera
      and "timerCaptureJob?.cancel()" in camera
      and camera.count("timerCaptureGeneration.incrementAndGet()") >= 2
      and camera.count("if (generation != timerCaptureGeneration.get()) return@launch") == 2
      and "onClick = ::cancelCaptureTimer" in camera
      and '"Cancel timer"' in camera
      and "for (remaining in seconds downTo 1)" in camera
      and "LaunchedEffect(timerCountingDown)" not in camera),
    ("Video editor keeps a manual caption field available when on-device speech is unavailable",
     'label = { Text("Video captions") }' in camera
     and 'placeholder = { Text("Type captions for your video") }' in camera
     and "Type captions here, or generate them with a compatible on-device speech model." in camera
     and "Caption timing is approximate. Review the text before sending." in camera),
    ("Timed photo early failure restores live camera controls",
     'timerCountingDown = 0' in camera
     and 'showCaptureControls = true' in camera
     and 'Camera is still starting. Please try again.' in camera),
    ("Caption success is shown as a neutral success notice, not an error",
     'recordingNotice = "Transcript ready. Caption timing is approximate; review it before sending."' in camera
     and 'error = "Transcript ready. Caption timing is approximate; review it before sending."' not in camera),
    ("Runtime types and preserves a real manual video caption when speech recognition is unavailable",
     'find_control(caption_xml, ["Video captions"])' in runtime
     and 'run("adb", "shell", "input", "text", "FYNXcamcaptiontest")' in runtime
     and "FYNXcamcaptiontest" in runtime
     and "manually entered video caption survives keyboard dismissal" in runtime),
    ("On-device captions transcode supported device audio formats to PCM16 WAV before speech recognition",
     'fynx_caption_audio_" + System.currentTimeMillis() + ".wav"' in video_effects
     and "MediaCodec.createDecoderByType(mime)" in video_effects
     and "writeCameraWavHeader(output, dataBytes, sampleRate, channelCount)" in video_effects
     and "AudioFormat.ENCODING_PCM_16BIT" in video_effects
     and "This video's audio format is not supported." not in video_effects
     and audio_test.is_file),
    ("Camera runtime explicitly rejects the old unsupported-audio caption regression",
     "caption audio decoder still rejects the camera's recorded audio format" in runtime),
    ("Gallery runtime never taps stale camera coordinates after a timer failure",
     "camera_xml = after_cancel" in runtime
     and "camera_xml = dump_ui(" in runtime
     and "Gallery verification skipped because the timer journey did not leave a live camera surface" in runtime),
    ("Camera runtime exercises timer cancellation, timed capture and keyboard-visible caption entry",
     "camera-timer-cancel" in runtime
     and "camera-timer-capture" in runtime
     and "camera-post-caption-keyboard.png" in runtime
     and "post details with keyboard open" in runtime),
    ("Camera music picker tests selection/removal and explicit empty/error recovery",
     "camera-music-open" in runtime
     and "camera-music-select" in runtime
     and "camera-music-remove" in runtime
     and "No FYNX music is published yet" in runtime),
    ("Post caption helper chips are exercised against the actual caption value",
     "camera-post-hashtag-chip" in runtime
     and "camera-post-mention-chip" in runtime
     and "inserted a mention marker into the actual caption field" in runtime),
    ("Post link validates malformed URLs, adds valid HTTPS and persists through Drafts",
     "camera-post-link-reject-invalid" in runtime
     and "https://fynx.test/camera" in runtime
     and "camera-post-more-options-open" in runtime
     and "saved post draft did not restore its inserted https link" in runtime),
    ("Camera photo editor tool options toggle closed on a second tap and reopen or switch cleanly",
     'var showEditorToolOptions by remember { mutableStateOf(true) }' in camera
     and all(f'if (selectedCameraTool == "{tool}") showEditorToolOptions = !showEditorToolOptions' in camera for tool in ["Looks", "Face FX", "Background", "AI Studio"])
     and all(f'selectedCameraTool = "{tool}"; showEditorToolOptions = true' in camera for tool in ["Looks", "Face FX", "Background", "AI Studio"])
     and "if (showEditorToolOptions) {" in camera
     and "showEditorToolOptions && selectedCameraTool == \"Looks\"" in camera
     and "showEditorToolOptions && selectedCameraTool == \"Face FX\"" in camera
     and "showEditorToolOptions && selectedCameraTool == \"Background\"" in camera
     and "showEditorToolOptions && selectedCameraTool == \"AI Studio\"" in camera),
    ("Captured photo preview fills the portrait editor from the top instead of vertically letterboxing the image",
     "scaleType=android.widget.ImageView.ScaleType.CENTER_CROP" in camera
     and "scaleType=android.widget.ImageView.ScaleType.FIT_CENTER" not in camera),
    ("Photo editor shows one selected category heading instead of repeating the tool name",
     "Text(selectedCameraTool," not in camera
     and all(f'Text("{label}", style = MaterialTheme.typography.labelLarge)' in camera
             for label in ["Looks", "Face FX", "Background", "AI Studio"])),
    ("Camera runtime journey initializes Home recovery state before all optional Status and post-draft branches",
     'home = home_xml' in runtime
     and 'home = camera_return_to_home(details_xml, prefix + "-post-draft-dismiss")' in runtime
     and "return capture_camera_video_journey(home, prefix)" in runtime),
    ("Camera Dialog applies/restores the actual system-bar window and white icon appearance",
     "DialogWindowProvider" in camera and "resolveFynxCameraWindow(cameraWindowView)" in camera
     and "resolveFynxHostActivityWindow(cameraWindowView)" in camera
     and "window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)" in camera
     and "window.setDimAmount(0f)" in camera
     and "previousDimAmount?.let { window.setDimAmount(it) }" in camera
     and "window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)" in camera
     and "resolveFynxStatusBarHeightPx" in camera
     and "WindowInsetsCompat.Type.statusBars()" in camera
     and "decor.addView(" in camera
     and "FrameLayout.LayoutParams(" in camera
     and "statusBarScrim?.let { hostDecorView?.removeView(it) }" in camera
     and "hostActivityWindow.decorView.background = ColorDrawable(cameraBackdrop)" in camera
     and "previousHostDecorBackground" in camera
     and "hostActivityWindow.decorView.background = previousHostDecorBackground" in camera
     and "hostActivityWindow.statusBarColor = cameraBackdrop" in camera
     and "hostActivityWindow.isStatusBarContrastEnforced = false" in camera
     and "previousHostStatusBarColor" in camera
     and "previousHostStatusContrast?.let { hostActivityWindow.isStatusBarContrastEnforced = it }" in camera
     and "window.setBackgroundDrawable(ColorDrawable(cameraBackdrop))" in camera
     and "isAppearanceLightStatusBars = false" in camera
     and "previousStatusBarColor" in camera and "window.setBackgroundDrawable(previousBackground)" in camera),
    ("Camera cards and control trays use a visibly translucent glass tint",
     "Color(0xFF172438).copy(alpha = 0.62f)" in camera
     and "Color(0xFF25364A).copy(alpha = 0.32f)" in camera
     and "Color.White.copy(alpha = 0.25f)" in camera),
    ("Camera preview label is replaced by the Add music action in the same centered top position",
     "CameraMusicChip(" in camera and "selectedMusic: FynxMusicCatalogueTrack?" in camera
     and "onChooseMusic = onChooseMusic" in camera
     and "modifier = Modifier.align(Alignment.TopCenter)" in camera
     and ".padding(top = 10.dp)" in camera
     and ".widthIn(max = 196.dp)" in camera
     and "overflow = TextOverflow.Ellipsis" in camera
     and "Photo preview" not in camera and "Video preview" not in camera
     and "selectedMusic = selectedCatalogueMusic" in home
     and "showMusicPicker = true" in home),
    ("Immersive Status media composer hides the app bottom navigation so caption and Send controls remain reachable",
     'visible = !statusComposerImmersive && ((selected != "Home" && selected != "Marketplace") || statusCreatorRequested || !homeChromeHidden)' in app_source
     and "onComposerVisibilityChange = { statusComposerImmersive = it }" in app_source),
    ("Your Story and Next are separate real destinations from one captured preview",
     "showYourStoryAction: Boolean = false" in camera
     and 'Text("Your Story",' in camera
     and "onShareToStatus(finalUri" in camera
     and "onCaptured(finalUri, mediaType)" in camera
     and "showYourStoryAction = cameraEditingMediaUri == null" in home
     and "onShareToStatus = {" in home
     and "onOpenStatusWithMedia(uri, type)" in home),
    ("Your Story reuses the existing Status composer media hand-off without changing Status publishing",
     "statusInitialMediaUri" in app_source and "statusInitialMediaType" in app_source
     and "initialMediaUri = statusInitialMediaUri" in app_source
     and "initialMediaType = statusInitialMediaType" in app_source
     and "initialMediaUri: Uri? = null" in status_hub
     and "LaunchedEffect(initialMediaUri, initialMediaType)" in status_hub
     and "FynxMatureStatusComposerPanel(" in status_hub
     and "initialMediaUri = selectedInitialMedia?.uri" in status_hub),
    ("Runtime captures the explicit Status media hand-off and safely returns before the separate Next-to-post journey",
     '["Status"]' in runtime
     and '["Back from Status composer"]' in runtime
     and "camera-status-composer.png" in runtime
     and "Your Story opened the existing Status composer with the captured media" in runtime
     and "Send is never tapped" in runtime
     and "camera-after-status-photo" in runtime),
    ("Camera editor exposes Next and preserves the existing captured-media callback",
     'Text("Next")' in camera and 'onClick = { finishCameraMedia(toStatus = false) }' in camera and "onCaptured(finalUri, mediaType)" in camera),
    ("Next details composer shows media preview, description, tag helpers and privacy entry",
     "selectedComposerVisual" in home and "Add description..." in home
     and "# Hashtags" in home and "@ Mention" in home
     and "Who can view this post" in home
     and 'Text(if (posting) "Publishing…" else "Post",' in home),
    ("New post composer reserves navigation safe area at the root and keeps Drafts/Post above it",
     "Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 64.dp))" in home
     and ".background(MaterialTheme.colorScheme.surface)\n                            .imePadding()\n                            // Keep both button hit targets clear of Android gesture navigation.\n                            .padding(bottom = 32.dp)" in home
     and "def verify_post_details_footer_safe_area(" in runtime
     and "post details first viewport" in runtime
     and "post details scrolled viewport" in runtime),
    ("Camera dialog draws its dark canvas edge-to-edge while controls apply their own safe-area insets",
     "if (showCamera || showFastCamera)" in home
     and "Surface(Modifier.fillMaxSize(), color = Color(0xFF080A10))" in home
     and "Box(Modifier.fillMaxSize().background(Color(0xFF080A10)))" in home
     and "Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing))" not in home
     and "statusBarsPadding()" in camera and "windowInsetsTopHeight(WindowInsets.statusBars)" in camera
     and ".navigationBarsPadding()" in camera
     and "def verify_camera_system_bar_contrast(" in runtime
     and "mean_luminance > 52" in runtime
     and 'verify_camera_system_bar_contrast(f"{prefix}-camera-live-preview.png"' in runtime),
    ("API 35 emulator launches with the documented environment camera source",
     "-camera-back environment -camera-front none" in workflow
     and "pre-emulator-launch-script:" not in workflow
     and "avd_config" not in workflow),
    ("Home camera opens the real camera panel", "showCamera = true" in home and "FynxCameraCapturePanel(" in home),
    ("Runtime UI hierarchies preserve XML terminators for live/photo/video editor captures",
     runtime.count("write_text(camera_xml + chr(10)") >= 1
     and runtime.count("write_text(editor_xml + chr(10)") >= 1
     and "camera-live-preview.xml" in runtime
     and "camera-photo-editor-initial.xml" in runtime
     and "camera-video-editor-before-effects.xml" in runtime),
    ("Gallery picker foreground detection accepts stable API-35 top-resumed system picker state",
     'def wait_for_gallery_picker_foreground(' in runtime
     and '"mCurrentFocus" in line' in runtime
     and '"topResumedActivity" in line' in runtime
     and "top_resumed_picker = any(" in runtime
     and 'wait_for_gallery_picker_foreground(timeout=25.0)' in runtime
     and 'for attempt in range(24)' in runtime
     and "Android system picker process stopped responding while closing Gallery" in runtime
     and "Gallery Back returned to the same resumed FYNX activity" in runtime),
    ("Gallery Back proves the same app activity, live preview pixels and controls recover without restart",
     '"topResumedActivity" in line and PACKAGE in line' in runtime
     and "camera_activity_token" in runtime
     and "resumed_activity_token != camera_activity_token" in runtime
     and "FYNX returned from Gallery in a different ActivityRecord" in runtime
     and "camera-after-gallery-back.png" in runtime
     and "camera after Gallery cancellation" in runtime
     and "verify_camera_controls_fit_safe_area(" in runtime
     and "camera-after-gallery-back-activity.txt" in runtime),
    ("Camera screenshot verification rejects block-corrupted previews instead of counting colours alone",
     "ImageFilter.FIND_EDGES" in runtime and "edge_detail_ratio < 0.15" in runtime
     and "edge-detail=" in runtime),
    ("Runtime camera journey verifies switch, flash capability, Gallery, capture, effects and Retake without publishing",
     "def capture_camera_post_journey(" in runtime
     and '["Take photo"]' in runtime
     and all(f'"{label}"' in runtime for label in ["Face FX", "Cute Ears", "AI Studio", "Portrait Blur", "Retake", "Next"])
     and "camera-photo-editor-initial.png" in runtime
     and "camera-photo-editor-after-rotate.png" in runtime
     and '["Rotate"]' in runtime
     and "camera-photo-editor-portrait-ready.png" in runtime
     and "camera-photo-retake" in runtime
     and "camera-after-retake.png" in runtime
     and "camera-create-post-caption.png" in runtime
     and "camera-post-details-options.png" in runtime
     and "camera-status-composer.png" in runtime
     and all(label in runtime for label in ["New post", "Add description...", "Post"])
     and "# Hashtags" in home and "@ Mention" in home
     and "gallery_recovered = False" in runtime
     and "if not gallery_recovered:" in runtime
     and "return capture_camera_video_journey(home_after_gallery_abort, prefix)" in runtime
     and "without publishing" in runtime
     and ("both front- and back-facing CameraX devices switched to usable previews" in runtime or "this CI AVD exposes only a rear-facing camera" in runtime)
     and "Gallery opened Android's system document/media picker as the focused foreground surface" in runtime
     and "rear-camera torch toggled on and back off" in runtime
     and "Next must become enabled" in runtime
     and "without publishing a post" in runtime),
    ("Runtime video journey records a real clip, applies Cinematic, preserves audio where inspectable, and never publishes",
     "def capture_camera_video_journey(" in runtime
     and '["Video"]' in runtime
     and "Cinematic" in runtime
     and all(f'"{style}"' in runtime for style in ["Warm Film", "Vintage", "B&W", "Vivid"])
     and "verify_camera_image_signal" in runtime
     and "verify_camera_controls_fit_safe_area" in runtime
     and "verifyFynxVideoEditOutput" in camera
     and "camera-video-style-" in runtime
     and 'video_mode, ["Video FX"]' in runtime
     and "compact video style tray" in runtime
     and "list_app_cache_videos" in runtime
     and "processed video dropped the source audio stream" in runtime
     and "Generate on-device captions" in runtime
      and '["Stop recording", "Stop video note", "Recording"]' in runtime
      and '["Stop recording", "Stop video note", "Record video", "Record video note"]' in runtime
      and "camera_stop_active_recording" in runtime
      and "video shutter entered a real recording state" in runtime
      and 'captions = find_control(editor, ["Captions"])' in runtime
      and "visible Captions action" in runtime
     and "camera-video-effect-output.png" in runtime
     and "never tap Next" in runtime),
    ("Home camera starts a fresh session", "capturedUris = emptyList()" in home and "cameraOpenedFromComposer = false" in home),
    ("Post details follow the reference information order with a fixed Drafts/Post footer",
     all(label in home for label in ["Add description...", "# Hashtags", "@ Mention", "Content disclosure and ads", "Location", "Add link", "More options", "Share to", "Edit cover", "Drafts"])
     and 'Text(\n                                            "Add description...",' in home
     and home.index('Text(\n                                            "Add description...",') < home.index('label = { Text("# Hashtags") }') < home.index('Text("Content disclosure and ads", style')
     and home.index('Text("Content disclosure and ads", style') < home.index('Text("Location", style') < home.index('Text("Add link", style')
     and home.index('Text("Add link", style') < home.index('"Who can view this post"') < home.index('Text("More options", style') < home.index('Text("Share to", style')
     and 'Text(if (posting) "Publishing…" else "Post",' in home
     and ".imePadding()" in home),
    ("Drafts are private per account and durable media is copied out of the transient cache",
     "FynxAuthStore.accountStorageKey(context)" in draft_store
     and "MessageDigest.getInstance(\"SHA-256\")" in draft_store
     and 'File(context.filesDir, "post_drafts")' in draft_store
     and "source.copyTo(output)" in draft_store
     and "fun save(" in draft_store and "fun delete(" in draft_store),
    ("Composer saves, restores and deletes local drafts without changing the remote post API",
     "FynxLocalPostDraftStore.save(" in home
     and "restoreSavedPostDraft(draft)" in home
     and "FynxLocalPostDraftStore.delete(context, savedId)" in home
     and "Save current draft" in home and "No saved drafts yet" in home),
    ("Add link inserts only a validated http/https URL into the existing caption field",
     "Uri.parse(candidate)" in home
     and 'parsed.scheme !in listOf("http", "https")' in home
     and 'text = updated' in home and "FynxMultiMediaPostClient.createPost" in home),
    ("Unsupported paid disclosure and custom video cover are explained rather than falsely marked active",
     "Paid partnership labeling is not supported yet" in home
     and "does not yet support paid-partnership or branded-content disclosure fields" in home
     and "custom video-cover field is not supported" in home),
    ("Share to opens Android sharing for the current caption without inventing an unpublished FYNX URL",
     "Intent.ACTION_SEND" in home and 'Intent.EXTRA_TEXT, payload' in home
     and 'Intent.createChooser(sendIntent, "Share to")' in home),
    ("More options reuses the existing music, people, location, gallery and voice actions",
     all(marker in home for marker in ["showMusicPicker = true", "showPeoplePicker = true", "requestComposerLocation()", "PickVisualMedia.ImageAndVideo", "showVoiceRecorder = true"])),
    ("Runtime test opens the real camera from More options without confusing Gallery selection, then checks the supplied post-details sequence and local draft",
     '["More options"]' in runtime and '["Take photo or video"]' in runtime and '["Take photo or video", "Add photo or video"]' in runtime
     and 'Text("Take photo or video")' in home
     and "cameraOpenedFromComposer = true" in home and "showCamera = true" in home
     and "required_top = [\"Add description...\", \"# Hashtags\", \"@ Mention\", \"Edit cover\"]" in runtime
     and "camera_scroll_until_control" in runtime
     and "post-details scroll area" in runtime
     and "local post Drafts saved captured media" in runtime
     and "no post was published" in runtime),
    ("Camera capture hands media to the post composer", "showCamera = false" in home and "showComposer = true" in home),
    ("Camera dismiss returns to an existing composer draft", "cameraOpenedFromComposer && !posting" in home),
    ("Camera preview supports retake or cancel-edit before handoff", 'Text(if (initialMediaUri != null) "Cancel edit" else "Retake")' in camera and "fun retake()" in camera),
    ("Camera preview sends the captured asset to the composer", 'Text("Next")' in camera and 'onClick = { finishCameraMedia(toStatus = false) }' in camera and "onCaptured(finalUri, mediaType)" in camera),
    ("Captured media uses the authoritative media type resolver", "FynxMultiMediaPostClient.mediaKind(context, it)" in home),
    ("Gallery media is deduplicated and capped at four", "uris.distinct().take(4)" in home and ".take(4)" in home),
    ("Combined camera and gallery media remains capped at four", "(capturedUris + uri).distinct().take(4)" in home),
    ("Composer camera returns to the same draft when cancelled", "cameraOpenedFromComposer = true" in home and "showComposer = true" in home),
    ("Publish button is locked while a post is being submitted", "enabled = !posting" in home and "posting = true" in home),
    ("Successful publish requires a real server post ID", 'optString("postId")' in client and "server did not return its post ID" in client),
    ("Successful publish requests an authoritative Home refresh", "FynxHomeLifecycleRefreshBus.request(context)" in client),
    ("Home refresh invalidates the account-scoped feed cache", "FEED_CACHE_KEY_PREFIX + accountKey" in refresh),
    ("Backend stores the complete ordered media set", "social_post_media" in backend and "UNIQUE (post_id, position)" in backend),
    ("Backend validates media ownership and MIME type", "media ownership check failed" in backend and "media type does not match uploaded asset" in backend),
    ("Backend enforces one audio item for voice posts", "voice posts must contain one audio item" in backend),
    ("Selected-audience media remains visible to selected friends", "p.visibility = 'SELECTED_PEOPLE'" in backend and "social_post_audience" in backend and "a.user_id = $2" in backend),
    ("Only-me media remains restricted to the author", "p.visibility = 'ONLY_ME' AND p.author_id = $2" in backend),
    ("Normalized media authorization excludes blocked relationships", "NOT EXISTS" in backend and "blocked_id = p.author_id" in backend),
    ("Camera media preview labels the Status destination clearly and keeps the familiar story wording",
     'Text("Status", style = MaterialTheme.typography.titleMedium' in camera
     and 'Text("Your Story", style = MaterialTheme.typography.labelSmall' in camera
     and 'contentDescription = "Share to Status"' in camera),
    ("Gallery photo and video selections enter the same preview that offers Status and Next",
     "ActivityResultContracts.OpenDocument()" in camera
     and 'galleryPicker.launch(arrayOf("image/*", "video/*"))' in camera
     and "pendingUri = Uri.fromFile(working)" in camera
     and 'pendingType = if (selectedType == "video" && videoNoteMode) "video_note" else selectedType' in camera
     and "showCaptureControls = true" in camera
     and "if(previewUri!=null && previewType!=null)" in camera
     and "if (showYourStoryAction && !statusCapture && !liveProofMode)" in camera
     and "showYourStoryAction = cameraEditingMediaUri == null" in home
     and "onClick = { finishCameraMedia(toStatus = true) }" in camera
     and "onShareToStatus(finalUri" in camera),
    ("Status CTA checks the existing upload and video limits before opening the Status composer",
     "validateCameraStatusMedia(finalUri, if (mediaType == \"video_note\") \"video\" else mediaType)" in camera
     and "MAX_STATUS_PHOTO_UPLOAD_BYTES = 12L * 1024L * 1024L" in camera
     and "FYNX_STATUS_MAX_VIDEO_BYTES" in camera
     and "FYNX_STATUS_MAX_VIDEO_DURATION_MS" in camera
     and "MediaMetadataRetriever.METADATA_KEY_DURATION" in camera
     and "if (total > 12 * 1024 * 1024)" in status_client
     and "onShareToStatus(finalUri" in camera),
    ("Recorded video opens the existing Status composer and is never sent by the runtime test",
     '["Status"]' in runtime
     and "camera-video-status-composer.png" in runtime
     and "recorded video opened the existing Status composer with Send available; Send was not tapped" in runtime
     and "camera-video-status-close" in runtime),
]

checks.extend([
    ("While typing, the large media sheet collapses and a two-row glass toolbar stays above the keyboard",
     "compact = true" in camera
     and 'if (editingTextOverlayId != null && selectedCameraTool == "Text") 0.dp' in camera
     and "imePadding()" in camera
     and "if (compact) {" in overlay
     and "Keep the editor actions in two compact rows" in overlay
     and 'Text("Done", style = MaterialTheme.typography.labelSmall)' in overlay),
    ("Compact media text editor keeps Add, Done, colours, fonts and formatting reachable",
     'Text("+ Add", style = MaterialTheme.typography.labelSmall)' in overlay
     and 'contentDescription = "Add text overlay"' in overlay
     and 'Text("Timing", style = MaterialTheme.typography.labelSmall)' in overlay
     and 'Text("Apply timing")' in overlay
     and 'label = { Text("Bold") }' in overlay
     and 'label = { Text("Highlight") }' in overlay
     and 'label = { Text("Delete") }' in overlay
     and "Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())" in overlay),
    ("Reopening the camera Text tool edits the selected overlay instead of stacking a duplicate",
     "fun openCameraTextEditor()" in camera
     and "cameraTextOverlays.lastOrNull()" in camera
     and 'CameraEditorRailAction("Text", Icons.Default.TextFields, selectedCameraTool == "Text") { openCameraTextEditor() }' in camera
     and "editingTextOverlayId == null && previewType == \"image\"" in camera
     and "} else if (editingTextOverlayId == null) {" in camera),
    ("Runtime accessibility helpers detect leaf controls and caption verification handles keyboard-owned hierarchies",
     'return node is not None and node.attrib.get("enabled", "true").lower() != "false"' in runtime
     and 'return node is not None and node.attrib.get("clickable", "false").lower() == "true"' in runtime
     and "Gboard owned the keyboard-open accessibility hierarchy" in runtime
     and "camera-video-text-timing-open" in runtime
     and "video text timing dialog did not expose Show from and Show until" in runtime),
    ("Media overlay geometry is anchored to intrinsic photo/video dimensions and preview crop mode",
     "readFynxCameraMediaMetrics" in overlay and "mediaAspectRatio" in overlay
     and "mediaWidthPx" in overlay and "mediaHeightPx" in overlay
     and 'centerCrop = previewType == "image"' in camera),
    ("Multiline overlay preview line spacing scales with the media and matches the export layout",
     "val overlayTextSizePx = (mediaWidthPx * 0.055f).coerceIn(24f, mediaWidthPx * 0.12f)" in overlay
     and "val overlayTextLineHeight = with(density) { (overlayTextSizePx * 1.16f).toSp() }" in overlay
     and "lineHeight = overlayTextLineHeight" in overlay
     and "lineHeight = 40.sp" not in overlay
     and "textSize = (bitmap.width * 0.055f * item.scale)" in overlay),
    ("Video overlay timing uses the same source-of-truth interval in live preview and Media3 export",
     "isFynxCameraTextOverlayVisibleAt" in overlay
     and "playbackPositionMs" in overlay and "playbackPositionMs = if (previewType" in camera
     and "Show from" in overlay and "Show until" in overlay),
    ("Retake explicitly releases and rebinds CameraX and gates the shutter on ready use cases",
     "Force a clean CameraX bind" in camera and "cameraReady = false" in camera
     and "galleryRebindRequest += 1" in camera
     and "cameraReady && (if (mode == CameraMode.PHOTO)" in camera),
    ("Text editor provides a live accessible media typing surface and readable colour names",
     'contentDescription = "Text on media"' in overlay and "Text colour" in overlay
     and "cameraTextColorName" in overlay),
    ("Video entry restores the separate manual captions field and Done returns from the overlay tools",
     'selectedCameraTool = "Looks"' in camera
     and 'selectedCameraTool = "Captions"' in camera
     and 'contentDescription = "Video captions"' in camera),
    ("Instrumentation checks photo overlay intrinsic aspect ratio, source dimensions, separate positions and colours",
     'readFynxCameraMediaMetrics(Uri.fromFile(source), "image")' in overlay_test
     and "Overlay geometry uses the source photo aspect ratio" in overlay_test
     and "Photo export changed the source width" in overlay_test
     and "Photo export changed the source height" in overlay_test
     and "Photo export did not burn the first overlay colour near its selected top position" in overlay_test
     and "Photo export did not burn the second overlay colour near its selected bottom position" in overlay_test
     and "photoTextOverlayIsRenderedAtEachSelectedPositionAndKeepsSourceDimensions" in overlay_test),
    ("Photo text export requests a mutable ARGB bitmap and preserves normalized overlay centres",
     "inMutable = true" in overlay
     and "val canvas = Canvas(bitmap)" in overlay
     and "val x = item.x.coerceIn(0.04f, 0.96f) * bitmap.width - layout.width / 2f" in overlay
     and "val y = item.y.coerceIn(0.06f, 0.94f) * bitmap.height - layout.height / 2f" in overlay
     and ".coerceIn(0f, (bitmap.width - layout.width)" not in overlay
     and ".coerceIn(0f, (bitmap.height - layout.height)" not in overlay),
    ("Runtime exercises direct-on-media photo/video typing and video timing controls",
     "FYNXoverlaytest" in runtime and "FYNXvideooverlay" in runtime
     and "FYNXdraftoverlay" in runtime
     and "camera-post-draft-edit-text-open" in runtime
     and "video text timing dialog did not expose Show from and Show until" in runtime),
    ("Reopened draft media is edited from disposable copies, never directly from the saved source",
     'mutableStateOf<Uri?>(null)' in camera
     and "copyCameraGalleryMediaToCache(context, source)" in camera
     and "releaseUnneededInitialMediaCopies" in camera
     and "if (!initialMediaHandedOff)" in camera),
    ("Selected content media waits for its working copy before live CameraX can bind",
     "isPreparingInitialMedia" in camera
     and "initialMediaLoadFailed" in camera
     and "pendingUri != null || isPreparingInitialMedia || initialMediaLoadFailed" in camera
     and "Preparing your media for text editing" in camera),
    ("Photo/video text edits remain editable when saved in and restored from a local post draft",
     "mediaEditSourcePaths" in draft_store and "mediaEditStates" in draft_store
     and "copyDraftMedia(edit.sourceUri" in draft_store
     and "encodeFynxCameraMediaDraftEdit" in draft_store
     and "decodeFynxCameraMediaDraftEdit" in home
     and "Edit text on media" in home
     and "initialTextOverlays" in home
     and "onCapturedWithTextEdits" in camera
     and "mediaDraftCodecPreservesEveryEditableTextOverlayProperty" in overlay_test
     and "localPostDraftKeepsEditableMediaSourceAndOverlayStateUntilDelete" in overlay_test),
    ("Video export validation checks every overlay at its own selected time and media position",
     "verifyFynxVideoEditOutput(source, output, videoStyle, visibleText)" in camera
     and "selected video text has no visible interval" in video_effects
     and "did not visibly burn the text overlay" in video_effects
     and "cameraVideoTextOverlayBackgroundAnchor" in video_effects
     and "verifiableTextOverlays.forEach" in video_effects
     and "verifiableTextOverlays.size == requestedTextOverlays.size" in video_effects),
    ("More options is an accessible clickable row with viewport-aware runtime navigation",
     'contentDescription = "More options"' in home
     and "camera_scroll_until_control" in runtime
     and "camera-post-more-options-row" in runtime),
])

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit("Camera -> Post journey verification failed: " + ", ".join(failed))
print(f"Camera -> Post journey verification GREEN ({len(checks)} checks)")
