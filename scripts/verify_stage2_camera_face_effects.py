from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

camera = read("app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt")
video = read("app/src/main/java/com/fynx/app/ui/CameraVideoEffects.kt")
effects = read("app/src/main/java/com/fynx/app/ui/CameraFaceEffects.kt")
build = read("app/build.gradle.kts")
test = read("app/src/test/java/com/fynx/app/ui/CameraMediaReliabilityTest.kt")

checks = [
    ("ML Kit face detection dependency", "com.google.mlkit:face-detection:16.1.5" in build),
    ("Speech captions use the SDK version with file AudioSource support", "com.google.mlkit:genai-speech-recognition:1.0.0-alpha2" in build and "com.google.mlkit.genai.common.audio.AudioSource" in video and "AudioSource.fromFile(audio)" in video),
    ("Kotlin 2.3 uses compilerOptions DSL and no fatal legacy kotlinOptions", "kotlin {\n    compilerOptions {" in build and "JvmTarget.fromTarget(\"17\")" in build and "optIn.add(\"androidx.compose.foundation.ExperimentalFoundationApi\")" in build and "kotlinOptions {" not in build and "freeCompilerArgs +=" not in build),
    ("CameraX analysis retains latest frame only", "STRATEGY_KEEP_ONLY_LATEST" in camera and "ImageAnalysis" in camera),
    ("Face detector tracks landmarks on device", "setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)" in effects and "enableTracking()" in effects),
    ("Front-camera overlay is mirrored", "isFrontCamera()" in effects and "viewWidth - right" in effects),
    ("Seven tracked face effects plus Off", all(label in effects for label in ["Cute Ears", "Smart Glasses", "Royal Crown", "Puppy / Animal", "Virtual Makeup", "Hair Colour", "Funny Face"])),
    ("Face effects give a clear error when no face is detected instead of silently doing nothing", "require(faces.isNotEmpty())" in effects and "No clear face was found. Try a front-facing photo in good light." in effects and "if (faces.isEmpty()) return" not in effects),
    ("Same sticker drawing used for saved photos", "applyCameraFaceEffectToPhotoFile" in camera and "drawFynxCameraFaceEffect(canvas" in effects),
    ("Face-effect choices covered by unit test", "stageTwoFaceEffectsHaveStableUserFacingOptions" in test),
    ("Gallery can select both photos and videos through the system photo picker", "ActivityResultContracts.PickVisualMedia()" in camera and "PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)" in camera),
    ("Gallery media is copied locally into bounded app-cache files before editing", "copyCameraGalleryMediaToCache(context, selectedUri)" in camera and "resolver.openInputStream(uri)" in camera and "MAX_CAMERA_GALLERY_IMAGE_BYTES" in camera and "MAX_CAMERA_GALLERY_VIDEO_BYTES" in camera),
    ("Gallery selections preserve the original image and route video notes correctly", "pendingOriginalUri = Uri.fromFile(original)" in camera and "if (selectedType == \"video\" && videoNoteMode) \"video_note\" else selectedType" in camera),
    ("Captured-photo editor exposes Face FX and applies the directly selected face effect", 'Text("Face FX", style = MaterialTheme.typography.labelLarge' in camera and "faceEffectOverride = option" in camera and camera.index('Text("Edit photo"') < camera.index('Text("Face FX", style = MaterialTheme.typography.labelLarge')),
    ("Newly tapped Studio effect is passed directly into photo processing", "studioEffectOverride: CameraStudioEffect? = null" in camera and "studioEffectOverride = option" in camera),
    ("Newly tapped background effect is passed directly into photo processing", "backgroundEffectOverride: CameraBackgroundEffect? = null" in camera and "backgroundEffectOverride = option" in camera),
    ("Live camera settings scroll within the screen while capture controls stay outside the scroll region", "heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.54f).verticalScroll(rememberScrollState())" in camera and "FilledIconButton(onClick={" in camera and camera.index("heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.54f)") < camera.index("FilledIconButton(onClick={")),
    ("Photo and video editing controls scroll above the persistent Retake/Send footer", "heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.55f).verticalScroll(rememberScrollState())" in camera and "onCaptured(previewUri,previewType)" in camera and camera.index("heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.55f)") < camera.index("onCaptured(previewUri,previewType)")),
    ("Existing capture-to-post callback preserved", "onCaptured(previewUri,previewType)" in camera),
]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit("Stage 2 camera face-effects verification failed: " + ", ".join(failed))
print("Stage 2 camera face-effects verification passed")
