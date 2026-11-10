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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** A Marketplace-native seller storefront, backed only by current FYNX listing/reputation data. */
@Composable
internal fun FynxMarketplaceSellerStoreDialog(
    username: String,
    viewerUsername: String,
    listings: List<FynxRemoteSocialClient.MarketplaceListing>,
    reputation: FynxMarketplaceClient.SellerReputation?,
    photoId: String?,
    onOpenListing: (FynxRemoteSocialClient.MarketplaceListing) -> Unit,
    onContact: (FynxRemoteSocialClient.MarketplaceListing) -> Unit,
    onContactShop: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storefrontListState = rememberLazyListState()
    val isOwnStore = username.removePrefix("@").trim().equals(viewerUsername.removePrefix("@").trim(), ignoreCase = true)
    var tab by remember { mutableStateOf("Products") }
    var profileName by remember(username) { mutableStateOf("") }
    var storeName by remember(username) { mutableStateOf("") }
    var profileBio by remember(username) { mutableStateOf("") }
    var shopDescription by remember(username) { mutableStateOf("") }
    var shopLogoMediaId by remember(username) { mutableStateOf<String?>(null) }
    var shopCoverMediaId by remember(username) { mutableStateOf<String?>(null) }
    var shopLocation by remember(username) { mutableStateOf("") }
    var shopContactPhone by remember(username) { mutableStateOf("") }
    var profileLoading by remember(username) { mutableStateOf(true) }
    var shopProfileError by remember(username) { mutableStateOf<String?>(null) }
    var followedByViewer by remember(username, viewerUsername) { mutableStateOf<Boolean?>(null) }
    var followerCount by remember(username) { mutableStateOf<Int?>(null) }
    var followBusy by remember(username, viewerUsername) { mutableStateOf(false) }
    var followError by remember(username, viewerUsername) { mutableStateOf<String?>(null) }
    var storefrontListings by remember(username) { mutableStateOf<List<FynxRemoteSocialClient.MarketplaceListing>?>(null) }
    var storefrontLoading by remember(username) { mutableStateOf(true) }
    var storefrontError by remember(username) { mutableStateOf<String?>(null) }
    var storefrontReloadKey by remember(username) { mutableStateOf(0) }
    var storefrontHasMore by remember(username) { mutableStateOf(false) }
    var storefrontOffset by remember(username) { mutableStateOf(0) }
    var storefrontLoadingMore by remember(username) { mutableStateOf(false) }
    var weeklyAwards by remember(username) { mutableStateOf<List<FynxMarketplaceSellerWeeklyAward>>(emptyList()) }
    var listingEngagement by remember(username, viewerUsername) {
        mutableStateOf<Map<String, FynxRemoteSocialClient.MarketplaceListingEngagement>?>(null)
    }
    var listingEngagementError by remember(username, viewerUsername) { mutableStateOf<String?>(null) }
    var previouslyPurchasedListingIds by remember(viewerUsername) { mutableStateOf<Set<String>>(emptySet()) }
    var purchaseHistoryError by remember(viewerUsername) { mutableStateOf<String?>(null) }
    var likeBusyListingIds by remember(username, viewerUsername) { mutableStateOf<Set<String>>(emptySet()) }
    var likeError by remember(username, viewerUsername) { mutableStateOf<String?>(null) }
    var sellerReviews by remember(username) { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var reviewsLoading by remember(username) { mutableStateOf(true) }
    var reviewsError by remember(username) { mutableStateOf<String?>(null) }
    fun loadMoreStorefront() {
        if (storefrontLoading || storefrontLoadingMore || !storefrontHasMore) return
        scope.launch {
            storefrontLoadingMore = true
            FynxRemoteSocialClient.marketplaceSellerListingsPage(context, username, limit = 24, offset = storefrontOffset)
                .onSuccess { page ->
                    storefrontListings = (storefrontListings.orEmpty() + page.listings).distinctBy { it.id }
                    storefrontOffset += page.listings.size
                    storefrontHasMore = page.hasMore
                    storefrontError = null
                }
                .onFailure { failure -> storefrontError = marketplaceSafeError(failure, "More shop products could not load.") }
            storefrontLoadingMore = false
        }
    }
    LaunchedEffect(username, viewerUsername, storefrontReloadKey) {
        storefrontLoading = true
        listingEngagement = null
        listingEngagementError = null
        purchaseHistoryError = null
        likeError = null
        storefrontError = null
        profileLoading = true
        shopProfileError = null
        followError = null
        storeName = listings.firstOrNull()?.storeName.orEmpty()

        // Products, profile/follow state and shop identity are independent requests.
        // Fetch them concurrently so a slow endpoint cannot delay the whole storefront.
        coroutineScope {
            launch {
                FynxRemoteSocialClient.marketplaceSellerListingsPage(context, username, limit = 24, offset = 0)
                    .onSuccess { page ->
                        storefrontListings = page.listings.filter { it.active && it.quantity > 0 }
                        storefrontOffset = page.listings.size
                        storefrontHasMore = page.hasMore
                        storefrontError = null
                    }
                    .onFailure { failure ->
                        storefrontError = if (storefrontListings.isNullOrEmpty()) {
                            marketplaceSafeError(failure, "Products could not load. Check your connection and try again.")
                        } else {
                            "Showing previously loaded products. Product availability could not be refreshed."
                        }
                    }
                storefrontLoading = false
            }
            launch {
                FynxProfileRemoteClient.get(context, username.removePrefix("@").trim())
                    .onSuccess { profile ->
                        profileName = profile.displayName.orEmpty()
                        profileBio = profile.bio.orEmpty()
                        followedByViewer = profile.followedByCurrentUser
                        followerCount = profile.followerCount
                        followError = null
                    }
                    .onFailure { failure ->
                        followError = marketplaceSafeError(failure, "Follow status could not load.")
                    }
                profileLoading = false
            }
            launch {
                FynxBackendClient.get(context, "/api/marketplace/shops/" + username.removePrefix("@").trim())
                    .mapCatching { raw ->
                        org.json.JSONObject(raw).optJSONObject("shop")
                            ?: throw IllegalStateException("Public shop profile response was incomplete.")
                    }
                    .onSuccess { shop ->
                        storeName = shop.optString("shopName").ifBlank { storeName }
                        shopDescription = shop.optString("description")
                        shopLogoMediaId = shop.optString("logoMediaId").takeIf { it.isNotBlank() && it != "null" }
                        shopCoverMediaId = shop.optString("coverMediaId").takeIf { it.isNotBlank() && it != "null" }
                        shopLocation = shop.optString("location")
                        shopContactPhone = shop.optString("contactPhone")
                    }
                    .onFailure { failure ->
                        shopProfileError = marketplaceSafeError(failure, "Shop details could not load.")
                    }
            }
            launch {
                FynxRemoteSocialClient.marketplaceSellerListingEngagement(context, username)
                    .onSuccess { listingEngagement = it }
                    .onFailure { failure -> listingEngagementError = marketplaceSafeError(failure, "Product likes could not load.") }
            }
            launch {
                val viewer = viewerUsername.trim().removePrefix("@")
                if (viewer.isNotBlank() && !viewer.equals("preview", ignoreCase = true)) {
                    FynxRemoteSocialClient.myPurchasedMarketplaceListingIds(context)
                        .onSuccess {
                            previouslyPurchasedListingIds = it
                            purchaseHistoryError = null
                        }
                        .onFailure { failure ->
                            previouslyPurchasedListingIds = emptySet()
                            purchaseHistoryError = "Previous purchases are temporarily unavailable."
                        }
                } else {
                    previouslyPurchasedListingIds = emptySet()
                }
            }
            launch {
                reviewsLoading = true
                FynxBackendClient.get(context, "/api/marketplace/sellers/" + username.removePrefix("@").trim() + "/reviews?limit=10&offset=0")
                    .mapCatching { raw ->
                        val rows = org.json.JSONObject(raw).optJSONArray("reviews") ?: org.json.JSONArray()
                        buildList { for (index in 0 until rows.length()) rows.optJSONObject(index)?.let { add(it) } }
                    }
                    .onSuccess { sellerReviews = it; reviewsError = null }
                    .onFailure { failure -> reviewsError = marketplaceSafeError(failure, "Seller reviews could not load.") }
                reviewsLoading = false
            }
        }
    }
    LaunchedEffect(storefrontListState, tab) {
        snapshotFlow {
            val layout = storefrontListState.layoutInfo
            Triple(
                layout.visibleItemsInfo.lastOrNull()?.index ?: -1,
                layout.totalItemsCount,
                tab == "Products" && storefrontHasMore && !storefrontLoading && !storefrontLoadingMore
            )
        }.collect { (lastVisible, totalItems, shouldLoad) ->
            if (shouldLoad && totalItems > 0 && lastVisible >= totalItems - 3) loadMoreStorefront()
        }
    }
    LaunchedEffect(username) {
        FynxMarketplaceWeeklySellerClient.history(context, username)
            .onSuccess { weeklyAwards = it }
        // Award history is optional reputation content and must not delay profile or product loading.
    }
    val sellerListings = remember(storefrontListings) {
        storefrontListings.orEmpty().filter { it.active && it.quantity > 0 }.distinctBy { it.id }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "Back to Marketplace") }
                Column(Modifier.weight(1f)) {
                    Text("Marketplace profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("@${username.removePrefix("@")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                state = storefrontListState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            shopCoverMediaId?.let { mediaId ->
                                FynxRemoteMedia(
                                    FynxMarketplaceClient.mediaUrl(context, mediaId),
                                    "image",
                                    Modifier.fillMaxWidth().height(112.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                            if (!shopLogoMediaId.isNullOrBlank()) {
                                FynxRemoteMedia(
                                    FynxMarketplaceClient.mediaUrl(context, shopLogoMediaId!!),
                                    "image",
                                    Modifier.size(72.dp).clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                FynxRemoteProfileAvatar(
                                    photoId,
                                    profileName.ifBlank { username },
                                    Modifier.size(64.dp).clip(CircleShape),
                                    ownerUsername = username
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Column(
                                    Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Text(
                                        storeName.ifBlank { profileName.ifBlank { username.removePrefix("@") } },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (profileName.isNotBlank() && !profileName.equals(storeName, ignoreCase = true)) {
                                        Text(
                                            "by " + profileName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        "@${username.removePrefix("@")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                reputation?.let { MarketplaceSellerTierBadge(it.tier) }
                            }
                            if (profileLoading) CircularProgressIndicator(Modifier.size(16.dp))
                            shopProfileError?.let { message ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        message,
                                        Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    TextButton(
                                        onClick = { storefrontReloadKey += 1 },
                                        enabled = !profileLoading,
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) { Text("Retry") }
                                }
                            }
                            if (shopDescription.isNotBlank()) {
                                Text(
                                    shopDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            val shopMetadata = listOfNotNull(
                                shopLocation.trim().takeIf { it.isNotBlank() },
                                shopContactPhone.trim().takeIf { it.isNotBlank() }?.let { "Contact: " + it }
                            ).joinToString("  •  ")
                            if (shopMetadata.isNotBlank()) {
                                Text(
                                    shopMetadata,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(sellerListings.size.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text("Products", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                followerCount?.let { count ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(count.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text("Followers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                reputation?.let { r ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(r.successfulSales.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text("Successful sales", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (r.reviewCount > 0) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                                                Text(String.format(Locale.US, "%.1f", r.averageRating), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                            }
                                            Text("${r.reviewCount} reviews", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!isOwnStore) {
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        enabled = !profileLoading && followedByViewer != null && !followBusy,
                                        onClick = {
                                            val wasFollowing = followedByViewer ?: return@OutlinedButton
                                            followBusy = true
                                            followError = null
                                            scope.launch {
                                                FynxRemoteSocialClient.follow(context, username, wasFollowing)
                                                    .onSuccess { nowFollowing ->
                                                        if (nowFollowing != wasFollowing) {
                                                            followerCount = followerCount?.let { count ->
                                                                (count + if (nowFollowing) 1 else -1).coerceAtLeast(0)
                                                            }
                                                        }
                                                        followedByViewer = nowFollowing
                                                    }
                                                    .onFailure { failure ->
                                                        followError = marketplaceSafeError(failure, "Could not update shop follow status.")
                                                    }
                                                followBusy = false
                                            }
                                        }
                                    ) {
                                        Text(
                                            when {
                                                followBusy -> "Updating…"
                                                profileLoading -> "Loading…"
                                                followedByViewer == true -> "Following"
                                                followedByViewer == false -> "Follow"
                                                else -> "Unavailable"
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Button(
                                    onClick = { sellerListings.firstOrNull()?.let(onContact) ?: onContactShop() },
                                    enabled = !isOwnStore,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Message seller", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            followError?.let { message ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (followedByViewer == null) {
                                        TextButton(
                                            onClick = { storefrontReloadKey += 1 },
                                            enabled = !storefrontLoading,
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                        ) { Text("Retry") }
                                    }
                                }
                            }
                            Text(
                                "Seller reputation is based on verified FYNX orders and buyer feedback.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (weeklyAwards.isNotEmpty()) {
                    item(key = "seller-weekly-awards-$username") {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(15.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🏆", style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("FYNX Seller Hall of Fame", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("Verified weekly Marketplace recognition", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Surface(color = Color(0xFFD4AF37), shape = RoundedCornerShape(50)) {
                                        Text(
                                            weeklyAwards.size.toString() + if (weeklyAwards.size == 1) " award" else " awards",
                                            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            color = Color(0xFF1C2434),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                weeklyAwards.take(12).forEach { award ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            color = if (award.rank == 1) Color(0xFFD4AF37) else MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                if (award.rank == 1) "WINNER" else "TOP ${award.rank}",
                                                Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                                color = if (award.rank == 1) Color(0xFF1C2434) else MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                if (award.rank == 1) "Best Seller of the Week" else "Weekly Top Seller",
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                award.weekLabel.ifBlank { "Weekly award" } + " · " +
                                                    award.completedOrders + " completed orders · " +
                                                    award.uniqueBuyers + " buyers",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "Recognition is based on completed Marketplace orders, not advertising spend.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Products", "About").forEach { label ->
                            androidx.compose.material3.FilterChip(
                                selected = tab == label,
                                onClick = { tab = label },
                                label = { Text(label) },
                                colors = fynxMarketplaceSelectedChipColors()
                            )
                        }
                    }
                }
                if (tab == "About") {
                    item {
                        Card(shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("About this shop", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(shopDescription.ifBlank { "This shop has not added a public description." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Shop: ${storeName.ifBlank { "Not provided" }}")
                                Text("Location: ${shopLocation.ifBlank { "Not provided" }}")
                                if (profileBio.isNotBlank()) {
                                    HorizontalDivider()
                                    Text("Seller profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(profileBio, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Username: @${username.removePrefix("@")}")
                                reputation?.let { r ->
                                    Text("Seller tier: ${r.tier}")
                                    Text("Order completion: ${String.format(Locale.US, "%.0f", r.completionRate)}%")
                                    if (r.totalOrders > 0) Text("Orders: ${r.totalOrders}")
                                } ?: Text("Seller reputation is not available right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                HorizontalDivider()
                                Text("Buyer reviews", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                when {
                                    reviewsLoading && sellerReviews.isEmpty() -> CircularProgressIndicator(Modifier.size(18.dp))
                                    reviewsError != null && sellerReviews.isEmpty() -> Text(reviewsError!!, color = MaterialTheme.colorScheme.error)
                                    sellerReviews.isEmpty() -> Text("No buyer reviews yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    else -> sellerReviews.forEach { review ->
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text("${review.optString("reviewerDisplayName", review.optString("reviewerUsername"))}  •  ${review.optInt("rating")}/5 ★", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                            Text(review.optString("productTitle"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            review.optString("comment").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                        }
                                        HorizontalDivider()
                                    }
                                }
                                reviewsError?.takeIf { sellerReviews.isNotEmpty() }?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                } else if (storefrontLoading && sellerListings.isEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Loading this seller’s products…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (storefrontError != null && sellerListings.isEmpty()) {
                    item {
                        Card(shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(storefrontError ?: "Products could not load.", color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { storefrontReloadKey += 1 }, enabled = !storefrontLoading) { Text("Retry products") }
                            }
                        }
                    }
                } else if (sellerListings.isEmpty()) {
                    item { Text("This seller has no active products available right now.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    if (storefrontLoading) item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Refreshing seller products…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    storefrontError?.let { message ->
                        item {
                            Card(shape = RoundedCornerShape(14.dp)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = { storefrontReloadKey += 1 }, enabled = !storefrontLoading) { Text("Retry") }
                                }
                            }
                        }
                    }
                    listingEngagementError?.let { message ->
                        item(key = "seller-store-like-error") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { storefrontReloadKey += 1 }, enabled = !storefrontLoading, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                    Text("Retry likes")
                                }
                            }
                        }
                    }
                    purchaseHistoryError?.let { message ->
                        item(key = "seller-store-purchase-history-error") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { storefrontReloadKey += 1 }, enabled = !storefrontLoading, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                    Text("Retry history")
                                }
                            }
                        }
                    }
                    likeError?.let { message ->
                        item(key = "seller-store-like-action-error") {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                TextButton(onClick = { likeError = null }, enabled = likeBusyListingIds.isEmpty()) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                    items(sellerListings.chunked(3), key = { row -> "seller-store-row-" + row.first().id }) { productRow ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            productRow.forEach { listing ->
                                val engagement = listingEngagement?.get(listing.id)
                                MarketplaceSellerProductGridCard(
                                    listing = listing,
                                    likeCount = engagement?.likeCount,
                                    liked = engagement?.likedByCurrentUser == true,
                                    likeBusy = listing.id in likeBusyListingIds,
                                    likeEnabled = !isOwnStore && engagement != null,
                                    purchasedBefore = listing.id in previouslyPurchasedListingIds,
                                    onOpen = { onOpenListing(listing) },
                                    onLike = {
                                        if (engagement != null && listing.id !in likeBusyListingIds && !isOwnStore) {
                                            likeBusyListingIds = likeBusyListingIds + listing.id
                                            likeError = null
                                            scope.launch {
                                                FynxRemoteSocialClient.setMarketplaceListingLike(context, listing.id, !engagement.likedByCurrentUser)
                                                    .onSuccess { updated -> listingEngagement = listingEngagement.orEmpty() + (listing.id to updated) }
                                                    .onFailure { failure -> likeError = marketplaceSafeError(failure, "Product like could not be updated.") }
                                                likeBusyListingIds = likeBusyListingIds - listing.id
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - productRow.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (storefrontHasMore || storefrontLoadingMore) {
                        item(key = "seller-store-load-more") {
                            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                if (storefrontLoadingMore) CircularProgressIndicator(Modifier.size(18.dp))
                                else TextButton(onClick = { loadMoreStorefront() }) { Text("Load more products") }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)); HorizontalDivider() }
            }
        }
    }
}

@Composable
internal fun MarketplaceSellerTierBadge(tier: String) {
    val badge = when (tier.trim().uppercase(Locale.US)) {
        "NEW SELLER" -> "NEW SELLER" to Color(0xFF64748B)
        "ELITE SELLER" -> "GOLD SELLER" to Color(0xFFD4AF37)
        "TOP SELLER" -> "SILVER SELLER" to Color(0xFFB8C0CC)
        "TRUSTED SELLER" -> "BRONZE SELLER" to Color(0xFFCD7F32)
        "RISING SELLER" -> "RISING SELLER" to Color(0xFF007FFF)
        else -> null
    } ?: return
    Surface(color = badge.second.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(15.dp), tint = badge.second)
            Text(badge.first, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = badge.second)
        }
    }
}

@Composable
private fun MarketplaceSellerProductGridCard(
    listing: FynxRemoteSocialClient.MarketplaceListing,
    likeCount: Int?,
    liked: Boolean,
    likeBusy: Boolean,
    likeEnabled: Boolean,
    purchasedBefore: Boolean,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(onClick = onOpen, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            Box {
                if (listing.mediaIds.isNotEmpty()) {
                    FynxRemoteMedia(FynxMarketplaceClient.mediaUrl(context, listing.mediaIds.first()),
                        FynxMarketplaceClient.mediaDisplayType(listing.mediaTypes.firstOrNull()),
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)))
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ShoppingBag, contentDescription = "Product image unavailable", modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (purchasedBefore) {
                    Surface(Modifier.align(Alignment.BottomStart).padding(5.dp), color = Color(0xFFE5F1FF), shape = RoundedCornerShape(6.dp)) {
                        Text("Bought before", Modifier.padding(horizontal = 6.dp, vertical = 4.dp), color = Color(0xFF0759A5), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                IconButton(onClick = onLike, enabled = likeEnabled && !likeBusy, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(34.dp)) {
                    if (likeBusy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    else Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (liked) "Unlike product" else "Like product", modifier = Modifier.size(20.dp),
                        tint = if (liked) Color(0xFFDC143C) else MaterialTheme.colorScheme.onSurface)
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(listing.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listing.currency.uppercase(Locale.US) + " " + String.format(Locale.US, "%,.2f", listing.price),
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Available: " + listing.quantity, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (listing.location.isNotBlank()) Text(listing.location.trim(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(11.dp), tint = Color(0xFFDC143C))
                    Text(likeCount?.toString() ?: "—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("likes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
