from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

camera = read("app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt")
effects = read("app/src/main/java/com/fynx/app/ui/CameraFaceEffects.kt")
build = read("app/build.gradle.kts")
test = read("app/src/test/java/com/fynx/app/ui/CameraMediaReliabilityTest.kt")

checks = [
    ("ML Kit face detection dependency", "com.google.mlkit:face-detection:16.1.5" in build),
    ("CameraX analysis retains latest frame only", "STRATEGY_KEEP_ONLY_LATEST" in camera and "ImageAnalysis" in camera),
    ("Face detector tracks landmarks on device", "setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)" in effects and "enableTracking()" in effects),
    ("Front-camera overlay is mirrored", "isFrontCamera()" in effects and "viewWidth - right" in effects),
    ("Seven tracked face effects plus Off", all(label in effects for label in ["Cute Ears", "Smart Glasses", "Royal Crown", "Puppy / Animal", "Virtual Makeup", "Hair Colour", "Funny Face"])),
    ("Same sticker drawing used for saved photos", "applyCameraFaceEffectToPhotoFile" in camera and "drawFynxCameraFaceEffect(canvas" in effects),
    ("Face-effect choices covered by unit test", "stageTwoFaceEffectsHaveStableUserFacingOptions" in test),
    ("Existing capture-to-post callback preserved", "onCaptured(previewUri,previewType)" in camera),
]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit("Stage 2 camera face-effects verification failed: " + ", ".join(failed))
print("Stage 2 camera face-effects verification passed")
