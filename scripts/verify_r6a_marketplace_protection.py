from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

protection = (ROOT / "backend/marketplaceProtection.js").read_text(encoding="utf-8")
resolution = (ROOT / "backend/marketplaceProtectionResolution.js").read_text(encoding="utf-8")
settlement = (ROOT / "backend/marketplaceSettlement.js").read_text(encoding="utf-8")

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

check("protection cases capture previous order and escrow state", "previous_order_status" in protection and "previous_escrow_status" in protection)
check("new protection cases synchronize order and escrow to DISPUTED", "SET status='DISPUTED'" in protection and "marketplace_escrows" in protection)
check("legacy disputes are reconciled into protection cases", "FYNX-DISPUTE-${dispute.id}" in protection and "DISPUTE_LINKED" in protection)
check("administrator access requires explicit admin-role membership", "fynx_admin_roles" in resolution and "SELECT id FROM users ORDER BY id ASC LIMIT 1" not in resolution)
check("buyer resolution creates an idempotent refund financial operation", "operation_type,idempotency_key,status,provider,amount,currency,metadata" in resolution and "operation_type='REFUND'" in resolution)
check("seller resolution is blocked while refund movement is active", "resolution === 'SELLER' && refundConflict" in resolution)
check("buyer resolution is blocked while seller payout movement is active", "resolution === 'BUYER' && payoutConflict" in resolution)
check("paid-order cancel resolution does not release inventory or cancel payment", "restoreOrderStatus" in resolution and "status='CANCELLED'" not in resolution.split("const restoreOrderStatus", 1)[0])
check("escrow supports explicit dispute and financial movement states", all(x in settlement for x in ["'REFUND_PENDING'", "'REFUNDED'", "'DISPUTED'", "'RELEASE_PENDING'", "'RELEASED'"]))
check("financial operations enforce unique idempotency and provider references", "UNIQUE (idempotency_key)" in settlement and "UNIQUE (provider_reference)" in settlement)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("R6-A marketplace protection gate failed: " + "; ".join(failed))
print(f"R6-A marketplace protection gate passed ({len(checks)} checks)")
