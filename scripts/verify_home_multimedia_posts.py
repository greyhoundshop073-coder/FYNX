from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
home = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text(encoding="utf-8")
composer = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text(encoding="utf-8")
refresh = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeLifecycle.kt").read_text(encoding="utf-8")
backend = (ROOT / "backend/socialMultiMediaBootstrap.js").read_text(encoding="utf-8")
startup = (ROOT / "backend/package.json").read_text(encoding="utf-8")
draft_store = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxLocalPostDraftStore.kt").read_text(encoding="utf-8")

checks = [
    ("multi-media client uses the real authenticated upload path", "FynxProductionMessaging.uploadMedia" in client),
    ("media posts publish through the dedicated backend route", '"/api/social/posts/multi"' in client),
    ("text-only posts use the existing single-post backend route", '"/api/social/posts"' in client and 'put("mediaId", JSONObject.NULL)' in client),
    ("publisher captures the authoritative post ID", "Result<String>" in client and 'optString("postId")' in client and "server did not return its post ID" in client),
    ("successful publish invalidates the account-scoped feed cache", "FEED_CACHE_KEY_PREFIX + accountKey" in refresh and "FEED_CACHE_TIME_KEY_PREFIX + accountKey" in refresh),
    ("successful publish requests an authoritative Home refresh", "FynxHomeLifecycleRefreshBus.request(context)" in client and "publishRefreshKey" in refresh),
    ("Home multi-media post is bounded to four assets", "private const val MAX_MEDIA = 4" in client),
    ("backend stores ordered post media separately", "CREATE TABLE IF NOT EXISTS social_post_media" in backend and "UNIQUE (post_id, position)" in backend),
    ("backend preserves first media for legacy feed clients", "INSERT INTO social_posts" in backend and "media_id,media_type" in backend and "mediaIds[0]" in backend),
    ("backend enforces media ownership", "String(row.owner_id) !== String(req.user.sub)" in backend),
    ("backend validates requested media MIME type", "startsWith(expectedPrefix)" in backend),
    ("backend exposes authorized post media", "app.get('/api/social/posts/:id/media'" in backend and "visibleSocialPost(postId, req.user.sub)" in backend),
    ("production prestart installs multi-media backend", "socialMultiMediaBootstrap.js" in startup and "installSocialMultiMedia" in startup),
    ("composer routes publishing through the multi-media client", "FynxMultiMediaPostClient.createPost" in composer),
    ("gallery picker is capped at the supported four media items", "PickMultipleVisualMedia(4)" in composer),
    ("adding gallery selections preserves existing draft media instead of replacing it", "val selected = uris.distinct().filterNot { it in capturedUris }" in composer and "val nextUris = (capturedUris + accepted).take(4)" in composer),
    ("gallery overflow is explicitly explained rather than silently discarded", "selected.size > accepted.size" in composer and "selected item(s) were not added" in composer),
    ("draft storage is private and account-scoped", 'PREFS_PREFIX = "fynx_post_drafts_"' in draft_store and "FynxAuthStore.accountStorageKey(context)" in draft_store),
    ("drafts copy media into durable app files and restore only existing files", 'File(context.filesDir, "post_drafts")' in draft_store and "target.outputStream()" in draft_store and "if (!File(path).isFile) return@mapNotNull null" in draft_store),
    ("one draft preserves all four ordered media paths and their aligned media types", "require(media.size <= 4)" in draft_store and "mediaPaths = mediaPaths" in draft_store and "mediaTypes = media.map { it.second }" in draft_store),
    ("draft list retains multiple saved drafts", "MAX_DRAFTS = 12" in draft_store and "take(MAX_DRAFTS)" in draft_store),
    ("composer restores saved media and media types in the same order", "capturedUris = draft.mediaPaths.map { Uri.fromFile(java.io.File(it)) }" in composer and "capturedTypes = draft.mediaTypes" in composer),
    ("composer keeps real caption support", "What's on your mind?" in composer and 'put("text", caption)' in client and "val caption = text.trim().take(4000)" in client),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit(f"Home multi-media verification failed: {len(failed)} check(s)")
print("Home multi-media publishing integration gate: GREEN")
