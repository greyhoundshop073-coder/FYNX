package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
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
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "All" to Icons.Default.Image,
                "Videos" to Icons.Default.PlayArrow,
                "Photos" to Icons.Default.Image,
                "Audio" to Icons.Default.MusicNote,
                "Marketplace" to Icons.Default.ShoppingBag
            ).forEach { (label, icon) ->
                val selected = selectedTab == label
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .clickable { selectedTab = label }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = label, modifier = Modifier.size(17.dp),
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(5.dp))
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
                    else -> {
                        val max = maxOf(posts.size, marketplace.size)
                        for (i in 0 until max) {
                            if (i < posts.size) add(FynxProfileGridItem.PostItem(posts[i]))
                            if (i < marketplace.size) add(FynxProfileGridItem.MarketItem(marketplace[i]))
                        }
                    }
                }
            }

            if (gridItems.isEmpty()) {
                Text(
                    when (selectedTab) {
                        "Videos" -> "No videos yet"
                        "Photos" -> "No photos yet"
                        "Audio" -> "No audio posts yet"
                        "Marketplace" -> "No marketplace items yet"
                        else -> "No content yet"
                    },
                    Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val rows = (gridItems.size + 2) / 3
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().height((rows * 184).dp),
                    userScrollEnabled = false,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(gridItems.size, key = { index ->
                        when (val item = gridItems[index]) {
                            is FynxProfileGridItem.PostItem -> "post:" + item.post.id
                            is FynxProfileGridItem.MarketItem -> "market:" + item.listing.id
                        }
                    }) { index ->
                        when (val item = gridItems[index]) {
                            is FynxProfileGridItem.PostItem -> FynxProfilePostTile(item.post, username) { selectedPost = item.post }
                            is FynxProfileGridItem.MarketItem -> FynxProfileMarketplaceTile(item.listing) { selectedListing = item.listing }
                        }
                    }
                }
            }
        }
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
    val mediaUrl = post.mediaUrl ?: post.mediaId?.let { "/api/social/media/" + it }
    val type = post.mediaType?.lowercase().orEmpty()
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
                FynxAvatar(
                    username,
                    Modifier.size(20.dp).clip(CircleShape)
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
                FynxRemoteMedia(mediaUrl = mediaUrl, type = post.mediaType ?: "auto", modifier = Modifier.fillMaxSize())
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

@Composable
private fun FynxProfileMarketplaceTile(listing: FynxMarketplaceClient.Listing, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().height(184.dp).clickable(onClick = onOpen),
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
                    Modifier.size(20.dp).clip(CircleShape)
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
                Text("Seller: " + listing.sellerDisplayName.ifBlank { listing.sellerUsername })
                if (listing.storeName.isNotBlank()) Text("Store: " + listing.storeName)
                Text(listing.quantity.toString() + " available • " + listing.condition)
                if (listing.location.isNotBlank()) Text("Location: " + listing.location)
                Text("FYNX protected payment", style = MaterialTheme.typography.labelLarge)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}
