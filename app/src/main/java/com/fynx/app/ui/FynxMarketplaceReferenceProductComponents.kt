package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/** Reference-board product presentation. All values come from the real listing. */
/**
 * Presentation helpers deliberately consume only server-backed listing and seller data.
 * Missing timestamps, discount baselines or engagement counts are never guessed.
 */
internal fun marketplaceReviewCountLabel(reviewCount: Int): String {
    val count = reviewCount.coerceAtLeast(0)
    if (count == 0) return ""
    fun compact(value: Double): String = String.format(Locale.US, "%.1f", value).removeSuffix(".0")
    return when {
        count >= 1_000_000 -> compact(count / 1_000_000.0) + "m"
        count >= 1_000 -> compact(count / 1_000.0) + "k"
        else -> count.toString()
    }
}

internal fun marketplaceDiscountPercent(currentPrice: Double, originalPrice: Double?): Int? {
    if (originalPrice == null || !originalPrice.isFinite() || !currentPrice.isFinite() || originalPrice <= currentPrice || originalPrice <= 0.0 || currentPrice <= 0.0) return null
    return (((originalPrice - currentPrice) / originalPrice) * 100.0).roundToInt().coerceAtLeast(1)
}

internal fun marketplaceCardPriceLabel(currency: String, price: Double): String {
    val code = currency.trim().uppercase(Locale.US)
    if (!price.isFinite()) return code.ifBlank { "—" }
    val amount = if (price % 1.0 == 0.0) {
        String.format(Locale.US, "%,.0f", price)
    } else {
        String.format(Locale.US, "%,.2f", price)
    }
    return when (code) {
        "NGN" -> "₦" + amount
        "USD" -> "\$" + amount
        else -> (code + " " + amount).trim()
    }
}

internal fun marketplaceConditionLabel(condition: String): String? = when (condition.trim().uppercase(Locale.US)) {
    "NEW" -> "New"
    "USED" -> "Used"
    "REFURBISHED", "REFURB" -> "Refurbished"
    else -> null
}

internal fun marketplaceStockLabel(quantity: Int): String? = when {
    quantity <= 0 -> "Out of stock"
    quantity <= 5 -> "Only $quantity left"
    else -> null
}

internal fun marketplaceIsNewArrival(
    createdAtEpochMs: Long,
    nowEpochMs: Long,
    windowMillis: Long = 7L * 24L * 60L * 60L * 1000L
): Boolean = createdAtEpochMs > 0L &&
    createdAtEpochMs <= nowEpochMs &&
    nowEpochMs - createdAtEpochMs <= windowMillis

/** Discovery rank is meaningful only when the backend supplied a positive activity/recency score. */
internal fun marketplaceIsTrendingDiscoveryRank(rank: Int, discoveryScore: Double): Boolean =
    rank in 1..5 && discoveryScore.isFinite() && discoveryScore > 0.0

/** A zero fee must be explicit; null never means free delivery. */
internal fun marketplaceDeliveryLabel(deliveryAvailable: Boolean, deliveryFee: Double?): String? = when {
    !deliveryAvailable -> null
    deliveryFee == 0.0 -> "Free Delivery"
    else -> "Delivery Available"
}

/** Seller ranking and review badges are derived from the existing reputation endpoint. */
internal fun marketplaceSellerHighlight(reputation: FynxMarketplaceClient.SellerReputation?): String? {
    if (reputation == null) return null
    if (reputation.successfulSales > 0 && reputation.rank in 1..5) return "Top Seller"
    if (reputation.reviewCount >= 5 && reputation.averageRating >= 4.5) return "Top Rated Seller"
    return null
}

internal fun marketplaceVerifiedSellerBadge(isVerified: Boolean): String? =
    if (isVerified) "Verified Seller" else null

internal fun marketplaceSponsoredBadge(isSponsored: Boolean): String? =
    if (isSponsored) "Sponsored" else null

