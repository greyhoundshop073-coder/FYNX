from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"HOME H3/H4 RED: missing {label}: {needle}")


home = read("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt")
client = read("app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt")
lifecycle = read("app/src/main/java/com/fynx/app/ui/FynxHomeLifecycle.kt")

# H3: feed continuity/pagination/recovery safeguards already present in the production Home.
for needle, label in (
    ("rememberLazyListState()", "stable Home list state"),
    ("feedRequestInFlight", "request gate"),
    ("FEED_REFRESH_DEBOUNCE_MS", "refresh debounce"),
    ("snapshotFlow { feedListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to feedListState.layoutInfo.totalItemsCount }", "automatic near-bottom pagination"),
    ("if (hasMore) loadMore() else if (discoveryHasMore) hydrateDiscovery(discoveryOffset)", "feed/discovery continuation"),
    ("val existing = posts.map { it.id }.toSet()", "page deduplication set"),
    ("filterNot { it.id in existing }", "duplicate page filtering"),
    ("if (clean.isEmpty()) return base", "discovery duplicate guard"),
    ("onFailure { error = it.message ?: \"Unable to load more posts.\" }", "non-destructive pagination error state"),
    ("if (loadingMore) item(key = \"feed_loading_more\")", "loading-more indicator"),
):
    require(home, needle, label)

# H4: all Home interaction paths use optimistic state, request serialization and rollback.
for needle, label in (
    ("fun runInteraction(", "shared interaction coordinator"),
    ("interactionBusy", "interaction request gate"),
    ("val previous = interactionStates[id]", "interaction snapshot"),
    ("interactionStates = interactionStates + (id to update(previous, desired, optimisticCount))", "optimistic interaction update"),
    ("onFailure { interactionStates = interactionStates + (id to previous)", "interaction rollback"),
    ("fun runLike(id: String)", "like optimistic path"),
    ("optimisticLiked", "like optimistic state"),
    ("FynxRemoteSocialClient.save(context", "save backend path"),
    ("FynxRemoteSocialClient.repost(context", "repost backend path"),
    ("FynxRemoteSocialClient.follow(context", "follow backend path"),
    ("FynxHomeCommentsPanel", "comment surface"),
    ("sharePost(context, post)", "share path"),
    ("deletePost = null", "delete reconciliation"),
):
    require(home, needle, label)

# Refresh must signal the Home without destroying its cached snapshot.
for needle, label in (
    ("FynxHomeLifecycleRefreshBus.currentVersion()", "lifecycle refresh signal"),
    ("refreshSignal.value++", "refresh notification"),
):
    require(home if "currentVersion" in needle else lifecycle, needle, label)

# The feed client must retain a stale first-page fallback when refresh/network fails.
for needle, label in (
    ("FEED_CACHE_TTL_MS", "feed cache TTL"),
    ("readCachedFeed(context)", "fresh cache read"),
    ("readStaleCachedFeed(context)", "stale cache fallback"),
    ("if (safeOffset == 0 && remote.isFailure)", "offline refresh fallback"),
):
    require(client, needle, label)

print("HOME H3/H4 GREEN: feed continuity, pagination, interaction optimistic/rollback, and offline refresh safeguards are present.")
