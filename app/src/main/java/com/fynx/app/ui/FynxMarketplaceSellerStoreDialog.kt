package com.fynx.app.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

/** A Marketplace-native seller storefront, backed only by current FYNX listing/reputation data. */
@Composable
internal fun FynxMarketplaceSellerStoreDialog(
    username: String,
    listings: List<FynxRemoteSocialClient.MarketplaceListing>,
    reputation: FynxMarketplaceClient.SellerReputation?,
    photoId: String?,
    onOpenListing: (FynxRemoteSocialClient.MarketplaceListing) -> Unit,
    onContact: (FynxRemoteSocialClient.MarketplaceListing) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf("Products") }
    var profileName by remember(username) { mutableStateOf("") }
    var storeName by remember(username) { mutableStateOf("") }
    var profileBio by remember(username) { mutableStateOf("") }
    var profileLoading by remember(username) { mutableStateOf(true) }
    LaunchedEffect(username) {
        FynxProfileRemoteClient.get(context, username.removePrefix("@").trim()).onSuccess { profile ->
            profileName = profile.displayName.orEmpty()
            profileBio = profile.bio.orEmpty()
        }
        storeName = listings.firstOrNull()?.storeName.orEmpty()
        profileLoading = false
    }
    val sellerListings = remember(listings, username) {
        listings.filter { it.active && it.sellerUsername.removePrefix("@").trim().equals(username.removePrefix("@").trim(), ignoreCase = true) }
            .distinctBy { it.id }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "Back to Marketplace") }
                Column(Modifier.weight(1f)) {
                    Text("Seller store", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("@${username.removePrefix("@")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            FynxRemoteProfileAvatar(photoId, profileName.ifBlank { username }, Modifier.size(84.dp).clip(CircleShape), ownerUsername = username)
                            Text(profileName.ifBlank { username.removePrefix("@") }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (storeName.isNotBlank()) Text(storeName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text("@${username.removePrefix("@")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (profileLoading) CircularProgressIndicator(Modifier.size(18.dp))
                            if (profileBio.isNotBlank()) Text(profileBio, style = MaterialTheme.typography.bodyMedium)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(sellerListings.size.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text("Available products", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                reputation?.let { r ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(r.successfulSales.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text("Successful sales", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (r.reviewCount > 0) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                            Text(String.format(Locale.US, "%.1f", r.averageRating), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        }
                                        Text("${r.reviewCount} reviews", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Button(onClick = { sellerListings.firstOrNull()?.let(onContact) }, enabled = sellerListings.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Message seller")
                            }
                            Text("Seller details and reputation are shown only when FYNX has real account data.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Products", "About").forEach { label ->
                            androidx.compose.material3.FilterChip(
                                selected = tab == label,
                                onClick = { tab = label },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                if (tab == "About") {
                    item {
                        Card(shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("About this seller", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(profileBio.ifBlank { "This seller has not added a public bio." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Username: @${username.removePrefix("@")}")
                                Text("Store: ${storeName.ifBlank { "Not provided" }}")
                                reputation?.let { r ->
                                    Text("Seller tier: ${r.tier}")
                                    Text("Order completion: ${String.format(Locale.US, "%.0f", r.completionRate)}%")
                                    if (r.totalOrders > 0) Text("Orders: ${r.totalOrders}")
                                } ?: Text("Seller reputation is not available right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else if (sellerListings.isEmpty()) {
                    item { Text("No active products from this seller are available in the currently loaded Marketplace results.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(sellerListings, key = { "seller-store-${it.id}" }) { listing ->
                        Surface(
                            onClick = { onOpenListing(listing) },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (listing.mediaIds.isNotEmpty()) FynxRemoteMedia(FynxMarketplaceClient.mediaUrl(context, listing.mediaIds.first()), "auto", Modifier.size(112.dp).aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
                                else Box(Modifier.size(112.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.ShoppingBag, "Product image unavailable", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(listing.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${listing.currency.uppercase(Locale.US)} ${String.format(Locale.US, "%,.2f", listing.price)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text("${listing.quantity} available · ${listing.condition}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (listing.location.isNotBlank()) Text(listing.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    TextButton(onClick = { onContact(listing) }, contentPadding = PaddingValues(0.dp)) { Text("Contact seller") }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)); HorizontalDivider() }
            }
        }
    }
}
