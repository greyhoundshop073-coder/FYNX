#!/usr/bin/env python3
"""Ensure checkout quote displays total marketplace fee separately from buyer share."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
client = (root / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceCheckout.kt").read_text()
server = (root / "backend/marketplaceCheckout.js").read_text()

checks = [
    ("backend quote exposes total marketplace fee", "marketplaceFee, marketplaceFeeBuyer, marketplaceFeeSeller" in server),
    ("Android maps total fee from marketplaceFee", 'marketplaceFee = q.optDouble("marketplaceFee")' in client),
    ("Android maps buyer share from marketplaceFeeBuyer", 'marketplaceFeeBuyer = q.optDouble("marketplaceFeeBuyer")' in client),
    ("total fee and buyer share are not accidentally mapped to same field",
     'marketplaceFee = q.optDouble("marketplaceFeeBuyer")' not in client),
]
failed = False
for label, passed in checks:
    print(f'{"PASS" if passed else "FAIL"}: {label}')
    failed |= not passed
raise SystemExit(1 if failed else 0)
