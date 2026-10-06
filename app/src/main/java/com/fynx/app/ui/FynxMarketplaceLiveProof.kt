package com.fynx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Live Proof entry surface for a real Marketplace listing.
 * The request remains tied to the seller/listing identity and is intentionally
 * presentation-only until the existing CameraX/realtime verification path is wired.
 */
@Composable
internal fun FynxMarketplaceLiveProof(
    listing: FynxRemoteSocialClient.MarketplaceListing,
    onRequestProof: (listingId: String, sellerUsername: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Live Proof", style = MaterialTheme.typography.titleMedium)
            Text("Ask the seller to verify this item with a live video proof.")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = {
                    onRequestProof(listing.id, listing.sellerUsername)
                }) {
                    Text("Request live proof")
                }
            }
        }
    }
}
