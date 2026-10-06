package com.fynx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Buying Assistant entry surface using the real Marketplace listing as its context. */
@Composable
internal fun FynxMarketplaceBuyingAssistant(
    listing: FynxRemoteSocialClient.MarketplaceListing,
    onOpenAssistant: (listingId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("FYNX Buying Assistant", style = MaterialTheme.typography.titleMedium)
            Text("Get help evaluating this real Marketplace listing before you buy.")
            Button(onClick = { onOpenAssistant(listing.id) }) {
                Text("Ask FYNX")
            }
        }
    }
}
