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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Marketplace")
    }
}
