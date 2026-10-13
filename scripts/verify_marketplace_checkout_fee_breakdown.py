#!/usr/bin/env python3
"""Ensure the checkout quote keeps the full FYNX fee distinct from the buyer's share."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
client = (root / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceCheckout.kt").read_text(encoding="utf-8")
server = (root / "backend/marketplaceCheckout.js").read_text(encoding="utf-8")

checks = [
    ("backend exposes total, buyer and seller fee fields",
     "marketplaceFee, marketplaceFeeBuyer, marketplaceFeeSeller" in server),
    ("Android maps total fee from marketplaceFee",
     'marketplaceFee = q.optDouble("marketplaceFee")' in client),
    ("Android maps buyer share from marketplaceFeeBuyer",
     'marketplaceFeeBuyer = q.optDouble("marketplaceFeeBuyer")' in client),
    ("total fee is not accidentally mapped from the buyer share",
     'marketplaceFee = q.optDouble("marketplaceFeeBuyer")' not in client),
    ("checkout display uses the buyer-payable fee share",
     'q.marketplaceFeeBuyer' in client),
]
failed = False
for label, passed in checks:
    print(f'{"PASS" if passed else "FAIL"}: {label}')
    failed |= not passed
raise SystemExit(1 if failed else 0)
