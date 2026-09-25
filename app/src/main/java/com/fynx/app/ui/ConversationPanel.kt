package com.fynx.app.ui

import android.Manifest
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationPanel(chat: ChatPreview, onBack: () -> Unit, onOpenProfile: (String) -> Unit = {}, onVoiceCall: () -> Unit = {}, onVideoCall: () -> Unit = {}) {
    val context = LocalContext.current
    val glassThemeId = FynxGlassThemeId.entries.firstOrNull { it.label == FynxConversationPreferences.chatWallpaper(context, chat.username) } ?: FynxGlassThemeId.PURE_BLACK
    val glassPalette = fynxGlassPalette(glassThemeId)
    val messageTextSizeSp = FynxConversationPreferences.chatTextSizeSp(context, chat.username)
    val bubbleTransparency = FynxConversationPreferences.chatBubbleTransparency(context, chat.username)
    val bubbleLighting = FynxConversationPreferences.chatBubbleLighting(context, chat.username)
    val bubbleGradient = FynxConversationPreferences.chatBubbleGradient(context, chat.username)
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var recipientProfile by remember(chat.username) { mutableStateOf<FynxProfileRemoteClient.Profile?>(null) }
    var remoteProfileLoaded by remember(chat.username) { mutableStateOf(false) }
    val resolvedAvatarUri = if (remoteProfileLoaded) {
        recipientProfile?.profilePhotoMediaId?.trim()?.takeIf { it.isNotBlank() }?.let { "/api/media/$it" }
    } else {
        chat.avatarUri
    }
    val fallbackMessage = remember(chat.lastMessage, resolvedAvatarUri) { chat.lastMessage.takeIf { it.isNotBlank() }?.let { ChatMessage(it, false, id = "initial", delivered = true, read = true, senderName = chat.name, senderUsername = chat.username, senderAvatarUri = resolvedAvatarUri) } }
    var text by remember(chat.username) { mutableStateOf("") }
    var messages by remember(chat.username) { mutableStateOf(FynxChatStore.load(context, chat.username, fallbackMessage)) }
    var replyToId by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var attachment by remember { mutableStateOf<Uri?>(null) }
    var attachmentType by remember { mutableStateOf<String?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isRecordingPaused by remember { mutableStateOf(false) }
    var recordingElapsed by remember { mutableLongStateOf(0L) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var playingVoiceId by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var menuMessageId by remember { mutableStateOf<String?>(null) }
    var showForwardDialog by remember { mutableStateOf(false) }
    var forwardMessageId by remember { mutableStateOf<String?>(null) }
    var forwardUsername by remember { mutableStateOf("") }
    var showGifts by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }
    var showChatSettings by remember { mutableStateOf(false) }
    var chatNotificationsEnabled by remember(chat.username) { mutableStateOf(FynxConversationPreferences.chatNotifications(context, chat.username)) }
    var showEmojiPanel by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var cameraInitialMode by remember { mutableStateOf(CameraMode.PHOTO) }
    var videoNoteMode by remember { mutableStateOf(false) }
    var reactionMessageId by remember { mutableStateOf<String?>(null) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var recipientUserId by remember { mutableStateOf<String?>(null) }
    var recipientCreatedAt by remember(chat.username) { mutableStateOf<String?>(null) }
    var isNewConversation by remember(chat.username) { mutableStateOf(false) }
    var isOnline by remember(chat.username) { mutableStateOf(chat.online) }
    var otherIsTyping by remember(chat.username) { mutableStateOf(false) }
    var networkError by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var typingSent by remember { mutableStateOf(false) }
    var stopRecordingAction: (() -> Unit)? = null

    val realtimeClient = remember(chat.username) {
        FynxRealtimeClient(
            context = context,
            onMessage = { remote ->
                val myId = currentUserId ?: return@FynxRealtimeClient
                if (remote.senderId != myId && remote.recipientId != myId) return@FynxRealtimeClient
                val converted = FynxProductionMessaging.toChatMessage(remote, myId).let { message ->
                    if (message.fromMe) message else message.copy(senderAvatarUri = resolvedAvatarUri)
                }
                isNewConversation = false
                messages = (messages.filterNot { it.id == remote.id } + converted).sortedBy { it.timestamp }
                if (remote.recipientId == myId) {
                    FynxInChatSound.play(context)
                    realtimeClient.acknowledgeMessage(remote.id)
                    scope.launch { FynxProductionMessaging.markRead(context, listOf(remote.id)) }
                }
            },
            onEvent = { event ->
                when (event) {
                    is FynxRealtimeClient.Event.MessageStatus -> {
                        messages = messages.map { message ->
                            if (message.id != event.messageId) message else when (event.status) {
                                FynxRealtimeClient.Status.READ -> message.copy(delivered = true, read = true)
                                FynxRealtimeClient.Status.DELIVERED -> message.copy(delivered = true)
                                FynxRealtimeClient.Status.SENT -> message
                            }
                        }
                    }
                    is FynxRealtimeClient.Event.Typing -> if (event.userId == recipientUserId) otherIsTyping = event.isTyping
                    is FynxRealtimeClient.Event.Presence -> if (event.userId == recipientUserId) isOnline = event.online
                }
            }
        )
    }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            attachment = null
            attachmentType = null
            return@rememberLauncherForActivityResult
        }
        val mimeType = context.contentResolver.getType(uri)?.lowercase()
        when {
            mimeType?.startsWith("image/") == true -> {
                attachment = uri
                attachmentType = "image"
                networkError = null
            }
            mimeType?.startsWith("video/") == true -> {
                attachment = uri
                attachmentType = "video"
                networkError = null
            }
            else -> {
                attachment = null
                attachmentType = null
                networkError = "Please choose an image or video."
            }
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && !isRecording) {
            val file = File(context.cacheDir, "voice_" + System.currentTimeMillis() + ".m4a")
            runCatching {
                createCompatibleMediaRecorder(context).apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(file.absolutePath)
                    setMaxDuration(120_000)
                    setOnInfoListener { _, what, _ ->
                        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopRecordingAction?.invoke()
                    }
                    prepare(); start()
                    recorder = this
                    recordingFile = file
                    recordingStartedAt = System.currentTimeMillis()
                    recordingElapsed = 0L
                    isRecordingPaused = false
                    isRecording = true
                }
            }.onFailure { networkError = it.message ?: "Unable to start recording" }
        }
    }

    LaunchedEffect(chat.username) {
        currentUserId = FynxBackendClient.currentUserId(context).getOrNull()
        val normalizedUsername = chat.username.removePrefix("@").trim()
        val searchedUser = FynxSocialClient.searchUsers(context, normalizedUsername)
            .getOrNull()?.firstOrNull { it.username.equals(normalizedUsername, true) }
        recipientUserId = searchedUser?.id
        recipientCreatedAt = searchedUser?.createdAt
        FynxProfileRemoteClient.get(context, normalizedUsername)
            .onSuccess { profile ->
                recipientProfile = profile
                remoteProfileLoaded = true
            }
        FynxProductionMessaging.history(context, normalizedUsername)
            .onSuccess { remoteMessages ->
                isNewConversation = remoteMessages.isEmpty()
                val myId = currentUserId
                if (myId != null) messages = remoteMessages.map { remote ->
                    FynxProductionMessaging.toChatMessage(remote, myId).let { message ->
                        if (message.fromMe) message else message.copy(senderAvatarUri = resolvedAvatarUri)
                    }
                }
                val unread = remoteMessages.filter { it.recipientId == myId && !it.read }.map { it.id }
                if (unread.isNotEmpty()) {
                    realtimeClient.sendRead(unread)
                    scope.launch { FynxProductionMessaging.markRead(context, unread) }
                }
            }
            .onFailure {
                isNewConversation = false
                networkError = it.message ?: "Unable to load messages"
            }
        realtimeClient.connect()
    }

    LaunchedEffect(text, recipientUserId) {
        val recipient = recipientUserId ?: return@LaunchedEffect
        if (text.isBlank()) {
            if (typingSent) { realtimeClient.sendTyping(recipient, false); typingSent = false }
            return@LaunchedEffect
        }
        if (!typingSent) { realtimeClient.sendTyping(recipient, true); typingSent = true }
        delay(1800L)
        if (typingSent) { realtimeClient.sendTyping(recipient, false); typingSent = false }
    }

    LaunchedEffect(isRecording, recordingStartedAt) {
        while (isRecording) {
            if (!isRecordingPaused) recordingElapsed = (System.currentTimeMillis() - recordingStartedAt).coerceAtLeast(0L)
            delay(200L)
        }
    }

    LaunchedEffect(messages) {
        FynxChatStore.save(context, chat.username, messages)
        val latest = messages.maxByOrNull { it.timestamp }
        if (latest != null) {
            val previewText = when {
                latest.voiceUri != null -> "Voice message"
                latest.attachmentUri != null && latest.text.isBlank() -> if (latest.attachmentType == "video") "Video" else "Photo"
                else -> latest.text
            }
            FynxChatStore.savePreview(context, chat.copy(lastMessage = previewText, time = formatChatTime(latest.timestamp)))
        }
    }

    DisposableEffect(realtimeClient) {
        onDispose {
            if (typingSent) realtimeClient.sendTyping(recipientUserId ?: "", false)
            realtimeClient.close()
            runCatching { recorder?.stop() }
            recorder?.release(); player?.release()
        }
    }

    fun startRecording() = microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    fun cancelRecording() {
        recorder?.release(); recorder = null; recordingFile?.delete(); recordingFile = null; recordingElapsed = 0L; isRecording = false
    }
    fun stopRecording() {
        val r = recorder ?: return
        val file = recordingFile
        val duration = (System.currentTimeMillis() - recordingStartedAt).coerceAtMost(120_000L)
        runCatching { r.stop() }; r.release(); recorder = null; isRecordingPaused = false; isRecording = false; recordingFile = null; recordingElapsed = 0L
        if (file != null && file.exists() && file.length() > 0L && duration >= 300L) {
            val pendingFile = file
            scope.launch {
                sending = true
                networkError = null
                FynxProductionMessaging.uploadMedia(context, Uri.fromFile(pendingFile), "audio/mp4")
                    .onSuccess { media ->
                        FynxProductionMessaging.sendText(context, chat.username.removePrefix("@"), "", mediaId = media.id, mediaType = "audio", voiceDurationMs = duration)
                            .onSuccess { remote ->
                                isNewConversation = false
                                currentUserId?.let { myId -> messages = (messages.filterNot { it.id == remote.id } + FynxProductionMessaging.toChatMessage(remote, myId)).sortedBy { it.timestamp } }
                                pendingFile.delete()
                            }.onFailure { networkError = it.message ?: "Voice message could not be sent" }
                    }.onFailure { networkError = it.message ?: "Voice recording upload failed" }
                sending = false
            }
        } else file?.delete()
    }
    stopRecordingAction = ::stopRecording

    fun playVoice(message: ChatMessage) {
        val voiceUrl = message.voiceUri ?: return
        val currentPlayer = player
        if (playingVoiceId == message.id && currentPlayer != null) {
            runCatching {
                if (currentPlayer.isPlaying) currentPlayer.pause() else currentPlayer.start()
            }.onFailure {
                playingVoiceId = null
                currentPlayer.release()
                player = null
            }
            return
        }
        player?.release()
        player = null
        playingVoiceId = null
        scope.launch {
            val localUri = if (voiceUrl.startsWith("http://") || voiceUrl.startsWith("https://") || voiceUrl.startsWith("/api/")) {
                val mediaId = message.mediaId ?: voiceUrl.substringAfterLast('/').takeIf { it.isNotBlank() }
                if (mediaId == null) {
                    networkError = "Voice message media is unavailable"
                    return@launch
                }
                FynxProductionMessaging.cacheRemoteMedia(context, mediaId, voiceUrl).getOrElse {
                    networkError = it.message ?: "Voice message could not be loaded"
                    return@launch
                }
            } else Uri.parse(voiceUrl)
            val preparedPlayer = runCatching {
                MediaPlayer().apply {
                    setDataSource(context, localUri)
                    setOnCompletionListener {
                        playingVoiceId = null
                        release()
                        player = null
                    }
                    setOnErrorListener { _, _, _ ->
                        playingVoiceId = null
                        release()
                        player = null
                        true
                    }
                    prepare()
                    start()
                }
            }.getOrElse {
                networkError = it.message ?: "Voice message could not be played"
                null
            }
            if (preparedPlayer != null) {
                player = preparedPlayer
                playingVoiceId = message.id
            }
        }
    }

    fun submitComposer() {
        val value = text.trim()
        if (value.isBlank() || sending) return
        sending = true
        scope.launch {
            if (editingId != null) {
                FynxProductionMessaging.editMessage(context, editingId!!, value)
                    .onSuccess { remote ->
                        currentUserId?.let { myId ->
                            messages = messages.map { existing ->
                                if (existing.id == remote.id) FynxProductionMessaging.toChatMessage(remote, myId) else existing
                            }
                        }
                        text = ""
                        editingId = null
                        replyToId = null
                    }
                    .onFailure { networkError = it.message ?: "Message could not be edited" }
            } else {
                val selectedAttachment = attachment
                val sendResult = if (selectedAttachment != null) {
                    val selectedType = attachmentType ?: "image"
                    FynxProductionMessaging.uploadMedia(context, selectedAttachment)
                        .mapCatching { media ->
                            FynxProductionMessaging.sendText(context, chat.username.removePrefix("@"), value, replyToId, media.id, selectedType, 0L).getOrThrow()
                        }
                } else {
                    FynxProductionMessaging.sendText(context, chat.username.removePrefix("@"), value, replyToId)
                }
                sendResult
                    .onSuccess { remote ->
                        currentUserId?.let { myId ->
                            messages = (messages.filterNot { it.id == remote.id } + FynxProductionMessaging.toChatMessage(remote, myId)).sortedBy { it.timestamp }
                        }
                        text = ""
                        editingId = null
                        replyToId = null
                        attachment = null
                        attachmentType = null
                    }
                    .onFailure { networkError = it.message ?: "Message could not be sent" }
            }
            sending = false
        }
    }

    val visibleMessages = if (searchQuery.isBlank()) messages else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    val pinnedMessage = messages.lastOrNull { it.pinned }
    val messageListState = rememberLazyListState()

    LaunchedEffect(visibleMessages.size, searchQuery) {
        if (visibleMessages.isEmpty()) return@LaunchedEffect
        delay(60L)
        if (searchQuery.isNotBlank()) {
            messageListState.scrollToItem(0)
        } else {
            val lastIndex = messageListState.layoutInfo.totalItemsCount - 1
            val lastVisible = messageListState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
            if (lastIndex >= 0 && (lastVisible < 0 || lastVisible >= lastIndex - 2)) {
                messageListState.animateScrollToItem(lastIndex)
            }
        }
    }

    if (showChatSettings) {
        FynxChatSettingsPanel(chatUsername = chat.username, onBack = { showChatSettings = false })
        return
    }

    FynxChatWallpaperBackground(
        modifier = Modifier.fillMaxSize(),
        wallpaperOverride = FynxConversationPreferences.chatWallpaper(context, chat.username), settingsKey = chat.username
    ) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp).background(MaterialTheme.colorScheme.surface).padding(horizontal = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { onOpenProfile(chat.username) }, modifier = Modifier.size(48.dp)) {
                    FynxAvatar(chat.name, resolvedAvatarUri, Modifier.size(40.dp))
                }
                Column(Modifier.weight(1f).padding(start = 4.dp).padding(end = 2.dp)) {
                    Text(chat.name, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    Text(
                        when { otherIsTyping -> "typing…"; isOnline -> "online"; else -> "last seen recently" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9AA4B4),
                        maxLines = 1
                    )
                }
                IconButton(onClick = onVoiceCall, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Call, "Voice call", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(23.dp)) }
                IconButton(onClick = onVideoCall, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Videocam, "Video call", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(23.dp)) }
                Box {
                    IconButton(onClick = { showChatMenu = true }, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(23.dp)) }
                    DropdownMenu(expanded = showChatMenu, onDismissRequest = { showChatMenu = false }) {
                        DropdownMenuItem(text = { Text("Chat settings") }, onClick = { showChatMenu = false; showChatSettings = true }, leadingIcon = { Icon(Icons.Default.Settings, null) })
                        DropdownMenuItem(text = { Text(if (chatNotificationsEnabled) "Mute notifications" else "Turn on notifications") }, onClick = { chatNotificationsEnabled = !chatNotificationsEnabled; FynxConversationPreferences.setChatNotifications(context, chat.username, chatNotificationsEnabled); showChatMenu = false }, leadingIcon = { Icon(Icons.Default.Notifications, null) })
                        DropdownMenuItem(text = { Text(if (searchOpen) "Close search" else "Search messages") }, onClick = { showChatMenu = false; searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }, leadingIcon = { Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, null) })
                        DropdownMenuItem(text = { Text("Take photo or video") }, onClick = { showChatMenu = false; showCamera = true }, leadingIcon = { Icon(Icons.Default.CameraAlt, null) })
                        DropdownMenuItem(text = { Text("Choose photo or video") }, onClick = { showChatMenu = false; mediaPicker.launch(arrayOf("image/*", "video/*")) }, leadingIcon = { Icon(Icons.Default.AttachFile, null) })
                        DropdownMenuItem(text = { Text("Send gift") }, onClick = { showChatMenu = false; showGifts = true }, leadingIcon = { Icon(Icons.Default.CardGiftcard, null) })
                    }
                }
            }
        }

        if (isNewConversation) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                color = Color(0xFF08090D),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 0.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = !sending,
                        onClick = {
                            scope.launch {
                                sending = true
                                FynxSocialClient.sendRequest(context, chat.username.removePrefix("@"))
                                    .onSuccess { networkError = "Contact request sent." }
                                    .onFailure { networkError = it.message ?: "Contact request could not be sent." }
                                sending = false
                            }
                        }
                    ) { Text("Add Contact", color = Color(0xFFB8C9FF), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        enabled = !sending,
                        onClick = {
                            scope.launch {
                                sending = true
                                FynxSocialClient.block(context, chat.username.removePrefix("@"))
                                    .onSuccess { networkError = "User blocked."; onBack() }
                                    .onFailure { networkError = it.message ?: "User could not be blocked." }
                                sending = false
                            }
                        }
                    ) { Text("Block User", color = Color(0xFFFF8B98), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Close, "Close", tint = Color(0xFF9FA3AF), modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        if (searchOpen) OutlinedTextField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true, placeholder = { Text("Search messages…") })
        pinnedMessage?.let { pinned ->
            Surface(onClick = { searchQuery = ""; val index = messages.indexOfFirst { it.id == pinned.id }; if (index >= 0) scope.launch { messageListState.animateScrollToItem(index) } }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), color = Color(0xFF090A0F), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PushPin, "Pinned message", tint = Color(0xFF8B7BE8), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) { Text("Pinned message", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9C90F0)); Text(pinned.text.ifBlank { "Media message" }, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE6E7EC)) }
                    Icon(Icons.Default.ChevronRight, "Open pinned message", tint = Color(0xFF8A8F9A))
                }
            }
        }
        networkError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) }

        LazyColumn(state = messageListState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            if (isNewConversation && searchQuery.isBlank()) {
                item(key = "fynx_first_contact_intro") {
                    FynxFirstContactIntro(recipientProfile, recipientCreatedAt, chat.name, chat.username, chat.avatarUri)
                }
            }
            if (visibleMessages.isEmpty() && searchQuery.isBlank()) {
                item(key = "fynx-empty-chat") {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f), contentColor = MaterialTheme.colorScheme.onSurfaceVariant, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                FynxAvatar(chat.name, resolvedAvatarUri, Modifier.size(64.dp))
                                Spacer(Modifier.height(14.dp))
                                Text("No messages here yet…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(5.dp))
                                Text("Start the conversation with " + chat.name + ".", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                items(visibleMessages, key = { it.id }) { message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                        Box {
                            val bubbleShape = RoundedCornerShape(16.dp)
                            val bubbleBrush = if (message.fromMe) {
                                Brush.horizontalGradient(listOf(glassPalette.outgoingStart.copy(alpha = bubbleTransparency), glassPalette.outgoingEnd.copy(alpha = bubbleGradient)))
                            } else {
                                Brush.linearGradient(listOf(glassPalette.incomingGlass.copy(alpha = bubbleTransparency), glassPalette.backgroundMid.copy(alpha = (0.55f + bubbleGradient * 0.4f).coerceIn(0.55f, 0.95f))))
                            }
                            Surface(
                                color = Color.Transparent,
                                contentColor = glassPalette.messageText,
                                shape = bubbleShape,
                                border = BorderStroke(0.7.dp, glassPalette.bubbleRim.copy(alpha = (bubbleLighting * bubbleTransparency).coerceIn(0f, 1f))),
                                tonalElevation = 0.dp,
                                modifier = Modifier
                                    .widthIn(max = 300.dp)
                                    .background(bubbleBrush, bubbleShape)
                                    .combinedClickable(onClick = { menuMessageId = message.id }, onLongClick = { menuMessageId = message.id })
                            ) {
                            Column(Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) {
                                if (message.pinned) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                                        Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = if (message.fromMe) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Pinned", style = MaterialTheme.typography.labelSmall, color = glassPalette.messageMuted)
                                    }
                                }
                                if (message.replyToId != null) {
                                    val replied = messages.firstOrNull { it.id == message.replyToId }
                                    Text("Reply: " + (replied?.text?.take(80) ?: "Original message"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 5.dp))
                                }
                                if (message.voiceUri != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { playVoice(message) }, modifier = Modifier.size(36.dp)) { Icon(if (playingVoiceId == message.id) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (playingVoiceId == message.id) "Pause voice message" else "Play voice message", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        Text("Voice message", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    if (message.attachmentUri != null) { if (message.attachmentType == "video") { Box(Modifier.size(170.dp).clip(androidx.compose.foundation.shape.CircleShape)) { FynxRemoteMedia(message.attachmentUri, "video", Modifier.fillMaxSize(), rounded = false, loopVideo = true); Surface(color = Color.Black.copy(alpha = 0.46f), shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) { Text("Video note", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) } } } else FynxRemoteMedia(mediaUrl = message.attachmentUri, type = message.attachmentType ?: "image", modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(bottom = if (message.text.isBlank()) 0.dp else 5.dp)) }
                                    if (message.text.isNotBlank()) SelectionContainer { Text(message.text, color = glassPalette.messageText, fontSize = messageTextSizeSp.sp) }
                                }
                                if (message.edited) Text("Edited", style = MaterialTheme.typography.labelSmall, color = glassPalette.messageMuted)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    Text(formatMessageClock(message.timestamp), style = MaterialTheme.typography.labelSmall, color = if (message.fromMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (message.fromMe) { Spacer(Modifier.width(4.dp)); Text(if (message.read) "✓✓" else if (message.delivered) "✓✓" else "✓", style = MaterialTheme.typography.labelSmall, color = glassPalette.messageMuted) }
                                }
                            }
                            }
                            DropdownMenu(expanded = menuMessageId == message.id, onDismissRequest = { menuMessageId = null }) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    listOf("❤️","😂","👍","🙏","🔥","😮","😢","👏").forEach { emoji ->
                                        TextButton(onClick = {
                                            menuMessageId = null
                                            scope.launch {
                                                FynxProductionMessaging.reactToMessage(context, message.id, if (message.reaction == emoji) null else emoji)
                                                    .onSuccess { remote -> currentUserId?.let { myId -> messages = messages.map { existing -> if (existing.id == remote.id) FynxProductionMessaging.toChatMessage(remote, myId) else existing } } }
                                                    .onFailure { networkError = it.message ?: "Reaction could not be saved" }
                                            }
                                        }, modifier = Modifier.size(34.dp), contentPadding = PaddingValues(0.dp)) { Text(emoji, style = MaterialTheme.typography.titleMedium) }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                DropdownMenuItem(text = { Text("Reply") }, onClick = { replyToId = message.id; menuMessageId = null }, leadingIcon = { Icon(Icons.Default.Reply, null) })
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    enabled = message.fromMe && message.text.isNotBlank() && !message.text.equals("Message deleted", true),
                                    onClick = {
                                        text = message.text
                                        editingId = message.id
                                        replyToId = null
                                        attachment = null
                                        attachmentType = null
                                        menuMessageId = null
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )

                                DropdownMenuItem(text = { Text("Copy") }, enabled = message.text.isNotBlank(), onClick = { clipboardManager.setText(AnnotatedString(message.text)); menuMessageId = null }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
                                DropdownMenuItem(
                                    text = { Text(if (message.pinned) "Unpin" else "Pin") },
                                    onClick = {
                                        menuMessageId = null
                                        scope.launch {
                                            FynxProductionMessaging.setPinned(context, message.id, !message.pinned)
                                                .onSuccess { remote ->
                                                    currentUserId?.let { myId ->
                                                        messages = messages.map { existing -> if (existing.id == remote.id) FynxProductionMessaging.toChatMessage(remote, myId) else existing }
                                                    }
                                                }
                                                .onFailure { networkError = it.message ?: "Message pin state could not be changed" }
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.PushPin, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Forward") },
                                    onClick = {
                                        menuMessageId = null
                                        forwardMessageId = message.id
                                        forwardUsername = ""
                                        showForwardDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Forward, null) }
                                )
                                DropdownMenuItem(text = { Text("Delete") }, onClick = {
                                    scope.launch {
                                        FynxProductionMessaging.deleteMessage(context, message.id)
                                            .onSuccess {
                                                messages = messages.map { existing -> if (existing.id == message.id) existing.copy(text = "Message deleted", attachmentUri = null, attachmentType = null, voiceUri = null, mediaId = null) else existing }
                                                menuMessageId = null
                                            }
                                            .onFailure { networkError = it.message ?: "Message could not be deleted" }
                                    }
                                }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                            }
                        }
                    }                }
            }
        }

        if (showEmojiPanel) {
            FynxChatEmojiPanel(onEmojiSelected = { emoji ->
                text += emoji
                showEmojiPanel = false
            })
        }

        if (replyToId != null || editingId != null || attachment != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                editingId != null -> "Editing message"
                                attachment != null -> "Attachment ready to send"
                                else -> "Replying to message"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (replyToId != null) {
                            val replied = messages.firstOrNull { it.id == replyToId }
                            Text(
                                replied?.text?.takeIf { it.isNotBlank() } ?: "Original message",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                    IconButton(onClick = { replyToId = null; editingId = null; attachment = null; attachmentType = null }) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                }
            }
        }

        if (isRecording) {
            Surface(color = Color(0xFF08090D), contentColor = Color.White, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, "Recording", tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Recording ${recordingElapsed / 1000L}s", Modifier.weight(1f))
                    TextButton(onClick = { cancelRecording() }) { Text("Cancel") }
                    Button(onClick = { stopRecording() }, enabled = !sending) { Text("Send") }
                }
            }
        } else Surface(color = Color(0xFF08090D), contentColor = Color.White, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(2.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF101217),
                        unfocusedContainerColor = Color(0xFF101217),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedPlaceholderColor = Color(0xFF707A8A),
                        unfocusedPlaceholderColor = Color(0xFF707A8A)
                    ),
                    placeholder = { Text(if (editingId == null) "Message..." else "Edit message...") },
                    leadingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showAttachmentSheet = true }) { Icon(Icons.Default.Add, "Attachments", Modifier.size(23.dp)) }
                            IconButton(onClick = { showEmojiPanel = !showEmojiPanel }) { Icon(Icons.Default.EmojiEmotions, "Emoji", Modifier.size(22.dp)) }
                        }
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { videoNoteMode = false; cameraInitialMode = CameraMode.PHOTO; showCamera = true }) { Icon(Icons.Default.CameraAlt, "Camera", Modifier.size(22.dp)) }
                        val voiceMode = text.isBlank() && attachment == null
                        Box(Modifier.size(46.dp).pointerInput(voiceMode, sending) {
                            if (!voiceMode || sending) return@pointerInput
                            detectTapGestures(onPress = {
                                startRecording()
                                tryAwaitRelease()
                                if (isRecording) stopRecording()
                            })
                        }, contentAlignment = Alignment.Center) {
                            Icon(if (voiceMode) Icons.Default.Mic else Icons.Default.Send, if (voiceMode) "Hold to record voice message" else "Send message", Modifier.size(22.dp))
                        }
                        }
                    },
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (text.isNotBlank() && !sending) submitComposer()
                    })
                )
                Spacer(Modifier.width(2.dp))
            }
        }
    }

    }

    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            containerColor = Color(0xFF08090D),
            tonalElevation = 0.dp
        ) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val items = listOf(
                    Triple("Camera", Icons.Default.CameraAlt) { showAttachmentSheet = false; cameraInitialMode = CameraMode.PHOTO; showCamera = true },
                    Triple("Gallery", Icons.Default.PhotoLibrary) { showAttachmentSheet = false; mediaPicker.launch(arrayOf("image/*", "video/*")) },
                    Triple("Document", Icons.Default.Description) { showAttachmentSheet = false; mediaPicker.launch(arrayOf("application/pdf", "text/plain", "application/zip", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation")) },
                    Triple("Video note", Icons.Default.Videocam) { showAttachmentSheet = false; videoNoteMode = true; cameraInitialMode = CameraMode.VIDEO; showCamera = true }
                )
                items.forEach { (label, icon, action) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = action,
                            modifier = Modifier.size(54.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = Color(0xFF252A34),
                            contentColor = Color(0xFFE1E4EA)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(icon, label, Modifier.size(24.dp))
                            }
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFFC4CBD1), maxLines = 1)
                    }
                }
            }
        }
    }

    if (showCamera) {
        Dialog(onDismissRequest = { showCamera = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().safeDrawingPadding()) { FynxCameraCapturePanel(initialMode = cameraInitialMode, videoNoteMode = videoNoteMode, onCaptured = { uri, type -> attachment = uri; attachmentType = type; videoNoteMode = false; showCamera = false }, onDismiss = { videoNoteMode = false; showCamera = false }) } }
        }
    }

    if (showForwardDialog) {
        AlertDialog(
            onDismissRequest = { if (!sending) showForwardDialog = false },
            title = { Text("Forward message") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text("Enter the FYNX username to receive this message.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = forwardUsername,
                        onValueChange = { forwardUsername = it.removePrefix("@").take(50) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Username") },
                        placeholder = { Text("@username") },
                        enabled = !sending
                    )
                }
            },
            dismissButton = { TextButton(onClick = { showForwardDialog = false }, enabled = !sending) { Text("Cancel") } },
            confirmButton = {
                TextButton(
                    onClick = {
                        val messageId = forwardMessageId
                        if (messageId == null || forwardUsername.isBlank() || sending) return@TextButton
                        sending = true
                        scope.launch {
                            FynxProductionMessaging.forwardMessage(context, messageId, forwardUsername)
                                .onSuccess {
                                    networkError = null
                                    showForwardDialog = false
                                    forwardMessageId = null
                                    forwardUsername = ""
                                }
                                .onFailure { networkError = it.message ?: "Message could not be forwarded" }
                            sending = false
                        }
                    },
                    enabled = forwardUsername.isNotBlank() && !sending
                ) { Text(if (sending) "Sending…" else "Forward") }
            }
        )
    }

    if (showGifts) {
        AlertDialog(onDismissRequest = { showGifts = false }, title = { Text("Send a gift") }, text = { Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)) { GiftsPanel(recipientName = chat.name, onGiftSelected = { showGifts = false }) } }, confirmButton = { TextButton(onClick = { showGifts = false }) { Text("Close") } })
    }
}

