#!/usr/bin/env python3
"""Guard the Home continuity contract used by every Android APK build.

This verifies the existing implementation rather than replacing it:
- Home has an account-scoped feed cache with stale-cache recovery.
- Home lifecycle refreshes can consume the cached feed.
- Remote media is persisted in account-scoped app storage and reused offline.
- Home video playback is focused to one visible post and is lifecycle-safe.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt"
SOCIAL = ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt"
MEDIA = ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteMedia.kt"

home = HOME.read_text(encoding="utf-8")
social = SOCIAL.read_text(encoding="utf-8")
media = MEDIA.read_text(encoding="utf-8")

checks = {
    "Home lifecycle refresh bus": "FynxHomeLifecycleRefreshBus.currentVersion()",
    "cached feed lifecycle path": "feedPage(context, limit = 20, offset = 0, useCache = !forceRefresh)",
    "feed stale-cache fallback": "readStaleCachedFeed(context)?.let { return Result.success(it) }",
    "account-scoped feed cache": "FEED_CACHE_KEY_PREFIX",
    "focused video selection": "focusedVideoPostId",
    "single focused playback gate": "playbackActive = focusedVideoPostId == post.id",
    "persistent remote media cache": "fynx_media_remote_",
    "account-scoped media cache": "FynxAuthStore.accountStorageKey(context)",
    "offline cached-media recovery": "cacheTarget?.exists() == true",
    "offline guard": "FynxNetworkQuality.current(context) == FynxNetworkQuality.Level.OFFLINE",
    "video lifecycle cleanup": "videoView?.stopPlayback()",
}

sources = {
    "Home lifecycle refresh bus": home,
    "cached feed lifecycle path": home,
    "feed stale-cache fallback": social,
    "account-scoped feed cache": social,
    "focused video selection": home,
    "single focused playback gate": home,
    "persistent remote media cache": media,
    "account-scoped media cache": media,
    "offline cached-media recovery": media,
    "offline guard": media,
    "video lifecycle cleanup": media,
}

missing = [f"{label}: {needle}" for label, needle in checks.items() if needle not in sources[label]]
if missing:
    raise SystemExit("Home cache/APK contract failed:\n- " + "\n- ".join(missing))

print("Home cache/APK contract: PASS")
