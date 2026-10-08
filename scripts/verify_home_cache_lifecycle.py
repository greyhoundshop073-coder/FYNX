#!/usr/bin/env python3
from pathlib import Path
client=Path("app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text()
home=Path("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text()
assert "fun hasCachedFeed(context: Context): Boolean" in client
assert "val hadFreshCache = !forceRefresh && FynxRemoteSocialClient.hasCachedFeed(context)" in home
assert "useCache = false" in home
assert "Keep the valid cached snapshot visible." in home
print("Home cache/lifecycle guard: PASS")
