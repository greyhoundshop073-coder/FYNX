package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.EmojiEmotions
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
import kotlinx.coroutines.launch

@Composable
fun FynxStatusTimelinePanel() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = remember(context) { FynxAuthStore.load(context) }
    val username = auth.username?.removePrefix("@").orEmpty()
    var statuses by remember { mutableStateOf<List<FynxStatus>>(emptyList()) }
    var followingUsernames by remember { mutableStateOf<Set<String>>(emptySet()) }
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
            val followingResult = FynxProfileRemoteClient.following(context)
            statusResult.onSuccess { statuses = it.filterNot(FynxStatus::isExpired) }
            followingResult.onSuccess { followingUsernames = it.map { user -> user.username.removePrefix("@").trim().lowercase() }.toSet() }
            val failure = statusResult.exceptionOrNull() ?: followingResult.exceptionOrNull()
            if (failure != null) error = failure.message ?: "Unable to load Status."
            loading = false
        }
    }

    LaunchedEffect(refreshKey) { refresh() }

    val visibleStatuses = statuses.filter { status ->
        val owner = status.ownerUsername.removePrefix("@").trim().lowercase()
        owner == username.removePrefix("@").trim().lowercase() || owner in followingUsernames
    }
    val latestByOwner = visibleStatuses.groupBy { it.ownerUsername }
        .mapNotNull { (_, values) -> values.maxByOrNull { it.createdAtMillis } }
        .sortedByDescending { it.createdAtMillis }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Status", style = MaterialTheme.typography.headlineSmall)
                Text("Photos, videos, text and voice • 24 hours", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { refreshKey++ }) { Text("Refresh") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!loading && latestByOwner.isEmpty() && error == null) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(10.dp))
                    Text("No active Status yet. Tap + to share your first one.")
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(latestByOwner, key = { it.ownerUsername }) { status ->
                    StatusBubble(status, status.ownerUsername.equals(username, true)) { selected = status }
                }
            }
        }
        HorizontalDivider()
        Text("Recent Status", style = MaterialTheme.typography.titleMedium)
        Text("Tap a circle to open Status. Views, likes, reactions and replies are saved to FYNX.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (visibleStatuses.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visibleStatuses.take(12), key = { it.id }) { status ->
                    AssistChip(onClick = { selected = status }, label = { Text("${status.ownerDisplayName.ifBlank { status.ownerUsername }} • ${statusTypeLabel(status.type)}") })
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
private fun StatusBubble(status: FynxStatus, isMe: Boolean, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cachedPhotoId = remember(status.ownerUsername) { FynxProfileRemoteClient.cachedProfilePhotoId(context, status.ownerUsername) }
    var profilePhotoMediaId by remember(status.ownerUsername) { mutableStateOf(cachedPhotoId) }
    var remoteProfileLoaded by remember(status.ownerUsername) { mutableStateOf(false) }

    LaunchedEffect(status.ownerUsername) {
        FynxProfileRemoteClient.get(context, status.ownerUsername)
            .onSuccess {
                // Cache is only a cold-start placeholder. Once the server responds,
                // its profilePhotoMediaId is authoritative, including an explicit null.
                profilePhotoMediaId = it.profilePhotoMediaId
                remoteProfileLoaded = true
            }
            .onFailure {
                if (!remoteProfileLoaded) profilePhotoMediaId = cachedPhotoId
            }
    }

    Column(Modifier.width(74.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(66.dp).border(3.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(4.dp)) {
            if (!profilePhotoMediaId.isNullOrBlank()) {
                FynxRemoteProfileAvatar(profilePhotoMediaId, status.ownerDisplayName.ifBlank { status.ownerUsername }, Modifier.fillMaxSize().clip(CircleShape))
            } else {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text(status.ownerDisplayName.ifBlank { status.ownerUsername }.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(if (isMe) "My status" else status.ownerDisplayName.ifBlank { status.ownerUsername }, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(statusTypeLabel(status.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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
    val status = statuses.getOrNull(index) ?: return
    var statusProgress by remember(status.id) { mutableFloatStateOf(0f) }

    fun refreshInteractions() {
        scope.launch {
            FynxStatusClient.interactions(context, status.id)
                .onSuccess { interactions = it; interactionError = null }
                .onFailure { interactionError = it.message }
        }
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
            if (!replyFocused) {
                statusProgress = (statusProgress + 50f / duration.toFloat()).coerceAtMost(1f)
            }
            kotlinx.coroutines.delay(50L)
        }
        if (!replyFocused) {
            if (index < statuses.lastIndex) index++ else onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(status.ownerDisplayName.ifBlank { status.ownerUsername }, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("${statusTypeLabel(status.type)} • ${statusTimeLeft(status.createdAtMillis)}", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { FynxShareActions.share(context, FynxShareActions.statusPayload(status)) }) { Icon(Icons.Default.Share, "Share Status", tint = Color.White) }
                    if (status.ownerUsername.equals(viewerUsername, true)) {
                        IconButton(enabled = !deleting, onClick = {
                            deleting = true
                            deleteError = null
                            scope.launch {
                                FynxStatusClient.delete(context, status.id)
                                    .onSuccess { onDeleted() }
                                    .onFailure { deleting = false; deleteError = it.message ?: "Status deletion failed." }
                            }
                        }) { Icon(Icons.Default.Delete, "Delete Status", tint = Color.White) }
                    }
                    TextButton(onClick = onDismiss) { Text("Close", color = Color.White) }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
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

                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    when (status.type) {
                        FynxStatusType.TEXT -> StatusViewerText(status)
                        FynxStatusType.PHOTO -> status.contentUri?.let { FynxRemoteMedia(it, "image", Modifier.fillMaxSize()) }
                        FynxStatusType.VIDEO -> status.contentUri?.let { FynxRemoteMedia(it, "video", Modifier.fillMaxSize(), loopVideo = false, onVideoCompleted = { if (index < statuses.lastIndex) index++ else onDismiss() }) }
                        FynxStatusType.VOICE -> status.contentUri?.let { FynxRemoteAudio(it, Modifier.fillMaxWidth().padding(horizontal = 20.dp)) }
                    }
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(0.25f).fillMaxHeight().clickable(enabled = index > 0) { if (index > 0) index-- })
                        Spacer(Modifier.weight(0.50f).fillMaxHeight())
                        Box(Modifier.weight(0.25f).fillMaxHeight().clickable(enabled = index < statuses.lastIndex) { if (index < statuses.lastIndex) index++ })
                    }
                }

                Surface(color = Color.Black, modifier = Modifier.fillMaxWidth().imePadding()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("👁 ${interactions.viewCount}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = { scope.launch { FynxStatusClient.toggleLike(context, status.id).onSuccess { refreshInteractions() }.onFailure { interactionError = it.message } } }) { Text(if (interactions.likedByMe) "♥ ${interactions.likeCount}" else "♡ ${interactions.likeCount}", color = Color.White) }
                            Text("💬 ${interactions.replyCount}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                        var showReactionPicker by remember(status.id) { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                OutlinedButton(onClick = { showReactionPicker = true }, enabled = !replying) {
                                    Icon(Icons.Default.EmojiEmotions, "React")
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (interactions.myReaction.isNullOrBlank()) "React" else interactions.myReaction!!)
                                }
                                DropdownMenu(expanded = showReactionPicker, onDismissRequest = { showReactionPicker = false }) {
                                    listOf("❤️", "😂", "😮", "😢", "👍", "👏", "🔥", "🎉").forEach { emoji ->
                                        DropdownMenuItem(
                                            text = { Text(emoji, fontSize = 22.sp) },
                                            onClick = {
                                                showReactionPicker = false
                                                scope.launch {
                                                    FynxStatusClient.react(context, status.id, emoji)
                                                        .onSuccess { refreshInteractions() }
                                                        .onFailure { interactionError = it.message }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            Text("Viewers ${interactions.viewCount}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.weight(1f))
                            Text("Replies ${interactions.replyCount}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = replyText,
                                onValueChange = { replyText = it.take(1000) },
                                modifier = Modifier.weight(1f).onFocusChanged { replyFocused = it.isFocused },
                                enabled = !replying,
                                singleLine = true,
                                placeholder = { Text("Reply to this Status…") },
                                trailingIcon = null
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                enabled = !replying && replyText.trim().isNotEmpty(),
                                onClick = {
                                    val body = replyText.trim()
                                    if (body.isEmpty()) return@IconButton
                                    replying = true
                                    scope.launch {
                                        FynxStatusClient.reply(context, status.id, body)
                                            .onSuccess { replyText = ""; refreshInteractions() }
                                            .onFailure { interactionError = it.message }
                                        replying = false
                                    }
                                }
                            ) {
                                Surface(shape = CircleShape, color = if (!replying && replyText.trim().isNotEmpty()) Color(0xFF1976D2) else Color.White.copy(alpha = .18f), modifier = Modifier.size(42.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Send, "Send reply", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                        interactionError?.let { Text(it, color = Color(0xFFFF8A80), style = MaterialTheme.typography.labelSmall) }
                        deleteError?.let { Text(it, color = Color(0xFFFF8A80), style = MaterialTheme.typography.labelSmall) }
                    }
                }

                if (statuses.size > 1) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(enabled = index > 0, onClick = { index-- }) { Icon(Icons.Default.ArrowBack, "Previous Status", tint = Color.White) }
                        Text("${index + 1} / ${statuses.size}", color = Color.White)
                        IconButton(enabled = index < statuses.lastIndex, onClick = { index++ }) { Icon(Icons.Default.ArrowForward, "Next Status", tint = Color.White) }
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
    Box(Modifier.fillMaxSize().background(Color(status.textStyle.backgroundColor)), contentAlignment = Alignment.Center) {
        Text(status.text.orEmpty(), color = Color(status.textStyle.foregroundColor), fontFamily = family, fontWeight = weight, textAlign = textAlign, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, lineHeight = 38.sp), modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 20.dp))
    }
}

private fun statusViewerAutoAdvanceMs(status: FynxStatus): Long = when (status.type) {
    FynxStatusType.TEXT -> 5_000L
    FynxStatusType.PHOTO -> 5_000L
    FynxStatusType.VOICE -> status.voiceDurationMs.coerceIn(1_000L, FYNX_STATUS_MAX_VOICE_DURATION_MS)
    FynxStatusType.VIDEO -> 0L
}

private fun statusTypeLabel(type: FynxStatusType) = when (type) { FynxStatusType.TEXT -> "Text"; FynxStatusType.PHOTO -> "Photo"; FynxStatusType.VIDEO -> "Video"; FynxStatusType.VOICE -> "Voice" }

private fun statusTimeLeft(createdAt: Long, now: Long = System.currentTimeMillis()): String {
    val remaining = (createdAt + FYNX_STATUS_EXPIRY_MS - now).coerceAtLeast(0L)
    val h = remaining / 3_600_000L
    val m = (remaining / 60_000L) % 60L
    return if (h > 0) "${h}h ${m}m left" else "${m}m left"
}
