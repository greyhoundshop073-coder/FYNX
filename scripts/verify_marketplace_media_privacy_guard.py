from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
guard = (ROOT / "backend/marketplaceMediaPrivacy.js").read_text(encoding="utf-8")
completion = (ROOT / "backend/marketplaceCompletion.js").read_text(encoding="utf-8")
scale = (ROOT / "backend/scalability.js").read_text(encoding="utf-8")

checks = [
    ("Marketplace media guard is installed by the production bootstrap", "installMarketplaceMediaPrivacyGuard(app)" in scale),
    ("Marketplace media endpoint itself requires authentication", "app.get('/api/marketplace/media/:id', auth" in completion),
    ("Guard only targets numeric marketplace media IDs", r"^\\/api\\/marketplace\\/media\\/(\\d+)$" in guard),
    ("Blocked seller relation is checked in both directions", "b.blocker_id=$2 AND b.blocked_id=ml.seller_id" in guard and "b.blocked_id=$2 AND b.blocker_id=ml.seller_id" in guard),
    ("An order-party media snapshot is an explicit privacy exception", "mo.buyer_id=$2 OR mo.seller_id=$2" in guard and "mo.product_snapshot->'mediaIds'" in guard),
    ("Listing availability does not suppress the block privacy check", "ml.active=TRUE" not in guard and "ml.quantity>0" not in guard and "ml.currency" not in guard),
    ("Database failures fail closed instead of exposing media", 'res.status(503).json({ error: "marketplace media privacy unavailable" })' in guard),
    ("Guard is moved ahead of the marketplace media route", 'path === "/api/marketplace/media/:id"' in guard and "Math.min(...targetIndexes)" in guard),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("Marketplace media privacy regression gate failed: " + "; ".join(failed))
print(f"Marketplace media privacy regression gate passed ({len(checks)} checks)")
