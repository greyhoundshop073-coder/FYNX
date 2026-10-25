#!/usr/bin/env python3
from pathlib import Path

client = Path("app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text()
home = Path("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text()
routes = Path("backend/socialRoutes.js").read_text()

checks = [
    ("feed page exposes a continuation cursor", "val nextCursor: String? = null" in client),
    ("backend health failure does not falsely block Home feed requests", "val activeNetworkReady = FynxNetworkQuality.current(context) != FynxNetworkQuality.Level.OFFLINE" in home and "networkReady ?: (FynxNetworkQuality.current(context)" not in home),
    ("feed client accepts and sends cursor", "beforeCursor: String? = null" in client and 'cursorQuery' in client),
    ("Home stores the feed cursor", "var feedNextCursor by remember" in home),
    ("Home uses cursor for subsequent pages", "beforeCursor = feedNextCursor" in home),
    ("backend validates the opaque cursor", "invalid feed cursor" in routes and "Buffer.from(rawCursor, 'base64url')" in routes),
    ("backend uses deterministic keyset ordering", "(p.created_at, p.id) < ($3::timestamptz, $4::bigint)" in routes and "ORDER BY p.created_at DESC,p.id DESC" in routes),
    ("backend returns the next cursor", "nextCursor" in routes and "cursor_created_at" in routes),
    ("legacy offset callers remain supported", "OFFSET $3" in routes),
    ("feed cache is not shared through HTTP", "Cache-Control', 'private, no-store'" in routes),
    ("matching feed index exists", "social_posts_created_id_idx" in routes),
]
checks.extend([
    ("failed next-page requests stop automatic re-fetch loops", "var paginationError by remember" in home and "paginationError == null" in home and "paginationError != null && !retry" in home),
    ("next-page retry keeps the existing continuation cursor", "fun loadMore(retry: Boolean = false)" in home and "beforeCursor = feedNextCursor" in home),
    ("Home presents a dedicated cursor-preserving pagination retry", 'item(key = "feed_pagination_error")' in home and 'loadMore(retry = true)' in home and 'Text("Retry loading posts")' in home),
    ("initial Home feed request has a bounded deadline", "private const val HOME_FEED_REQUEST_TIMEOUT_MS = 18_000L" in home and "withTimeoutOrNull(HOME_FEED_REQUEST_TIMEOUT_MS)" in home),
    ("timeout exposes a retryable error without erasing loaded posts", "Your feed is taking too long to load. Check your connection and retry." in home and "Could not refresh the feed. Showing the posts already loaded." in home),
    ("Home feed always clears loading and request lock in finally", "finally {" in reload and "loading = false\n                feedRequestInFlight = false" in reload),
    ("cancelled feed requests are rethrown after state cleanup", "catch (cancelled: kotlinx.coroutines.CancellationException)" in reload and "throw cancelled" in reload),
    ("cached feed revalidation is bounded in the owning request lifecycle", "val refreshedPage = withTimeoutOrNull(HOME_FEED_REQUEST_TIMEOUT_MS)" in reload and reload.count("scope.launch") == 1),
])
for name, ok in checks:
    if not ok:
        raise SystemExit(f"Home feed correctness gate FAILED: {name}")
print("Home feed correctness/pagination gate: PASS")
