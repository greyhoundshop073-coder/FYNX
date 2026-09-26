package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun FynxProfileContentSection(
    username: String,
    onError: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    var posts by remember(username) { mutableStateOf<List<FynxProfileRemoteClient.ProfilePost>>(emptyList()) }
    var marketplace by remember(username) { mutableStateOf<List<FynxMarketplaceClient.Listing>>(emptyList()) }
    var loading by remember(username) { mutableStateOf(true) }
    var selectedTab by rememberSaveable(username) { mutableStateOf("All") }
    var selectedPost by remember { mutableStateOf<FynxProfileRemoteClient.ProfilePost?>(null) }
    var selectedListing by remember { mutableStateOf<FynxMarketplaceClient.Listing?>(null) }

    LaunchedEffect(username) {
        loading = true
        FynxProfileRemoteClient.posts(context, username)
            .onSuccess { posts = it }
            .onFailure { onError(it.message) }
        FynxMarketplaceClient.listings(context, username, "")
            .onSuccess { listings -> marketplace = listings.filter { it.sellerUsername.equals(username, ignoreCase = true) } }
            .onFailure { marketplace = emptyList() }
        loading = false
    }

    val videoPosts = posts.filter { it.mediaType?.lowercase()?.contains("video") == true }
    val audioPosts = posts.filter { it.mediaType?.lowercase()?.contains("audio") == true }
    val photoPosts = posts.filter {
        val type = it.mediaType?.lowercase().orEmpty()
        type.contains("image") || type.contains("photo")
    }

    Column(Modifier.fillMaxWidth()) {
        FynxMySoulSection(
            posts = posts,
            onOpenLatest = { posts.firstOrNull()?.let { selectedPost = it } },
            onOpenAudio = { audioPosts.firstOrNull()?.let { selectedPost = it } }
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "All",
                "Videos",
                "Photos",
                "Audio",
                "Marketplace"
            ).forEach { label ->
                val selected = selectedTab == label
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .clickable { selectedTab = label },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier.fillMaxWidth()
                            .height(if (selected) 3.dp else 1.dp)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val gridItems = buildList<FynxProfileGridItem> {
                when (selectedTab) {
                    "Videos" -> videoPosts.forEach { add(FynxProfileGridItem.PostItem(it)) }
                    "Photos" -> photoPosts.forEach { add(FynxProfileGridItem.PostItem(it)) }
                    "Audio" -> audioPosts.forEach { add(FynxProfileGridItem.PostItem(it)) }
                    "Marketplace" -> marketplace.forEach { add(FynxProfileGridItem.MarketItem(it)) }
                    else -> posts.forEach { add(FynxProfileGridItem.PostItem(it)) }
                }
            }

            if (gridItems.isEmpty()) {
                Text(when (selectedTab) {
                    "Videos" -> "No videos yet"
                    "Photos" -> "No photos yet"
                    "Audio" -> "No audio posts yet"
                    "Marketplace" -> "No marketplace items yet"
                    else -> "No content yet"
                }, Modifier.fillMaxWidth().padding(vertical = 28.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (selectedTab == "Videos" || selectedTab == "Photos" || selectedTab == "Audio" || selectedTab == "Marketplace") {
                val columns = 3
                val rows = (gridItems.size + columns - 1) / columns
                val tileWidthDp = (context.resources.configuration.screenWidthDp - 4) / columns
                val tileHeightDp = (tileWidthDp * 4) / 3
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxWidth().height((rows * tileHeightDp + (rows - 1).coerceAtLeast(0) * 2).dp),
                    userScrollEnabled = false,
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(gridItems.size, key = { index ->
                        when (val item = gridItems[index]) {
                            is FynxProfileGridItem.PostItem -> "post:" + item.post.id
                            is FynxProfileGridItem.MarketItem -> "market:" + item.listing.id
                        }
                    }) { index ->
                        when (val item = gridItems[index]) {
                            is FynxProfileGridItem.PostItem ->
                                FynxProfileMediaGridTile(item.post) { selectedPost = item.post }
                            is FynxProfileGridItem.MarketItem ->
                                FynxProfileMarketplaceGridTile(item.listing) { selectedListing = item.listing }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    gridItems.filterIsInstance<FynxProfileGridItem.PostItem>().forEach { item ->
                        FynxProfilePostFeedCard(item.post, username) { selectedPost = item.post }
                    }
                }
            }        }
    }

    selectedPost?.let { post ->
        FynxProfilePostViewer(post, context) { selectedPost = null }
    }
    selectedListing?.let { listing ->
        FynxProfileMarketplaceDetails(listing) { selectedListing = null }
    }
}

private sealed class FynxProfileGridItem {
    data class PostItem(val post: FynxProfileRemoteClient.ProfilePost) : FynxProfileGridItem()
    data class MarketItem(val listing: FynxMarketplaceClient.Listing) : FynxProfileGridItem()
}

@Composable
private fun FynxProfilePostTile(post: FynxProfileRemoteClient.ProfilePost, username: String, onOpen: () -> Unit) {
    val context = LocalContext.current
    val mediaUrl = post.mediaUrl ?: post.mediaId?.let { "/api/social/media/" + it }
    val type = post.mediaType?.lowercase().orEmpty()
    var profilePhotoId by remember(username) { mutableStateOf<String?>(null) }

    LaunchedEffect(username) {
        FynxProfileRemoteClient.get(context, username)
            .onSuccess { profilePhotoId = it.profilePhotoMediaId?.trim()?.takeIf { id -> id.isNotBlank() } }
    }
    Card(
        Modifier.fillMaxWidth().height(184.dp).clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .22f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FynxRemoteProfileAvatar(
                    mediaId = profilePhotoId,
                    contentDescription = username,
                    modifier = Modifier.size(20.dp).clip(CircleShape),
                    ownerUsername = username
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "@" + username.removePrefix("@"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (!mediaUrl.isNullOrBlank()) {
                FynxRemoteMedia(mediaUrl = mediaUrl, type = post.mediaType ?: "auto", modifier = Modifier.fillMaxSize(), autoPlay = false, playbackActive = true)
            } else {
                Text(post.text.ifBlank { "Post" }, Modifier.padding(10.dp),
                    style = MaterialTheme.typography.labelMedium, maxLines = 6,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            if (type.contains("video")) {
                Box(Modifier.align(Alignment.Center).size(34.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .88f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (type.contains("audio")) {
                Box(Modifier.align(Alignment.Center).size(34.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .90f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, contentDescription = "Audio", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (post.likeCount > 0 || post.commentCount > 0) {
            Text(
                post.likeCount.toString() + " likes • " + post.commentCount.toString() + " comments",
                Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
}

@Composable
private fun FynxMySoulSection(
    posts: List<FynxProfileRemoteClient.ProfilePost>,
    onOpenLatest: () -> Unit = {},
    onOpenAudio: () -> Unit = {}
) {
    val latest = posts.firstOrNull()
    val audio = posts.firstOrNull { it.mediaType?.lowercase()?.contains("audio") == true }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("MY FYNX", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .18f))) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("FYNX Moment", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                SoulPill(
                    title = "Latest FYNX post",
                    value = latest?.text?.ifBlank { "Open your latest post" } ?: "No post yet",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = if (latest != null) onOpenLatest else null
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoulPill("Today I Am…", if (latest == null) "Not set" else "From latest post", Modifier.weight(1f))
                    SoulPill("My World", "Not set", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoulPill("Keep This", "Not pinned", Modifier.weight(1f))
                    SoulPill(
                        "My Sound",
                        if (audio != null) "Open audio post" else "No audio yet",
                        Modifier.weight(1f),
                        onClick = if (audio != null) onOpenAudio else null
                    )
                }
                Text("A Little About Me", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("Your profile bio remains the source of truth.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
@Composable
private fun SoulPill(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val pillModifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))
        .padding(10.dp)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    Column(pillModifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
@Composable
private fun FynxProfilePostFeedCard(post: FynxProfileRemoteClient.ProfilePost, username: String, onOpen: () -> Unit) {
    val context = LocalContext.current
    val mediaUrl = post.mediaUrl ?: post.mediaId?.let { "/api/social/media/" + it }
    val type = post.mediaType?.lowercase().orEmpty()
    var profilePhotoId by remember(username) { mutableStateOf<String?>(null) }
    LaunchedEffect(username) { FynxProfileRemoteClient.get(context, username).onSuccess { profilePhotoId = it.profilePhotoMediaId } }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(0.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(0.dp, MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                FynxRemoteProfileAvatar(profilePhotoId, username, Modifier.size(38.dp).clip(CircleShape), ownerUsername = username)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("@"+username.removePrefix("@"), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("FYNX post", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (post.text.isNotBlank()) Text(post.text, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyLarge)
            if (!mediaUrl.isNullOrBlank()) {
                if (type.contains("audio")) {
                    Column(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(82.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(com.fynx.app.R.drawable.ic_fynx_logo),
                                    contentDescription = "FYNX audio post",
                                    modifier = Modifier.size(58.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("FYNX AUDIO", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("Voice / audio recording", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                Text("Tap to listen", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        FynxRemoteMedia(mediaUrl, post.mediaType ?: "audio", Modifier.fillMaxWidth().height(64.dp), autoPlay = false, playbackActive = true)
                    }
                } else {
                    val mediaModifier = when {
                        type.contains("video") -> Modifier.fillMaxWidth().aspectRatio(9f / 16f)
                        type.contains("image") || type.contains("photo") -> Modifier.fillMaxWidth().aspectRatio(4f / 5f)
                        else -> Modifier.fillMaxWidth()
                    }
                    FynxRemoteMedia(mediaUrl, post.mediaType ?: "auto", mediaModifier, autoPlay = false, playbackActive = true)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(post.likeCount.toString()+" likes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(post.commentCount.toString()+" comments", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable
private fun FynxProfileMediaGridTile(post: FynxProfileRemoteClient.ProfilePost, onOpen: () -> Unit) {
    val mediaUrl = post.mediaUrl ?: post.mediaId?.let { "/api/social/media/" + it }
    val type = post.mediaType?.lowercase().orEmpty()
    Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clickable(onClick = onOpen)
        .background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (type.contains("audio")) {
            Image(painter = painterResource(com.fynx.app.R.drawable.ic_fynx_logo),
                contentDescription = "FYNX audio post",
                modifier = Modifier.fillMaxSize().padding(24.dp))
            Box(Modifier.align(Alignment.BottomStart).padding(7.dp).size(32.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .88f), CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, contentDescription = "Play audio", tint = MaterialTheme.colorScheme.primary)
            }
        } else if (!mediaUrl.isNullOrBlank()) {
            FynxRemoteMedia(mediaUrl = mediaUrl, type = post.mediaType ?: "auto",
                modifier = Modifier.fillMaxSize(), autoPlay = false, playbackActive = false)
            if (type.contains("video")) {
                Box(Modifier.align(Alignment.BottomStart).padding(7.dp).size(32.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .88f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play video", tint = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            Text(post.text.ifBlank { "Post" }, Modifier.align(Alignment.Center).padding(10.dp),
                maxLines = 6, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun FynxProfileMarketplaceGridTile(listing: FynxMarketplaceClient.Listing, onOpen: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clickable(onClick = onOpen)
        .background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (listing.mediaIds.isNotEmpty()) {
            FynxRemoteMedia(mediaUrl = FynxMarketplaceClient.mediaUrl(LocalContext.current, listing.mediaIds.first()),
                type = "auto", modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.ShoppingBag, contentDescription = "Marketplace",
                modifier = Modifier.align(Alignment.Center).size(34.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f)).padding(7.dp)) {
            Text(listing.title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listing.currency + " " + String.format(Locale.US, "%,.0f", listing.price),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
    }
}

@Composable
private fun FynxProfileMarketplaceTile(listing: FynxMarketplaceClient.Listing, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .22f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FynxAvatar(
                    listing.sellerDisplayName.ifBlank { listing.sellerUsername },
                    Modifier.size(20.dp).clip(CircleShape),
                    ownerUsername = listing.sellerUsername
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    listing.sellerDisplayName.ifBlank { listing.sellerUsername }.ifBlank { "FYNX Seller" },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (listing.mediaIds.isNotEmpty()) {
                FynxRemoteMedia(
                    mediaUrl = FynxMarketplaceClient.mediaUrl(LocalContext.current, listing.mediaIds.first()),
                    type = "auto", modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                Box(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ShoppingBag, contentDescription = "Marketplace", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.padding(horizontal = 7.dp, vertical = 5.dp)) {
                Text(listing.title, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(listing.currency + " " + String.format(Locale.US, "%,.0f", listing.price),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun FynxProfilePostViewer(
    post: FynxProfileRemoteClient.ProfilePost,
    context: Context,
    onDismiss: () -> Unit
) {
    val viewerPost = FynxRemoteSocialClient.RemotePost(
        id = post.id, authorId = "", authorUsername = "", authorDisplayName = "",
        text = post.text, visibility = post.visibility, mediaId = post.mediaId,
        mediaType = post.mediaType,
        mediaUrl = post.mediaUrl ?: post.mediaId?.let { "/api/social/media/" + it },
        timestamp = post.timestamp, likeCount = post.likeCount, commentCount = post.commentCount,
        likedByCurrentUser = false, followedByCurrentUser = false
    )
    FynxPostMediaViewer(
        context = context, post = viewerPost,
        media = listOfNotNull(post.mediaId?.let {
            FynxPostViewerItem(it, post.mediaType ?: "auto", 0, "/api/social/media/" + it)
        }),
        onDismiss = onDismiss
    )
}

@Composable
private fun FynxProfileMarketplaceDetails(
    listing: FynxMarketplaceClient.Listing,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(listing.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (listing.mediaIds.isNotEmpty()) {
                    FynxRemoteMedia(
                        mediaUrl = FynxMarketplaceClient.mediaUrl(LocalContext.current, listing.mediaIds.first()),
                        type = "auto", modifier = Modifier.fillMaxWidth().height(220.dp)
                    )
                }
                Text(listing.currency + " " + String.format(Locale.US, "%,.2f", listing.price),
                    style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                if (listing.description.isNotBlank()) Text(listing.description)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FynxRemoteProfileAvatar(
                        mediaId = null,
                        contentDescription = listing.sellerDisplayName.ifBlank { listing.sellerUsername },
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                        ownerUsername = listing.sellerUsername
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Seller: " + listing.sellerDisplayName.ifBlank { listing.sellerUsername })
                }
                if (listing.storeName.isNotBlank()) Text("Store: " + listing.storeName)
                Text(listing.quantity.toString() + " available • " + listing.condition)
                if (listing.location.isNotBlank()) Text("Location: " + listing.location)
                Text("FYNX protected payment", style = MaterialTheme.typography.labelLarge)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}
