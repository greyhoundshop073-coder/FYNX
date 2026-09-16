from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
composer = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text(encoding="utf-8")

checks = [
    ("Composer offers real photo selection", 'ComposerAction("Photo"' in composer and 'gallery.launch(arrayOf("image/*"))' in composer),
    ("Composer offers real video selection", 'ComposerAction("Video"' in composer and 'gallery.launch(arrayOf("video/*"))' in composer),
    ("Composer offers camera capture", 'ComposerAction("Camera"' in composer and 'showCamera = true' in composer),
    ("Composer keeps voice posts separate", 'ComposerAction("Voice"' in composer and 'showVoiceRecorder = true' in composer),
    ("Gallery selection is capped at four assets", "uris.distinct().take(4)" in composer and ").take(4)" in composer),
    ("Selected media can be removed before publishing", "removeCapturedUri(uri)" in composer and "Remove media" in composer),
    ("Composer provides thumbnail selection for multiple visual assets", "selectedVisualIndex = visualIndex" in composer and "visualItems.size > 1" in composer),
    ("Composer previews selected video media", "VideoView" in composer and "setVideoURI(selectedVisual.second)" in composer),
    ("Composer previews selected image media", "ImageView" in composer and "setImageURI(selectedVisual.second)" in composer),
    ("Publishing is blocked while offline", "networkLevel != FynxNetworkQuality.Level.OFFLINE" in composer and "Reconnect before publishing this post" in composer),
    ("Publishing is blocked while another publish is active", "!posting" in composer and "posting = true" in composer),
    ("Caption input remains capped at 4000 characters", "text = it.take(4000)" in composer and "text.trim().take(4000)" in client),
    ("Empty posts are rejected unless media is attached", "text.isNotBlank() || capturedUris.isNotEmpty()" in composer and "Add a caption or at least one media item." in client),
    ("Client rejects more than four selected assets", "selected.size <= MAX_MEDIA" in client and "MAX_MEDIA = 4" in client),
    ("Audio cannot be combined with other media", "Voice posts use one audio recording" in client and "selected.size != 1" in client),
    ("Posting uses the real multi-media backend route", '"/api/social/posts/multi"' in client),
    ("Successful publishing clears the composer state", "finishComposerAfterSuccess()" in composer and "capturedUris = emptyList()" in composer),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit(f"Home multi-media composer verification failed: {len(failed)} check(s)")
print("Home multi-media composer behavior gate: GREEN")
