from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "backend/marketplaceReputation.js").read_text(encoding="utf-8")

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

payment_route = source.split("app.post('/api/marketplace/orders/:id/payment'", 1)[1].split("app.get('/api/marketplace/payments/verify/:reference'", 1)[0]
check("Paystack initialization happens before the database transaction", "const data = await paystackRequest('/transaction/initialize'" in payment_route and payment_route.index("const data = await paystackRequest('/transaction/initialize'") < payment_route.index("await client.query('BEGIN')"))
check("payment commit is protected by a locked recheck", "FOR UPDATE" in payment_route and "status='PAYMENT_PENDING'" in payment_route)
check("payment reference cannot be overwritten by a racing initializer", "payment_reference IS NULL" in payment_route)
check("payment initialization remains idempotent for an existing authorization", "idempotent: true" in payment_route and "payment_authorization_url" in payment_route)
check("provider metadata binds initialization to the marketplace order", "purpose: 'FYNX_MARKETPLACE_ORDER'" in payment_route and "orderId: String(existing.id)" in payment_route and "buyerId: String(existing.buyer_id)" in payment_route)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("R6-B payment reliability gate failed: " + "; ".join(failed))
print(f"R6-B payment reliability gate passed ({len(checks)} checks)")
