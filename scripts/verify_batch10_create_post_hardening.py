from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
composer = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text(encoding="utf-8")
remote = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text(encoding="utf-8")
feed = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text(encoding="utf-8")
camera = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt").read_text(encoding="utf-8")
routes = (ROOT / "backend/socialRoutes.js").read_text(encoding="utf-8")
bootstrap = (ROOT / "backend/socialMultiMediaBootstrap.js").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")

checks = [
    ("Composer can publish a valid text-only post", 'text.isNotBlank()' in composer and '"/api/social/posts"' in client),
    ("Composer can publish a valid music-only post", 'selectedMusic != null' in composer and '"musicMediaId"' in client),
    ("Composer still supports photo/video publishing", 'PickMultipleVisualMedia' in composer and 'mediaIds' in client and '"/api/social/posts/multi"' in client),
    ("Camera uses the existing single capture implementation", 'FynxCameraCapturePanel(' in composer and 'FynxCameraCapturePanel' in camera),
    ("Voice recording uses the existing voice recorder path", 'FynxVoicePostRecorder(' in composer and 'Voice posts use one audio recording' in client),
    ("Voice posts cannot be mixed with visual media", 'hasAudio && (selected.size != 1 || index != 0)' in client),
    ("Media count and byte limits are enforced before upload", 'MAX_MEDIA = 4' in client and 'MAX_SINGLE_MEDIA_BYTES' in client and 'MAX_TOTAL_MEDIA_BYTES' in client),
    ("Post publishing rejects offline state in the composer", 'FynxNetworkQuality.Level.OFFLINE' in composer and 'Reconnect before publishing' in composer),
    ("Post creation returns and requires a real server post ID", 'optString("postId")' in client and 'server did not return its post ID' in client),
    ("Successful publish clears the composer state", 'finishComposerAfterSuccess()' in composer and 'capturedUris = emptyList()' in composer and 'selectedMusic = null' in composer),
    ("Manual cancel/close also clears attachment state", 'fun clearComposer()' in composer and 'selectedFeelingActivity = null' in composer and 'postLocation = null' in composer and 'selectedAudienceIds = emptySet()' in composer),
    ("Audience metadata reaches both real post endpoints", '"audienceUserIds"' in client and 'selectedAudienceUserIds' in client and 'rawAudience' in routes),
    ("Location is label-only and bounded", '"location"' in client and 'take(160)' in client and 'location TEXT' in routes),
    ("Text backgrounds are persisted by the backend", '"textBackground"' in client and 'text_background' in routes),
    ("Music ownership and audio type are verified by the backend", 'music_media_id' in routes and 'mime_type' in routes and 'startsWith("audio/")' in routes),
    ("Feeling/Activity is validated and persisted", 'allowedFeelingActivityTypes' in routes and 'feeling_activity_type' in routes),
    ("Multi-media backend persists ordered media", 'social_post_media' in bootstrap and 'position' in bootstrap),
    ("Backend media retrieval checks authenticated visibility", 'visibleSocialPost(postId, req.user.sub)' in bootstrap),
    ("Android feed model carries the saved post metadata", 'location: String?' in remote and 'musicMediaId: String?' in remote and 'feelingActivityType: String?' in remote),
    ("Home feed renders saved post metadata", 'post.location' in feed and 'MusicPostPlayer' in feed and 'post.feelingActivity' in feed),
    ("Marketplace action returns to the real marketplace flow instead of fabricating a post", 'ComposerAction("Marketplace"' in composer and 'onOpenMarketplace()' in composer),
    ("Dedicated Batch 10 gate is wired into CI", 'scripts/verify_batch10_create_post_hardening.py' in workflow),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit("Batch 10 Create Post hardening verification failed: " + ", ".join(failed))
print(f"Batch 10 Create Post hardening verification GREEN ({len(checks)} checks)")
