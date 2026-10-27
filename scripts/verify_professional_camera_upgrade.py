#!/usr/bin/env python3
"""Professional Camera Upgrade source-contract checks."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ui = ROOT / "app/src/main/java/com/fynx/app/ui"
camera = (ui / "FynxCameraCapturePanel.kt").read_text(encoding="utf-8")
camera_parent_sources = {
    "Home": (ui / "FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8"),
    "Private chat": (ui / "ConversationPanel.kt").read_text(encoding="utf-8"),
    "Group chat": (ui / "GroupChatPanel.kt").read_text(encoding="utf-8"),
    "Group social composer": (ui / "FynxGroupSocialBatch.kt").read_text(encoding="utf-8"),
    "Marketplace listing": (ui / "FynxMarketplacePanel.kt").read_text(encoding="utf-8"),
    "Status hub": (ui / "FynxStatusHubPanel.kt").read_text(encoding="utf-8"),
    "Mature Status composer": (ui / "FynxMatureStatusComposerPanel.kt").read_text(encoding="utf-8"),
    "Marketplace live proof": (ui / "FynxMarketplaceLiveProof.kt").read_text(encoding="utf-8"),
}
def camera_parent_has_duplicate_insets(content: str) -> bool:
    index = content.find("FynxCameraCapturePanel(")
    if index < 0:
        return True
    parent_context = content[max(0, index - 340):index]
    return "safeDrawingPadding()" in parent_context or "windowInsetsPadding(WindowInsets.safeDrawing)" in parent_context
mature_status = (ui / "FynxMatureStatusComposerPanel.kt").read_text(encoding="utf-8")
video_editor = (ui / "FynxCameraVideoEditor.kt").read_text(encoding="utf-8")
unit_tests = (ROOT / "app/src/test/java/com/fynx/app/ui/CameraMediaReliabilityTest.kt").read_text(encoding="utf-8")

checks = {
    "photo crop has real bitmap geometry and ratio presets": "fun cropPhotoAspect(aspectRatio: Float, presetName: String)" in camera and "Bitmap.createBitmap(" in camera and "rect.cropWidth, rect.cropHeight" in camera and "fun cropPhotoSquare()" in camera and "fun cropPhotoPortrait()" in camera and "fun cropPhotoLandscape()" in camera,
    "photo editor exposes square, portrait 4:5 and landscape 16:9 crop actions": "Crop square" in camera and "Portrait 4:5" in camera and "Landscape 16:9" in camera,
    "crop keeps the filter source in sync": "cropToPreset(current, currentTemp)" in camera and "cropToPreset(original, originalTemp)" in camera,
    "text overlay has an editable input and bounded output length": "showPhotoTextDialog" in camera and "OutlinedTextField(" in camera and '"Add text"' in camera and "photoTextOverlay = it.take(96)" in camera and "}.take(4)" in camera,
    "text/sticker preview uses the same photo-relative geometry as saved output": "drawFynxCameraPhotoPreviewOverlays(" in camera and "wrapFynxCameraOverlayText(textOverlay, paint, imageWidth * 0.82f)" in camera and "imageHeight * 0.43f" in camera and "imageWidth * 0.78f" in camera,
    "sticker picker exposes actual choices": 'listOf("✨", "❤️", "🔥", "😂", "😎", "🎉", "🌈")' in camera and "selectedPhotoSticker" in camera,
    "text is composited into the outgoing image": "fun renderPhotoOverlays(" in camera and "canvas.drawText(line" in camera and "temp.copyTo(destination, overwrite = true)" in camera,
    "sticker is composited into the outgoing image": "canvas.drawText(sticker" in camera and "sendPreview(previewUri, previewType)" in camera,
    "photo filters run bitmap work off the main thread": "fun applyFilterToPhoto(" in camera and "val result = withContext(Dispatchers.IO)" in camera and "fynx_photo_filter_" in camera and "temp.copyTo(target, overwrite = true)" in camera,
    "AI enhancement establishes a fresh immutable filter baseline": "pendingOriginalUri = baselineUri" in camera and "pendingUri = workingUri" in camera and "fynx_smart_enhanced_working_" in camera,
    "the established camera-to-caller URI and type callback is retained": "onCaptured(previewUri, previewType)" in camera and 'pendingType = if (videoNoteMode) "video_note" else "video"' in camera,
    "retake removes the draft and resets creative edits": "fun retake()" in camera and 'photoTextOverlay = ""' in camera and "selectedPhotoSticker = null" in camera,
    "photo/video status upload protections remain": "FYNX_STATUS_MAX_VIDEO_BYTES" in camera and "FYNX_STATUS_MAX_VIDEO_DURATION_MS" in camera and "recorderBuilder.setTargetVideoEncodingBitRate(700_000)" in camera,
    "mature Status camera opts into Status video size and duration limits": "statusCapture = true" in mature_status and "FynxCameraCapturePanel(" in mature_status,
    "mature Status camera does not double-apply safe drawing insets": "Box(Modifier.fillMaxSize()) {" in mature_status and "Box(Modifier.fillMaxSize().safeDrawingPadding()) {" not in mature_status,
    "video trim uses real MP4 track remuxing with correctly mapped sample flags": "MediaExtractor" in video_editor and "MediaMuxer" in video_editor and "SEEK_TO_NEXT_SYNC" in video_editor and "writeSampleData" in video_editor and "toMuxerBufferFlags(flags)" in video_editor and "BUFFER_FLAG_KEY_FRAME" in video_editor and "SAMPLE_FLAG_ENCRYPTED" in video_editor,
    "mute removes audio track instead of only muting preview": "muteAudio" in video_editor and "if (!isVideo && (!isAudio || muteAudio))" in video_editor,
    "video rotation metadata is preserved during editing": "METADATA_KEY_VIDEO_ROTATION" in video_editor and "setOrientationHint(rotation)" in video_editor,
    "video editor enforces a minimum valid clip range": "MIN_CLIP_DURATION_MS" in video_editor and "normalize(startMs, endMs, durationMs)" in video_editor,
    "camera exposes real trim controls and apply action": '"Edit video"' in camera and '"Apply video edits"' in camera and "applyVideoEdits(previewUri)" in camera,
    "mute toggle changes the saved output": 'Text("Mute original audio"' in camera and "onCheckedChange = { muteVideoAudio = it }" in camera and "FynxCameraVideoEditor.edit(source, startMs, endMs, muteVideoAudio)" in camera,
    "drawing pen maps gestures to the visible photo rather than letterbox": "normalizeFynxCameraDrawPoint(" in camera and "drawFynxCameraDrawingStrokes(" in camera and "drawStrokes.forEach" in camera,
    "drawn strokes are rendered into the sent photo asset": "drawingStrokesToRender" in camera and "canvas.drawPath(path, pen)" in camera and "sendPreview(previewUri, previewType)" in camera,
    "drawing has undo, clear, finish and palette controls": 'Text("Draw")' in camera and 'Text("Undo")' in camera and 'Text("Clear")' in camera and 'Text("Finish drawing")' in camera and "selectedDrawingColorArgb" in camera,
    "drawing geometry has unit regression tests": "drawingPointMapsToImageCoordinatesInsideFitCenterImage" in unit_tests and "drawingPointRejectsLetterboxAreaOutsideTheActualPhoto" in unit_tests,
    "drawing color palette uses the ARGB-int constructor safely": "Color(argb)" in camera and "Color(stroke.colorArgb)" in camera and "Color(argb.toLong())" not in camera and "Color(stroke.colorArgb.toLong())" not in camera,
    "every shared camera caller leaves system-inset management to CameraX panel": all(not camera_parent_has_duplicate_insets(content) for content in camera_parent_sources.values()),
    "all shared-camera dialogs use edge-to-edge window fitting": all("decorFitsSystemWindows = false" in content for content in camera_parent_sources.values()),
    "video-edit unit regression tests are present": "videoEditRangeIsClampedToValidMinimumAndSourceBounds" in unit_tests and "videoEditRangeClampsNegativeAndOverlongSelections" in unit_tests,
    "capture screen exposes close, add sound and the right-side control rail": '"FYNX CAMERA"' in camera and '"Close camera"' in camera and '"Add sound"' in camera and 'title = "Flash"' in camera and 'title = "Timer"' in camera and 'title = "Grid"' in camera and 'title = "Effects"' in camera,
    "capture screen uses a rounded dark studio tray": "RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)" in camera and "Color(0xE6111218)" in camera and '\"PHOTO STUDIO\"' in camera and '\"VIDEO STUDIO\"' in camera,
    "photo and video mode chips remain in the camera capture path": camera.count('selected = mode == CameraMode.PHOTO') >= 1 and camera.count('selected = mode == CameraMode.VIDEO') >= 1 and "FilledIconButton(" in camera,
    "advanced exposure and timer controls are expandable and functional": "showProControls" in camera and '\"Exposure\"' in camera and '\"Timer\"' in camera and "cameraControl?.setExposureCompensationIndex(exposure)" in camera,
    "regular video duration selector has four real limits and a hard-stop guard": "FynxCameraRecordingDuration.videoOptions" in camera and '"15s"' in camera and '"60s"' in camera and '"3m"' in camera and '"10m"' in camera and "maximumDurationMs" in camera and "active.stop()" in camera,
    "Status duration and upload-size caps remain enforced on recording and gallery import": "FYNX_STATUS_MAX_VIDEO_DURATION_MS" in camera and "FYNX_STATUS_MAX_VIDEO_BYTES" in camera and "Status videos must be no longer than 90 seconds." in camera,
    "camera Gallery imports photo and video into the local editor": 'galleryPicker.launch(arrayOf("image/*", "video/*"))' in camera and "normalizeImportedPhotoToJpeg(file)" in camera and "pendingUri = Uri.fromFile(file)" in camera,
    "Add sound reads and previews published FYNX catalogue tracks": "FynxMusicCatalogueClient.listPublished(context, cameraMusicSearch)" in camera and '"/api/social/music/catalogue/"' in camera and "FynxRemoteAudio(" in camera and "onMusicSelected(track)" in camera,
    "photo looks apply real bitmap filters to captured media": "captureStyleFilter" in camera and "LaunchedEffect(pendingUri, pendingType, captureStyleFilter)" in camera and "applyFilterToPhoto(captureStyleFilter, 1f)" in camera,
    "camera controls persist without hiding the shutter after five seconds": "FynxCameraRailAction(" in camera and "showCaptureControls, pendingUri, recording, cameraControl, hasCamera" not in camera,
    "Home composer receives selected camera music": "initialMusic = selectedCatalogueMusic" in camera_parent_sources["Home"] and "onMusicSelected = { track -> selectedCatalogueMusic = track" in camera_parent_sources["Home"],
    "Status composer receives selected camera music": "initialMusic = selectedStatusMusic" in camera_parent_sources["Status hub"] and "onMusicSelected = { track -> selectedStatusMusic = track" in camera_parent_sources["Status hub"],

}
caller_files = [
    "FynxHomeSocialHubPanel.kt",
    "FynxStatusHubPanel.kt",
    "FynxMatureStatusComposerPanel.kt",
    "ConversationPanel.kt",
    "GroupChatPanel.kt",
    "FynxGroupSocialBatch.kt",
    "FynxMarketplacePanel.kt",
    "FynxMarketplaceLiveProof.kt",
]
for filename in caller_files:
    path = ui / filename
    checks[f"{filename} remains wired to shared CameraX"] = path.exists() and "FynxCameraCapturePanel(" in path.read_text(encoding="utf-8")

failures = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
if failures:
    raise SystemExit("Professional camera verification failed: " + ", ".join(failures))
print(f"PROFESSIONAL_CAMERA_UPGRADE_SOURCE_GREEN ({len(checks)} checks)")
