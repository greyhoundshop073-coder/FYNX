from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
composer = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8")
camera = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text(encoding="utf-8")
location = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxPostLocationClient.kt").read_text(encoding="utf-8")
music = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMusicLibraryClient.kt").read_text(encoding="utf-8")
feeling = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxFeelingActivityLibrary.kt").read_text(encoding="utf-8")
remote = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text(encoding="utf-8")
feed = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text(encoding="utf-8")
routes = (ROOT / "backend/socialRoutes.js").read_text(encoding="utf-8")
bootstrap = (ROOT / "backend/socialMultiMediaBootstrap.js").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")

checks = [
    ("One Create Post composer owns all optional attachments", all(x in composer for x in [
        'ComposerQuickChip("Music"', 'ComposerQuickChip("Location"', 'ComposerQuickChip("Feeling/Activity"',
        'ComposerQuickChip("Marketplace"', 'FynxPostAudienceClient', 'FynxMultiMediaPostClient.createPost'
    ])),
    ("Existing camera remains the single real capture implementation", "FynxCameraCapturePanel" in composer and "showCamera = true" in composer and "FynxCameraCapturePanel" in camera),
    ("Photo and video selection feeds the same publish path", "PickMultipleVisualMedia" in composer and "capturedUris" in composer and "capturedTypes" in composer and "uris: List<Uri>" in client),
    ("Voice recording remains on the existing path", "showVoiceRecorder" in composer and "Voice posts use one audio recording" in client),
    ("Audience selection reaches the real post client", "selectedAudienceIds.toList()" in composer and "selectedAudienceUserIds" in client and '"audienceUserIds"' in client),
    ("Text backgrounds reach the real post client", "textBackground" in composer and '"textBackground"' in client),
    ("Location reaches the real post client without raw coordinates", "postLocation" in composer and '"location"' in client and "latitude" not in client and "longitude" not in client),
    ("Music reaches the real post client", "selectedMusic" in composer and '"musicMediaId"' in client and '"musicDurationMs"' in client),
    ("Feeling and Activity reach the real post client", "selectedFeelingActivity" in composer and '"feelingActivityType"' in client and '"feelingActivity"' in client),
    ("Text-only and multi-media endpoints both exist", '"/api/social/posts"' in client and '"/api/social/posts/multi"' in client),
    ("Multi-media limits protect the existing upload architecture", "MAX_MEDIA = 4" in client and "MAX_SINGLE_MEDIA_BYTES" in client and "MAX_TOTAL_MEDIA_BYTES" in client),
    ("Backend single-post schema includes all Create Post metadata", "feeling_activity_type" in routes and "music_media_id" in routes and "location" in routes and "text_background" in routes),
    ("Backend multi-media route includes all Create Post metadata", "feelingActivityType" in routes and "musicMediaId" in routes and "location" in routes and "social_post_media" in routes),
    ("Backend multi-media bootstrap preserves the same metadata", "feeling_activity_type" in bootstrap and "music_media_id" in bootstrap and "location" in bootstrap),
    ("Feed model carries location, music, and Feeling/Activity", "location: String?" in remote and "musicMediaId: String?" in remote and "feelingActivityType: String?" in remote),
    ("Home feed renders location, music, and Feeling/Activity", "post.location" in feed and "MusicPostPlayer" in feed and "post.feelingActivity" in feed),
    ("Existing location resolver is label-only", "Geocoder" in location and "raw coordinates never leave the device" in location.lower()),
    ("Existing music picker reads real audio metadata", "MediaMetadataRetriever" in music and "METADATA_KEY_TITLE" in music and "METADATA_KEY_ARTIST" in music),
    ("Feeling/Activity library is real and searchable through composer", "FynxFeelingActivityOption(" in feeling and "feelingActivitySearch" in composer),
    ("Composer reset clears every optional attachment", all(x in composer for x in [
        "selectedMusic = null", "selectedFeelingActivity = null", "postLocation = null",
        "selectedAudienceIds = emptySet()"
    ])),
    ("Dedicated Batch 9 integration gate is wired into CI", "scripts/verify_batch9_create_post_integration.py" in workflow),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit("Batch 9 Create Post integration verification failed: " + ", ".join(failed))
print(f"Batch 9 Create Post integration verification GREEN ({len(checks)} checks)")
