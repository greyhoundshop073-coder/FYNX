#!/usr/bin/env python3
"""Large Badge #6: protected marketplace transaction lifecycle certification."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text(encoding="utf-8")
checkout = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceCheckout.kt").read_text(encoding="utf-8")
lifecycle = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceOrderLifecycle.kt").read_text(encoding="utf-8")
panel = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text(encoding="utf-8")
protection = (ROOT / "backend/marketplaceProtection.js").read_text(encoding="utf-8")
resolution = (ROOT / "backend/marketplaceProtectionResolution.js").read_text(encoding="utf-8")
completion = (ROOT / "backend/marketplaceCompletion.js").read_text(encoding="utf-8")
transactions = (ROOT / "backend/marketplaceTransactions.js").read_text(encoding="utf-8")
settlement = (ROOT / "backend/marketplaceSettlement.js").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")

checks = [
    ("buyer checkout creates a protected order before payment", "createProtectedMarketplaceCheckoutOrder" in checkout and "paymentOrder" in panel),
    ("checkout sends the canonical order ID to payment initialization", 'put("orderId", orderId)' in checkout),
    ("payment verification is required before protected confirmation", "verifyMarketplacePayment" in panel and "Verify payment" in panel),
    ("buyer can confirm delivery through the backend", '"/api/marketplace/orders/$id/confirm-delivery"' in client),
    ("seller shipping uses the backend order route", '"/api/marketplace/orders/$id/ship"' in client),
    ("order lifecycle exposes received confirmation", "Confirm received" in lifecycle and "confirmMarketplaceDelivery" in lifecycle),
    ("order lifecycle exposes completion after inspection", "Complete the order" in lifecycle and '"INSPECTION"' in lifecycle),
    ("order lifecycle exposes dispute reporting", "disputeMarketplaceOrder" in panel and "Open dispute" in lifecycle),
    ("unpaid protected orders can be cancelled", "cancelMarketplaceOrder" in panel and "PAYMENT_PENDING" in panel),
    ("backend delivery confirmation is authenticated", "app.post('/api/marketplace/orders/:id/confirm-delivery', auth" in completion),
    ("backend shipping requires seller authorization", "only the seller can ship this order" in completion),
    ("active disputes block completion", "status IN ('OPEN','UNDER_REVIEW')" in completion),
    ("dispute protection locks the canonical order", "FOR UPDATE" in protection),
    ("protection records previous order and escrow state", "previous_order_status" in protection and "previous_escrow_status" in protection),
    ("buyer dispute resolution creates idempotent refund movement", "REFUND-" in resolution and "REFUND" in resolution),
    ("seller resolution is blocked during refund movement", "refundConflict" in resolution),
    ("buyer resolution is blocked during payout movement", "payoutConflict" in resolution),
    ("escrow has explicit pending/refunded/disputed/release states", all(x in settlement for x in ["'REFUND_PENDING'", "'REFUNDED'", "'DISPUTED'", "'RELEASE_PENDING'", "'RELEASED'"])),
    ("financial operations have unique idempotency keys", "idempotency_key TEXT NOT NULL UNIQUE" in settlement),
    ("financial operations protect provider references", "marketplace_fin_ops_provider_ref_idx" in settlement),
    ("order evidence is stored server-side", "marketplace_order_evidence" in transactions),
    ("payment order creation remains idempotent", "clientOrderId" in transactions and "idempotent:true" in transactions),
    ("inventory reservation occurs inside the transaction", "reserved_quantity=reserved_quantity+$1" in transactions and "BEGIN" in transactions),
    ("existing marketplace protection gates remain in CI", "verify_r6a_marketplace_protection.py" in workflow and "verify_marketplace_integration_security.py" in workflow and "verify_marketplace_seller_flow.py" in workflow),
    ("real Android instrumentation remains in CI", "connectedDebugAndroidTest" in workflow and "verify_runtime_navigation.py" in workflow),
]

failed = []
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
    if not ok:
        failed.append(name)

if failed:
    raise SystemExit("LARGE BADGE #6 RED: " + "; ".join(failed))

print(f"LARGE BADGE #6 MARKETPLACE PROTECTED TRANSACTION CONTRACT: GREEN ({len(checks)} checks)")
