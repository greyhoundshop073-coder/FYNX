#!/usr/bin/env python3
from pathlib import Path
client=Path("app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text()
home=Path("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text()
assert 'fun hasCachedFeed(context: Context, mode: String = "FOR_YOU"): Boolean' in client
assert 'normalizedFeedMode(mode)' in client
assert 'FEED_CACHE_KEY_PREFIX + it + if (normalizedFeedMode(mode) == "following") "_following" else ""' in client
assert "val hadFreshCache = !forceRefresh && FynxRemoteSocialClient.hasCachedFeed(context, mode = feedMode)" in home
assert "FynxRemoteSocialClient.cachedFeedPage(context, mode = feedMode)" in home
assert "FynxRemoteSocialClient.feedPage(context, limit = 20, offset = 0, useCache = !forceRefresh, mode = feedMode)" in home
assert "useCache = false" in home
assert "Keep the valid cached snapshot visible." in home
print("Home cache/lifecycle guard: PASS")
