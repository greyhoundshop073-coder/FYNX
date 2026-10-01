package com.fynx.app.ui

import androidx.compose.runtime.Composable

/**
 * Compatibility entry point for older callers.
 * The production Marketplace implementation is the single source of truth.
 */
@Composable
fun FynxMarketplaceRemotePanel(
    currentUsername: String = "",
    onOpenProfile: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {}
) {
    FynxMarketplacePanel(
        currentUsername = currentUsername,
        onOpenProfile = onOpenProfile
    )
}
