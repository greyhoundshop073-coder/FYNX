#!/usr/bin/env python3
"""Home Feed inline-video and threaded-conversation regression guard."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOME = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text(encoding="utf-8")
COMMENTS = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeCommentsPanel.kt").read_text(encoding="utf-8")
BOOTSTRAP = (ROOT / "backend/realtimeIsolationBootstrap.js").read_text(encoding="utf-8")
REACTIONS = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomePostReactionsClient.kt").read_text(encoding="utf-8")
MEDIA_CACHE = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMediaCache.kt").read_text(encoding="utf-8")
DISCOVERY = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeDiscoverySection.kt").read_text(encoding="utf-8")
RUNTIME_NAVIGATION = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")

checks = [
    ("feed video is laid out against available width", "BoxWithConstraints(Modifier.fillMaxWidth())" in HOME),
    ("portrait video height is capped inside the feed", "coerceAtMost(420.dp)" in HOME),
    ("video keeps its full source aspect ratio without stretching", "applyFynxFeedVideoFit" in HOME and "view.setTransform(Matrix()" in HOME),
    ("video controls expose an explicit full-screen action", '"Open video full screen"' in HOME and "onOpenFullscreen = { fullscreen = true }" in HOME),
    ("player is automatically paused outside the focused item", "playbackActive = playbackActive && !fullscreen" in HOME),
    ("video surface and player are released safely", "DisposableEffect(surface)" in HOME and "player.release()" in HOME),
    ("comments preserve post context", '"POST CONVERSATION"' in COMMENTS and "post.authorDisplayName" in COMMENTS),
    ("conversation supports expanded nested reply trees", "flattenHomeReplies(" in COMMENTS and "toggleReplies(reply)" in COMMENTS),
    ("users can reply directly to another reply", "replyingToId = reply.id" in COMMENTS and "availableComments.firstOrNull" in COMMENTS),
    ("reply composer remains keyboard-safe", ".imePadding()" in COMMENTS and "KeyboardActions(onSend = { send() })" in COMMENTS),
    ("comment composer announces whether it sends a comment or reply", '"Send comment" else "Send reply"' in COMMENTS),
    ("Home post identity presents an explicit X-style account handle", 'Text("@${post.authorUsername.removePrefix("@")} • ${relative(post.timestamp)}"' in HOME),
    ("all five X-style emoji reactions remain visible", all(token in HOME for token in ('ReactionChoice("👍", "LIKE"', 'ReactionChoice("❤️", "LOVE"', 'ReactionChoice("😂", "LAUGH"', 'ReactionChoice("😮", "WOW"', 'ReactionChoice("😢", "SAD"'))),
    ("emoji reactions have meaningful accessible labels", 'contentDescription = "React with $reactionLabel"' in HOME and "role = Role.Button" in HOME and "import androidx.compose.ui.semantics.role" in HOME),
    ("reaction selection and removal use the real backend client", "FynxHomePostReactionsClient.set(context, id, reaction)" in HOME and "FynxHomePostReactionsClient.clear(context, id)" in HOME),
    ("reaction client accepts only the supported emoji reaction types", 'setOf("LIKE", "LOVE", "LAUGH", "WOW", "SAD")' in REACTIONS),
    ("reaction changes roll back on API failure", "reactionStates = reactionStates + (id to previous)" in HOME),
    ("manual video retry refreshes failed cached media", "forceRefresh: Boolean = false" in MEDIA_CACHE and "forceRefresh = reloadNonce > 0" in HOME and "forceRefresh = retryNonce > 0" in DISCOVERY),
    ("media refresh preserves the last good cached file until replacement succeeds", "file.name}.refresh" in MEDIA_CACHE and "file.name}.backup" in MEDIA_CACHE and "if (hadPrevious) backup.renameTo(file)" in MEDIA_CACHE),
    ("interrupted media refresh restores the last good cache on next access", "if (!hasUsableCachedFile() && backup.isFile" in MEDIA_CACHE and "backup.renameTo(file)" in MEDIA_CACHE and "if (!backup.renameTo(file)) return backup" in MEDIA_CACHE),
    ("comment pagination uses the last returned row as cursor", "String(result.rows[limit - 1].id)" in BOOTSTRAP),
    ("pagination does not skip the look-ahead row", "String(result.rows[result.rows.length - 1].id)" not in BOOTSTRAP),
    ("backend preserves the selected reply parent", "const directParentId = parentId;" in BOOTSTRAP and "[postId, req.user.sub, directParentId, text]" in BOOTSTRAP),
    ("reply endpoints validate post and parent visibility", "visiblePost(postId, req.user.sub)" in BOOTSTRAP and "NOT EXISTS (SELECT 1 FROM blocks" in BOOTSTRAP),
    ("both reply-parent visibility queries close their NOT EXISTS predicates", BOOTSTRAP.count("OR (b.blocker_id=c.author_id AND b.blocked_id=$3))") >= 2),
    ("runtime screenshot flow captures a visible inline video card", 'def capture_home_feed_visuals(home_xml: str)' in (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8") and 'authenticated-home-inline-video.png' in (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")),
    ("Home feed scan preserves the Home navigation shell", "is_home_feed_navigation_visible(current)" in RUNTIME_NAVIGATION and "refusing to continue on another screen" in RUNTIME_NAVIGATION),
    ("Home screenshot capture scans multiple real feed viewports", "def scroll_home_feed_page(index: int)" in RUNTIME_NAVIGATION and "for page in range(1, 9)" in RUNTIME_NAVIGATION),
    ("runtime screenshot flow opens a real Comments conversation without sending content", 'authenticated-home-comments.png' in (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8") and "No comment or reply was sent." in (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")),
]

failed = []
for label, passed in checks:
    print(f"{'PASS' if passed else 'FAIL'}: {label}")
    if not passed:
        failed.append(label)

if failed:
    raise SystemExit(f"Home Feed conversation quality gate failed: {len(failed)} check(s)")
print("HOME FEED VIDEO + THREADS QUALITY GATE: GREEN")
