package com.fynx.app.ui

import android.Manifest
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ConversationPanel(chat: ChatPreview, onBack: () -> Unit, onOpenProfile: (String) -> Unit = {}, onVoiceCall: () -> Unit = {}, onVideoCall: () -> Unit = {}) {
    val context = LocalContext.current
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
    var showGifts by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }
    var showChatSettings by remember { mutableStateOf(false) }
    var showEmojiPanel by remember { mutableStateOf(false) }
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

    val visibleMessages = if (searchQuery.isBlank()) messages else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }

    if (showChatSettings) {
        FynxChatSettingsPanel(chatUsername = chat.username, onBack = { showChatSettings = false })
        return
    }

    FynxChatWallpaperBackground(
        modifier = Modifier.fillMaxSize(),
        wallpaperOverride = FynxConversationPreferences.chatWallpaper(context, chat.username)
    ) {
    Column(Modifier.fillMaxSize()) {
        Surface(color = Color(0xFF1E1E1E), contentColor = Color.White, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.size(24.dp)) }
                IconButton(onClick = { onOpenProfile(chat.username) }, modifier = Modifier.size(40.dp)) { FynxAvatar(chat.name, resolvedAvatarUri, Modifier.size(40.dp)) }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(chat.name, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White, maxLines = 1)
                    Text(when { otherIsTyping -> "typing…"; isOnline -> "online"; else -> "last seen recently" }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.62f), maxLines = 1)
                }
                IconButton(onClick = onVoiceCall, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Call, "Voice call", Modifier.size(24.dp)) }
                IconButton(onClick = onVideoCall, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Videocam, "Video call", Modifier.size(24.dp)) }
                IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }, modifier = Modifier.size(40.dp)) { Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, "Search", Modifier.size(24.dp)) }
                Box {
                    IconButton(onClick = { showChatMenu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "More", Modifier.size(24.dp)) }
                    DropdownMenu(expanded = showChatMenu, onDismissRequest = { showChatMenu = false }) {
                        DropdownMenuItem(text = { Text("Chat settings") }, onClick = { showChatMenu = false; showChatSettings = true }, leadingIcon = { Icon(Icons.Default.Settings, null) })
                        DropdownMenuItem(text = { Text("Send gift") }, onClick = { showChatMenu = false; showGifts = true }, leadingIcon = { Icon(Icons.Default.CardGiftcard, null) })
                    }
                }
            }
        }

        if (searchOpen) OutlinedTextField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth().padding(10.dp), singleLine = true, placeholder = { Text("Search messages…") })
        networkError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isNewConversation && searchQuery.isBlank()) {
                item(key = "fynx_first_contact_intro") {
                    FynxFirstContactIntro(recipientProfile, recipientCreatedAt, chat.name, chat.username, chat.avatarUri)
                }
            }
            if (visibleMessages.isEmpty() && searchQuery.isBlank()) {
                item(key = "fynx-empty-chat") {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Surface(color = Color(0xFF242424).copy(alpha = 0.94f), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                FynxAvatar(chat.name, resolvedAvatarUri, Modifier.size(64.dp))
                                Spacer(Modifier.height(14.dp))
                                Text("No messages here yet…", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                Spacer(Modifier.height(5.dp))
                                Text("Start the conversation with " + chat.name + ".", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.68f))
                            }
                        }
                    }
                }
            } else {
                items(visibleMessages, key = { it.id }) { message ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                        if (!message.fromMe) {
                            FynxAvatar(message.senderName ?: chat.name, message.senderAvatarUri ?: resolvedAvatarUri, Modifier.size(28.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Surface(color = if (message.fromMe) MaterialTheme.colorScheme.primary else Color(0xFF303030), contentColor = Color.White, shape = RoundedCornerShape(18.dp), tonalElevation = 0.dp, modifier = Modifier.widthIn(max = 320.dp).combinedClickable(onClick = {}, onLongClick = { menuMessageId = message.id })) {
                            Column(Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) {
                                if (!message.fromMe && !message.senderName.isNullOrBlank()) Text(message.senderName!!, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.72f))
                                if (message.replyToId != null) {
                                    val replied = messages.firstOrNull { it.id == message.replyToId }
                                    Surface(color = Color.Black.copy(alpha = 0.20f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
                                        Text(replied?.text?.take(80) ?: "Original message", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.82f), modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                                    }
                                }
                                if (message.voiceUri != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { playVoice(message) }, modifier = Modifier.size(36.dp)) { Text(if (playingVoiceId == message.id) "Ⅱ" else "▶", color = Color.White) }
                                        Text("Voice message", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f))
                                    }
                                } else {
                                    if (message.attachmentUri != null) FynxRemoteMedia(mediaUrl = message.attachmentUri, type = message.attachmentType ?: "image", modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(bottom = if (message.text.isBlank()) 0.dp else 5.dp))
                                    if (message.text.isNotBlank()) SelectionContainer { Text(message.text, color = Color.White) }
                                }
                                if (message.edited) Text("Edited", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    Text(formatMessageClock(message.timestamp), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f))
                                    if (message.fromMe) { Spacer(Modifier.width(4.dp)); Text(if (message.read) "✓✓" else if (message.delivered) "✓✓" else "✓", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.68f)) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding().imePadding()) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                if (attachment != null) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (attachmentType == "video") Icons.Default.Videocam else Icons.Default.Image, null)
                        Text(if (attachmentType == "video") "Video ready to send" else "Photo ready to send", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { attachment = null; attachmentType = null }) { Icon(Icons.Default.Close, "Remove attachment") }
                    }
                }
                if (replyToId != null) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text("Replying to message", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); IconButton(onClick = { replyToId = null }) { Icon(Icons.Default.Close, "Cancel reply") } }
                }
                if (isRecording) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))); Spacer(Modifier.width(8.dp)); Text(if (isRecordingPaused) "Paused" else "Recording", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.width(8.dp)); Text(formatRecordingTime(recordingElapsed), style = MaterialTheme.typography.labelLarge); Spacer(Modifier.width(10.dp))
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) { repeat(18) { index -> val height = 5.dp + (((recordingElapsed / 100L + index * 7L) % 20L).toInt()).dp; Box(Modifier.width(3.dp).height(height).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))) } }
                        }
                    }
                }
                if (showEmojiPanel && !isRecording) { FynxChatEmojiPanel(onEmojiSelected = { emoji -> text += emoji; showEmojiPanel = false }) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    IconButton(onClick = { showEmojiPanel = !showEmojiPanel }, enabled = !isRecording) { Text("☺", style = MaterialTheme.typography.titleLarge) }
                    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp), placeholder = { Text(if (editingId == null) "Message" else "Edit message…") }, maxLines = 5, enabled = !isRecording)
                    IconButton(onClick = { mediaPicker.launch(arrayOf("image/*", "video/*")) }, enabled = !isRecording) { Icon(Icons.Default.AttachFile, "Attach photo or video") }
                    if (isRecording) {
                        IconButton(onClick = { stopRecording() }) { Icon(Icons.Default.Stop, "Stop recording") }
                        IconButton(onClick = { cancelRecording() }) { Icon(Icons.Default.Close, "Cancel recording") }
                    } else if (text.isBlank() && attachment == null) {
                        IconButton(onClick = { startRecording() }) { Icon(Icons.Default.Mic, "Voice note") }
                    } else {
                        IconButton(onClick = {
                            val value = text.trim()
                            val recipient = recipientUserId
                            if (recipient == null) networkError = "Unable to find this FYNX user."
                            else if (value.isNotEmpty() || attachment != null) {
                                sending = true
                                scope.launch {
                                    val selectedAttachment = attachment
                                    val sendResult = if (selectedAttachment != null) {
                                        val selectedType = attachmentType ?: "image"
                                        FynxProductionMessaging.uploadMedia(context, selectedAttachment).mapCatching { media -> FynxProductionMessaging.sendText(context, chat.username.removePrefix("@"), value, replyToId, media.id, selectedType, 0L).getOrThrow() }
                                    } else FynxProductionMessaging.sendText(context, chat.username.removePrefix("@"), value, replyToId)
                                    sendResult.onSuccess { remote ->
                                        currentUserId?.let { myId -> messages = (messages.filterNot { it.id == remote.id } + FynxProductionMessaging.toChatMessage(remote, myId)).sortedBy { it.timestamp } }
                                        text = ""; editingId = null; replyToId = null; attachment = null; attachmentType = null
                                    }.onFailure { networkError = it.message ?: "Message could not be sent" }
                                    sending = false
                                }
                            }
                        }) { Icon(if (editingId == null) Icons.Default.Send else Icons.Default.Edit, if (editingId == null) "Send" else "Save") }
                    }
                }
            }
        }
    }

    }

    if (showCamera) {
        Dialog(onDismissRequest = { showCamera = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().safeDrawingPadding()) { FynxCameraCapturePanel(onCaptured = { uri, type -> attachment = uri; attachmentType = type; showCamera = false }) } }
        }
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
