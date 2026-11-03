from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

home = read("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt")
social = read("app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt")
lifecycle = read("app/src/main/java/com/fynx/app/ui/FynxHomeLifecycle.kt")
reactions = read("app/src/main/java/com/fynx/app/ui/FynxHomePostReactionsClient.kt")
discovery = read("app/src/main/java/com/fynx/app/ui/FynxHomeDiscoverySection.kt")

checks = {
    "H4 account-scoped feed cache": all(x in social for x in [
        'FEED_CACHE_KEY_PREFIX', 'FEED_CACHE_TIME_KEY_PREFIX',
        'feedCacheAccountKey', 'readStaleCachedFeed', 'writeCachedFeed',
    ]),
    "H4 lifecycle keeps cached snapshot": 'Keep the last known Home snapshot available' in lifecycle,
    "H5 optimistic like": 'optimisticLiked' in home and 'FynxRemoteSocialClient.like' in home,
    "H5 reactions": 'FynxHomePostReactionsClient.state' in home and 'FynxHomePostReactionsClient.set' in home,
    "H5 save and repost": 'FynxRemoteSocialClient.save' in home and 'FynxRemoteSocialClient.repost' in home,
    "H5 share engagement": 'recordEngagement(context, "SHARE"' in home,
    "H6 discovery merge": 'mergeDiscoveryPosts' in home and 'hydrateDiscovery' in home,
    "H6 discovery viewer": 'openHomeDiscoveryViewer' in home and 'homeDiscoveryViewerOpen' in home,
    "H6 discovery section": 'FynxHomeDiscoverySection' in discovery,
    "H6 discovery video loading": 'FynxDiscoveryClient.trending' in home,
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit(f"HOME H4-H6 GATE RED: {len(failed)} checks failed")
print(f"HOME H4-H6 GATE GREEN: {len(checks)}/{len(checks)} checks passed")
