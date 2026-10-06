package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Reference-board product presentation only.
 * The supplied listings and callbacks remain the production Marketplace source of truth.
 * No fake product, seller, price, rating, or activity data is introduced here.
 */
@Composable
internal fun FynxMarketplaceReferenceProductGrid(
    listings: List<FynxRemoteSocialClient.MarketplaceListing>,
    nearbyMode: Boolean,
    onOpen: (FynxRemoteSocialClient.MarketplaceListing) -> Unit,
    onProfile: (String) -> Unit,
    onContact: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize().background(FynxMarketplaceReferenceStyle.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 132.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                if (nearbyMode) "Popular near you" else "Recommended for you",
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                color = FynxMarketplaceReferenceStyle.text,
                fontWeight = FontWeight.Bold
            )
        }
        items(listings, key = { it.id }) { listing ->
            FynxMarketplaceReferenceProductCard(
                listing = listing,
                onOpen = { onOpen(listing) },
                onProfile = { onProfile(listing.sellerUsername) },
                onContact = { onContact(listing.sellerUsername, listing.id) }
            )
        }
    }
}

@Composable
private fun FynxMarketplaceReferenceProductCard(
    listing: FynxRemoteSocialClient.MarketplaceListing,
    onOpen: () -> Unit,
    onProfile: () -> Unit,
    onContact: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        color = FynxMarketplaceReferenceStyle.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                shape = RoundedCornerShape(13.dp),
                color = FynxMarketplaceReferenceStyle.surfaceRaised,
                onClick = onOpen
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Bottom) {
                    Text(
                        listing.title.ifBlank { "Marketplace item" },
                        color = FynxMarketplaceReferenceStyle.text,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                }
            }
            Text(
                "${listing.currency} ${String.format(java.util.Locale.US, "%,.2f", listing.price)}",
                color = FynxMarketplaceReferenceStyle.text,
                fontWeight = FontWeight.Bold
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    listing.sellerUsername.removePrefix("@").ifBlank { "Seller" },
                    modifier = Modifier.weight(1f).clickable(onClick = onProfile),
                    color = FynxMarketplaceReferenceStyle.textMuted,
                    maxLines = 1
                )
                if (listing.location.isNotBlank()) {
                    Text(listing.location, color = FynxMarketplaceReferenceStyle.textMuted, maxLines = 1)
                }
            }
        }
    }
}
