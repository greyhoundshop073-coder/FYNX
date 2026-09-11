from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
worker = (ROOT / "backend" / "marketplacePaymentExpiry.js").read_text()
scalability = (ROOT / "backend" / "scalability.js").read_text()
transactions = (ROOT / "backend" / "marketplaceTransactions.js").read_text()

checks = [
    ("expiry worker exists", "expireUnpaidMarketplaceReservations" in worker),
    ("30 minute reservation TTL", "PAYMENT_RESERVATION_TTL_MINUTES = 30" in worker),
    ("all stale payment-pending orders are candidates", "status='PAYMENT_PENDING'" in worker and "created_at <= NOW() - ($1::text || ' minutes')::interval" in worker and "payment_reference IS NULL" not in worker.split("WHERE status='PAYMENT_PENDING'", 1)[1].split("ORDER BY", 1)[0]),
    ("initialized payments are verified with Paystack before expiry", "verifyProviderPayment" in worker and "transaction/verify/" in worker and "payment_reference" in worker),
    ("provider payment amount currency and metadata are validated", "expectedAmount !== null" in worker and "metadataOrderId === String(order.id)" in worker and "transaction.status === 'success'" in worker),
    ("verified late payment becomes paid", "confirmLatePayment" in worker and "status='PAID'" in worker and "PAYMENT_CONFIRMED" in worker),
    ("unpaid stale orders expire", "status='CANCELLED'" in worker and "ORDER_PAYMENT_EXPIRED" in worker),
    ("expired reservation releases inventory", "reserved_quantity=GREATEST(0,reserved_quantity-$1)" in worker),
    ("expiry is concurrency safe", "FOR UPDATE SKIP LOCKED" in worker),
    ("expiry runs periodically", "setInterval" in worker and "60_000" in worker),
    ("expiry is registered", "registerMarketplacePaymentExpiryWorker" in scalability),
    ("order creation reserves inventory", "reserved_quantity=reserved_quantity+$1" in transactions),
    ("manual cancellation remains payment-pending only", "order.status!=='PAYMENT_PENDING'" in transactions),
]

failed = [name for name, ok in checks if not ok]
if failed:
    print("R6-B payment expiry guard FAILED")
    for name in failed:
        print(f" - {name}")
    raise SystemExit(1)

print(f"R6-B payment expiry guard GREEN ({len(checks)} checks)")
