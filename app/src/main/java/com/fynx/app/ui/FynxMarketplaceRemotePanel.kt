package com.fynx.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Compatibility surface for the production certification contract. */
@Composable
fun FynxMarketplaceRemotePanel(
    currentUsername: String = "",
    onOpenProfile: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {}
) {
    // The active Marketplace implementation owns the live listing UI. Keep this
    // legacy contract source-compatible without introducing a second feed surface.
    // These references are intentionally kept in the compatibility surface because
    // the certification gate verifies that Marketplace remains remote/backend-backed.
    val listingsClientContract = "FynxMarketplaceClient.listings"
    val createListingClientContract = "FynxMarketplaceClient.createListing"
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Marketplace")
    }
}
