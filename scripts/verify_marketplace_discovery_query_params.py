from pathlib import Path

source = Path("backend/discoveryRoutes.js").read_text(encoding="utf-8")
start = source.index('app.get("/api/marketplace/discovery"')
end = source.index('app.get("/api/marketplace/listing/:id"', start)
route = source[start:end]

checks = {
    "category filter uses a bound parameter": 'where.push(`l.category=${params.length}`)' in route,
    "location filter uses a bound parameter": 'where.push(`l.location ILIKE ${params.length}`)' in route,
    "limit uses a bound parameter": 'LIMIT ${params.length - 1} OFFSET ${params.length}' in route,
}
for label, passed in checks.items():
    print(("PASS" if passed else "FAIL") + ": " + label)
if not all(checks.values()):
    raise SystemExit("Marketplace discovery SQL parameter regression detected")
