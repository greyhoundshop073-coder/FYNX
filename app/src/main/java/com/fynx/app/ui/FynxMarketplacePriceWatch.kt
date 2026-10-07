package com.fynx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Price Watch entry point tied to an existing Marketplace listing. */
@Composable
internal fun FynxMarketplacePriceWatch(
    listing: FynxRemoteSocialClient.MarketplaceListing,
    watched: Boolean,
    busy: Boolean = false,
    onWatchPrice: (listingId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Price Watch", style = MaterialTheme.typography.titleMedium)
            Text(if (watched) "You will be notified when this listing price changes." else "Get a FYNX notification when this listing price changes.")
            if (watched) {
                TextButton(enabled = !busy, onClick = { onWatchPrice(listing.id) }) { Text("Stop watching") }
            } else {
                Button(enabled = !busy, onClick = { onWatchPrice(listing.id) }) {
                    if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text("Watch price")
                }
            }
        }
    }
}
