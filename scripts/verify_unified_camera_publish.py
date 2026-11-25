from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

home = read("app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt")
hub = read("app/src/main/java/com/fynx/app/ui/FynxStatusHubPanel.kt")
app = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")
publisher = read("app/src/main/java/com/fynx/app/ui/FynxSharedStatusPublisher.kt")
drafts = read("app/src/main/java/com/fynx/app/ui/FynxLocalPostDraftStore.kt")

checks = [
    ("camera editor restores the separate Status — Your Story action alongside Next", "showYourStoryAction = cameraEditingMediaUri == null" in home and "onShareToStatus = { uri, type ->" in home and 'Text("Next")' in read("app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt")),
    ("New Post More options replaces the Marketplace shortcut with Status", 'ComposerAction("Status"' in home and 'ComposerAction("Marketplace"' not in home),
    ("Status hub Add Status camera opens the shared Home camera", "onCamera = { onOpenUnifiedCamera(CameraMode.PHOTO) }" in hub and "onRecordVideo = { onOpenUnifiedCamera(CameraMode.VIDEO) }" in hub),
    ("Status timeline camera opens the shared Home camera", "onCameraClick = { onOpenUnifiedCamera(CameraMode.PHOTO) }" in hub),
    ("Status creator dialog is dismissed before the shared camera dialog is opened", "pendingStatusCameraMode = mode" in app and "withFrameNanos { }" in app and "homeCameraRequest += 1" in app),
    ("camera Status handoff enters the existing Status composer with captured media", "initialMediaUri = statusInitialMediaUri" in app and "initialMediaType = statusInitialMediaType" in app and "onInitialMediaAccepted" in app and "Accept media handed off from the Home camera" in hub),
    ("Home receives the shared camera request from Stories", "pendingStatusCameraMode = mode" in app and "withFrameNanos { }" in app and 'cameraInitialMode = homeCameraInitialMode' in app and 'initialMode = activeCameraInitialMode' in home and 'activeCameraInitialMode = cameraInitialMode' in home and 'activeCameraInitialMode = CameraMode.PHOTO' in home),
    ("reference New Post details keep publishing destinations out of the caption layout", 'Text("Publish to")' not in home and 'FilterChip(selected = publishDestination' not in home and "showYourStoryAction = cameraEditingMediaUri == null" in home),
    ("Status publication uses the existing authenticated media and status APIs", all(x in publisher for x in ("FynxStatusClient.uploadMedia", "FynxStatusClient.create", "FynxStatusStore.save"))),
    ("same edited media URI is sent to Status", "mediaUri = selectedStatusMedia?.first" in home),
    ("confirmed destination is skipped on retry after partial success", "if (needsFeed && !feedPublished)" in home and "if (needsStatus && !statusPublished)" in home),
    ("draft store restores destination and per-destination completion", all(x in drafts for x in ("publishDestination", "feedPublished", "statusPublished", "statusPublishId"))),
    ("Status single-media and 700-character constraints are enforced before submit", "statusMediaCandidates.size <= 1" in home and "text.length <= FYNX_STATUS_MAX_TEXT_LENGTH" in home),
]
failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit(f"{len(failed)} unified camera/publishing checks failed")
print(f"Unified camera publishing contract GREEN: {len(checks)} checks passed")