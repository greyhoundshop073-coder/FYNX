from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
home = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8")
camera = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text(encoding="utf-8")
refresh = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeLifecycle.kt").read_text(encoding="utf-8")
backend = (ROOT / "backend/socialMultiMediaBootstrap.js").read_text(encoding="utf-8")
runtime = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
status_hub = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxStatusHubPanel.kt").read_text(encoding="utf-8")
status_client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxStatusClient.kt").read_text(encoding="utf-8")
app_source = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text(encoding="utf-8")
draft_store = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxLocalPostDraftStore.kt").read_text(encoding="utf-8")

checks = [
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
    ("Camera capture and preview show a separate, width-bounded music chip without overlapping the centered preview label",
     "CameraMusicChip(" in camera and "selectedMusic: FynxMusicCatalogueTrack?" in camera
     and "onChooseMusic = onChooseMusic" in camera
     and "modifier = Modifier.align(Alignment.TopEnd)" in camera
     and ".padding(end = 12.dp, top = 62.dp)" in camera
     and ".widthIn(max = 172.dp)" in camera
     and "overflow = TextOverflow.Ellipsis" in camera
     and "Photo preview" in camera and "Video preview" in camera
     and "selectedMusic = selectedCatalogueMusic" in home
     and "showMusicPicker = true" in home),
    ("Immersive Status media composer hides the app bottom navigation so caption and Send controls remain reachable",
     'visible = !statusComposerImmersive && ((selected != "Home" && selected != "Marketplace") || statusCreatorRequested || !homeChromeHidden)' in app_source
     and "onComposerVisibilityChange = { statusComposerImmersive = it }" in app_source),
    ("Your Story and Next are separate real destinations from one captured preview",
     "showYourStoryAction: Boolean = false" in camera
     and 'Text("Your Story",' in camera
     and "onShareToStatus(previewUri" in camera
     and "onCaptured(previewUri, previewType)" in camera
     and "showYourStoryAction = true" in home
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
     'Text("Next")' in camera and "onCaptured(previewUri, previewType)" in camera),
    ("Next details composer shows media preview, description, tag helpers and privacy entry",
     "selectedComposerVisual" in home and "Add description..." in home
     and "# Hashtags" in home and "@ Mention" in home
     and "Who can view this post" in home
     and 'Text(if (posting) "Publishing…" else "Post",' in home),
    ("New post composer reserves navigation safe area at the root and keeps Drafts/Post above it",
     "Column(Modifier.fillMaxSize().statusBarsPadding())" in home
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
     and "required_options = [\"Content disclosure and ads\", \"Location\", \"Add link\", \"More options\", \"Share to\", \"Post\"]" in runtime
     and "local post Drafts saved captured media" in runtime
     and "no post was published" in runtime),
    ("Camera capture hands media to the post composer", "showCamera = false" in home and "showComposer = true" in home),
    ("Camera dismiss returns to an existing composer draft", "cameraOpenedFromComposer && !posting" in home),
    ("Camera preview supports retake before handoff", 'Text("Retake")' in camera and "retake()" in camera),
    ("Camera preview sends the captured asset to the composer", 'Text("Next")' in camera and "onCaptured(previewUri, previewType)" in camera),
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
     and "showYourStoryAction = true" in home
     and "onShareToStatus(previewUri" in camera),
    ("Status CTA checks the existing upload and video limits before opening the Status composer",
     "validateCameraStatusMedia(previewUri, previewType)" in camera
     and "MAX_STATUS_PHOTO_UPLOAD_BYTES = 12L * 1024L * 1024L" in camera
     and "FYNX_STATUS_MAX_VIDEO_BYTES" in camera
     and "FYNX_STATUS_MAX_VIDEO_DURATION_MS" in camera
     and "MediaMetadataRetriever.METADATA_KEY_DURATION" in camera
     and "if (total > 12 * 1024 * 1024)" in status_client
     and "onShareToStatus(previewUri" in camera),
    ("Recorded video opens the existing Status composer and is never sent by the runtime test",
     '["Status"]' in runtime
     and "camera-video-status-composer.png" in runtime
     and "recorded video opened the existing Status composer with Send available; Send was not tapped" in runtime
     and "camera-video-status-close" in runtime),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit("Camera -> Post journey verification failed: " + ", ".join(failed))
print(f"Camera -> Post journey verification GREEN ({len(checks)} checks)")
