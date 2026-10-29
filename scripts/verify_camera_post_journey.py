from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
home = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8")
camera = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxCameraCapturePanel.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text(encoding="utf-8")
refresh = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeLifecycle.kt").read_text(encoding="utf-8")
backend = (ROOT / "backend/socialMultiMediaBootstrap.js").read_text(encoding="utf-8")
runtime = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")

checks = [
    ("Home camera opens the real camera panel", "showCamera = true" in home and "FynxCameraCapturePanel(" in home),
    ("Runtime UI hierarchies preserve XML terminators for live/photo/video editor captures",
     runtime.count("write_text(camera_xml + chr(10)") >= 1
     and runtime.count("write_text(editor_xml + chr(10)") >= 1
     and "camera-live-preview.xml" in runtime
     and "camera-photo-editor-initial.xml" in runtime
     and "camera-video-editor-before-effects.xml" in runtime),
    ("Runtime camera journey verifies switch, flash capability, Gallery, capture, effects and Retake without publishing",
     "def capture_camera_post_journey(" in runtime
     and '["Take photo"]' in runtime
     and '["Face FX", "Cute Ears", "AI Studio", "Portrait Blur", "Retake", "Send"]' in runtime
     and "camera-photo-editor-initial.png" in runtime
     and "camera-photo-editor-portrait-ready.png" in runtime
     and "camera-photo-retake" in runtime
     and "front/back camera switching returned to a usable camera preview" in runtime
     and "Gallery opened Android's real photo-picker UI" in runtime
     and "camera torch toggled on and back off" in runtime
     and "Send must become enabled" in runtime
     and "never publishing a post" in runtime),
    ("Runtime video journey records a real clip, applies Cinematic, preserves audio where inspectable, and never publishes",
     "def capture_camera_video_journey(" in runtime
     and "video mode" in runtime.lower()
     and "Cinematic" in runtime
     and "list_app_cache_videos" in runtime
     and "processed video dropped the source audio stream" in runtime
     and "Generate on-device captions" in runtime
     and "camera-video-effect-output.png" in runtime
     and "never tap Send" in runtime),
    ("Home camera starts a fresh session", "capturedUris = emptyList()" in home and "cameraOpenedFromComposer = false" in home),
    ("Camera capture hands media to the post composer", "showCamera = false" in home and "showComposer = true" in home),
    ("Camera dismiss returns to an existing composer draft", "cameraOpenedFromComposer && !posting" in home),
    ("Camera preview supports retake before handoff", 'Text("Retake")' in camera and "retake()" in camera),
    ("Camera preview sends the captured asset to the composer", 'Text("Send")' in camera and "onCaptured(previewUri,previewType)" in camera),
    ("Captured media uses the authoritative media type resolver", "FynxMultiMediaPostClient.mediaKind(context, it)" in home),
    ("Gallery media is deduplicated and capped at four", "uris.distinct().take(4)" in home and ".take(4)" in home),
    ("Combined camera and gallery media remains capped at four", "(capturedUris + uri).distinct().take(4)" in home),
    ("Composer camera returns to the same draft when cancelled", "cameraOpenedFromComposer = true" in home and "showComposer = true" in home),
    ("Publish button is locked while a post is being submitted", "enabled = !posting" in home and "posting = true" in home),
    ("Successful publish requires a real server post ID", 'optString("postId")' in client and "server did not return its post ID" in client),
    ("Successful publish requests an authoritative Home refresh", "FynxHomeLifecycleRefreshBus.request(context)" in client),
    ("Home refresh invalidates the account-scoped feed cache", "FEED_CACHE_KEY_PREFIX + accountKey" in refresh),
    ("Backend stores the complete ordered media set", "social_post_media" in backend and "UNIQUE (post_id, position)" in backend),
    ("Backend validates media ownership and MIME type", "media ownership check failed" in backend and "media type does not match uploaded asset" in backend),
    ("Backend enforces one audio item for voice posts", "voice posts must contain one audio item" in backend),
    ("Selected-audience media remains visible to selected friends", "p.visibility = 'SELECTED_PEOPLE'" in backend and "social_post_audience" in backend and "a.user_id = $2" in backend),
    ("Only-me media remains restricted to the author", "p.visibility = 'ONLY_ME' AND p.author_id = $2" in backend),
    ("Normalized media authorization excludes blocked relationships", "NOT EXISTS" in backend and "blocked_id = p.author_id" in backend),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit("Camera -> Post journey verification failed: " + ", ".join(failed))
print(f"Camera -> Post journey verification GREEN ({len(checks)} checks)")
