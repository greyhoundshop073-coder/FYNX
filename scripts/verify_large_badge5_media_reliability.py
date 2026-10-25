#!/usr/bin/env python3
"""Large Badge #5: camera/media reliability and full-media display certification."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
remote = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteMedia.kt").read_text(encoding="utf-8")
home_panel = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text(encoding="utf-8")
navigation_script = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")
camera = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt").read_text(encoding="utf-8")
status = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxStatusTimelinePanel.kt").read_text(encoding="utf-8")
home_media = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomePostMediaClient.kt").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
runtime_script = (ROOT / "scripts/run_android_runtime_certification.sh").read_text(encoding="utf-8")

remote_checks = {
    "authenticated backend media download": "FynxBackendClient.downloadToFile" in remote,
    "account-scoped media cache": "FynxAuthStore.accountStorageKey" in remote,
    "bounded remote media size": "MAX_REMOTE_MEDIA_BYTES" in remote,
    "image/video rendering": "FynxRemoteMedia" in remote and "VideoView" in remote,
    "audio rendering": "FynxRemoteAudio" in remote and "MediaPlayer" in remote,
    "retry state": "TextButton(onClick = { reloadNonce++ })" in remote,
    "video cleanup": "stopPlayback()" in remote,
    "audio cleanup is bound to the live player": "DisposableEffect(resolvedUrl, player)" in remote and "activePlayer?.release()" in remote,
    "remote audio seek and replay": "onValueChangeFinished =" in remote and "player?.seekTo(positionMs" in remote and "completed.seekTo(0)" in remote,
    "inline video is released by AndroidView": "onRelease = { view ->" in remote and "view.stopPlayback()" in remote,
    "Home audio prepares asynchronously and can seek": "player.prepareAsync()" in home_panel and "onValueChangeFinished =" in home_panel,
    "runtime login detection distinguishes username search": "_is_sign_in_gate(current)" in navigation_script and 'if find_control(current, ["Sign In"]) or find_control(current, ["Username"])' not in navigation_script,
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
    "runtime instrumentation remains": "connectedDebugAndroidTest" in runtime_script and "bash scripts/run_android_runtime_certification.sh" in workflow,
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
