#!/usr/bin/env python3
"""Static Home 4F media/lifecycle audit.

This is intentionally scoped to existing production Home media code. It does not
invent a second media implementation; it verifies that the existing implementation
has the lifecycle and failure-safety hooks expected after navigation/re-entry.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt"
PANEL = ROOT / "app/src/main/java/com/fynx/app/ui/HomePanel.kt"
LIFECYCLE = ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeLifecycle.kt"

home = HOME.read_text(encoding="utf-8")
panel = PANEL.read_text(encoding="utf-8")
lifecycle = LIFECYCLE.read_text(encoding="utf-8")

required_home = {
    "FynxMediaCache.getOrDownload": "real media cache/download path",
    "MediaMetadataRetriever": "video metadata handling",
    "MediaController": "native video controls",
    "DisposableEffect(videoView)": "video lifecycle cleanup",
    "stopPlayback()": "video playback cleanup",
    "DisposableEffect(player)": "audio lifecycle cleanup",
    "player.release()": "audio resource release",
    "FynxRemoteProfileAvatar": "real author identity media",
    "MaterialTheme.colorScheme": "theme-aware Home presentation",
    'Icon(Icons.Default.Refresh, "Refresh feed")': "refresh accessibility action",
    'Icon(Icons.Default.Share, "Share")': "share accessibility action",
}

missing = [label for token, label in required_home.items() if token not in home]
if missing:
    raise SystemExit("HOME 4F MEDIA RED: missing " + ", ".join(missing))

required_panel = ["FynxHomeLifecycleRefresh", "key(refreshKey)"]
missing_panel = [token for token in required_panel if token not in panel]
if missing_panel:
    raise SystemExit("HOME 4F MEDIA RED: lifecycle boundary missing " + ", ".join(missing_panel))

required_lifecycle = ["LocalLifecycleOwner", "ON_RESUME", "removeObserver"]
missing_lifecycle = [token for token in required_lifecycle if token not in lifecycle]
if missing_lifecycle:
    raise SystemExit("HOME 4F MEDIA RED: lifecycle observer incomplete " + ", ".join(missing_lifecycle))

print("HOME 4F MEDIA/LIFECYCLE GREEN: existing Home media, accessibility, theme, and re-entry cleanup paths are wired")
