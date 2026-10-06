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
 *
 * The request remains tied to the real listing and seller. Capture and realtime
 * verification stay behind the existing shared CameraX/realtime infrastructure;
 * this surface does not create a second camera or fabricate verification state.
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
            Text("Request a live product verification from the seller before you buy.")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = { onRequestProof(listing.id, listing.sellerUsername) }) {
                    Text("Request Live Proof")
                }
            }
        }
    }
}