@Composable
private fun MarketplaceProductCardBadge(label: String, fill: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = fill,
        shadowElevation = 1.dp
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Reference-board product presentation. All displayed values and actions come from real FYNX data. */
@Composable
internal fun MarketplaceReferenceProductCard(
    listing: FynxRemoteSocialClient.MarketplaceListing,
    sellerRating: FynxMarketplaceClient.SellerReputation? = null,
    sellerPhotoId: String? = null,
    onOpen: () -> Unit,
    onProfile: () -> Unit,
    onContact: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    isFavoriteBusy: Boolean = false,
    onFavorite: (() -> Unit)? = null,
    onAddToCart: (() -> Unit)? = null,
    isInCart: Boolean = false,
    isWeeklyBestSeller: Boolean = false,
    isVerifiedSeller: Boolean = false,
    isSponsored: Boolean = false,
    isTrending: Boolean = false,
    isPopularNearYou: Boolean = false,
    isNewArrival: Boolean = false,
    isSpecialOffer: Boolean = false
) {
    val context = LocalContext.current
    val deliveryLabel = marketplaceDeliveryLabel(listing.deliveryAvailable, listing.deliveryFee)
    val stockLabel = marketplaceStockLabel(listing.quantity)
    val conditionLabel = marketplaceConditionLabel(listing.condition)
    val sellerHighlight = marketplaceSellerHighlight(sellerRating)
    val discountPercent = marketplaceDiscountPercent(listing.price, listing.originalPrice)
    val hasProductRating = listing.productReviewCount > 0 && listing.productAverageRating > 0.0
    val isTopRatedProduct = listing.productReviewCount >= 5 && listing.productAverageRating >= 4.5
    val prominentBadges = buildList {
        if (listing.quantity <= 0) {
            add("Out of stock" to Color(0xFFDC143C))
        } else {
            if (isWeeklyBestSeller) add("Best Seller" to Color(0xFF007FFF))
            if (isSpecialOffer) add("Special Offer" to Color(0xFFDB2777))
            if (discountPercent != null && discountPercent >= 20) add("Hot Deal" to Color(0xFFDC143C))
            else if (discountPercent != null) add("$discountPercent% OFF" to Color(0xFFDC143C))
            if (listing.priceDrop) add("Price Drop" to Color(0xFFEA580C))
            if (isTopRatedProduct) add("Top Rated" to Color(0xFF8B5CF6))
            marketplaceSponsoredBadge(isSponsored)?.let { add(it to Color(0xFF475569)) }
            marketplaceVerifiedSellerBadge(isVerifiedSeller)?.let { add(it to Color(0xFF007FFF)) }
            if (isTrending) add("Trending" to Color(0xFF2563EB))
            else if (isPopularNearYou) add("Popular Near You" to Color(0xFF0D9488))
            else if (sellerHighlight == "Top Seller") add("Top Seller" to Color(0xFF007FFF))
            else if (sellerHighlight == "Top Rated Seller") add("Top Rated" to Color(0xFF8B5CF6))
            if (isNewArrival) add("New Arrival" to Color(0xFF16A34A))
            if (deliveryLabel == "Free Delivery") add("Free Delivery" to Color(0xFF008000))
            if (stockLabel != null) add("Limited Stock" to Color(0xFFD97706))
        }
    }.take(2)

    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.42f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                if (listing.mediaIds.isNotEmpty()) {
                    RemoteMarketMedia(
                        context,
                        listing.mediaIds.first(),
                        Modifier.fillMaxSize(),
                        listing.mediaTypes.firstOrNull()
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
                        Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ShoppingBag,
                            "Product",
                            Modifier.size(42.dp),
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    prominentBadges.forEach { (label, fill) -> MarketplaceProductCardBadge(label, fill) }
                }

                onFavorite?.let { favorite ->
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.32f)
                    ) {
                        IconButton(
                            onClick = favorite,
                            enabled = !isFavoriteBusy,
                            modifier = Modifier.size(34.dp)
                        ) {
                            if (isFavoriteBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(17.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    if (isFavorite) "Remove from Saved Products" else "Save product",
                                    tint = if (isFavorite) Color(0xFFDC143C) else Color.White,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                }
            }

            Column(
                Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    listing.title,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (hasProductRating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Product rating",
                            modifier = Modifier.size(15.dp),
                            tint = Color(0xFFFFB72B)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            String.format(Locale.US, "%.1f", listing.productAverageRating.coerceIn(0.0, 5.0)),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "(" + marketplaceReviewCountLabel(listing.productReviewCount) + " reviews)",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onProfile,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FynxRemoteProfileAvatar(
                                sellerPhotoId,
                                listing.sellerDisplayName.ifBlank { listing.sellerUsername },
                                Modifier.size(24.dp).clip(CircleShape),
                                ownerUsername = listing.sellerUsername
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                val sellerLabel = listing.sellerDisplayName.ifBlank {
                                    listing.sellerUsername.removePrefix("@")
                                }
                                Text(
                                    listing.storeName.ifBlank { sellerLabel },
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (listing.storeName.isNotBlank() &&
                                    !listing.storeName.equals(sellerLabel, ignoreCase = true)
                                ) {
                                    Text(
                                        "by " + sellerLabel,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                                    )
                                }
                                sellerRating?.let { MarketplaceSellerTierBadge(it.tier) }
                                sellerRating?.takeIf { it.reviewCount > 0 }?.let { reputation ->
                                    Text(
                                        "Seller rating " + String.format(Locale.US, "%.1f", reputation.averageRating.coerceIn(0.0, 5.0)),
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                    if (onContact != null) {
                        androidx.compose.material3.TextButton(
                            onClick = onContact,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "Contact",
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                if (listing.location.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "Seller location",
                            modifier = Modifier.size(14.dp),
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                        Text(
                            listing.location.trim(),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                        )
                    }
                }

                val productMeta = buildList {
                    conditionLabel?.let { add(it) }
                    stockLabel?.let { if (it != "Out of stock") add(it) }
                    if (listing.deliveryAvailable && listing.deliveryFee != null && listing.deliveryFee > 0.0) {
                        add("Delivery • " + marketplaceCardPriceLabel(listing.currency, listing.deliveryFee))
                    } else {
                        deliveryLabel?.let { add(it) }
                    }
                    if (listing.pickupAvailable) add("Local pickup")
                }.distinct().take(3)
                if (productMeta.isNotEmpty()) {
                    Text(
                        productMeta.joinToString(" · "),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            marketplaceCardPriceLabel(listing.currency, listing.price),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (discountPercent != null && listing.originalPrice != null) {
                            Text(
                                marketplaceCardPriceLabel(listing.currency, listing.originalPrice),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                                maxLines = 1
                            )
                        }
                    }
                    if (onAddToCart != null && listing.quantity > 0) {
                        Surface(
                            onClick = onAddToCart,
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFE3F0FF),
                            contentColor = Color(0xFF007FFF)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text(
                                    if (isInCart) "In Cart" else "Add to Cart",
                                    fontWeight = FontWeight.Bold,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    maxLines = 1
                                )
                            }
                        }
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
                RemoteMarketMedia(context, listing.mediaIds.first(), Modifier.size(112.dp).clip(RoundedCornerShape(14.dp)), listing.mediaTypes.firstOrNull())
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
private fun RemoteMarketMedia(context: android.content.Context, mediaId: String, modifier: Modifier, mimeType: String? = null) {
    val mediaUrl = remember(mediaId) { FynxMarketplaceClient.mediaUrl(context, mediaId) }
    FynxRemoteMedia(mediaUrl, FynxMarketplaceClient.mediaDisplayType(mimeType), modifier)
}
