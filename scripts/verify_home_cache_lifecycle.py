#!/usr/bin/env python3
from pathlib import Path
client=Path("app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text()
home=Path("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text()
assert "fun hasCachedFeed(context: Context): Boolean" in client
assert "val hadFreshCache = !forceRefresh && FynxRemoteSocialClient.hasCachedFeed(context)" in home
assert "useCache = false" in home
assert "HOME_FEED_REQUEST_TIMEOUT_MS = 18_000L" in home
assert "val feedPageResult = withTimeoutOrNull(HOME_FEED_REQUEST_TIMEOUT_MS)" in home
assert "Your feed is taking too long to load. Check your connection and retry." in home
assert "Could not refresh the feed. Showing the posts already loaded." in home
assert "val refreshedPage = withTimeoutOrNull(HOME_FEED_REQUEST_TIMEOUT_MS)" in home
print("Home cache/lifecycle guard: PASS")
