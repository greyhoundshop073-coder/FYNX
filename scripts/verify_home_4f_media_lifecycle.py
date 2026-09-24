#!/usr/bin/env python3
"""Static Home 4F media/lifecycle/integrity audit."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt"
PANEL = ROOT / "app/src/main/java/com/fynx/app/ui/HomePanel.kt"
LIFECYCLE = ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeLifecycle.kt"
MEDIA_CACHE = ROOT / "app/src/main/java/com/fynx/app/ui/FynxMediaCache.kt"
DISCOVERY = ROOT / "backend/discoveryRoutes.js"

home = HOME.read_text(encoding="utf-8")
panel = PANEL.read_text(encoding="utf-8")
lifecycle = LIFECYCLE.read_text(encoding="utf-8")
media_cache = MEDIA_CACHE.read_text(encoding="utf-8")
discovery = DISCOVERY.read_text(encoding="utf-8")

required_home = {
    "FynxMediaCache.getOrDownload": "real media cache/download path",
    "MediaMetadataRetriever": "video metadata handling",
    "FilledIconButton": "FYNX video play/pause control",
    "Fullscreen": "FYNX video fullscreen control",
    '"Open video full screen"': "FYNX video fullscreen accessibility action",
    "DisposableEffect(videoView)": "video lifecycle cleanup",
    "stopPlayback()": "video playback cleanup",
    "DisposableEffect(player)": "audio lifecycle cleanup",
    "player.release()": "audio resource release",
    "FynxRemoteProfileAvatar": "real author identity media",
    "MaterialTheme.colorScheme": "theme-aware Home presentation",
    'Icon(Icons.Default.Refresh, "Refresh feed")': "refresh accessibility action",
    'label = "Share"': "share accessibility action",
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

required_panel = ["FynxHomeLifecycleRefresh", "key(refreshKey)"]
missing_panel = [token for token in required_panel if token not in panel]
if missing_panel:
    raise SystemExit("HOME 4F MEDIA RED: lifecycle boundary missing " + ", ".join(missing_panel))

required_lifecycle = ["LocalLifecycleOwner", "ON_RESUME", "removeObserver"]
missing_lifecycle = [token for token in required_lifecycle if token not in lifecycle]
if missing_lifecycle:
    raise SystemExit("HOME 4F MEDIA RED: lifecycle observer incomplete " + ", ".join(missing_lifecycle))

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
]
missing_saved = [token for token in required_saved if token not in discovery]
if missing_saved:
    raise SystemExit("HOME 4F SAVED RED: missing " + ", ".join(missing_saved))

print("HOME 4F MEDIA/LIFECYCLE GREEN: existing Home media, authenticated social-media cache paths, accessibility, theme, re-entry cleanup, and durable Saved-post privacy/pagination boundaries are wired")
