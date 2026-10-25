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

voice_recorder = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxVoicePostRecorder.kt").read_text(encoding="utf-8")
viewer = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxPostMediaViewer.kt").read_text(encoding="utf-8")
media_client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text(encoding="utf-8")

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
    "voice preview prepares asynchronously": "prepareAsync()" in voice_recorder and "MediaPlayer().apply { setDataSource(file.absolutePath); prepare() }" not in voice_recorder,
    "voice publication waits for a decoded nonempty preview": "previewReady && !previewLoading && previewPlayer != null" in voice_recorder and "previewDurationMs > 0L" in voice_recorder and "enabled = previewReady" in voice_recorder,
    "failed voice recorder start cleans up partial files and recorder": "candidate?.runCatching { reset() }" in voice_recorder and "candidate?.runCatching { release() }" in voice_recorder and "file.delete()" in voice_recorder,
    "stop failure invalidates the recording": "stopSucceeded && file?.isFile == true" in voice_recorder,
    "audio media opens with the shared audio player in the post viewer": 'item.mediaType.equals("audio", true) ->' in viewer and "FynxRemoteAudio(item.mediaUrl" in viewer,
    "viewer video tears down at AndroidView release": "onRelease = { view ->" in viewer and "view.stopPlayback()" in viewer,
    "multi-media selection validates before upload begins": "val validated = selected.mapIndexed" in media_client and media_client.index("val validated = selected.mapIndexed") < media_client.index("FynxProductionMessaging.uploadMedia"),
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
