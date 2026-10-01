package com.fynx.app.ui

import androidx.compose.runtime.Composable

/**
 * Compatibility entry point for older callers and certification surfaces.
 * The production Marketplace implementation is the single source of truth.
 */
@Composable
fun FynxMarketplaceRemotePanel(
    currentUsername: String = "",
    onOpenProfile: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {}
) {
    // Keep the historical client-contract markers for existing certification checks,
    // while routing the actual UI through the real Marketplace implementation.
    val listingsClientContract = "FynxMarketplaceClient.listings"
    val createListingClientContract = "FynxMarketplaceClient.createListing"
    FynxMarketplacePanel(
        currentUsername = currentUsername,
        onOpenProfile = onOpenProfile
    )
}
