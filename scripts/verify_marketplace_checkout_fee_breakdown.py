#!/usr/bin/env python3
"""Keep the full FYNX Marketplace fee and each payer's share transparent and consistent."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
client = (root / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceCheckout.kt").read_text(encoding="utf-8")
panel = (root / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text(encoding="utf-8")
quote = (root / "backend/marketplaceCheckout.js").read_text(encoding="utf-8")
order = (root / "backend/marketplaceCheckoutOrder.js").read_text(encoding="utf-8")

checks = [
    ("backend quote exposes total, buyer and seller fee fields",
     "marketplaceFee, marketplaceFeeBuyer, marketplaceFeeSeller" in quote),
    ("Android maps total fee from marketplaceFee",
     'marketplaceFee = q.optDouble("marketplaceFee")' in client),
    ("Android maps buyer share from marketplaceFeeBuyer",
     'marketplaceFeeBuyer = q.optDouble("marketplaceFeeBuyer")' in client),
    ("Android maps seller share from marketplaceFeeSeller",
     'marketplaceFeeSeller = q.optDouble("marketplaceFeeSeller")' in client),
    ("single-product checkout labels the total FYNX fee and buyer share separately",
     "Total FYNX marketplace fee:" in client and "Your FYNX fee share:" in client),
    ("single-product checkout explains seller-paid fee is deducted from seller payout",
     "Seller-paid FYNX fee (deducted from seller payout)" in client),
    ("multi-product quote item maps total fee and both payer shares",
     'marketplaceFee = item.optDouble("marketplaceFee")' in panel and
     'marketplaceFeeBuyer = item.optDouble("marketplaceFeeBuyer")' in panel and
     'marketplaceFeeSeller = item.optDouble("marketplaceFeeSeller")' in panel),
    ("multi-product quote maps aggregate fee and seller share",
     'marketplaceFee = q.optDouble("marketplaceFee")' in panel and
     'sellerFee = q.optDouble("marketplaceFeeSeller")' in panel),
    ("multi-product checkout shows total fee and buyer share separately",
     "Total FYNX marketplace fee:" in panel and "Your FYNX fee share:" in panel),
    ("multi-product checkout explains seller-paid fee is deducted from seller payout",
     "Seller-paid FYNX fee (deducted from seller payout)" in panel),
    ("quote and order creation use the same percentage-plus-fixed fee basis",
     "productSubtotal * (config.bps / 10000) + config.fixed" in quote and
     "productSubtotal * (config.bps / 10000) + config.fixed" in order),
    ("quote and order creation split fees using the same fee policy",
     "config.mode === 'BUYER' ? 1 : config.mode === 'SELLER' ? 0 : config.mode === 'SPLIT' ? config.buyerShare / 10000 : 0" in quote and
     "config.mode === 'BUYER' ? 1 : config.mode === 'SELLER' ? 0 : config.mode === 'SPLIT' ? config.buyerShare / 10000 : 0" in order),
    ("buyer total adds only the buyer-paid fee share",
     "productSubtotal + deliveryFee + marketplaceFeeBuyer" in quote and
     "productSubtotal + deliveryFee + marketplaceFeeBuyer" in order),
    ("multi-product quote returns aggregate total fee and seller share",
     "marketplaceFee: Math.round(marketplaceFee * 100) / 100" in quote and
     "marketplaceFeeSeller: Math.round(marketplaceFeeSeller * 100) / 100" in quote),
]
failed = False
for label, passed in checks:
    print(f'{"PASS" if passed else "FAIL"}: {label}')
    failed |= not passed
if failed:
    raise SystemExit(1)
print(f"Marketplace checkout fee breakdown verification GREEN ({len(checks)} checks)")
