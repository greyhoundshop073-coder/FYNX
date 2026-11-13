from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
home = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8")
visible_updates = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxVisibleUpdatesPanel.kt").read_text(encoding="utf-8")
camera = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt").read_text(encoding="utf-8")
overlay = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxCameraTextOverlay.kt").read_text(encoding="utf-8")
overlay_test = (ROOT / "app/src/androidTest/java/com/fynx/app/ui/CameraTextOverlayInstrumentationTest.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text(encoding="utf-8")
refresh = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeLifecycle.kt").read_text(encoding="utf-8")
backend = (ROOT / "backend/socialMultiMediaBootstrap.js").read_text(encoding="utf-8")
runtime = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
storage = (ROOT / "scripts/configure_android_emulator_storage.sh").read_text(encoding="utf-8")
status_hub = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxStatusHubPanel.kt").read_text(encoding="utf-8")
status_client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxStatusClient.kt").read_text(encoding="utf-8")
video_effects = (ROOT / "app/src/main/java/com/fynx/app/ui/CameraVideoEffects.kt").read_text(encoding="utf-8")
audio_test = ROOT / "app/src/androidTest/java/com/fynx/app/ui/CameraVideoAudioExtractionInstrumentationTest.kt"
app_source = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text(encoding="utf-8")
draft_store = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxLocalPostDraftStore.kt").read_text(encoding="utf-8")

checks = [
    ("Home header camera and quick-capture button have distinct accessible names",
     'contentDescription = "Open FYNX camera"' in app_source
     and 'contentDescription = "Open quick camera"' in visible_updates
     and 'contentDescription = "Open FYNX camera"' not in visible_updates),
    ("Live camera music chip sits below the FYNX identity while the captured-media editor keeps its centered chip",
     'if (pendingUri == null && !statusCapture && !liveProofMode)' in camera
     and "modifier = Modifier.align(Alignment.TopStart)" in camera
     and ".padding(start = 8.dp, top = 52.dp)" in camera
     and "modifier = Modifier.align(Alignment.TopCenter)" in camera
     and ".padding(top = 10.dp)" in camera
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
    ("Caption success is shown as an inline neutral notice, not a blocking error",
     'videoCaptionMessage = "Captions are ready. Review the wording and timing before continuing."' in camera
     and 'error = "Captions are ready. Review the wording and timing before continuing."' not in camera
     and "videoCaptionMessage?.let { message ->" in camera
     and "message,\n                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)," in camera
     and "style = MaterialTheme.typography.bodySmall" in camera),
    ("Runtime fully reveals the video caption field before tapping and only dismisses a verified IME",
     'control_is_safely_revealed_in_scroll_view(caption_xml, ["Video captions"]' in runtime
     and 'control_scroll_view_geometry(caption_xml, ["Video captions"])' in runtime
     and "bottom_margin=72" in runtime
     and "safely revealed Video captions field neither opened the keyboard nor accepted text" in runtime
     and "Never send a second Back key" in runtime
     and 'input_text_slowly("FYNXcap")' in runtime
     and "manually entered video caption survives keyboard dismissal" in runtime),
    ("On-device captions transcode supported device audio formats to PCM16 WAV before speech recognition",
     'fynx_caption_audio_" + System.currentTimeMillis() + ".wav"' in video_effects
     and "MediaCodec.createDecoderByType(mime)" in video_effects
     and "writeCameraWavHeader(output, dataBytes, sampleRate, channelCount)" in video_effects
     and "AudioFormat.ENCODING_PCM_16BIT" in video_effects
     and "This video's audio format is not supported." not in video_effects
     and audio_test.is_file),
    ("Video captions field is brought into view when focused",
     "BringIntoViewRequester" in camera
     and "bringIntoViewRequester(videoCaptionBringIntoViewRequester)" in camera
     and "videoCaptionBringIntoViewRequester.bringIntoView()" in camera),
    ("IME Back is not intercepted and text editing has an explicit completion action",
     "WindowInsets.ime.getBottom(LocalDensity.current)" in camera
     and "BackHandler(enabled = recording == null && !cameraImeVisible)" in camera
     and 'contentDescription = "Finish text editing"' in overlay
     and '["Finish text editing"]' in runtime),
    ("Camera timer cancellation exposes a dedicated accessible action",
     'contentDescription = "Cancel camera capture timer"' in camera
     and '["Cancel camera capture timer"]' in runtime),
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
     and "No official tracks have been published yet." in runtime
     and 'find_control(music_state, ["Sign in to FYNX to browse published music"])' in runtime
     and 'find_control(music_state, ["Use"], exact_only=True)' in runtime),
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
    ("One shared CameraX editor proceeds through its single Next action to New post",
     "showYourStoryAction = false" in home
     and "onShareToStatus =" not in home
     and 'Text("Next")' in camera
     and 'onClick = { finishCameraMedia(toStatus = false) }' in camera
     and "onCaptured(finalUri, mediaType)" in camera),
    ("Status camera entry points route back to Home's shared CameraX panel",
     "onCamera = { onOpenUnifiedCamera(CameraMode.PHOTO) }" in status_hub
     and "onRecordVideo = { onOpenUnifiedCamera(CameraMode.VIDEO) }" in status_hub
     and "onCameraClick = { onOpenUnifiedCamera(CameraMode.PHOTO) }" in status_hub
     and "FynxCameraCapturePanel(" not in status_hub
      and "cameraInitialMode = homeCameraInitialMode" in app_source
      and "initialMode = activeCameraInitialMode" in home
     and 'onOpenUnifiedCamera = { mode -> homeCameraInitialMode = mode; selected = "Home"; homeCameraRequest += 1 }' in app_source),
    ("Shared composer exposes all destinations and uses the existing Status publisher",
     all(f'Text("{label}")' in home for label in ["Home Feed", "Status", "Both"])
     and "FynxSharedStatusPublisher.publish(" in home
     and "FynxStatusClient.create" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxSharedStatusPublisher.kt").read_text(encoding="utf-8")
     and "statusMediaCandidates.size <= 1" in home),
    ("Camera editor exposes Next and preserves the existing captured-media callback",
     'Text("Next")' in camera and 'onClick = { finishCameraMedia(toStatus = false) }' in camera and "onCaptured(finalUri, mediaType)" in camera),
    ("Next details composer shows media preview, description, tag helpers, privacy and publish destinations",
     "selectedComposerVisual" in home and "Add description..." in home
     and "# Hashtags" in home and "@ Mention" in home
     and "Who can view this post" in home
     and all(f'Text("{label}")' in home for label in ["Home Feed", "Status", "Both"])
     and 'publishDestination == "BOTH" -> "Publish to both"' in home),
    ("New post composer reserves navigation safe area at the root and keeps Drafts/Post above it",
     "Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 64.dp))" in home
     and ".background(MaterialTheme.colorScheme.surface)\n                            .imePadding()\n                            // Keep both button hit targets clear of Android gesture navigation.\n                            .padding(bottom = 32.dp)" in home
     and "def verify_post_details_footer_safe_area(" in runtime
     and "post details first viewport" in runtime
     and "post details scrolled viewport" in runtime),
    ("Camera dialog draws its dark canvas edge-to-edge while controls apply their own safe-area insets",
     "if (showCamera || showFastCamera)" in home
     and "Surface(Modifier.fillMaxSize(), color = Color(0xFF000000))" in home
     and "Box(Modifier.fillMaxSize().background(Color(0xFF000000)))" in home
     and "Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing))" not in home
     and "statusBarsPadding()" in camera and "windowInsetsTopHeight(WindowInsets.statusBars)" in camera
     and ".navigationBarsPadding()" in camera
     and "def verify_camera_system_bar_contrast(" in runtime
     and "mean_luminance > 52" in runtime
     and 'verify_camera_system_bar_contrast(f"{prefix}-camera-live-preview.png"' in runtime),
    ("API 35 emulator launches with the documented environment camera source and bounded AVD storage",
     "-camera-back environment -camera-front none" in workflow
     and "pre-emulator-launch-script: bash scripts/configure_android_emulator_storage.sh" in workflow
     and "disk.dataPartition.size=2G" in storage
     and "hw.ramSize=2048" in storage),
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
     and "camera-video-destination-options.png" in runtime
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
     and home.index('Text("Content disclosure and ads", style') < home.index('Text("Location", style') < home.index('"Who can view this post"') < home.index('Text("Share to", style')
     and "Keep the two frequently needed composer actions fixed above the publish bar." in home
     and home.index('Text("Add link")') < home.index('Text("More options")') < home.index('Text("Drafts"')
     and 'onClick = { postLinkInput = ""; showPostLinkDialog = true }' in home
     and 'onClick = { showMorePostOptions = true }' in home
     and "camera-post-more-options-row" in home
     and 'publishDestination == "BOTH" -> "Publish to both"' in home
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
    ("Runtime video flow opens CameraX directly from Home while existing composer media entry and local draft flow remain covered",
     "def capture_camera_video_journey(" in runtime
     and 'f"{prefix}-video-home-camera", ["Switch front/back camera"]' in runtime
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
    ("Gallery photo/video enters the shared CameraX editor and Next handoff",
     "ActivityResultContracts.OpenDocument()" in camera
     and 'galleryPicker.launch(arrayOf("image/*", "video/*"))' in camera
     and "pendingUri = Uri.fromFile(working)" in camera
     and 'pendingType = if (selectedType == "video" && videoNoteMode) "video_note" else selectedType' in camera
     and "showCaptureControls = true" in camera
     and "if(previewUri!=null && previewType!=null)" in camera
     and 'onClick = { finishCameraMedia(toStatus = false) }' in camera
     and "onCaptured(finalUri, mediaType)" in camera),
    ("Local image/video draft editor restores semantic type for file-based media",
     "copyCameraGalleryMediaToCache(context, source, initialMediaType)" in camera
     and "normalizedDeclaredType" in camera
     and '"image" -> "image/jpeg"' in camera
     and '"video" -> "video/mp4"' in camera),
    ("Unified Status publication retains the existing Status API and local cache while reusing the one camera",
     "FynxStatusClient.uploadMedia" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxSharedStatusPublisher.kt").read_text(encoding="utf-8")
     and "FynxStatusClient.create" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxSharedStatusPublisher.kt").read_text(encoding="utf-8")
     and "FynxStatusStore.save" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxSharedStatusPublisher.kt").read_text(encoding="utf-8")
     and "onCamera = { onOpenUnifiedCamera(CameraMode.PHOTO) }" in status_hub
     and "FynxCameraCapturePanel(" not in status_hub),
    ("Recorded video keeps the same Next and Home Feed/Status/Both destination choice as photo media",
     'onClick = { finishCameraMedia(toStatus = false) }' in camera
     and 'Text("Next")' in camera
     and all(f'Text("{label}")' in home for label in ["Home Feed", "Status", "Both"])),
]

checks.extend([
    ("While typing, the large media sheet collapses and a two-row glass toolbar stays above the keyboard",
     "compact = true" in camera
     and 'if (editingTextOverlayId != null && selectedCameraTool == "Text") 0f' in camera
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
     and 'Text("Delete")' in overlay
     and "Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())" in overlay),
    ("Unified camera routing preserves the supported rear lens and returns Status camera requests to Home",
     "-camera-front none" in workflow
     and 'onOpenUnifiedCamera = { mode -> homeCameraInitialMode = mode; selected = "Home"; homeCameraRequest += 1 }' in app_source
     and "camera-video-destination-options.png" in runtime
     and 'lens_switch = find_control(fresh_camera_xml, ["Switch front/back camera"])' not in runtime),
    ("Reopening the camera Text tool edits the selected overlay instead of stacking a duplicate",
     "fun openCameraTextEditor()" in camera
     and "cameraTextOverlays.lastOrNull()" in camera
     and 'CameraEditorRailAction("Text", Icons.Default.TextFields, selectedCameraTool == "Text") { openCameraTextEditor() }' in camera
     and "editingTextOverlayId == null && previewType == \"image\"" in camera
     and "} else if (editingTextOverlayId == null) {" in camera
     and 'label for label in ["Looks", "Face FX", "Background", "AI Studio"]' in runtime
     and "text-editing keyboard view hides the right-side effect rail and keeps Done visible" in runtime),
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
    ("ffprobe CSV stream parsing recognizes video when the formatter emits a trailing comma",
     'for field in line.split(",")' in runtime
     and 'if field.strip()' in runtime
     and "return {line.strip().lower() for line in result.stdout.splitlines() if line.strip()}" not in runtime),
    ("Photo editor keeps the five editing actions in a bounded scrollable rail and exposes Rotate in the fixed top row", "Modifier.align(Alignment.TopEnd)" in camera and "heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.40f)" in camera and ".verticalScroll(rememberScrollState())" in camera and all(f'CameraEditorRailAction("{label}"' in camera for label in ["Looks", "Face FX", "Background", "AI Studio", "Text"]) and 'Text("Rotate", color = Color.White' in camera and 'contentDescription = "Rotate photo"' in camera and 'CameraEditorRailAction("Rotate"' not in camera),
    ("Saved image/video draft media type falls back to persisted type if file MIME is unavailable",
     "copyCameraGalleryMediaToCache(context, source, initialMediaType)" in camera
     and "The saved media type does not match the selected file." in camera),
    ("Camera and post primary actions follow FYNX Azure Blue",
     "primary = Color(0xFF007FFF)" in camera
     and "containerColor = Color(0xFF007FFF)" in camera
     and "containerColor = Color(0xFF007FFF)" in home),
    ("Video duration metadata is recomputed when CameraX finalizes the recorded-duration fallback",
     "LaunchedEffect(pendingUri, pendingType, recordedVideoDurationFallbackMs)" in camera
     and "recordedVideoDurationFallbackMs =" in camera
     and "resolveFynxCameraVideoDurationMs(" in camera),
    ("Video overlay timing retains recorded duration when Android metadata omits it",
     "isFynxCameraTextOverlayVisibleAt" in overlay
     and "playbackPositionMs" in overlay and "playbackPositionMs = if (previewType" in camera
     and "Show from" in overlay and "Show until" in overlay
     and "resolveFynxCameraVideoDurationMs" in overlay
     and "recordedVideoDurationFallbackMs" in camera
     and "videoTextTimingFallsBackToRecordedDurationWhenMetadataIsMissing" in overlay_test),
    ("Video recording duration fallback resets on a different media item, Gallery selection and Retake",
     "recordedVideoDurationFallbackMs = 0L" in camera
     and "recordedVideoDurationFallbackMs = 0L" in camera.split("fun retake()", 1)[1].split("fun rotatePhoto()", 1)[0]
     and "recordedVideoDurationFallbackMs = 0L" in camera.split("result.onSuccess { (working, original, selectedType) ->", 1)[1].split("}.onFailure", 1)[0]),
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
     "FYNXoverlaytest" in runtime
     and 'input_text_slowly("FYNXvideo")' in runtime
     and "FYNXvideo" in runtime
     and "FYNXdraftoverlay" in runtime
     and "camera-post-draft-edit-text-open" in runtime
     and "video text timing dialog did not expose Show from and Show until" in runtime),
    ("Reopened draft media is edited from disposable copies, never directly from the saved source",
     'mutableStateOf<Uri?>(null)' in camera
     and "copyCameraGalleryMediaToCache(context, source, initialMediaType)" in camera
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
    # Upload the precise failed assertion names even when workflow logs are unavailable.
    # The Android workflow always uploads this directory after a source-gate failure.
    report_dir = ROOT / "fynx-runtime-screenshots"
    report_dir.mkdir(parents=True, exist_ok=True)
    report = [
        "FYNX Camera -> Post source gate diagnostics",
        f"Total checks: {len(checks)}",
        f"Failed checks: {len(failed)}",
        "",
        *[f"FAIL: {name}" for name in failed],
        "",
        *[f"PASS: {name}" for name, ok in checks if ok],
    ]
    (report_dir / "camera-to-post-source-gate.txt").write_text(chr(10).join(report) + chr(10), encoding="utf-8")
    raise SystemExit("Camera -> Post journey verification failed: " + ", ".join(failed))
print(f"Camera -> Post journey verification GREEN ({len(checks)} checks)")
