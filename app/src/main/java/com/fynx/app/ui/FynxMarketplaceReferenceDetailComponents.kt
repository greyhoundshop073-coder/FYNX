package com.fynx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Reference-board detail sections. Existing Marketplace actions/data remain the source of truth. */
@Composable
internal fun MarketplaceReferenceProductSummary(
    listing: FynxRemoteSocialClient.MarketplaceListing,
    onMessageSeller: () -> Unit,
    onBuyNow: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(listing.title, color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Text(
            listing.currency.uppercase(Locale.US) + " " + String.format(Locale.US, "%,.2f", listing.price),
            color = FynxMarketplaceReferenceStyle.primarySoft,
            fontWeight = FontWeight.Bold,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (listing.condition.isNotBlank()) {
                Surface(shape = RoundedCornerShape(10.dp), color = FynxMarketplaceReferenceStyle.surfaceRaised) {
                    Text(listing.condition, color = FynxMarketplaceReferenceStyle.text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            if (listing.location.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = FynxMarketplaceReferenceStyle.textMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(listing.location, color = FynxMarketplaceReferenceStyle.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share", tint = FynxMarketplaceReferenceStyle.text) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onMessageSeller, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Message seller")
            }
            Button(onClick = onBuyNow, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Buy now")
            }
        }
    }
}

@Composable
internal fun MarketplaceReferenceDescriptionCard(
    listing: FynxRemoteSocialClient.MarketplaceListing,
    modifier: Modifier = Modifier
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = FynxMarketplaceReferenceStyle.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Description", color = FynxMarketplaceReferenceStyle.text, fontWeight = FontWeight.Bold)
            Text(
                listing.description.ifBlank { "No description provided by the seller." },
                color = FynxMarketplaceReferenceStyle.textMuted
            )
            if (listing.quantity > 0) Text("${listing.quantity} available", color = FynxMarketplaceReferenceStyle.textMuted)
            if (listing.deliveryAvailable || listing.pickupAvailable) {
                Text("Delivery ${if (listing.deliveryAvailable) "available" else "unavailable"} · Pickup ${if (listing.pickupAvailable) "available" else "unavailable"}", color = FynxMarketplaceReferenceStyle.textMuted)
            }
        }
    }
}
