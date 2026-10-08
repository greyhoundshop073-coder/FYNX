#!/usr/bin/env python3
"""Guard Home request amplification: reuse the profile cache path for photo hydration."""
from pathlib import Path
p=Path("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text()
start=p.index("fun resolveAuthorPhotos(")
end=p.index("fun loadHomeMarketplace(",start)
block=p[start:end]
assert "FynxSocialClient.searchUsers(context, username)" not in block
assert "FynxProfileRemoteClient.cachedProfilePhotoId" in block
assert "FynxProfileRemoteClient.get(context, username)" in block
start=p.index("fun hydratePeopleRecommendations(")
end=p.index("fun hydrateActiveStatuses(",start)
block=p[start:end]
assert "missingPeople" in block
assert "awaitAll().toMap()" in block
print("Home request efficiency guard: PASS")
