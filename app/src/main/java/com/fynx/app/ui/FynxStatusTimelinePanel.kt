package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

@Composable
fun FynxStatusTimelinePanel(
    onCameraClick: () -> Unit = {},
    onCreateClick: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = remember(context) { FynxAuthStore.load(context) }
    val username = auth.username?.removePrefix("@").orEmpty()
    var statuses by remember { mutableStateOf<List<FynxStatus>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<FynxStatus?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            val statusResult = FynxStatusClient.list(context)
            statusResult.onSuccess { statuses = it.filterNot(FynxStatus::isExpired) }
            val failure = statusResult.exceptionOrNull()
            if (failure != null) error = failure.message ?: "Unable to load Status."
            loading = false
        }
    }

    LaunchedEffect(refreshKey) { refresh() }

    val visibleStatuses = statuses.filterNot(FynxStatus::isExpired)
    val latestByOwner = visibleStatuses.groupBy { it.ownerUsername }
        .mapNotNull { (_, values) -> values.maxByOrNull { it.createdAtMillis } }
        .sortedByDescending { it.createdAtMillis }
    val myStatus = latestByOwner.firstOrNull { it.ownerUsername.equals(username, true) }
    val recentUpdates = latestByOwner.filterNot { it.ownerUsername.equals(username, true) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Status", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onCameraClick) { Icon(Icons.Default.CameraAlt, "Camera") }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Status menu") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Refresh") }, onClick = { menuOpen = false; refreshKey++ })
                }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 18.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 18.dp)
        ) {
            item {
                StatusHomeRow(
                    ownerUsername = username,
                    ownerDisplayName = username,
                    status = myStatus,
                    isMe = true,
                    onClick = { if (myStatus != null) selected = myStatus else onCreateClick() },
                    showAdd = true
                )
            }
            item {
                Spacer(Modifier.height(22.dp))
                Text("Recent updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
            if (recentUpdates.isEmpty()) {
                item {
                    Text(
                        if (loading) "Loading recent updates…" else "No recent updates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(recentUpdates, key = { it.ownerUsername }) { status ->
                    StatusHomeRow(
                        ownerUsername = status.ownerUsername,
                        ownerDisplayName = status.ownerDisplayName.ifBlank { status.ownerUsername },
                        status = status,
                        isMe = false,
                        onClick = { selected = status }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }

    selected?.let { initial ->
        val ownerStatuses = visibleStatuses.filter { it.ownerUsername == initial.ownerUsername }.sortedBy { it.createdAtMillis }
        FynxStatusStoryViewer(
            ownerStatuses,
            ownerStatuses.indexOfFirst { it.id == initial.id }.coerceAtLeast(0),
            username,
            { selected = null },
            { selected = null; refreshKey++ }
        )
    }
}

@Composable
private fun StatusAvatar(
    ownerUsername: String,
    ownerDisplayName: String,
    showAdd: Boolean = false,
    status: FynxStatus? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cachedPhotoId = remember(ownerUsername) {
        if (status != null) {
            FynxProfileRemoteClient.cachedProfilePhotoId(context, status.ownerUsername)
        } else {
            FynxProfileRemoteClient.cachedProfilePhotoId(context, ownerUsername)
        }
    }
    var profilePhotoMediaId by remember(ownerUsername) { mutableStateOf(cachedPhotoId) }
    var remoteProfileLoaded by remember(ownerUsername) { mutableStateOf(false) }
    LaunchedEffect(ownerUsername) {
        remoteProfileLoaded = false
        if (status != null) {
            FynxProfileRemoteClient.get(context, status.ownerUsername)
                .onSuccess {
                    profilePhotoMediaId = it.profilePhotoMediaId
                    remoteProfileLoaded = true
                }
                .onFailure {
                    remoteProfileLoaded = false
                }
        } else {
            FynxProfileRemoteClient.get(context, ownerUsername)
                .onSuccess {
                    profilePhotoMediaId = it.profilePhotoMediaId
                    remoteProfileLoaded = true
                }
                .onFailure {
                    remoteProfileLoaded = false
                }
        }
    }
    val avatarId = if (remoteProfileLoaded) profilePhotoMediaId else cachedPhotoId
    Box(modifier.size(58.dp)) {
        Box(Modifier.fillMaxSize().border(2.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(3.dp)) {
            if (!avatarId.isNullOrBlank()) {
                FynxRemoteProfileAvatar(avatarId, ownerDisplayName, Modifier.fillMaxSize().clip(CircleShape))
            } else {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text(ownerDisplayName.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (showAdd) {
            Box(Modifier.size(22.dp).align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, "Add Status", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun StatusHomeRow(
    ownerUsername: String,
    ownerDisplayName: String,
    status: FynxStatus?,
    isMe: Boolean,
    onClick: () -> Unit,
    showAdd: Boolean = false
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusAvatar(ownerUsername, ownerDisplayName, showAdd, status)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (isMe) "My status" else ownerDisplayName, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                if (isMe && status == null) "Tap to add status update"
                else if (isMe) "Tap to view your latest update"
                else formatStatusTimestamp(status?.createdAtMillis ?: 0L),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private fun formatStatusTimestamp(timeMillis: Long): String {
    val now = java.util.Calendar.getInstance()
    val date = java.util.Calendar.getInstance().apply { timeInMillis = timeMillis }
    val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(timeMillis))
    return if (now.get(java.util.Calendar.YEAR) == date.get(java.util.Calendar.YEAR) && now.get(java.util.Calendar.DAY_OF_YEAR) == date.get(java.util.Calendar.DAY_OF_YEAR)) {
        "Today, " + time
    } else {
        java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(timeMillis))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FynxStatusStoryViewer(
    statuses: List<FynxStatus>,
    startIndex: Int,
    viewerUsername: String,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    if (statuses.isEmpty()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var index by remember(statuses, startIndex) { mutableIntStateOf(startIndex) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var interactions by remember(statuses, startIndex) { mutableStateOf(FynxStatusInteractions()) }
    var interactionError by remember(statuses, startIndex) { mutableStateOf<String?>(null) }
    var replyText by remember(statuses, startIndex) { mutableStateOf("") }
    var replying by remember { mutableStateOf(false) }
    var replyFocused by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    var showReplyEmojiPicker by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val status = statuses.getOrNull(index) ?: return
    BackHandler(onBack = onDismiss)
    var statusProgress by remember(status.id) { mutableFloatStateOf(0f) }

    fun refreshInteractions() {
        scope.launch {
            FynxStatusClient.interactions(context, status.id)
                .onSuccess { interactions = it; interactionError = null }
                .onFailure { interactionError = it.message }
        }
    }

    fun movePrevious() {
        if (index > 0) index-- else onDismiss()
    }

    fun moveNext() {
        if (index < statuses.lastIndex) index++ else onDismiss()
    }

    LaunchedEffect(status.id) {
        replyText = ""
        replyFocused = false
        statusProgress = 0f
        FynxStatusClient.markViewed(context, status.id)
        refreshInteractions()
    }

    LaunchedEffect(status.id, replyFocused) {
        val duration = statusViewerAutoAdvanceMs(status)
        if (duration <= 0L) return@LaunchedEffect
        while (statusProgress < 1f) {
            if (!replyFocused) statusProgress = (statusProgress + 50f / duration.toFloat()).coerceAtMost(1f)
            kotlinx.coroutines.delay(50L)
        }
        if (!replyFocused) moveNext()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                // Viewer media layer: Text owns the whole canvas; media preserves its natural aspect ratio.
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(status.id, index) {
                            var totalX = 0f
                            var totalY = 0f
                            detectDragGestures(
                                onDragStart = { totalX = 0f; totalY = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    totalX += dragAmount.x
                                    totalY += dragAmount.y
                                },
                                onDragEnd = {
                                    when {
                                        totalY > 140f && kotlin.math.abs(totalY) > kotlin.math.abs(totalX) -> onDismiss()
                                        totalX > 120f && kotlin.math.abs(totalX) > kotlin.math.abs(totalY) -> movePrevious()
                                        totalX < -120f && kotlin.math.abs(totalX) > kotlin.math.abs(totalY) -> moveNext()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when (status.type) {
                        FynxStatusType.TEXT -> StatusViewerText(status)
                        FynxStatusType.PHOTO -> status.contentUri?.let {
                            FynxRemoteMedia(
                                it,
                                "image",
                                Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                rounded = false
                            )
                        }
                        FynxStatusType.VIDEO -> status.contentUri?.let {
                            FynxRemoteMedia(
                                it,
                                "video",
                                Modifier.fillMaxSize(),
                                loopVideo = false,
                                onVideoCompleted = { moveNext() },
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                rounded = false
                            )
                        }
                        FynxStatusType.VOICE -> status.contentUri?.let {
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = Color.White.copy(alpha = 0.10f),
                                modifier = Modifier.padding(horizontal = 28.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    StatusAvatar(status.ownerUsername, status.ownerDisplayName, status = status)
                                    Text("Voice status", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                    FynxRemoteAudio(it, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }

                    // Tap zones for previous/next status without adding visible controls.
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(0.30f).fillMaxHeight().clickable(enabled = index > 0) { if (index > 0) index-- })
                        Spacer(Modifier.weight(0.40f).fillMaxHeight())
                        Box(Modifier.weight(0.30f).fillMaxHeight().clickable(enabled = index < statuses.lastIndex) { if (index < statuses.lastIndex) index++ })
                    }
                }

                if (status.type != FynxStatusType.TEXT && !status.text.isNullOrBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.62f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 18.dp, end = 18.dp, bottom = 154.dp)
                    ) {
                        Text(
                            status.text.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 4,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }

                // Top overlay stays readable over every status type.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                        }
                        StatusAvatar(
                            status.ownerUsername,
                            status.ownerDisplayName,
                            modifier = Modifier.size(38.dp),
                            status = status
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                status.ownerDisplayName.ifBlank { status.ownerUsername },
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                formatStatusTimestamp(status.createdAtMillis),
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, "Status menu", tint = Color.White)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        statuses.forEachIndexed { segmentIndex, _ ->
                            LinearProgressIndicator(
                                progress = {
                                    when {
                                        segmentIndex < index -> 1f
                                        segmentIndex == index -> statusProgress
                                        else -> 0f
                                    }
                                },
                                modifier = Modifier.weight(1f).height(3.dp),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.28f)
                            )
                        }
                    }
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            menuOpen = false
                            FynxShareActions.share(context, FynxShareActions.statusPayload(status))
                        }
                    )
                    if (status.ownerUsername.equals(viewerUsername, true)) {
                        DropdownMenuItem(
                            text = { Text(if (deleting) "Deleting…" else "Delete") },
                            enabled = !deleting,
                            onClick = {
                                menuOpen = false
                                deleting = true
                                deleteError = null
                                scope.launch {
                                    FynxStatusClient.delete(context, status.id)
                                        .onSuccess { onDeleted() }
                                        .onFailure {
                                            deleting = false
                                            deleteError = it.message ?: "Status deletion failed."
                                        }
                                }
                            }
                        )
                    }
                }

                // Bottom interaction bar: no counts; reply, reaction, share and send stay above system navigation.
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        status.musicCatalogueId?.let { musicId ->
                            Surface(
                                color = Color.White.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.MusicNote, "Status music", tint = Color.White)
                                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                        Text(status.musicTitle?.ifBlank { "FYNX Music" } ?: "FYNX Music", color = Color.White, maxLines = 1)
                                        Text(status.musicArtist?.ifBlank { "FYNX" } ?: "FYNX", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                    FynxRemoteAudio("/api/social/music/catalogue/" + musicId + "/media", Modifier.width(120.dp), status.musicDurationMs.coerceAtLeast(1_000L))
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Counts remain available to accessibility/interaction state without being rendered on the viewer bar.
                            Spacer(Modifier.size(0.dp).semantics {
                                contentDescription = "Status views ${interactions.viewCount}, likes ${interactions.likeCount}"
                            })
                            OutlinedTextField(
                                value = replyText,
                                onValueChange = { replyText = it.take(1000) },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { replyFocused = it.isFocused },
                                enabled = !replying,
                                singleLine = true,
                                shape = RoundedCornerShape(50),
                                placeholder = { Text("Reply to this Status…", color = Color.White.copy(alpha = 0.72f)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White.copy(alpha = 0.45f),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.30f),
                                    cursorColor = Color.White,
                                    focusedPlaceholderColor = Color.White.copy(alpha = 0.72f),
                                    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.72f)
                                ),
                                trailingIcon = {
                                    IconButton(
                                        onClick = { showReplyEmojiPicker = !showReplyEmojiPicker },
                                        enabled = !replying
                                    ) {
                                        Icon(Icons.Default.EmojiEmotions, "Add emoji", tint = Color.White)
                                    }
                                }
                            )
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        FynxStatusClient.toggleLike(context, status.id)
                                            .onSuccess { refreshInteractions() }
                                            .onFailure { interactionError = it.message }
                                    }
                                },
                                enabled = !replying
                            ) {
                                Icon(
                                    if (interactions.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    "Like Status (${interactions.likeCount})",
                                    tint = if (interactions.likedByMe) Color.Red else Color.White
                                )
                            }
                            IconButton(onClick = { showReactionPicker = true }, enabled = !replying) {
                                Icon(Icons.Default.EmojiEmotions, "React", tint = Color.White)
                            }
                            IconButton(
                                onClick = { FynxShareActions.share(context, FynxShareActions.statusPayload(status)) }
                            ) {
                                Icon(Icons.Default.Share, "Share", tint = Color.White)
                            }
                            IconButton(
                                enabled = !replying && replyText.trim().isNotEmpty(),
                                onClick = {
                                    val body = replyText.trim()
                                    if (body.isEmpty()) return@IconButton
                                    replying = true
                                    scope.launch {
                                        FynxStatusClient.reply(context, status.id, body)
                                            .onSuccess {
                                                replyText = ""
                                                showReplyEmojiPicker = false
                                                refreshInteractions()
                                            }
                                            .onFailure { interactionError = it.message }
                                        replying = false
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Send, "Send reply", tint = Color.White)
                            }
                        }

                        if (showReplyEmojiPicker && !replying) {
                            Surface(
                                color = Color(0xFF1F1F1F),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("😀", "😂", "😍", "🔥", "❤️", "👍", "🎉", "😮", "🙏", "👏").forEach { emoji ->
                                        TextButton(onClick = { replyText = (replyText + emoji).take(1000) }) { Text(emoji, fontSize = 22.sp) }
                                    }
                                }
                            }
                        }
                        interactionError?.let { Text(it, color = Color(0xFFFF8A80), style = MaterialTheme.typography.labelSmall) }
                        deleteError?.let { Text(it, color = Color(0xFFFF8A80), style = MaterialTheme.typography.labelSmall) }
                    }
                }

                if (showReactionPicker && !replying) {
                    ModalBottomSheet(
                        onDismissRequest = { showReactionPicker = false },
                        containerColor = Color(0xFF1F1F1F)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text("React to this Status", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            LazyRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                contentPadding = PaddingValues(bottom = 18.dp)
                            ) {
                                items(listOf("❤️", "😂", "😮", "😢", "👍", "👏", "🔥", "🎉")) { emoji ->
                                    TextButton(onClick = {
                                        showReactionPicker = false
                                        scope.launch {
                                            FynxStatusClient.react(context, status.id, emoji)
                                                .onSuccess { refreshInteractions() }
                                                .onFailure { interactionError = it.message }
                                        }
                                    }) { Text(emoji, fontSize = 28.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusViewerText(status: FynxStatus) {
    val family = when (status.textStyle.font) {
        FynxStatusTextFont.SERIF -> FontFamily.Serif
        FynxStatusTextFont.TYPEWRITER -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }
    val weight = if (status.textStyle.font == FynxStatusTextFont.BOLD) FontWeight.Bold else FontWeight.Normal
    val textAlign = when (status.textStyle.alignment) { 0 -> TextAlign.Start; 2 -> TextAlign.End; else -> TextAlign.Center }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(status.textStyle.backgroundColor)), contentAlignment = Alignment.Center) {
        val baseSize = (maxWidth.value * 0.085f).coerceIn(24f, 42f)
        val scale = when {
            status.text.orEmpty().length > 420 -> 0.72f
            status.text.orEmpty().length > 240 -> 0.84f
            else -> 1f
        }
        Text(
            status.text.orEmpty(),
            color = Color(status.textStyle.foregroundColor),
            fontFamily = family,
            fontWeight = weight,
            textAlign = textAlign,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = (baseSize * scale).sp, lineHeight = (baseSize * 1.18f * scale).sp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 20.dp)
        )
    }
}

private fun statusViewerAutoAdvanceMs(status: FynxStatus): Long {
    val base = when (status.type) {
        FynxStatusType.TEXT -> 5_000L
        FynxStatusType.PHOTO -> 5_000L
        FynxStatusType.VOICE -> status.voiceDurationMs.coerceIn(1_000L, FYNX_STATUS_MAX_VOICE_DURATION_MS)
        FynxStatusType.VIDEO -> 0L
    }
    return if (status.musicCatalogueId != null && status.musicDurationMs > 0L) {
        kotlin.math.max(base, status.musicDurationMs.coerceAtLeast(1_000L))
    } else base
}

private fun statusTypeLabel(type: FynxStatusType) = when (type) { FynxStatusType.TEXT -> "Text"; FynxStatusType.PHOTO -> "Photo"; FynxStatusType.VIDEO -> "Video"; FynxStatusType.VOICE -> "Voice" }

private fun statusTimeLeft(createdAt: Long, now: Long = System.currentTimeMillis()): String {
    val remaining = (createdAt + FYNX_STATUS_EXPIRY_MS - now).coerceAtLeast(0L)
    val h = remaining / 3_600_000L
    val m = (remaining / 60_000L) % 60L
    return if (h > 0) "${h}h ${m}m left" else "${m}m left"
}
