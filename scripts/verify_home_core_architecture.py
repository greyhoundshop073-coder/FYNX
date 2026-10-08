#!/usr/bin/env python3
"""Guard the Home core/secondary loading boundary."""
from pathlib import Path

HOME = Path("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text()
assert "FynxRemoteSocialClient.feedPage(context, limit = 20, offset = 0, useCache = !forceRefresh)" in HOME
assert "hydrateDiscovery(0, true)" in HOME

start = HOME.index("fun reload(forceRefresh: Boolean = false)")
end = HOME.index("LaunchedEffect(publishRefreshKey)", start)
reload_block = HOME[start:end]

for forbidden in (
    "hydrateDiscovery(0, true)",
    "hydratePeopleRecommendations(",
    "loadHomeMarketplace(",
    "loadDiscoveryVideos(",
):
    assert forbidden not in reload_block, f"secondary loader leaked into reload(): {forbidden}"

effect_start = HOME.index("LaunchedEffect(publishRefreshKey)")
effect_end = HOME.index("LaunchedEffect(feedListState", effect_start)
secondary_effect = HOME[effect_start:effect_end]
for required in (
    "hydratePeopleRecommendations(0, true)",
    "loadHomeMarketplace(true)",
    "loadDiscoveryVideos(true)",
    "hydrateDiscovery(0, true)",
):
    assert required in secondary_effect, f"secondary loader missing: {required}"

print("Home core architecture independence: PASS")
