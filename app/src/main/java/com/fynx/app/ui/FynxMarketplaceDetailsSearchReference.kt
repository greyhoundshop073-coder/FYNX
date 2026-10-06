package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Reference-board Product Details presentation. Production callbacks/data stay external. */
@Composable
internal fun FynxMarketplaceReferenceProductDetails(
    context: Context,
    listing: FynxRemoteSocialClient.MarketplaceListing,
    onBack: () -> Unit,
    onSeller: () -> Unit,
    onContact: () -> Unit,
    onShare: () -> Unit,
    onAddToCart: () -> Unit,
    onBuyNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(FynxMarketplaceReferenceStyle.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = FynxMarketplaceReferenceStyle.text) }
            Text("Product details", color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (listing.mediaIds.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
                    items(listing.mediaIds.take(12), key = { it }) { mediaId ->
                        val url = FynxMarketplaceClient.mediaUrl(context, mediaId)
                        FynxRemoteMedia(url, "auto", Modifier.width(300.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(20.dp)))
                    }
                }
            } else {
                Surface(Modifier.fillMaxWidth().aspectRatio(4f / 3f), shape = RoundedCornerShape(20.dp), color = FynxMarketplaceReferenceStyle.surfaceRaised) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Storefront, null, tint = FynxMarketplaceReferenceStyle.primarySoft, modifier = Modifier.size(52.dp)) }
                }
            }
            Text(listing.title, color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Text("${listing.currency} ${String.format(Locale.US, "%,.2f", listing.price)}", color = FynxMarketplaceReferenceStyle.primarySoft, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${listing.quantity} available", color = FynxMarketplaceReferenceStyle.textMuted)
                Text("• ${listing.condition}", color = FynxMarketplaceReferenceStyle.textMuted)
            }
            Surface(Modifier.fillMaxWidth(), shape = FynxMarketplaceReferenceStyle.radius, color = FynxMarketplaceReferenceStyle.surface) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Seller", color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.Bold)
                    Text(listing.sellerDisplayName.ifBlank { listing.sellerUsername.removePrefix("@") }, color = FynxMarketplaceReferenceStyle.text)
                    TextButton(onClick = onSeller) { Text("View seller") }
                }
            }
            if (listing.location.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, null, tint = FynxMarketplaceReferenceStyle.primarySoft); Spacer(Modifier.width(6.dp)); Text(listing.location, color = FynxMarketplaceReferenceStyle.textMuted) }
            if (listing.deliveryAvailable) Text("Delivery available", color = FynxMarketplaceReferenceStyle.text)
            if (listing.pickupAvailable) Text("Pickup available", color = FynxMarketplaceReferenceStyle.text)
            if (listing.description.isNotBlank()) {
                Text("Description", color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.Bold)
                Text(listing.description, color = FynxMarketplaceReferenceStyle.textMuted)
            }
            Surface(Modifier.fillMaxWidth(), shape = FynxMarketplaceReferenceStyle.radius, color = FynxMarketplaceReferenceStyle.surface) {
                Text("FYNX protected payment", color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(14.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Share, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Share") }
                OutlinedButton(onClick = onContact, modifier = Modifier.weight(1f)) { Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Contact") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAddToCart, enabled = listing.quantity > 0, modifier = Modifier.weight(1f)) { Icon(Icons.Default.ShoppingCart, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Add to cart") }
                Button(onClick = onBuyNow, enabled = listing.quantity > 0, modifier = Modifier.weight(1f)) { Text("Buy now") }
            }
            Spacer(Modifier.height(120.dp))
        }
    }
}

/** Reference-board Search presentation. Search results remain real Marketplace listings supplied by the caller. */
@Composable
internal fun FynxMarketplaceReferenceSearch(
    query: String,
    recentSearches: List<String>,
    categories: List<String>,
    trendingSearches: List<String>,
    results: List<FynxRemoteSocialClient.MarketplaceListing>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRecent: (String) -> Unit,
    onCategory: (String) -> Unit,
    onTrending: (String) -> Unit,
    onOpenListing: (FynxRemoteSocialClient.MarketplaceListing) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(FynxMarketplaceReferenceStyle.background)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = FynxMarketplaceReferenceStyle.surface) {
                Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = FynxMarketplaceReferenceStyle.textMuted)
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.BasicAlertDialog
                    Text(query.ifBlank { "Search Marketplace" }, color = if (query.isBlank()) FynxMarketplaceReferenceStyle.textMuted else FynxMarketplaceReferenceStyle.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TextButton(onClick = onSearch) { Text("Search", color = FynxMarketplaceReferenceStyle.primarySoft) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (query.isBlank() && recentSearches.isNotEmpty()) {
                MarketplaceReferenceSectionTitle("Recent searches")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(recentSearches.take(10)) { item -> Surface(onClick = { onRecent(item) }, shape = RoundedCornerShape(14.dp), color = FynxMarketplaceReferenceStyle.surface) { Text(item, color = FynxMarketplaceReferenceStyle.text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) } } }
            }
            MarketplaceReferenceSectionTitle("Categories")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(categories.take(12)) { item -> Surface(onClick = { onCategory(item) }, shape = RoundedCornerShape(14.dp), color = FynxMarketplaceReferenceStyle.surfaceRaised) { Text(item, color = FynxMarketplaceReferenceStyle.text, modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) } } }
            if (query.isBlank()) {
                MarketplaceReferenceSectionTitle("Trending searches")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { trendingSearches.take(10).forEach { item -> TextButton(onClick = { onTrending(item) }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Search, null, tint = FynxMarketplaceReferenceStyle.primarySoft, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(item, color = FynxMarketplaceReferenceStyle.text) } } } }
            }
            if (results.isNotEmpty()) {
                MarketplaceReferenceSectionTitle("Results")
                results.take(20).forEach { listing ->
                    Surface(onClick = { onOpenListing(listing) }, modifier = Modifier.fillMaxWidth(), shape = FynxMarketplaceReferenceStyle.radius, color = FynxMarketplaceReferenceStyle.surface) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(listing.title, color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${listing.currency} ${String.format(Locale.US, "%,.2f", listing.price)}", color = FynxMarketplaceReferenceStyle.primarySoft, fontWeight = FontWeight.Bold)
                            Text(listing.sellerDisplayName.ifBlank { listing.sellerUsername.removePrefix("@") }, color = FynxMarketplaceReferenceStyle.textMuted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(120.dp))
        }
    }
}
