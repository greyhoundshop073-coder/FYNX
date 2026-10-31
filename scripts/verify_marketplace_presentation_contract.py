#!/usr/bin/env python3
"""Guard Marketplace-owned presentation, seller avatar and first-listing entry points."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/fynx/app/ui"

def read(name: str) -> str:
    path = UI / name
    if not path.is_file():
        raise AssertionError(f"required Marketplace UI source is missing: {name}")
    return path.read_text(encoding="utf-8")

checks = []

def check(name: str, ok: bool) -> None:
    checks.append((name, bool(ok)))

panel = read("FynxMarketplacePanel.kt")
reference = read("FynxMarketplaceReferenceProductComponents.kt")
storefront = read("FynxMarketplaceSellerStoreDialog.kt")
details = read("FynxMarketplaceDetailsSearchReference.kt")
seller_center = read("FynxMarketplaceSellerCenterPanel.kt")
remote = read("FynxRemoteSocialClient.kt")
client = read("FynxMarketplaceClient.kt")

# The active Marketplace screen must let a first-time seller create their first listing.
sell_lines = [line.strip() for line in panel.splitlines() if "FloatingActionButton(onClick = { showSell = true }" in line]
check("Sell entry point remains visible when Marketplace has no current listings",
      len(sell_lines) == 1 and "if (listings.isNotEmpty())" not in sell_lines[0])
check("Sell entry point opens the real product-publish dialog",
      "if (showSell) MarketplaceSellDialog(" in panel and "onPublished = { listingId ->" in panel)

# Marketplace fetches sellerPhotoId/photoId itself. Passing ownerUsername makes the shared
# avatar composable prefer its separate profile cache and ignore that fetched media ID.
def avatar_lines(source: str):
    return [line.strip() for line in source.splitlines() if "FynxRemoteProfileAvatar(" in line]

panel_avatars = avatar_lines(panel)
reference_avatars = avatar_lines(reference)
storefront_avatars = avatar_lines(storefront)
check("Marketplace seller-card and details avatars consume fetched photo IDs",
      len(panel_avatars) == 2 and all("ownerUsername =" not in line for line in panel_avatars)
      and all("photoId," in line for line in panel_avatars))
check("Marketplace fetches seller avatars for all currently loaded product sellers",
      "val sellers = listings.distinctBy" in panel
      and "val reputationSellers = sellers.take(12)" in panel
      and "if (!photoMap.containsKey(key))" in panel
      and "FynxProfileRemoteClient.cachedProfilePhotoId(context, username)" in panel)
check("Marketplace product-card avatar consumes sellerPhotoId directly",
      len(reference_avatars) == 1 and "FynxRemoteProfileAvatar(sellerPhotoId," in reference_avatars[0]
      and "ownerUsername =" not in reference_avatars[0])
check("Public seller-store avatar consumes fetched photoId directly",
      len(storefront_avatars) == 1 and "FynxRemoteProfileAvatar(photoId," in storefront_avatars[0]
      and "ownerUsername =" not in storefront_avatars[0])

# Verify listing media IDs are wired through the authenticated remote-media loader in all
# buyer and seller presentation surfaces.
check("Marketplace product cards render the listing's uploaded media IDs",
      "RemoteMarketMedia(context, listing.mediaIds.first()" in reference
      and "FynxMarketplaceClient.mediaUrl(context, mediaId)" in reference)
check("Product detail gallery renders listing media through FYNX media URLs",
      "listing.mediaIds.take(12)" in details
      and "FynxMarketplaceClient.mediaUrl(context, mediaId)" in details
      and "FynxRemoteMedia(url" in details)
check("My Shop inventory loads owner listings and renders uploaded product media",
      "FynxMarketplaceClient.myListings(context)" in seller_center
      and "FynxMarketplaceClient.mediaUrl(context, listing.mediaIds.first())" in seller_center)
check("Public seller storefront requests that seller's own available inventory",
      'FynxRemoteSocialClient.listings(context, seller = username.removePrefix("@").trim())' in storefront
      and "storefrontListings = fetched.filter { it.active && it.quantity > 0 }" in storefront)

# Publish path uploads the selected product media and posts listing media IDs before confirming.
check("Product publishing uploads selected media before creating the listing",
      "FynxProductionMessaging.uploadMedia(context, u, mime).getOrThrow().id" in remote
      and 'FynxBackendClient.postJson(context, "/api/marketplace/listings"' in remote
      and 'put("mediaIds", JSONArray(mediaIds))' in remote)
check("My Shop client uses the private owner-inventory endpoint",
      '"/api/marketplace/my-listings"' in client)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"[{'PASS' if ok else 'FAIL'}] {name}")
if failed:
    print(f"\nMARKETPLACE PRESENTATION CONTRACT RED ({len(failed)} failures)")
    sys.exit(1)
print(f"\nMARKETPLACE PRESENTATION CONTRACT GREEN ({len(checks)} checks)")