private fun formatMessageClock(timestamp: Long): String {
    if (timestamp <= 0L) return "Now"
    return java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.getDefault()))
}

@Composable
private fun FynxFirstContactIntro(profile: FynxProfileRemoteClient.Profile?, createdAt: String?, fallbackName: String, fallbackUsername: String, fallbackAvatarUri: String?) {
    val displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: fallbackName
    val username = profile?.username?.takeIf { it.isNotBlank() } ?: fallbackUsername.removePrefix("@")
    val country = profile?.country?.trim().orEmpty()
    val joined = createdAt?.let(::formatJoinedMonth)
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FynxAvatar(displayName, profile?.profilePhotoMediaId ?: fallbackAvatarUri, Modifier.size(54.dp))
        Spacer(Modifier.height(7.dp))
        Text(displayName, style = MaterialTheme.typography.titleSmall)
        Text("@$username", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (country.isNotBlank() || joined != null) {
            Spacer(Modifier.height(3.dp))
            Text(listOfNotNull(country.takeIf { it.isNotBlank() }, joined?.let { "Joined FYNX $it" }).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(Modifier.fillMaxWidth(0.72f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        Spacer(Modifier.height(9.dp))
        Text("You’re starting a new conversation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatJoinedMonth(createdAt: String): String? = runCatching {
    Instant.parse(createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
}.getOrNull()

private fun formatRecordingTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000L
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun formatChatTime(timestamp: Long): String {
    val elapsed = System.currentTimeMillis() - timestamp
    return when {
        elapsed < 60_000L -> "Now"
        elapsed < 3_600_000L -> "${elapsed / 60_000L}m"
        elapsed < 86_400_000L -> "${elapsed / 3_600_000L}h"
        else -> "${elapsed / 86_400_000L}d"
    }
}

@Suppress("DEPRECATION")
private fun createCompatibleMediaRecorder(context: android.content.Context): MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()