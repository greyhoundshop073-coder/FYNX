from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
home = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text(encoding="utf-8")
composer = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text(encoding="utf-8")
media_client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomePostMediaClient.kt").read_text(encoding="utf-8")
backend = (ROOT / "backend/socialMultiMediaBootstrap.js").read_text(encoding="utf-8")

checks = [
    ("Home renders the real ordered media manifest", "FynxHomePostMediaClient.list(context, post.id)" in home),
    ("Home preserves legacy single-media feed posts", "post.mediaUrl != null" in home and "RemoteSocialMedia(post.mediaUrl, post.mediaType)" in home),
    ("Home has the required 1-4 media branches", "media.size == 1" in home and "media.size == 2" in home and "media.size == 3" in home and "media.size == 4" in home),
    ("Two-media layout is side-by-side", "media.size == 2 -> Row" in home),
    ("Three-media layout is one large plus two stacked", "media.size == 3 -> Row" in home and "media[1].mediaUrl" in home and "media[2].mediaUrl" in home),
    ("Four-media layout is a 2x2 grid", "media.size == 4" in home and "media[3].mediaUrl" in home),
    ("Mixed photo/video uses the same renderer", "RemoteSocialMedia(item.mediaUrl, item.mediaType)" in home),
    ("Composer visibly states the four-media limit", "up to 4 photos/videos" in composer),
    ("Composer caps gallery selection at four", "uris.distinct().take(4)" in composer and ").take(4)" in composer),
    ("Multi-media client rejects more than four assets", "selected.size <= MAX_MEDIA" in client and "MAX_MEDIA = 4" in client),
    ("Media client preserves backend order", "sortedBy { it.position }" in media_client),
    ("Backend stores up to four ordered assets", "position < 4" in backend and "UNIQUE (post_id, position)" in backend),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit(f"Home media layout verification failed: {len(failed)} check(s)")
print("Home media layout gate: GREEN")
