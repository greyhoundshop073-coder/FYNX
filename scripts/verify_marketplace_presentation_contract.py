#!/usr/bin/env python3
"""Regression checks for Marketplace seller avatars and first-listing entry points."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/fynx/app/ui"

def source(name: str) -> str:
    path = UI / name
    if not path.is_file():
        raise AssertionError(f"missing Marketplace source: {name}")
    return path.read_text(encoding="utf-8")

panel = source("FynxMarketplacePanel.kt")
reference = source("FynxMarketplaceReferenceProductComponents.kt")
storefront = source("FynxMarketplaceSellerStoreDialog.kt")
avatar = source("FynxMarketplaceSellerAvatar.kt")

checks = [
    ("Sell shortcut remains available when the catalogue is empty",
     "FloatingActionButton(onClick = { showSell = true }" in panel
     and "if (listings.isNotEmpty()) FloatingActionButton(onClick = { showSell = true }" not in panel),
    ("Marketplace loads avatar IDs for all current sellers while limiting reputation calls to twelve",
     "val reputationSellers = sellers.take(12)" in panel
     and "val reputationKeys = reputationSellers.map" in panel
     and "val sellers = listings.map" in panel
     and "pending.chunked(4)" in panel),
    ("Marketplace avatar wrapper follows shared avatar revisions and authoritative removal",
     "FynxAvatarIdentityStore.revisions().collectAsState()" in avatar
     and "hasCachedProfilePhotoEntry(context, normalizedOwner)" in avatar
     and "cachedProfilePhotoId(context, it)" in avatar
     and "FynxRemoteProfileAvatar(" in avatar),
    ("Marketplace product card uses the seller avatar wrapper",
     "FynxMarketplaceSellerAvatar(photoId, l.sellerUsername" in panel),
    ("Marketplace seller ranking card uses the seller avatar wrapper",
     "FynxMarketplaceSellerAvatar(photoId, listing.sellerUsername" in panel),
    ("Marketplace product details uses the seller avatar wrapper",
     "FynxMarketplaceSellerAvatar(" in reference and "FynxMarketplaceSellerAvatar(\n                                sellerPhotoId," in reference),
    ("Public seller storefront uses the seller avatar wrapper",
     "FynxMarketplaceSellerAvatar(" in storefront and "FynxMarketplaceSellerAvatar(\n                                    photoId," in storefront),
]
failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"[{'PASS' if ok else 'FAIL'}] {name}")
if failed:
    print(f"\nMARKETPLACE PRESENTATION CONTRACT RED ({len(failed)} failures)")
    sys.exit(1)
print(f"\nMARKETPLACE PRESENTATION CONTRACT GREEN ({len(checks)} checks)")
