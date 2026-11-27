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
    ("Marketplace loads seller identity for the first twelve distinct sellers in bounded batches",
     "val sellers = listings.map { it.sellerUsername.removePrefix(\"@\").trim() }" in panel
     and ".distinctBy { it.lowercase(Locale.US) }.take(12)" in panel
     and "val pending = sellers.filter" in panel
     and "pending.chunked(4)" in panel
     and "FynxProfileRemoteClient.get(context, username)" in panel
     and "photoUpdates[key] = profile.profilePhotoMediaId" in panel),
    ("Marketplace avatar wrapper follows shared avatar revisions and authoritative removal",
     "FynxAvatarIdentityStore.revisions().collectAsState()" in avatar
     and "hasCachedProfilePhotoEntry(context, normalizedOwner)" in avatar
     and "cachedProfilePhotoId(context, it)" in avatar
     and "FynxRemoteProfileAvatar(" in avatar),
    ("Marketplace product cards use the account-aware seller avatar wrapper",
     "FynxMarketplaceSellerAvatar(" in reference
     and "sellerPhotoId," in reference
     and "listing.sellerUsername," in reference),
    ("Marketplace seller ranking cards use the account-aware seller avatar wrapper",
     "FynxMarketplaceSellerAvatar(photoId, listing.sellerUsername" in panel),
    ("Public seller storefront uses the account-aware seller avatar wrapper",
     "FynxMarketplaceSellerAvatar(" in storefront
     and "username," in storefront
     and "photoId," in storefront),
]
failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(( "PASS" if ok else "FAIL") + ": " + name)
if failed:
    print(f"\nMARKETPLACE PRESENTATION CONTRACT RED ({len(failed)} failures)")
    sys.exit(1)
print(f"\nMARKETPLACE PRESENTATION CONTRACT GREEN ({len(checks)} checks)")
