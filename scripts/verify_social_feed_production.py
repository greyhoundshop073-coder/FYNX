from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
package = (ROOT / "backend/package.json").read_text()
bootstrap = (ROOT / "backend/socialFeedBootstrap.js").read_text()
realtime = (ROOT / "backend/realtimeIsolationBootstrap.js").read_text()
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text()

checks = [
    ("production start uses social feed bootstrap", '"start": "node socialFeedBootstrap.js"' in package),
    ("feed bootstrap delegates to existing production isolation bootstrap", 'import("./realtimeIsolationBootstrap.js")' in bootstrap),
    ("feed bootstrap patches the real social route source", 'socialRoutes.js' in bootstrap and 'writeFile(socialPath' in bootstrap),
    ("production feed route is installed", "app.get('/api/social/feed'" in bootstrap),
    ("feed uses authenticated user identity", "req.user.sub" in bootstrap),
    ("feed applies public/friends/self visibility", "FRIENDS_ONLY" in bootstrap and "fr.status = 'accepted'" in bootstrap),
    ("feed applies block isolation", "FROM blocks b" in bootstrap),
    ("feed uses offset pagination", "OFFSET $3" in bootstrap and "requestedOffset" in bootstrap),
    ("feed returns authoritative hasMore", "const hasMore = result.rows.length > limit" in bootstrap and "{ posts, hasMore }" in bootstrap),
    ("client sends offset pagination", "offset=$safeOffset" in client),
    ("client consumes hasMore", 'root.optBoolean("hasMore"' in client),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit(f"social feed production verification failed: {len(failed)} check(s)")
print("Social feed production verification GREEN")
