package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private const val MARKETPLACE_PROMO_ROTATION_MS = 6_500L
private const val MARKETPLACE_PROMO_BANNER_HEIGHT_DP = 224

private data class MarketplacePromoSlide(
    val key: String,
    val eyebrow: String,
    val headline: String,
    val body: String,
    val actionLabel: String,
    val listing: FynxRemoteSocialClient.MarketplaceListing? = null,
    val sellerUsername: String? = null,
    val isWeeklyWinner: Boolean = false,
    val celebration: Boolean = false
)

@Composable
fun MarketplacePromotionalBanner(
    listings: List<FynxRemoteSocialClient.MarketplaceListing>,
    sellerReputations: Map<String, FynxMarketplaceClient.SellerReputation>,
    weeklySeller: FynxMarketplaceWeeklySeller?,
    weeklySellerListing: FynxRemoteSocialClient.MarketplaceListing?,
    onOpenListing: (FynxRemoteSocialClient.MarketplaceListing) -> Unit,
    onOpenSeller: (String) -> Unit,
    onShopNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sellerSales = remember(sellerReputations) {
        sellerReputations.mapKeys { it.key.removePrefix("@").trim().lowercase(Locale.US) }
            .mapValues { it.value.successfulSales }
    }
    val featuredListings = remember(listings, sellerSales) {
        val index = listings.distinctBy { it.id.trim() }.associateBy { it.id.trim() }
        selectMarketplaceAdvertisingCandidates(
            listings.map { listing ->
                MarketplaceAdvertisingCandidate(
                    id = listing.id,
                    sellerUsername = listing.sellerUsername,
                    title = listing.title,
                    price = listing.price,
                    currency = listing.currency,
                    quantity = listing.quantity,
                    active = listing.active
                )
            },
            sellerSales
        ).take(4).mapNotNull { index[it.id.trim()] }
    }
    val celebration = remember { marketplaceCelebrationFor(LocalDate.now(ZoneId.of("Africa/Lagos"))) }
    val slides = remember(weeklySeller, weeklySellerListing, featuredListings, celebration) {
        buildList {
            if (weeklySeller != null) {
                add(
                    MarketplacePromoSlide(
                        key = "weekly-seller-${weeklySeller.sellerId}",
                        eyebrow = "BEST SELLER OF THE WEEK",
                        headline = weeklySeller.displayName,
                        body = "Celebrating ${weeklySeller.completedOrders} completed orders from ${weeklySeller.uniqueBuyers} customers this week.",
                        actionLabel = "Visit seller shop",
                        listing = weeklySellerListing,
                        sellerUsername = weeklySeller.username,
                        isWeeklyWinner = true
                    )
                )
            }
            if (celebration != null) {
                add(
                    MarketplacePromoSlide(
                        key = "seasonal-${celebration.first}",
                        eyebrow = "A CELEBRATION FROM FYNX",
                        headline = celebration.second.first,
                        body = celebration.second.second,
                        actionLabel = "Explore Marketplace",
                        celebration = true
                    )
                )
            }
            featuredListings.forEachIndexed { index, listing ->
                val seller = listing.sellerDisplayName.trim()
                    .ifBlank { listing.storeName.trim() }
                    .ifBlank { listing.sellerUsername.removePrefix("@").trim() }
                add(
                    MarketplacePromoSlide(
                        key = "featured-${listing.id}",
                        eyebrow = if (index == 0) "FEATURED FROM FYNX SELLERS" else "DISCOVER SOMETHING NEW",
                        headline = listing.title,
                        body = "${listing.currency.trim().uppercase(Locale.US)} ${String.format(Locale.US, "%,.2f", listing.price)}  •  Sold by $seller",
                        actionLabel = "Shop this product",
                        listing = listing,
                        sellerUsername = listing.sellerUsername
                    )
                )
            }
            if (isEmpty()) {
                add(
                    MarketplacePromoSlide(
                        key = "fynx-welcome",
                        eyebrow = "WELCOME TO FYNX MARKETPLACE",
                        headline = "Discover more. Shop with confidence.",
                        body = "Explore products and independent shops from FYNX sellers.",
                        actionLabel = "Explore Marketplace"
                    )
                )
            }
        }
    }
    val loopSlides = remember(slides) {
        if (slides.size > 1) slides + slides.first() else slides
    }
    val listState = rememberLazyListState()
    val currentIndex by remember(slides) {
        derivedStateOf { if (slides.isEmpty()) 0 else listState.firstVisibleItemIndex.coerceIn(0, slides.lastIndex) }
    }

    LaunchedEffect(slides.map { it.key }) {
        listState.scrollToItem(0)
        if (slides.size > 1) {
            while (isActive) {
                delay(MARKETPLACE_PROMO_ROTATION_MS)
                if (listState.isScrollInProgress) continue
                val next = listState.firstVisibleItemIndex + 1
                listState.animateScrollToItem(next)
                if (next >= slides.size) listState.scrollToItem(0)
            }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val bannerWidth = maxWidth
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                userScrollEnabled = true
            ) {
                itemsIndexed(loopSlides, key = { index, slide -> "${slide.key}-$index" }) { _, slide ->
                    MarketplacePromotionalSlide(
                        context = context,
                        slide = slide,
                        width = bannerWidth,
                        onClick = {
                            when {
                                slide.listing != null -> onOpenListing(slide.listing)
                                !slide.sellerUsername.isNullOrBlank() -> onOpenSeller(slide.sellerUsername)
                                else -> onShopNow()
                            }
                        }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(slides.size) { index ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .width(if (index == currentIndex) 18.dp else 6.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (index == currentIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                        )
                )
            }
        }
    }
}

@Composable
private fun MarketplacePromotionalSlide(
    context: Context,
    slide: MarketplacePromoSlide,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(width).height(MARKETPLACE_PROMO_BANNER_HEIGHT_DP.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF16263D),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22007FFF))
    ) {
        Box(Modifier.fillMaxSize()) {
            val listing = slide.listing
            if (listing != null && listing.mediaIds.isNotEmpty()) {
                val mediaUrl = remember(listing.id, listing.mediaIds.first()) {
                    FynxMarketplaceClient.mediaUrl(context, listing.mediaIds.first())
                }
                FynxRemoteMedia(
                    mediaUrl = mediaUrl,
                    type = FynxMarketplaceClient.mediaDisplayType(listing.mediaTypes.firstOrNull()),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    rounded = false
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            if (slide.isWeeklyWinner) {
                                listOf(Color(0xFF172E50), Color(0xFF0D1B31), Color(0xFF74551D))
                            } else if (slide.celebration) {
                                listOf(Color(0xFF8B1E2D), Color(0xFFB94329), Color(0xFF243D64))
                            } else {
                                listOf(Color(0xFF07549A), Color(0xFF007FFF), Color(0xFF10213A))
                            }
                        )
                    )
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to Color(0xFF101D31).copy(alpha = 0.97f),
                        0.52f to Color(0xFF101D31).copy(alpha = 0.82f),
                        1f to Color(0xFF101D31).copy(alpha = if (listing?.mediaIds?.isNotEmpty() == true) 0.18f else 0.58f)
                    )
                )
            )
            Column(
                modifier = Modifier.fillMaxWidth(0.78f).align(Alignment.CenterStart).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Surface(
                    color = if (slide.isWeeklyWinner) Color(0xFFD4AF37) else Color(0xFF007FFF),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (slide.isWeeklyWinner) Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFF172033), modifier = Modifier.size(15.dp))
                        Text(
                            slide.eyebrow,
                            color = if (slide.isWeeklyWinner) Color(0xFF172033) else Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    slide.headline,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    slide.body,
                    color = Color.White.copy(alpha = 0.90f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF12233C)),
                    contentPadding = PaddingValues(horizontal = 15.dp, vertical = 7.dp)
                ) {
                    Text(slide.actionLabel, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        if (slide.sellerUsername != null) Icons.Default.Storefront else Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (slide.listing == null && slide.isWeeklyWinner) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFD4AF37).copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).size(76.dp)
                )
            }
        }
    }
}

