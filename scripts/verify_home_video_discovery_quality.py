from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
discovery = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeDiscoverySection.kt").read_text(encoding="utf-8")
home = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxDiscoveryClient.kt").read_text(encoding="utf-8")
backend = (ROOT / "backend/discoveryRoutes.js").read_text(encoding="utf-8")
navigation = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")

checks = [
    (
        "local Discovery fixture views and engagements never emit analytics writes",
        'postId?.startsWith("ci-home-discovery-") == true' in client
        and "return Result.success(Unit)" in client,
    ),
    (
        "offline Discovery fixtures are opt-in, debuggable-build-only, and use the seeded private media cache",
        "localVisualFixtureVideos" in client
        and "ApplicationInfo.FLAG_DEBUGGABLE" in client
        and '"fynx_ci_visual_fixtures"' in client
        and '"home_discovery_videos"' in client
        and '"ci-home-discovery-portrait"' in client
        and "FynxDiscoveryClient.localVisualFixtureVideos(context)" in home
        and "ci-home-discovery-fixtures.xml" in navigation,
    ),
    (
        "runtime certification exercises both repost choices and the original-media quote composer without submitting",
        "repost_quote_captured" in navigation
        and 'find_exact_home_action(current, "Repost")' in navigation
        and 'find_exact_home_action(repost_menu_xml, "Quote post")' in navigation
        and "plain_option_visible" in navigation
        and "authenticated-home-quote-composer.png" in navigation
        and "No repost or quote was submitted." in navigation,
    ),
    (
        "quote-post backend stores a distinct public commentary post linked to another public post transactionally",
        'app.post("/api/social/posts/:id/quote"' in backend
        and "quoted_post_id" in backend
        and "visibility !== \"PUBLIC\"" in backend
        and "String(original.rows[0].author_id) === String(req.user.sub)" in backend
        and '"BEGIN"' in backend
        and '"COMMIT"' in backend
        and '"ROLLBACK"' in backend,
    ),
    (
        "feed quote projection excludes non-public originals and blocked users",
        "qp.visibility='PUBLIC'" in (ROOT / "backend/socialRoutes.js").read_text(encoding="utf-8")
        and "qb.blocker_id=$1" in (ROOT / "backend/socialRoutes.js").read_text(encoding="utf-8"),
    ),
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
        "Home feed plays only the focused video inline and opens the same player full screen",
        "previewHeight = minOf(340.dp, maxWidth / previewAspectRatio)" in home
        and "previewWidth = previewHeight * previewAspectRatio" in home
        and "if (fullscreen && file != null)" in home
        and home.count("FynxFeedTextureVideo(") >= 2
        and "playbackActive = playbackActive && !fullscreen" in home
        and "fullScreenControls = false" in home
        and "fullScreenControls = true" in home
        and '"authenticated-home-fullscreen-video.png"' in navigation,
    ),
    (
        "Discovery fullscreen VideoView opens private cached files with a file URI",
        "setVideoURI(Uri.fromFile(file!!))" in discovery
        and "setVideoPath(file!!.absolutePath)" not in discovery
        and "import android.net.Uri" in discovery,
    ),
    (
        "Discovery cards use poster artwork and do not autoplay every preview player",
        "FynxDiscoveryPreviewCard" in discovery
        and "visual?.poster" in discovery
        and "VideoView(ctx)" not in discovery[discovery.index("private fun FynxDiscoveryPreviewCard"):discovery.index("fun FynxHomeDiscoveryViewer")],
    ),
    (
        "full-screen Home player runtime verifies active playback, two-way mute, time/progress and same-feed return",
        'authenticated-home-fullscreen-unmute' in navigation
        and 'authenticated-home-fullscreen-remute' in navigation
        and '["Pause full-screen video"]' in navigation
        and "Elapsed / total playback time" in navigation
        and "Playback progress track" in navigation
        and 'find_control(fullscreen_xml, ["Video playback progress"])' in navigation
        and 'find_control(restored_xml, ["Video duration"])' in navigation
        and 're.search(r"\\d{2}:\\d{2}\\s*/\\s*\\d{2}:\\d{2}", fullscreen_xml)' in navigation,
    ),
    (
        "Home inline and full-screen video error states provide a clear retry message",
        "if (!prepared && !playbackError)" in home
        and "else if (prepared && !playbackError)" in home
        and 'Text("This video could not start"' in home
        and "TextButton(onClick = onRetryMedia)" in home,
    ),
    (
        "Home video mute toggle changes the active MediaPlayer volume after preparation",
        "LaunchedEffect(muted, prepared)" in home
        and "player.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)" in home,
    ),
    (
        "Discovery viewer mute control updates the actual MediaPlayer volume",
        "playerState.value = player" in discovery
        and "playerState.value?.setVolume" in discovery
        and 'if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp' in discovery
        and '"Unmute Discovery video" else "Mute Discovery video"' in discovery,
    ),
    (
        "Discovery player audio and view state are released on disposal",
        "runCatching { viewState.value?.stopPlayback() }" in discovery
        and "playerState.value = null" in discovery,
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
        "def _home_header_is_restored(xml_text: str)" in navigation
        and "if _home_header_is_restored(latest):" in navigation
        and "def _home_content_scroll_point(" in navigation
        and 'find_control(xml_text, ["FYNX Assistance"])' in navigation
        and 'find_control(xml_text, ["Your feed"])' in navigation
        and '["Open FYNX profile"]' in navigation
        and '"720", "520", "720", "2600"' not in navigation,
    ),
    (
        "runtime certification opens a real Discovery clip and verifies the action rail",
        '"Open Discovery video"' in navigation
        and 'find_control(candidate, ["Like video", "Unlike video"])' in navigation
        and 'find_control(candidate, ["Open comments"])' in navigation
        and 'find_control(candidate, ["Share video"])' in navigation
        and '"authenticated-discovery-video-viewer.png"' in navigation,
    ),
    (
        "Discovery viewer requires a prepared native player and all visible actions, then always restores Home",
        "authenticated-home-discovery-viewer-settle.xml" in navigation
        and "has_prepared_player and has_all_actions" in navigation
        and "FYNX Visual Fixture" in navigation
        and "CI TEST FIXTURE" in navigation
        and 'find_control(candidate, ["Pause Discovery video", "Play Discovery video"])' in navigation
        and 'find_control(candidate, ["Like video", "Unlike video"])' in navigation
        and 'find_control(candidate, ["Open comments"])' in navigation
        and 'find_control(candidate, ["Share video"])' in navigation
        and "viewer_xml = candidate" in navigation
        and "Unverified Discovery viewer could not be dismissed back to Home." in navigation
        and "stable_viewer_dumps >= 2" not in navigation,
    ),
    (
        "Discovery remains a stable Home insertion whenever real trending videos are available",
        'listOf("discovery") + ordered.filterNot { it == "discovery" }' in home
        and '"home_discovery_empty_feed"' in home
        and '"discovery" in ordered' in home,
    ),
    (
        "Home Discovery remains reachable when For You has only one or two feed posts",
        'posts.size < 3 -> if (discoveryVideos.isNotEmpty() && "discovery" in ordered) 1 else 0' in home
        and '"discovery" -> item(key = "home_discovery_${postIndex}")' in home,
    ),
    (
        "runtime feed scrolling starts from visible non-clickable captions on full-width landscape clips",
        '_home_content_scroll_point(current, width, height, "up")' in navigation
        and "video can consume the gesture instead of the parent LazyColumn scrolling" in navigation,
    ),
    (
        "final Home navigation detection requires the visible bottom Home label",
        'text_value != "home" and description != "home"' in navigation
        and 'node.attrib.get("visible-to-user", "true")' in navigation
        and 'screen_height * 0.68' in navigation,
    ),
    (
        "Home action-row geometry parser reads emulator dimensions before safe taps",
        "def scroll_home_action_row_into_safe_viewport(" in navigation
        and r're.search(r"(?:Physical|Override) size:\s*(\d+)x(\d+)", size)' in navigation
        and "safe_limits = [int(height * 0.85)]" in navigation
        and "viewport_bottoms.append(bottom)" in navigation
        and "min(viewport_bottoms) - 8" in navigation
        and "safe_limits.append(int(height * 0.78))" in navigation
        and "nav_tops.append(top)" in navigation
        and "refusing to tap an obscured control" in navigation,
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
