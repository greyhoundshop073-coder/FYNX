from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

camera = read("app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt")
home = read("app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt")
remote = read("app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt")
manifest = read("app/src/main/AndroidManifest.xml")

checks = [
    ("camera permission", 'Manifest.permission.CAMERA' in camera and 'android.permission.CAMERA' in manifest),
    ("microphone permission", 'Manifest.permission.RECORD_AUDIO' in camera and 'android.permission.RECORD_AUDIO' in manifest),
    ("front/back camera switching", 'LENS_FACING_FRONT' in camera and 'Cameraswitch' in camera),
    ("photo capture", 'ImageCapture' in camera and 'takePicture' in camera),
    ("video capture", 'VideoCapture' in camera and 'VideoRecordEvent.Finalize' in camera),
    ("capture preview", 'pendingUri' in camera and 'Preview before' not in camera),
    ("photo filters", 'CameraFilter' in camera and 'FilterChip' in camera),
    ("photo rotation editing", 'rotatePhoto' in camera and 'Rotate' in camera),
    ("AI photo enhancement", 'FynxAiPhotoEnhancer.enhance' in camera),
    ("camera result connected to post composer", 'FynxCameraCapturePanel' in home and 'showComposer = true' in home),
    ("caption composer", 'OutlinedTextField' in home and 'text.take(4000)' in home),
    ("media upload and social post", 'uploadMedia' in remote and 'createPost' in remote and '/api/social/posts' in remote),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")

if failed:
    raise SystemExit("Stage 16 camera/media verification failed: " + ", ".join(failed))

print("Stage 16 camera/media verification passed")
