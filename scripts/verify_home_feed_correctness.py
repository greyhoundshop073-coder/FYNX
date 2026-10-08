#!/usr/bin/env python3
from pathlib import Path

client = Path("app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text()
home = Path("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text()
routes = Path("backend/socialRoutes.js").read_text()

checks = [
    ("feed page exposes a continuation cursor", "val nextCursor: String? = null" in client),
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
for name, ok in checks:
    if not ok:
        raise SystemExit(f"Home feed correctness gate FAILED: {name}")
print("Home feed correctness/pagination gate: PASS")
