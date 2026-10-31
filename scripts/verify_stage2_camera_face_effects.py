from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

camera = read("app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt")
video = read("app/src/main/java/com/fynx/app/ui/CameraVideoEffects.kt")
effects = read("app/src/main/java/com/fynx/app/ui/CameraFaceEffects.kt")
build = read("app/build.gradle.kts")
enhancer = read("app/src/main/java/com/fynx/app/ui/FynxAiPhotoEnhancer.kt")
test = read("app/src/test/java/com/fynx/app/ui/CameraMediaReliabilityTest.kt")

portrait_vector_assets = [ROOT / f"app/src/main/res/drawable/camera_effect_portrait_{index}.xml" for index in range(4, 13)]
portrait_vectors_present = all(path.is_file() and path.stat().st_size > 500 for path in portrait_vector_assets)

checks = [
    ("ML Kit face detection dependency", "com.google.mlkit:face-detection:16.1.5" in build),
    ("Media3 transformation APIs declare AndroidX UnstableApi opt-in for lint", "@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])" in video),
    ("Speech captions use the SDK version with file AudioSource support", "com.google.mlkit:genai-speech-recognition:1.0.0-alpha2" in build and "com.google.mlkit.genai.common.audio.AudioSource" in video and "AudioSource.fromFile(audio)" in video),
    ("Kotlin 2.3 uses compilerOptions DSL and no fatal legacy kotlinOptions", "kotlin {\n    compilerOptions {" in build and "JvmTarget.fromTarget(\"17\")" in build and "optIn.add(\"androidx.compose.foundation.ExperimentalFoundationApi\")" in build and "kotlinOptions {" not in build and "freeCompilerArgs +=" not in build),
    ("CameraX analysis retains latest frame only", "STRATEGY_KEEP_ONLY_LATEST" in camera and "ImageAnalysis" in camera),
    ("Face detector tracks landmarks on device", "setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)" in effects and "enableTracking()" in effects),
    ("Front-camera overlay is mirrored", "isFrontCamera()" in effects and "viewWidth - right" in effects),
    ("Seven tracked face effects plus Off", all(label in effects for label in ["Cute Ears", "Smart Glasses", "Royal Crown", "Puppy / Animal", "Virtual Makeup", "Hair Colour", "Funny Face"])),
    ("Face effects give a clear error when no face is detected instead of silently doing nothing", "require(faces.isNotEmpty())" in effects and "No clear face was found. Try a front-facing photo in good light." in effects and "if (faces.isEmpty()) return" not in effects),
    ("Large photo edits share a bounded bitmap decoder", "internal fun decodeCameraEditingBitmap(file: File, maxEdge: Int = 2400)" in effects and "decodeCameraEditingBitmap(source, maxEdge = 2400)" in camera and "decodeCameraEditingBitmap(file, maxEdge = 2400)" in effects),
    ("AI enhancement decodes high-resolution photos before allocating output pixels", "decodeCameraEditingBitmap(inputFile, maxEdge = 2048)" in enhancer and "bitmap.recycle()" in enhancer),
    ("Gallery photos are normalized to JPEG before source and working files diverge", 'val extension = if (type == "image")' in camera and "normalizeCapturedPhoto(working, mirrorFront = false, forceJpeg = true)" in camera and "working.copyTo(original, overwrite = true)" in camera),
    ("Same sticker drawing used for saved photos", "applyCameraFaceEffectToPhotoFile" in camera and "drawFynxCameraFaceEffect(canvas" in effects),
    ("Photo rotation updates both preview and source baseline with safe file replacement", "pendingOriginalUri?.path?.let(::File)" in camera and "distinctBy { it.absolutePath }" in camera and "forEach(::rotateCameraPhotoFile)" in camera and "temp.renameTo(file)" in camera),
    ("Photo rotation compresses a non-null bitmap to a temp file before replacing both image versions", "val rotatedBitmap = android.graphics.Bitmap.createBitmap" in camera and "rotatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, it)" in camera and "temp.renameTo(file)" in camera and "forEach(::rotateCameraPhotoFile)" in camera),
    ("AI enhancement has a separate reset baseline and clears effects already baked into the image", "pendingOriginalUri = baselineUri" in camera and "enhancedUri to Uri.fromFile(baselineFile)" in camera and "faceEffect = CameraFaceEffect.OFF" in camera and "backgroundEffect = CameraBackgroundEffect.OFF" in camera and "studioEffect = CameraStudioEffect.OFF" in camera),
    ("Face-effect choices covered by unit test", "stageTwoFaceEffectsHaveStableUserFacingOptions" in test),
    ("Gallery can select both photos and videos through the system photo picker", "ActivityResultContracts.PickVisualMedia()" in camera and "PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)" in camera),
    ("Gallery media is copied locally into bounded app-cache files before editing", "copyCameraGalleryMediaToCache(context, selectedUri)" in camera and "resolver.openInputStream(uri)" in camera and "MAX_CAMERA_GALLERY_IMAGE_BYTES" in camera and "MAX_CAMERA_GALLERY_VIDEO_BYTES" in camera),
    ("Gallery selections preserve the original image and route video notes correctly", "pendingOriginalUri = Uri.fromFile(original)" in camera and "if (selectedType == \"video\" && videoNoteMode) \"video_note\" else selectedType" in camera),
    ("Captured-photo editor exposes Face FX and applies the directly selected face effect", 'Text("Face FX", style = MaterialTheme.typography.labelLarge' in camera and "faceEffectOverride = option" in camera and camera.index('Text("Edit photo"') < camera.index('Text("Face FX", style = MaterialTheme.typography.labelLarge')),
    ("Newly tapped Studio effect is passed directly into photo processing", "studioEffectOverride: CameraStudioEffect? = null" in camera and "studioEffectOverride = option" in camera),
    ("Newly tapped background effect is passed directly into photo processing", "backgroundEffectOverride: CameraBackgroundEffect? = null" in camera and "backgroundEffectOverride = option" in camera),
    ("Live camera settings scroll within the screen while capture controls stay outside the scroll region", "heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.30f).verticalScroll(rememberScrollState())" in camera and "FilledIconButton(onClick" in camera and camera.index("heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.30f)") < camera.index("FilledIconButton(onClick")),
    ("Captured media preview exposes a translucent right-side rail for Looks, Face FX, Background, AI Studio and Rotate", "CameraEditorRailAction" in camera and "Modifier.align(Alignment.CenterEnd)" in camera and all(f'CameraEditorRailAction("{label}"' in camera for label in ["Looks", "Face FX", "Background", "AI Studio", "Rotate"])),
    ("Editor content scrolls within bounded height while Retake/Rotate/Next stay fixed", "WindowInsets.ime.getBottom(LocalDensity.current) > 0) 0.12f else 0.26f" in camera and "onCaptured(previewUri,previewType)" in camera and camera.index("heightIn(max = LocalConfiguration.current.screenHeightDp.dp * if (WindowInsets.ime") < camera.index("onCaptured(previewUri,previewType)")),
    ("Existing capture-to-post callback preserved", "onCaptured(previewUri,previewType)" in camera),
    ("Effects sheet places thumbnails directly below category tabs without a redundant second heading", 'effectOptions.forEach { option ->' in camera and 'listOf("Looks", "Face FX", "Background", "AI Studio")' in camera and 'Choose an effect' not in camera and 'if (mode == CameraMode.VIDEO) "Video effects" else "Choose an effect"' not in camera and 'Effect chooser uses scrollable category pills' not in camera),
    ("Video FX opens the same compact thumbnail tray rather than a separate chip row", 'CameraVideoStyle.values().map { option ->' in camera and 'if (mode == CameraMode.VIDEO) listOf("Video FX")' in camera and 'if (mode == CameraMode.VIDEO) "Video FX" else "Effects"' in camera and 'if (recording == null && !fastCapture && mode == CameraMode.VIDEO)' not in camera),
    ("Effect carousel stays in one horizontal lane on shorter Android screens", 'effectOptions.forEach { option ->' in camera and '.horizontalScroll(rememberScrollState())' in camera and 'modifier = Modifier.width(76.dp)' in camera and 'heightIn(min = 112.dp)' in camera),
    ("Photo/Video mode selector is positioned above the shutter", "Capture mode sits above the shutter" in camera and camera.index("Capture mode sits above the shutter") < camera.index("Main capture row:") < camera.index("FilledIconButton(onClick = {", camera.index("Main capture row:"))),
    ("Effects is left of the shutter and Gallery is right of the shutter", camera.index("onClick = { showEffectTray = !showEffectTray", camera.index("Main capture row:")) < camera.index("FilledIconButton(onClick = {", camera.index("Main capture row:")) < camera.index("galleryPicker.launch", camera.index("FilledIconButton(onClick = {", camera.index("Main capture row:")))),
    ("Effect chooser uses scrollable category pills", 'Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())' in camera and 'RoundedCornerShape(18.dp)' in camera and 'Scrollable category pills' in camera),
    ("Effect choices use bundled photographic thumbnails in a horizontally scrollable carousel", 'import com.fynx.app.R' in camera and 'effectOptions.forEach { option ->' in camera and 'Modifier.width(76.dp)' in camera and 'R.drawable.camera_effect_portrait_1' in camera and 'R.drawable.camera_effect_portrait_2' in camera and 'R.drawable.camera_effect_portrait_3' in camera and 'painterResource(id = cameraEffectPreviewPortraitRes(previewStyle))' in camera and 'cameraEffectPreviewMatrix(previewStyle)' in camera and 'glyph = cameraEffectGlyph' not in camera),
    ("Camera and editor docks stay above navigation with a fixed action row", "Color(0xFF172438).copy(alpha = 0.62f)" in camera and "Color(0xFF25364A).copy(alpha = 0.56f)" in camera and "Color.White.copy(alpha = 0.24f)" in camera and 'Text("Next")' in camera and "RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)" in camera and "RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)" in camera and camera.count("bottom = 76.dp") == 2 and camera.count("heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.44f)") == 1 and camera.count("heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.68f)") == 1 and camera.count(".navigationBarsPadding()") == 2),
    ("Captured-photo editor uses the same photo-based previews for every effect category", all(f'category = "{category}"' in camera for category in ("Face FX", "Background", "AI Studio", "Looks", "Video FX")) and camera.count("CameraEffectTile(") >= 6),
    ("Face FX thumbnails reuse the real sticker renderer on top of the photo preview", 'drawFynxCameraFaceEffect(drawContext.canvas.nativeCanvas, previewFace, facePreviewEffect)' in camera and 'CameraFaceEffect.values().firstOrNull { it.label == label }' in camera),
    ("Twelve portrait previews are shared by the live creator tray and photo editor", all(f"R.drawable.camera_effect_portrait_{index}" in camera for index in range(1, 13)) and "cameraEffectPreviewPortraitRes(previewStyle)" in camera and "Math.floorMod(previewStyle, 12)" in camera and portrait_vectors_present),
    ("Per-effect thumbnail grading leaves actual face/photo processing paths unchanged", "cameraEffectPreviewMatrix(previewStyle)" in camera and "applyCameraFaceEffectToPhotoFile" in camera and "applyCameraBackgroundEffectToPhotoFile" in camera and "applyFynxVideoEdits" in camera),
    ("Effects tray uses a translucent mirror surface with a light border", 'color = Color(0xFF25364A).copy(alpha = 0.56f)' in camera and 'border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.24f))' in camera),
    ("Camera self-timer overlay uses a translucent mirror surface", 'Color(0xFF25364A).copy(alpha = 0.48f)' in camera and 'Color.White.copy(alpha = 0.26f)' in camera),
    ("Video shutter exposes an explicit, state-aware Stop accessibility label", '.semantics(mergeDescendants = true)' in camera and 'contentDescription = if (recording != null) "Stop video note"' in camera),
    ("Camera quick tools sit in a fixed right-side rail with consistent icon-led controls", 'Modifier.align(Alignment.TopEnd).statusBarsPadding()' in camera and all(label in camera for label in ['Text("Flip"', 'Text("Flash"', 'Text("Grid"', 'Text("Timer"']) and 'Icons.Default.Timer' in camera and 'contentDescription = "Set capture timer"' in camera and 'Modifier.width(58.dp)' in camera),
    ("Effects and Gallery flank a centered shutter", camera.index("onClick = { showEffectTray = !showEffectTray", camera.index("Main capture row:")) < camera.index("FilledIconButton(onClick = {", camera.index("Main capture row:")) < camera.index("galleryPicker.launch", camera.index("FilledIconButton(onClick = {", camera.index("Main capture row:")))),
    ("Camera upgrade preserves Marketplace product-photo subject segmentation", 'com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1' in build),
]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit("Stage 2 camera face-effects verification failed: " + ", ".join(failed))
print("Stage 2 camera face-effects verification passed")
