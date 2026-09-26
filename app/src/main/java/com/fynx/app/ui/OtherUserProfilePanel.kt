package com.fynx.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun OtherUserProfilePanel(
    username: String,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    onOpenStatus: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember(username) { mutableStateOf<FynxProfileRemoteClient.Profile?>(null) }
    var loading by remember(username) { mutableStateOf(true) }
    var error by remember(username) { mutableStateOf<String?>(null) }
    var following by remember(username) { mutableStateOf(false) }
    var hasActiveStatus by remember(username) { mutableStateOf(false) }
    var busy by remember(username) { mutableStateOf(false) }
    var reportOpen by remember(username) { mutableStateOf(false) }
    var reportReason by remember(username) { mutableStateOf("Safety or spam") }
    var reportDetails by remember(username) { mutableStateOf("") }
    var reportMessage by remember(username) { mutableStateOf<String?>(null) }
    var showProfilePhoto by remember(username) { mutableStateOf(false) }
    var profileMenuOpen by remember(username) { mutableStateOf(false) }
    var blockConfirmOpen by remember(username) { mutableStateOf(false) }

    fun loadProfile() {
        scope.launch {
            loading = true
            error = null
            FynxProfileRemoteClient.get(context, username)
                .onSuccess { loaded -> profile = loaded; following = loaded.followedByCurrentUser }
            FynxStatusClient.list(context).onSuccess { statuses -> hasActiveStatus = statuses.any { !it.isExpired() && it.ownerUsername.equals(username, true) } }
                .onFailure { error = it.message ?: "Unable to load this profile." }
            loading = false
        }
    }

    LaunchedEffect(username) { loadProfile() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text("Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box {
                IconButton(enabled = !busy, onClick = { profileMenuOpen = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                DropdownMenu(expanded = profileMenuOpen, onDismissRequest = { profileMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Report profile") },
                        onClick = { profileMenuOpen = false; reportMessage = null; reportOpen = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Block @${username.removePrefix("@")}") },
                        onClick = { profileMenuOpen = false; blockConfirmOpen = true }
                    )
                }
            }
        }
        when {
            loading && profile == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            profile == null -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(error ?: "User not found", color = FynxDesign.TextSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { loadProfile() }) { Text("Retry") }
            }
            else -> {
                val person = profile!!
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(86.dp)
                                        .clip(CircleShape)
                                        .then(if (hasActiveStatus) Modifier.background(Color(0xFF25D366), CircleShape).padding(3.dp) else Modifier)
                                        .clickable { if (person.profilePhotoMediaId != null) showProfilePhoto = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    FynxRemoteProfileAvatar(mediaId = person.profilePhotoMediaId, contentDescription = person.displayName, modifier = Modifier.size(80.dp).clip(CircleShape), ownerUsername = person.username)
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(person.displayName.ifBlank { person.username }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (person.verified) {
                                        Spacer(Modifier.width(5.dp))
                                        Box(Modifier.size(16.dp).background(Color(0xFF1877F2), CircleShape), contentAlignment = Alignment.Center) {
                                            Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text("@${person.username.removePrefix("@").trim()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                if (person.bio.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(person.bio, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                if (person.country.isNotBlank()) Text(person.country, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(14.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                    ProfileCount("Posts", person.postCount)
                                    person.followerCount?.let { ProfileCount("Followers", it) }
                                    person.followingCount?.let { ProfileCount("Following", it) }
                                }
                                Spacer(Modifier.height(16.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    
                                    Button(
                                        enabled = !busy,
                                        onClick = {
                                            scope.launch {
                                                busy = true
                                                val result = when {
                                                    person.viewerReceivedRequest && person.pendingRequestId != null -> FynxSocialClient.acceptRequest(context, person.pendingRequestId)
                                                    person.viewerSentRequest && person.pendingRequestId != null -> FynxSocialClient.cancelRequest(context, person.pendingRequestId)
                                                    person.relationship == "none" -> FynxSocialClient.sendRequest(context, person.username)
                                                    else -> FynxRemoteSocialClient.follow(context, person.username, following)
                                                }
                                                result.onSuccess { loadProfile() }.onFailure { error = it.message ?: "That action could not be completed." }
                                                busy = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(22.dp)
                                    ) {
                                        Text(
                                            when {
                                                person.relationship == "friends" -> if (following) "Following" else "Follow"
                                                person.viewerReceivedRequest -> "Accept request"
                                                person.viewerSentRequest -> "Request sent"
                                                else -> "Add friend"
                                            }
                                        )
                                    }
                                    if (person.canMessage) {
                                        OutlinedButton(enabled = !busy, onClick = { onMessage(person.username) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) {
                                            Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Message")
                                        }
                                    } else {
                                        Text("Messaging unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).padding(horizontal = 6.dp), textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        FynxProfileContentSection(username = person.username, onError = { error = it })
                    }
                }
            }
        }
    }

    if (showProfilePhoto && profile?.profilePhotoMediaId != null) {
        ProfilePhotoViewerDialog(profile!!.profilePhotoMediaId!!, profile!!.displayName) { showProfilePhoto = false }
    }

    if (blockConfirmOpen && profile != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) blockConfirmOpen = false },
            title = { Text("Block @${profile!!.username}") },
            text = { Text("They will no longer be able to view your profile or interact with you through FYNX. You can unblock them later from Friends & People.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        FynxSocialClient.block(context, profile!!.username)
                            .onSuccess { blockConfirmOpen = false; onBack() }
                            .onFailure { error = it.message ?: "This account could not be blocked." }
                        busy = false
                    }
                }) { Text("Block") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { blockConfirmOpen = false }) { Text("Cancel") } }
        )
    }

    if (reportOpen && profile != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) reportOpen = false },
            title = { Text("Report profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(reportReason, { reportReason = it }, label = { Text("Reason") }, singleLine = true)
                    OutlinedTextField(reportDetails, { reportDetails = it }, label = { Text("Details (optional)") }, minLines = 3)
                    reportMessage?.let { Text(it, color = FynxDesign.TextSecondary) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        FynxProfileRemoteClient.report(context, profile!!.username, reportReason, reportDetails)
                            .onSuccess { reportMessage = "Report submitted (" + it.status.lowercase() + ")." }
                            .onFailure { reportMessage = it.message ?: "Report failed. Try again." }
                        busy = false
                    }
                }) { Text("Submit") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { reportOpen = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfilePhotoViewerDialog(mediaId: String, name: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(mediaId) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(mediaId) {
        val uri = FynxProductionMessaging.cacheRemoteMedia(context, mediaId, "/api/social/media/$mediaId").getOrNull()
        bitmap = if (uri != null) withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } }.getOrNull() } else null
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = "Profile photo", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Fit)
            else FynxAvatar(name, Modifier.size(120.dp))
        }
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
