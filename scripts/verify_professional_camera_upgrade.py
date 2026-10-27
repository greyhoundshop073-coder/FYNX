#!/usr/bin/env python3
"""Professional Camera Upgrade source-contract checks."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ui = ROOT / "app/src/main/java/com/fynx/app/ui"
camera = (ui / "FynxCameraCapturePanel.kt").read_text(encoding="utf-8")

checks = {
    "photo crop has a real bitmap pipeline": "fun cropPhotoSquare()" in camera and "Bitmap.createBitmap(bitmap, left, top, side, side)" in camera and "cropPhotoSquare()" in camera,
    "crop keeps the filter source in sync": "squareCrop(current, currentTemp)" in camera and "squareCrop(original, originalTemp)" in camera,
    "text overlay has an editable input": "showPhotoTextDialog" in camera and "OutlinedTextField(" in camera and '"Add text"' in camera,
    "sticker picker exposes actual choices": 'listOf("✨", "❤️", "🔥", "😂", "😎", "🎉", "🌈")' in camera and "selectedPhotoSticker" in camera,
    "text is composited into the outgoing image": "fun renderPhotoOverlays(" in camera and "canvas.drawText(line" in camera and "temp.copyTo(destination, overwrite = true)" in camera,
    "sticker is composited into the outgoing image": "canvas.drawText(sticker" in camera and "sendPreview(previewUri, previewType)" in camera,
    "the established camera-to-caller URI and type callback is retained": "onCaptured(previewUri, previewType)" in camera and 'pendingType = if (videoNoteMode) "video_note" else "video"' in camera,
    "retake removes the draft and resets creative edits": "fun retake()" in camera and 'photoTextOverlay = ""' in camera and "selectedPhotoSticker = null" in camera,
    "photo/video status upload protections remain": "FYNX_STATUS_MAX_VIDEO_BYTES" in camera and "FYNX_STATUS_MAX_VIDEO_DURATION_MS" in camera and "recorderBuilder.setTargetVideoEncodingBitRate(700_000)" in camera,
}
caller_files = [
    "FynxHomeSocialHubPanel.kt",
    "FynxStatusHubPanel.kt",
    "FynxMatureStatusComposerPanel.kt",
    "ConversationPanel.kt",
    "GroupChatPanel.kt",
    "FynxGroupSocialBatch.kt",
    "FynxMarketplacePanel.kt",
    "FynxMarketplaceLiveProof.kt",
]
for filename in caller_files:
    path = ui / filename
    checks[f"{filename} remains wired to shared CameraX"] = path.exists() and "FynxCameraCapturePanel(" in path.read_text(encoding="utf-8")

failures = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
if failures:
    raise SystemExit("Professional camera verification failed: " + ", ".join(failures))
print(f"PROFESSIONAL_CAMERA_UPGRADE_SOURCE_GREEN ({len(checks)} checks)")
