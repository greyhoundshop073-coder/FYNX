#!/usr/bin/env python3
"""Large Badge #5: camera/media reliability and full-media display certification."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
remote = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteMedia.kt").read_text(encoding="utf-8")
camera = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt").read_text(encoding="utf-8")
status = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxStatusTimelinePanel.kt").read_text(encoding="utf-8")
home_media = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomePostMediaClient.kt").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")

remote_checks = {
    "authenticated backend media download": "FynxBackendClient.downloadToFile" in remote,
    "account-scoped media cache": "FynxAuthStore.accountStorageKey" in remote,
    "bounded remote media size": "MAX_REMOTE_MEDIA_BYTES" in remote,
    "image/video rendering": "FynxRemoteMedia" in remote and "VideoView" in remote,
    "audio rendering": "FynxRemoteAudio" in remote and "MediaPlayer" in remote,
    "retry state": "TextButton(onClick = { reloadNonce++ })" in remote,
    "video cleanup": "stopPlayback()" in remote,
    "audio cleanup": "player?.release()" in remote,
}
camera_checks = {
    "CameraX capture": "ImageCapture" in camera and "VideoCapture<Recorder>" in camera,
    "camera permissions": "Manifest.permission.CAMERA" in camera,
    "audio permission for video": "Manifest.permission.RECORD_AUDIO" in camera,
    "lens switch": "Cameraswitch" in camera,
    "flash": "enableTorch" in camera,
    "zoom": "setZoomRatio" in camera,
    "exposure": "setExposureCompensationIndex" in camera,
    "timer": "captureTimerSeconds" in camera,
    "preview and send": "pendingUri" in camera and "onCaptured(previewUri,previewType)" in camera,
    "retake": "retake()" in camera,
}
surface_checks = {
    "Status uses shared remote media": "FynxRemoteMedia(" in status,
    "Status video completion": "onVideoCompleted" in status,
    "Home uses real media manifest": "optJSONArray(\"media\")" in home_media,
    "Home preserves media ordering": "sortedBy { it.position }" in home_media,
    "runtime instrumentation remains": "connectedDebugAndroidTest" in workflow,
    "camera/media gate remains": "verify_stage16_camera.py" in workflow,
    "camera-to-post gate remains": "verify_camera_post_journey.py" in workflow,
}

failed = []
for group, checks in (("remote-media", remote_checks), ("camera", camera_checks), ("surfaces", surface_checks)):
    for name, ok in checks.items():
        print(f"{'PASS' if ok else 'FAIL'}: {group}: {name}")
        if not ok:
            failed.append(f"{group}: {name}")

if failed:
    raise SystemExit("LARGE BADGE #5 RED: " + "; ".join(failed))

print("LARGE BADGE #5 MEDIA/CAMERA CONTRACT: GREEN")
