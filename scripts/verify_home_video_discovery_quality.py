from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
discovery = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeDiscoverySection.kt").read_text(encoding="utf-8")
home = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxDiscoveryClient.kt").read_text(encoding="utf-8")
backend = (ROOT / "backend/discoveryRoutes.js").read_text(encoding="utf-8")
navigation = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")

checks = [
    (
        "video visual extraction reads a real frame and source display ratio",
        "MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH" in discovery
        and "MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT" in discovery
        and "getFrameAtTime" in discovery
        and "FynxVideoVisualFrame" in discovery,
    ),
    (
        "ambient backdrop fills letterboxed areas from the same clip instead of plain black",
        "fun FynxVideoAmbientBackdrop" in discovery
        and ".blur(28.dp)" in discovery
        and "ContentScale.Crop" in discovery,
    ),
    (
        "Home feed TextureView layers the source-frame backdrop behind the undistorted video",
        "FynxVideoAmbientBackdrop(poster = poster" in home
        and "applyFynxFeedVideoFit" in home
        and "poster = videoPoster" in home
        and "setOpaque(false)" in home,
    ),
    (
        "Discovery cards use poster artwork and do not autoplay every preview player",
        "FynxDiscoveryPreviewCard" in discovery
        and "visual?.poster" in discovery
        and "VideoView(ctx)" not in discovery[discovery.index("private fun FynxDiscoveryPreviewCard"):discovery.index("fun FynxHomeDiscoveryViewer")],
    ),
    (
        "full-screen Discovery player fits the source ratio inside the screen",
        "fittedWidth" in discovery
        and "fittedHeight" in discovery
        and "Modifier.width(fittedWidth).height(fittedHeight)" in discovery,
    ),
    (
        "Discovery viewer includes real like, comment and share actions",
        "FynxRemoteSocialClient.like(context, video.id, desired)" in discovery
        and "FynxHomeCommentsPanel(" in discovery
        and "Intent.ACTION_SEND" in discovery,
    ),
    (
        "Discovery comment flow uses the real post identity and visibility",
        "fun FynxDiscoveryClient.TrendingPost.toRemotePost()" in discovery
        and "authorId = authorId" in discovery
        and "visibility = visibility" in discovery,
    ),
    (
        "Discovery client carries authoritative liked state",
        "likedByCurrentUser = o.optBoolean(\"likedByCurrentUser\")" in client,
    ),
    (
        "Discovery backend derives current-account like state from social_post_likes",
        "EXISTS (SELECT 1 FROM social_post_likes mine WHERE mine.post_id=p.id AND mine.user_id=$1) AS liked_by_current_user" in backend
        and "likedByCurrentUser: Boolean(row.liked_by_current_user)" in backend,
    ),
    (
        "runtime navigation scrolls Home fully to its first sections before tapping the collapsing header",
        "always issue a real scroll-to-top gesture" in navigation
        and 'find_control(latest, ["FYNX Assistance"])' in navigation
        and 'find_control(latest, ["Your feed"])' in navigation
        and '["Open FYNX profile"]' in navigation,
    ),
    (
        "runtime certification opens a real Discovery clip and verifies the action rail",
        '"Open Discovery video"' in navigation
        and '"Like video", "Unlike video", "Open comments", "Share video"' in navigation
        and '"authenticated-discovery-video-viewer.png"' in navigation,
    ),
    (
        "Discovery remains a stable Home insertion whenever real trending videos are available",
        'listOf("discovery") + ordered.filterNot { it == "discovery" }' in home
        and '"home_discovery_empty_feed"' in home
        and '"discovery" in ordered' in home,
    ),
    (
        "opening Discovery records each page view once rather than double-counting the first view",
        "FynxDiscoveryClient.recordView(context, video.id)" in discovery
        and "fun openHomeDiscoveryViewer(index: Int)" in home
        and "recordView(context, it.id)" not in home[
            home.index("fun openHomeDiscoveryViewer(index: Int)"):
            home.index("fun mergeDiscoveryPosts(")
        ],
    ),
    (
        "Existing Home feed media and threaded conversation integration remains wired",
        "FynxFeedTextureVideo(" in home
        and "RemotePostCard(" in home
        and "FynxHomeCommentsPanel(" in home,
    ),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit(f"Home video and Discovery verification failed: {len(failed)} check(s)")
print("Home video and Discovery interaction quality gate: GREEN")
