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
    ("camera no longer has a separate Your Story publish action", "showYourStoryAction = false" in home and "onShareToStatus =" not in home),
    ("Status hub Add Status camera opens the shared Home camera", "onCamera = { onOpenUnifiedCamera(CameraMode.PHOTO) }" in hub and "onRecordVideo = { onOpenUnifiedCamera(CameraMode.VIDEO) }" in hub),
    ("Status timeline camera opens the shared Home camera", "onCameraClick = { onOpenUnifiedCamera(CameraMode.PHOTO) }" in hub),
    ("Status hub no longer creates a second CameraX panel", "FynxCameraCapturePanel(" not in hub),
    ("Home receives the shared camera request from Stories", 'onOpenUnifiedCamera = { mode -> homeCameraInitialMode = mode; selected = "Home"; homeCameraRequest += 1 }' in app and 'cameraInitialMode = homeCameraInitialMode' in app and 'initialMode = activeCameraInitialMode' in home),
    ("composer presents Home Feed, Status and Both targets", all(f'Text("{label}")' in home for label in ("Home Feed", "Status", "Both"))),
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