/** Calendar-based fallback for fixed-date celebrations; lunar dates are admin-scheduled in the campaign layer. */
private fun marketplaceCelebrationFor(date: LocalDate): Pair<String, Pair<String, String>>? {
    fun between(start: LocalDate, end: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
    val year = date.year
    val christmasStart = LocalDate.of(year, 12, 18)
    val christmasEnd = LocalDate.of(year, 12, 27)
    if (between(christmasStart, christmasEnd)) {
        return "christmas" to ("Merry Christmas from FYNX!" to "Celebrate the season with gifts, great finds and products from FYNX sellers.")
    }
    if (between(LocalDate.of(year, 12, 29), LocalDate.of(year + 1, 1, 3)) ||
        between(LocalDate.of(year - 1, 12, 29), LocalDate.of(year, 1, 3))
    ) {
        return "new-year" to ("Happy New Year!" to "Start the year with fresh discoveries from FYNX Marketplace.")
    }
    if (between(LocalDate.of(year, 2, 12), LocalDate.of(year, 2, 15))) {
        return "valentines" to ("Share the love this season" to "Discover thoughtful gifts and special finds from FYNX sellers.")
    }
    if (date == LocalDate.of(year, 3, 8)) {
        return "womens-day" to ("Happy International Women's Day" to "Celebrate women building businesses and serving customers on FYNX.")
    }
    if (between(easterSunday(year).minusDays(2), easterSunday(year).plusDays(1))) {
        return "easter" to ("Happy Easter from FYNX!" to "Wishing you a joyful celebration. Explore products from FYNX sellers.")
    }
    if (between(LocalDate.of(year, 5, 27), LocalDate.of(year, 5, 28))) {
        return "childrens-day" to ("Happy Children's Day!" to "Celebrate family with discoveries from FYNX Marketplace.")
    }
    if (between(LocalDate.of(year, 10, 1), LocalDate.of(year, 10, 3))) {
        return "independence" to ("Happy Independence Day, Nigeria!" to "Celebrate local businesses and discover products made and sold across Nigeria.")
    }
    val novemberEnd = LocalDate.of(year, 11, 30)
    val blackFriday = novemberEnd.minusDays(((novemberEnd.dayOfWeek.value - 5 + 7) % 7).toLong())
    if (between(blackFriday.minusDays(2), blackFriday.plusDays(2))) {
        return "black-friday" to ("Black Friday on FYNX" to "Explore offers from sellers. Check each product for its current price.")
    }
    return null
}

private fun easterSunday(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = ((h + l - 7 * m + 114) % 31) + 1
    return LocalDate.of(year, month, day)
}