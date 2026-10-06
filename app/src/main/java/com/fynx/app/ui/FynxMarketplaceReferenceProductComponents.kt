package com.fynx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Reference-board product presentation. All values come from the real listing. */
@Composable
internal fun MarketplaceReferenceProductCard(
    listing: FynxRemoteSocialClient.MarketplaceListing,
    sellerRating: FynxMarketplaceClient.SellerReputation? = null,
    sellerPhotoId: String? = null,
    onOpen: () -> Unit,
    onProfile: (() -> Unit)? = null,
    onSeller: () -> Unit,
    onContact: (() -> Unit)? = null,
    onFavorite: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = FynxMarketplaceReferenceStyle.surfaceRaised
    ) {
        Column {
            Box {
                if (listing.mediaIds.isNotEmpty()) {
                    RemoteMarketMedia(context, listing.mediaIds.first(), Modifier.fillMaxWidth().aspectRatio(0.92f).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)))
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.92f).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)), Alignment.Center) {
                        Icon(Icons.Default.ShoppingBag, "Product", Modifier.size(42.dp), tint = FynxMarketplaceReferenceStyle.primarySoft)
                    }
                }
                if (listing.condition.isNotBlank()) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(9.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = FynxMarketplaceReferenceStyle.background.copy(alpha = 0.88f)
                    ) { Text(listing.condition, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = FynxMarketplaceReferenceStyle.text) }
                }
                onFavorite?.let { favorite ->
                    IconButton(onClick = favorite, modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(38.dp)) {
                        Icon(Icons.Default.FavoriteBorder, "Save", tint = FynxMarketplaceReferenceStyle.text)
                    }
                }
            }
            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(listing.title, color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listing.currency.uppercase() + " " + String.format(Locale.US, "%,.2f", listing.price), color = FynxMarketplaceReferenceStyle.primarySoft, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FynxRemoteProfileAvatar(sellerPhotoId, listing.sellerDisplayName.ifBlank { listing.sellerUsername }, Modifier.size(24.dp).clip(CircleShape), ownerUsername = listing.sellerUsername)
                    Spacer(Modifier.width(6.dp))
                    Text(listing.sellerDisplayName.ifBlank { listing.sellerUsername.removePrefix("@") }, modifier = Modifier.weight(1f), color = Color(0xFFDCE6FF), maxLines = 1, overflow = TextOverflow.Ellipsis, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    sellerRating?.takeIf { it.reviewCount > 0 }?.let {
                        Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = Color(0xFFFFC857))
                        Spacer(Modifier.width(2.dp))
                        Text(String.format(Locale.US, "%.1f", it.averageRating), color = FynxMarketplaceReferenceStyle.textMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    }
                }
                if (listing.location.isNotBlank()) Text(listing.location, color = FynxMarketplaceReferenceStyle.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                onProfile?.let { profile ->
                    androidx.compose.material3.TextButton(onClick = profile, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp)) {
                        Text("View seller", color = FynxMarketplaceReferenceStyle.primarySoft)
                    }
                }
                onContact?.let { contact ->
                    androidx.compose.material3.TextButton(onClick = contact, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp)) {
                        androidx.compose.material3.Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Contact seller", color = FynxMarketplaceReferenceStyle.primarySoft)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MarketplaceReferenceDealBanner(
    listing: FynxRemoteSocialClient.MarketplaceListing,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(onClick = onOpen, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = FynxMarketplaceReferenceStyle.surfaceRaised) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (listing.mediaIds.isNotEmpty()) {
                RemoteMarketMedia(context, listing.mediaIds.first(), Modifier.size(112.dp).clip(RoundedCornerShape(14.dp)))
            } else {
                Box(Modifier.size(112.dp).clip(RoundedCornerShape(14.dp)), Alignment.Center) { Icon(Icons.Default.ShoppingBag, "Deal", Modifier.size(38.dp), tint = FynxMarketplaceReferenceStyle.primarySoft) }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Great Deals", color = FynxMarketplaceReferenceStyle.primarySoft, fontWeight = FontWeight.Bold)
                Text("Better prices from real FYNX sellers", color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.SemiBold)
                Text(listing.title, color = FynxMarketplaceReferenceStyle.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listing.currency.uppercase() + " " + String.format(Locale.US, "%,.2f", listing.price), color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RemoteMarketMedia(context: android.content.Context, mediaId: String, modifier: Modifier) {
    val mediaUrl = remember(mediaId) { FynxMarketplaceClient.mediaUrl(context, mediaId) }
    FynxRemoteMedia(mediaUrl, "auto", modifier)
}
