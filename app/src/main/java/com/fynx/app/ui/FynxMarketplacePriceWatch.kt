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

/** Price Watch entry point tied to an existing Marketplace listing. */
@Composable
internal fun FynxMarketplacePriceWatch(
    listing: FynxRemoteSocialClient.MarketplaceListing,
    onWatchPrice: (listingId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Price Watch", style = MaterialTheme.typography.titleMedium)
            Text("Watch this listing for future price changes.")
            Button(onClick = { onWatchPrice(listing.id) }) {
                Text("Watch price")
            }
        }
    }
}
