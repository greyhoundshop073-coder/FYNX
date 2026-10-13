from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxUniversalSearchPanel.kt").read_text(encoding="utf-8")

checks = {
    "query edits invalidate previous in-flight requests before debounce": "val generation = ++searchGeneration" in source and "if (generation != searchGeneration) return@LaunchedEffect" in source,
    "short queries clear stale people, listings, and posts": all(token in source for token in ["people = emptyList()", "listings = emptyList()", "posts = emptyList()"]),
    "short queries stop the stale loading indicator": "loading = false" in source,
    "one-character queries show the minimum-length hint": '"Enter at least 2 characters to search FYNX."' in source,
    "stale async responses are ignored before updating visible results": "if (generation != searchGeneration) return@launch" in source,
    "debounced search passes the query and generation captured for that edit": "search(q, generation)" in source,
    "manual search creates a fresh generation": "expectedGeneration ?: ++searchGeneration" in source,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("UNIVERSAL SEARCH CONTRACT RED: " + "; ".join(failed))
print(f"UNIVERSAL SEARCH CONTRACT GREEN ({len(checks)} checks)")
