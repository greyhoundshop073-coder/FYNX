from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomePostMediaClient.kt").read_text(encoding="utf-8")
backend = (ROOT / "backend/socialMultiMediaBootstrap.js").read_text(encoding="utf-8")

checks = [
    ("Home media client uses the real authenticated backend client", "FynxBackendClient.get(context" in client),
    ("Home media client uses the authorized post-media endpoint", '"/api/social/posts/$numericId/media"' in client),
    ("Home media client accepts the real backend media manifest", 'optJSONArray("media")' in client),
    ("Home media client preserves media type and order", "mediaType" in client and "sortedBy { it.position }" in client),
    ("Home media client keeps the UI bounded to four assets", ".take(4)" in client),
    ("backend exposes the authorized ordered media endpoint", "app.get('/api/social/posts/:id/media'" in backend and "ORDER BY spm.position ASC" in backend),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit(f"Home post media client verification failed: {len(failed)} check(s)")
print("Home post media client gate: GREEN")
