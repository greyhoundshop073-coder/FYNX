# FYNX Marketplace Home Route Audit

## Verified root cause
Marketplace social posts already persist the canonical listing identity in the real post body as `Listing ID: <id>`. The Home `View in Marketplace` control previously discarded that identity and opened the generic Marketplace surface.

## Corrective path
The existing Home marketplace-post control now extracts the persisted listing ID and uses the existing FYNX Marketplace deep-link route. The existing app deep-link handler carries that ID into `FynxMarketplacePanel`, which loads the exact real listing through the existing exact-listing loader.

## Fallback
If an older marketplace post has no valid listing ID, the existing generic Marketplace callback remains the fallback instead of inventing or guessing a listing.

## Verification evidence
- Existing Marketplace deep-link parser and app destination: `FynxDeepLinkDestination.Marketplace(listingId)`.
- Existing exact-listing loader: `loadExactMarketplaceListing(context, listingId)`.
- Corrective code commit: `886b32069898e619c7e806e1488fec6ce8c8d452`.
- Temporary patch workflow completed successfully and removed itself.
- Main branch after cleanup: `d158059ca5e6e305182aeae6bca238426ad85bd5`.

This document records the continuity fix; it does not introduce a second Marketplace routing system.
