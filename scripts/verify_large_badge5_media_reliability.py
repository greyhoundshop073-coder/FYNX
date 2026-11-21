#!/usr/bin/env python3
"""Large Badge #5: camera/media reliability and full-media display certification."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")
remote = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteMedia.kt").read_text(encoding="utf-8")
camera = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt").read_text(encoding="utf-8")
status = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxStatusTimelinePanel.kt").read_text(encoding="utf-8")
home_media = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomePostMediaClient.kt").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
runtime_script = (ROOT / "scripts/run_android_runtime_certification.sh").read_text(encoding="utf-8")
emulator_wrapper = (ROOT / "scripts/run_android_runtime_emulator_certification.sh").read_text(encoding="utf-8")

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
    "preview and Next hand-off": "pendingUri" in camera and 'Text("Next")' in camera and 'onClick = { finishCameraMedia(toStatus = false) }' in camera and "onCaptured(finalUri, mediaType)" in camera and "renderFynxPhotoTextOverlays(context, current, cameraTextOverlays)" in camera and "applyFynxVideoEdits(context, source, videoStyle, videoCaptionText, visibleText)" in camera,
    "retake": "retake()" in camera,
}
surface_checks = {
    "Status uses shared remote media": "FynxRemoteMedia(" in status,
    "Status video completion": "onVideoCompleted" in status,
    "Home uses real media manifest": "optJSONArray(\"media\")" in home_media,
    "Home preserves media ordering": "sortedBy { it.position }" in home_media,
    "runtime instrumentation remains": "connectedDebugAndroidTest" in runtime_script and "bash scripts/run_android_runtime_emulator_certification.sh" in workflow and "bash scripts/run_android_runtime_certification.sh" in emulator_wrapper,
    "camera/media gate remains": "verify_stage16_camera.py" in workflow,
    "camera-to-post gate remains": "verify_camera_post_journey.py" in workflow,
}


sampling_checks = {
    "shared bitmap sampler checks bounds before allocation": "inJustDecodeBounds = true" in read("app/src/main/java/com/fynx/app/ui/FynxBitmapSampling.kt") and "computeInSampleSize" in read("app/src/main/java/com/fynx/app/ui/FynxBitmapSampling.kt"),
    "media cache uses shared bounded sampling": "FynxBitmapSampling.computeInSampleSize" in read("app/src/main/java/com/fynx/app/ui/FynxMediaCache.kt"),
    "remote Home feed previews use sampled decoding": "FynxBitmapSampling.decodeFileSampled" in read("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt"),
    "post media viewer uses sampled decoding": "FynxBitmapSampling.decodeFileSampled" in read("app/src/main/java/com/fynx/app/ui/FynxPostMediaViewer.kt"),
    "own and other profile photos use sampled decoding": "FynxBitmapSampling.decodeUriSampled" in read("app/src/main/java/com/fynx/app/ui/ProfilePanel.kt") and "FynxBitmapSampling.decodeUriSampled" in read("app/src/main/java/com/fynx/app/ui/OtherUserProfilePanel.kt"),
    "saved post opens the author's username profile": "onOpenAuthorProfile(entry.post.authorUsername.removePrefix" in read("app/src/main/java/com/fynx/app/ui/FynxSavedPostsPanel.kt"),
    "passive video allows parent feed scrolling": "return false" in read("app/src/main/java/com/fynx/app/ui/FynxPassiveVideoView.kt"),
}

failed = []
for group, checks in (("remote-media", remote_checks), ("camera", camera_checks), ("surfaces", surface_checks), ("preview-sampling", sampling_checks)):
    for name, ok in checks.items():
        print(f"{'PASS' if ok else 'FAIL'}: {group}: {name}")
        if not ok:
            failed.append(f"{group}: {name}")

if failed:
    raise SystemExit("LARGE BADGE #5 RED: " + "; ".join(failed))

print("LARGE BADGE #5 MEDIA/CAMERA CONTRACT: GREEN")
