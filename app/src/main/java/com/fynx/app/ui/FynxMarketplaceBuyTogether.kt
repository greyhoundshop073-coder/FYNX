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
 * Buy Together entry surface. It carries the real Marketplace listing identity
 * into the future private shopping-room flow; it does not create fake products
 * or a second Marketplace data source.
 */
@Composable
internal fun FynxMarketplaceBuyTogether(
    listing: FynxRemoteSocialClient.MarketplaceListing,
    onStart: (listingId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Buy Together", style = MaterialTheme.typography.titleMedium)
            Text("Shop this item with friends using the real Marketplace listing.")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = { onStart(listing.id) }) {
                    Text("Start with friends")
                }
            }
        }
    }
}
