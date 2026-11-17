package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Presentation-only seller profile and empty-marketplace surfaces.
 * Real listing data and existing navigation/actions remain the source of truth.
 */
@Composable
internal fun FynxMarketplaceSellerReferenceProfile(
    sellerName: String,
    storeName: String,
    listings: List<FynxRemoteSocialClient.MarketplaceListing>,
    onProduct: (FynxRemoteSocialClient.MarketplaceListing) -> Unit,
    onMessage: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().background(FynxMarketplaceReferenceStyle.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(shape = FynxMarketplaceReferenceStyle.radius, color = FynxMarketplaceReferenceStyle.surface) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sellerName.ifBlank { storeName }, color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.Bold)
                if (storeName.isNotBlank() && storeName != sellerName) {
                    Text(storeName, color = FynxMarketplaceReferenceStyle.textMuted)
                }
                Text("${listings.size} listed product${if (listings.size == 1) "" else "s"}", color = FynxMarketplaceReferenceStyle.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onFollow) { Text("Follow") }
                    Button(onClick = onMessage) { Text("Message") }
                }
            }
        }
        MarketplaceReferenceSectionTitle("Products")
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(listings, key = { it.id }) { listing ->
                Surface(onClick = { onProduct(listing) }, shape = FynxMarketplaceReferenceStyle.radius, color = FynxMarketplaceReferenceStyle.surface) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Storefront, null, tint = FynxMarketplaceReferenceStyle.primarySoft, modifier = Modifier.size(28.dp))
                        Text(listing.title, color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text("${listing.currency} ${listing.price}", color = FynxMarketplaceReferenceStyle.primarySoft, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FynxMarketplaceReferenceEmptyMarketplace(
    onSell: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(shape = FynxMarketplaceReferenceStyle.radius, color = FynxMarketplaceReferenceStyle.surfaceRaised) {
            Icon(Icons.Default.Storefront, null, tint = FynxMarketplaceReferenceStyle.primarySoft, modifier = Modifier.padding(22.dp).size(42.dp))
        }
        Text("Nothing to show yet", color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.Bold)
        Text("There are no marketplace listings here yet.", color = FynxMarketplaceReferenceStyle.textMuted)
        Button(onClick = onSell) { Text("Sell something") }
    }
}
