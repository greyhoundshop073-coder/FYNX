from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
home = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text(encoding="utf-8")
composer = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text(encoding="utf-8")

checks = [
    ("Media screen reads the real post manifest", "FynxHomePostMediaClient.list(context, post.id)" in home),
    ("Legacy single-media posts still render", "post.mediaUrl != null -> RemoteSocialMedia(post.mediaUrl, post.mediaType)" in home),
    ("One-media screen uses the normal full-width renderer", "media.size == 1 -> RemoteSocialMedia(media.first().mediaUrl, media.first().mediaType)" in home),
    ("Two-media screen uses a side-by-side row", "media.size == 2 -> Row" in home and "media.forEach { item -> Box(Modifier.weight(1f).fillMaxHeight())" in home),
    ("Three-media screen uses one large item and two stacked items", "media.size == 3 -> Row" in home and "Box(Modifier.weight(2f).fillMaxHeight())" in home and "Column(Modifier.weight(1f).fillMaxHeight()" in home),
    ("Four-media screen uses a 2x2 grid", "media.size == 4 -> Column" in home and "media[0].mediaUrl" in home and "media[1].mediaUrl" in home and "media[2].mediaUrl" in home and "media[3].mediaUrl" in home),
    ("Mixed media uses the same item renderer", "RemoteSocialMedia(item.mediaUrl, item.mediaType)" in home),
    ("Video playback remains available", "VideoView" in home and "MediaController" in home),
    ("Audio playback remains available", "MediaPlayer" in home and 'type == "audio"' in home),
    ("Media renderer preserves existing aspect-ratio scaling", "ContentScale.Fit" in home and ".aspectRatio((it.width.toFloat() / it.height.toFloat()).coerceIn(0.62f, 1.9f))" in home),
    ("Existing Like action remains wired", "onLike = { id -> runLike(id) }" in home),
    ("Existing Comment action remains wired", "onComment = { commentsPost = post }" in home),
    ("Existing Share action remains wired", "onShare = { runShare(post) }" in home),
    ("Existing Save action remains wired", "onSave = { id, saved -> runInteraction" in home),
    ("Existing Repost action remains wired", "onRepost = { id, reposted -> runInteraction" in home),
    ("Composer enforces the real four-asset limit", "require(selected.size <= MAX_MEDIA)" in client and "MAX_MEDIA = 4" in client),
    ("Composer exposes four-media guidance", "up to 4 photos/videos" in composer),
    ("Composer supports camera capture", "FynxCameraCapturePanel" in composer),
    ("Composer exposes media removal guidance", "remove media you don't want to post" in composer),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit(f"Home media screen behavior verification failed: {len(failed)} check(s)")
print("Home media screen behavior gate: GREEN")
