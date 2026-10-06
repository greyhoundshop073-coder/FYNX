package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Reference-board product presentation only.
 * The supplied listings and callbacks remain the production Marketplace source of truth.
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
            MarketplaceCard(
                l = listing,
                onProfile = { onProfile(listing.sellerUsername) },
                onContact = { onContact(listing.sellerUsername, listing.id) },
                onOpen = { onOpen(listing) }
            )
        }
    }
}
