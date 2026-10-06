package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reference-board Home header for Marketplace.
 * Presentation only: callers supply the existing Marketplace search/cart actions.
 */
@Composable
internal fun MarketplaceReferenceHomeHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onCart: () -> Unit,
    cartCount: Int,
    nearbySelected: Boolean,
    onNearby: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.background(FynxMarketplaceReferenceStyle.background), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Marketplace", color = FynxMarketplaceReferenceStyle.text)
                Text("Discover products on FYNX", color = FynxMarketplaceReferenceStyle.textMuted)
            }
            IconButton(onClick = onCart) { Icon(Icons.Default.ShoppingCart, "Cart", tint = FynxMarketplaceReferenceStyle.text) }
        }
        Surface(
            onClick = { },
            modifier = Modifier.fillMaxWidth(),
            shape = FynxMarketplaceReferenceStyle.radius,
            color = FynxMarketplaceReferenceStyle.surface
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = FynxMarketplaceReferenceStyle.textMuted)
                Spacer(Modifier.width(8.dp))
                Text(if (query.isBlank()) "Search products, sellers or categories" else query, color = FynxMarketplaceReferenceStyle.textMuted)
            }
        }
        Surface(onClick = onNearby, shape = FynxMarketplaceReferenceStyle.radius, color = if (nearbySelected) FynxMarketplaceReferenceStyle.surfaceRaised else FynxMarketplaceReferenceStyle.surface) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = FynxMarketplaceReferenceStyle.primarySoft)
                Spacer(Modifier.width(6.dp))
                Text(if (nearbySelected) "Near me" else "Nearby products", color = FynxMarketplaceReferenceStyle.text)
            }
        }
    }
}
