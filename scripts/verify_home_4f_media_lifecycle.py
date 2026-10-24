#!/usr/bin/env python3
"""Static Home 4F media/lifecycle/integrity audit."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt"
PANEL = ROOT / "app/src/main/java/com/fynx/app/ui/HomePanel.kt"
LIFECYCLE = ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeLifecycle.kt"
MEDIA_CACHE = ROOT / "app/src/main/java/com/fynx/app/ui/FynxMediaCache.kt"
DISCOVERY = ROOT / "backend/discoveryRoutes.js"
SAVED_PANEL = ROOT / "app/src/main/java/com/fynx/app/ui/FynxSavedPostsPanel.kt"
VIEWER = ROOT / "app/src/main/java/com/fynx/app/ui/FynxPostMediaViewer.kt"
RUNTIME_NAVIGATION = ROOT / "scripts/verify_authenticated_runtime_navigation.py"

home = HOME.read_text(encoding="utf-8")
panel = PANEL.read_text(encoding="utf-8")
lifecycle = LIFECYCLE.read_text(encoding="utf-8")
media_cache = MEDIA_CACHE.read_text(encoding="utf-8")
discovery = DISCOVERY.read_text(encoding="utf-8")
saved_panel = SAVED_PANEL.read_text(encoding="utf-8")
viewer = VIEWER.read_text(encoding="utf-8")
runtime_navigation = RUNTIME_NAVIGATION.read_text(encoding="utf-8")

required_home = {
    "FynxMediaCache.getOrDownload": "real media cache/download path",
    "MediaMetadataRetriever": "video metadata handling",
    "FilledIconButton": "FYNX video play/pause control",
    "Fullscreen": "FYNX video fullscreen control",
    '"Open video full screen"': "FYNX video fullscreen accessibility action",
    "FynxFeedTextureVideo": "scroll-safe Home feed video surface",
    "TextureView": "regular View-backed feed video surface",
    "prepareAsync()": "asynchronous feed video preparation",
    "DisposableEffect(file)": "video lifecycle cleanup",
    "DisposableEffect(player)": "audio lifecycle cleanup",
    "rememberHomeAudioPlayback": "shared asynchronous audio setup for Home music and voice notes",
    "player.release()": "audio resource release",
    "FynxRemoteProfileAvatar": "real author identity media",
    "MaterialTheme.colorScheme": "theme-aware Home presentation",
    '"Loading Home feed"': "accessible Home loading state for runtime certification",
    'label = "Share"': "share accessibility action",
    'label = "Comment"': "comment accessibility action",
    'label = "Like"': "like accessibility action",
    'label = if (interactionState.saved) "Saved" else "Save"': "primary Save accessibility/action label",
}

missing = [label for token, label in required_home.items() if token not in home]
if missing:
    raise SystemExit("HOME 4F MEDIA RED: missing " + ", ".join(missing))
if "MediaController" in home:
    raise SystemExit("HOME 4F MEDIA RED: legacy Android MediaController must not be present in the Home feed")

required_media_cache = [
    'path.startsWith("/api/media/")',
    'path.startsWith("/api/social/media/")',
    'FynxBackendClient.downloadToFile',
    'FynxAuthStore.accountStorageKey',
]
missing_media_cache = [token for token in required_media_cache if token not in media_cache]
if missing_media_cache:
    raise SystemExit("HOME 4F MEDIA RED: authenticated social media cache contract missing " + ", ".join(missing_media_cache))

# The Home feed must remain mounted while an explicit refresh signal is consumed.
# Re-keying the feed on refresh would recreate its LazyColumn and can discard scroll/cache state.
required_panel = ["FynxHomeLifecycleRefresh", "LaunchedEffect(refreshKey)", "PullToRefreshBox", "FynxHomeLifecycleRefreshBus.request(context)"]
missing_panel = [token for token in required_panel if token not in panel]
if missing_panel:
    raise SystemExit("HOME 4F MEDIA RED: refresh lifecycle boundary missing " + ", ".join(missing_panel))
if "key(refreshKey)" in panel:
    raise SystemExit("HOME 4F MEDIA RED: refresh must not re-key/recreate the Home feed")

# Home must refresh only through the explicit publish/refresh bus. Normal lifecycle resume
# must not recreate the feed, because that causes unnecessary media/feed reloads.
if "LocalLifecycleOwner" in lifecycle or "ON_RESUME" in lifecycle or "removeObserver" in lifecycle:
    raise SystemExit("HOME 4F MEDIA RED: legacy ON_RESUME lifecycle observer must not recreate Home")
required_lifecycle = [
    "FynxHomeLifecycleRefreshBus",
    "currentVersion()",
    "refreshSignal",
]
missing_lifecycle = [token for token in required_lifecycle if token not in lifecycle]
if missing_lifecycle:
    raise SystemExit("HOME 4F MEDIA RED: Home refresh bus incomplete " + ", ".join(missing_lifecycle))

required_saved = [
    'app.get("/api/social/saved", auth',
    "const limit = Math.min(Math.max(Number(req.query?.limit) || 30, 1), 100);",
    "const offset = Math.max(Number(req.query?.offset) || 0, 0);",
    "FROM social_saved_posts sp JOIN social_posts p ON p.id=sp.post_id JOIN users u ON u.id=p.author_id",
    "WHERE sp.user_id=$1",
    "p.author_id=$1 OR p.visibility='PUBLIC'",
    "p.visibility='FRIENDS_ONLY'",
    "AND NOT EXISTS (SELECT 1 FROM blocks b",
    "ORDER BY sp.created_at DESC LIMIT $2 OFFSET $3",
    "saved_at",
    "COALESCE(l.likes,0) AS like_count",
    "COALESCE(c.comments,0) AS comment_count",
    "AS liked_by_current_user",
    "[req.user.sub, limit + 1, offset]",
    "const hasMore = result.rows.length > limit",
    "const rows = hasMore ? result.rows.slice(0, limit) : result.rows",
]
missing_saved = [token for token in required_saved if token not in discovery]
if missing_saved:
    raise SystemExit("HOME 4F SAVED RED: missing " + ", ".join(missing_saved))

required_saved_panel = [
    'FynxHomePostMediaClient.list(context, entry.post.id)',
    'RemoteSocialMedia(',
    'mediaUrl = mediaId?.let { "/api/social/media/$it" }',
    'hasMore',
    'nextOffset',
    'Load more saved posts',
    'FynxPostMediaViewer(',
    'FynxRemoteSocialClient.save(context, entry.post.id, false)',
    'likeCount = o.optInt("likeCount"), commentCount = o.optInt("commentCount")',
    'likedByCurrentUser = o.optBoolean("likedByCurrentUser")',
]
missing_saved_panel = [token for token in required_saved_panel if token not in saved_panel]
if missing_saved_panel:
    raise SystemExit("HOME 4F SAVED RED: saved list media/pagination/state missing " + ", ".join(missing_saved_panel))

required_viewer = [
    'FynxRemoteSocialClient.interactionState(context, post.id)',
    'onPostChanged(liked, likes, saved, savedCount, reposted, repostCount, comments)',
    'if (saved) "Saved" else "Save"',
    'if (reposted) "Reposted" else "Repost"',
    'BackHandler(enabled = !commentsOpen) { onDismiss() }',
]
missing_viewer = [token for token in required_viewer if token not in viewer]
if missing_viewer:
    raise SystemExit("HOME 4F VIEWER RED: media viewer interaction synchronization missing " + ", ".join(missing_viewer))

required_media_recovery = [
    'var mediaLoading by remember(path)',
    'val activeNetworkReady = FynxNetworkQuality.current(context) != FynxNetworkQuality.Level.OFFLINE',
    'FynxRemoteSocialClient.cachedFeedPage(context)',
    'You\'re offline. Connect to a network to load your feed.',
    'var mediaFailed by remember(path)',
    'var reloadNonce by remember(path)',
    'Text("Retry media")',
    'if (fullscreen && file != null)',
    'playbackActive = playbackActive && !fullscreen',
    'Pause full-screen video',
    'Unmute full-screen video',
]
missing_media_recovery = [token for token in required_media_recovery if token not in home]
if missing_media_recovery:
    raise SystemExit("HOME 4F MEDIA RED: media loading/error/retry state missing " + ", ".join(missing_media_recovery))

required_runtime_capture = [
    'def wait_for_home_feed_settled(timeout:float=30.0)->tuple[str,bool]:',
    'settled_xml,feed_settled=wait_for_home_feed_settled()',
    'if not feed_settled:',
    'Home feed did not leave its loading state within 30 seconds',
]
missing_runtime_capture = [token for token in required_runtime_capture if token not in runtime_navigation]
if missing_runtime_capture:
    raise SystemExit("HOME 4F RUNTIME RED: stable Home capture gate missing " + ", ".join(missing_runtime_capture))


# Audio decoding must never block the Compose/UI thread. The shared helper must prepare
# asynchronously, expose a prepared/error state, disable premature playback, and release resources.
audio_helper_start = home.index("private fun rememberHomeAudioPlayback(")
audio_helper_end = home.index("@Composable\nprivate fun MusicPostPlayer(", audio_helper_start)
audio_helper = home[audio_helper_start:audio_helper_end]
for token, label in (
    ("prepareAsync()", "asynchronous player preparation"),
    ("setOnPreparedListener", "prepared callback"),
    ("setOnErrorListener", "audio decoder error handling"),
    ("onDispose", "audio lifecycle disposal"),
    ("player.release()", "audio player release"),
):
    if token not in audio_helper:
        raise SystemExit("HOME 4F MEDIA RED: audio helper missing " + label)
if ".prepare()" in audio_helper:
    raise SystemExit("HOME 4F MEDIA RED: synchronous audio preparation can block the Home UI")
for component in ("MusicPostPlayer(", "AudioPostPlayer("):
    start = home.index("private fun " + component)
    end = home.find("\n@Composable", start + 1)
    if end < 0:
        end = len(home)
    body = home[start:end]
    if "rememberHomeAudioPlayback(" not in body:
        raise SystemExit("HOME 4F MEDIA RED: " + component.rstrip("(") + " does not use lifecycle-managed async playback")
if "enabled = playback.prepared && !playback.failed" not in home:
    raise SystemExit("HOME 4F MEDIA RED: audio playback controls must wait for prepared state")
if "if (!playback.prepared || playback.failed)" not in home:
    raise SystemExit("HOME 4F MEDIA RED: voice-note progress updater must not read decoder duration before preparation")
fullscreen_start = home.index("if (fullscreen && file != null)")
fullscreen_end = home.index("private fun relative(", fullscreen_start)
if "contentScale = ContentScale.Fit" not in home[fullscreen_start:fullscreen_end]:
    raise SystemExit("HOME 4F MEDIA RED: fullscreen photo viewer must fit the complete image instead of cropping edges")

print("HOME 4F MEDIA/LIFECYCLE GREEN: account-scoped Home cache, Saved-post media and pagination, authoritative viewer interaction state, media retry/error handling, lifecycle safety, accessibility, theme, and privacy boundaries are wired")
