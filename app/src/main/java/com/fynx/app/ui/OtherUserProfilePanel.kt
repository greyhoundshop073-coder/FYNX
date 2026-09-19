package com.fynx.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun OtherUserProfilePanel(
    username: String,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember(username) { mutableStateOf<FynxProfileRemoteClient.Profile?>(null) }
    var posts by remember(username) { mutableStateOf<List<FynxProfileRemoteClient.ProfilePost>>(emptyList()) }
    var marketplace by remember(username) { mutableStateOf<List<FynxMarketplaceClient.Listing>>(emptyList()) }
    var loading by remember(username) { mutableStateOf(true) }
    var error by remember(username) { mutableStateOf<String?>(null) }
    var following by remember(username) { mutableStateOf(false) }
    var busy by remember(username) { mutableStateOf(false) }
    var reportOpen by remember(username) { mutableStateOf(false) }
    var reportReason by remember(username) { mutableStateOf("Safety or spam") }
    var reportDetails by remember(username) { mutableStateOf("") }
    var reportMessage by remember(username) { mutableStateOf<String?>(null) }
    var selectedPostIndex by remember(username) { mutableStateOf<Int?>(null) }
    var selectedTab by remember(username) { mutableStateOf("Posts") }
    var selectedListing by remember(username) { mutableStateOf<FynxMarketplaceClient.Listing?>(null) }

    fun loadProfile() {
        scope.launch {
            loading = true
            error = null
            FynxProfileRemoteClient.get(context, username)
                .onSuccess { loaded ->
                    profile = loaded
                    following = loaded.followedByCurrentUser
                    if (loaded.postCount > 0 || loaded.relationship == "friends" || loaded.relationship == "self") {
                        FynxProfileRemoteClient.posts(context, loaded.username)
                            .onSuccess { loadedPosts -> posts = loadedPosts }
                            .onFailure { posts = emptyList() }
                    } else posts = emptyList()
                    // Marketplace is a distinct content type. The existing real Marketplace
                    // discovery endpoint already returns seller identity, so filter by the
                    // exact profile owner and never manufacture profile listings.
                    FynxMarketplaceClient.listings(context, loaded.username, "")
                        .onSuccess { listings ->
                            marketplace = listings.filter { it.sellerUsername.equals(loaded.username, ignoreCase = true) }
                            if (selectedTab == "Marketplace" && marketplace.isEmpty()) selectedTab = "Posts"
                        }
                        .onFailure { marketplace = emptyList() }
                }
                .onFailure { error = it.message ?: "Unable to load this profile." }
            loading = false
        }
    }

    LaunchedEffect(username) { loadProfile() }

    if (selectedPostIndex != null && posts.isNotEmpty()) {
        ProfilePostSwipeViewer(
            posts = posts,
            initialIndex = selectedPostIndex!!.coerceIn(0, posts.lastIndex),
            onClose = { selectedPostIndex = null }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text("Profile", style = MaterialTheme.typography.titleLarge)
        }
        when {
            loading && profile == null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            profile == null -> {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "User not found", color = FynxDesign.TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { loadProfile() }) { Text("Retry") }
                }
            }
            else -> {
                val person = profile!!
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    item {
                        RemoteProfilePhoto(person.profilePhotoMediaId, person.displayName, Modifier.size(104.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(person.displayName.ifBlank { person.username }, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp))
                        Text("@${person.username.removePrefix("@").trim()}", color = FynxDesign.TextSecondary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp))
                        if (person.bio.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(person.bio, color = FynxDesign.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp)) }
                        if (person.country.isNotBlank()) Text(person.country, color = FynxDesign.TextSecondary, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp))
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth().widthIn(max = 520.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            ProfileCount(label = "Posts", value = person.postCount)
                            person.followerCount?.let { ProfileCount(label = "Followers", value = it) }
                            person.followingCount?.let { ProfileCount(label = "Following", value = it) }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth().widthIn(max = 520.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(enabled = !busy, onClick = {
                                scope.launch {
                                    busy = true
                                    val actionResult = when {
                                        person.viewerReceivedRequest && person.pendingRequestId != null -> FynxSocialClient.acceptRequest(context, person.pendingRequestId)
                                        person.viewerSentRequest && person.pendingRequestId != null -> FynxSocialClient.cancelRequest(context, person.pendingRequestId)
                                        person.relationship == "none" -> FynxSocialClient.sendRequest(context, person.username)
                                        else -> FynxRemoteSocialClient.follow(context, person.username, following)
                                    }
                                    actionResult.onSuccess { loadProfile() }.onFailure { error = it.message ?: "That action could not be completed." }
                                    busy = false
                                }
                            }) {
                                Text(when {
                                    person.relationship == "friends" -> if (following) "Following" else "Follow"
                                    person.viewerReceivedRequest -> "Accept request"
                                    person.viewerSentRequest -> "Request sent"
                                    else -> "Add friend"
                                })
                            }
                            if (person.canMessage) {
                                OutlinedButton(enabled = !busy, onClick = { onMessage(person.username) }) {
                                    Icon(Icons.Default.Message, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Message")
                                }
                            }
                        }
                        if (person.viewerSentRequest) Text("Friend request is pending.", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
                        else if (person.viewerReceivedRequest) Text("This person sent you a friend request.", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
                        TextButton(enabled = !busy, onClick = { reportMessage = null; reportOpen = true }) { Text("Report") }

                        if (marketplace.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            TabRow(selectedTabIndex = if (selectedTab == "Marketplace") 1 else 0, modifier = Modifier.fillMaxWidth()) {
                                Tab(selected = selectedTab == "Posts", onClick = { selectedTab = "Posts" }, text = { Text("Posts") })
                                Tab(selected = selectedTab == "Marketplace", onClick = { selectedTab = "Marketplace" }, text = { Text("Marketplace") })
                            }
                        } else {
                            Spacer(Modifier.height(10.dp))
                            Text("Posts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp))
                        }
                    }
                    if (selectedTab == "Marketplace" && marketplace.isNotEmpty()) {
                        item { ProfileMarketplaceGrid(listings = marketplace, onOpen = { selectedListing = it }) }
                    } else if (posts.isEmpty()) {
                        item { Text("No posts to show", Modifier.padding(top = 20.dp), color = FynxDesign.TextSecondary) }
                    } else {
                        item {
                            if (marketplace.isNotEmpty()) Text("Posts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp))
                            ProfilePostGrid(posts = posts, onOpenPost = { selectedPostIndex = it })
                        }
                    }
                }
            }
        }
    }

    selectedListing?.let { listing ->
        ProfileMarketplaceDetails(listing = listing, onClose = { selectedListing = null })
    }

    if (reportOpen && profile != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) reportOpen = false },
            title = { Text("Report profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = reportReason, onValueChange = { reportReason = it }, label = { Text("Reason") }, singleLine = true)
                    OutlinedTextField(value = reportDetails, onValueChange = { reportDetails = it }, label = { Text("Details (optional)") }, minLines = 3)
                    reportMessage?.let { message -> Text(message, color = FynxDesign.TextSecondary) }
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    FynxProfileRemoteClient.report(context, profile!!.username, reportReason, reportDetails)
                        .onSuccess { report -> reportMessage = "Report submitted (${report.status.lowercase()})." }
                        .onFailure { failure -> reportMessage = failure.message ?: "Report failed. Try again." }
                    busy = false
                }
            }) { Text("Submit") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { reportOpen = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProfileCount(label: String, value: Int) {
    Column(Modifier.widthIn(min = 72.dp, max = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(label, color = FynxDesign.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProfilePostGrid(
    posts: List<FynxProfileRemoteClient.ProfilePost>,
    onOpenPost: (Int) -> Unit
) {
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = gridState,
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 560.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
            ProfilePostTile(post = post, onClick = { onOpenPost(index) })
        }
    }
}

@Composable
private fun ProfilePostTile(post: FynxProfileRemoteClient.ProfilePost, onClick: () -> Unit) {
    val mediaUrl = post.mediaUrl ?: post.mediaId?.let { "/api/social/media/$it" }
    Card(Modifier.fillMaxWidth().aspectRatio(1f).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!mediaUrl.isNullOrBlank()) {
                FynxRemoteMedia(
                    mediaUrl = mediaUrl,
                    type = post.mediaType ?: "auto",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    post.text.ifBlank { "Post" }.take(80),
                    modifier = Modifier.padding(7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 6
                )
            }
        }
    }
}

@Composable
private fun ProfileMarketplaceGrid(
    listings: List<FynxMarketplaceClient.Listing>,
    onOpen: (FynxMarketplaceClient.Listing) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 620.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(listings, key = { it.id }) { listing ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(listing) }) {
                Column {
                    if (listing.mediaIds.isNotEmpty()) {
                        FynxRemoteMedia(
                            mediaUrl = FynxMarketplaceClient.mediaUrl(LocalContext.current, listing.mediaIds.first()),
                            type = "auto",
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ShoppingBag, contentDescription = "Marketplace product", modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(listing.title, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                        Text("${listing.currency} ${String.format(Locale.US, "%,.2f", listing.price)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        if (listing.storeName.isNotBlank()) Text(listing.storeName, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMarketplaceDetails(
    listing: FynxMarketplaceClient.Listing,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(listing.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (listing.mediaIds.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                    ) {
                        item {
                            FynxRemoteMedia(
                                mediaUrl = FynxMarketplaceClient.mediaUrl(LocalContext.current, listing.mediaIds.first()),
                                type = "auto",
                                modifier = Modifier.fillMaxWidth().height(220.dp)
                            )
                        }
                    }
                }
                Text("${listing.currency} ${String.format(Locale.US, "%,.2f", listing.price)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                if (listing.description.isNotBlank()) Text(listing.description)
                Text("Seller: ${listing.sellerDisplayName.ifBlank { listing.sellerUsername }}")
                if (listing.storeName.isNotBlank()) Text("Store: ${listing.storeName}")
                Text("${listing.quantity} available • ${listing.condition}")
                if (listing.location.isNotBlank()) Text("Location: ${listing.location}")
                if (listing.deliveryAvailable) Text("Delivery available${listing.deliveryFee?.let { " • ${listing.currency} ${String.format(Locale.US, "%,.2f", it)} fee" } ?: ""}")
                if (listing.pickupAvailable) Text("Pickup available")
                Text("🛡 FYNX protected payment", style = MaterialTheme.typography.labelLarge)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}

@Composable
private fun ProfilePostSwipeViewer(
    posts: List<FynxProfileRemoteClient.ProfilePost>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    var index by remember(posts, initialIndex) { mutableIntStateOf(initialIndex) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val post = posts[index]
    val mediaUrl = post.mediaUrl ?: post.mediaId?.let { "/api/social/media/$it" }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(posts, index) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> dragDistance += amount },
                    onDragEnd = {
                        when {
                            dragDistance < -80f && index < posts.lastIndex -> index += 1
                            dragDistance > 80f && index > 0 -> index -= 1
                        }
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f }
                )
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close post") }
                Column(Modifier.weight(1f)) {
                    Text("Post ${index + 1} of ${posts.size}", style = MaterialTheme.typography.titleMedium)
                    Text("Swipe left or right to browse", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                if (!mediaUrl.isNullOrBlank()) {
                    FynxRemoteMedia(
                        mediaUrl = mediaUrl,
                        type = post.mediaType ?: "auto",
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 520.dp)
                    )
                }
                if (post.text.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(post.text, style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "${post.likeCount} likes • ${post.commentCount} comments",
                    color = FynxDesign.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun RemoteProfilePhoto(mediaId: String?, name: String, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(mediaId) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(mediaId) {
        bitmap = if (mediaId != null) {
            val uri = FynxProductionMessaging.cacheRemoteMedia(context, mediaId, "/api/social/media/$mediaId").getOrNull()
            if (uri != null) withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } }.getOrNull() } else null
        } else null
    }
    val image = bitmap
    if (image != null) Image(image.asImageBitmap(), contentDescription = "Profile photo", modifier = modifier, contentScale = ContentScale.Crop)
    else FynxAvatar(name, modifier)
}
