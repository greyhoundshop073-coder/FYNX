package com.fynx.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.ImageView
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Keeps the existing Home experience intact while providing a real, large social post composer. */
@Composable
fun FynxHomeSocialHubPanel(
    currentUsername: String,
    initialCaption: String? = null,
    onCaptionConsumed: () -> Unit = {},
    cameraRequest: Int = 0,
    onCameraRequestConsumed: () -> Unit = {},
    onOpenChats: () -> Unit = {},
    onOpenStories: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenFindPeople: () -> Unit = {},
    onOpenAi: () -> Unit = {},
    onOpenAuthorProfile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuredPostVisibility = remember { FynxPreferencesStore.loadVisibility(context, "privacy_posts_visibility") }
    val postingAllowed = configuredPostVisibility != "Nobody"
    val defaultPostVisibility = if (configuredPostVisibility == "Everyone") FynxPostVisibility.PUBLIC else FynxPostVisibility.FRIENDS_ONLY
    var showComposer by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var showVoiceRecorder by remember { mutableStateOf(false) }
    var capturedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var capturedTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedVisualIndex by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(defaultPostVisibility) }
    var audience by remember { mutableStateOf(if (configuredPostVisibility == "Everyone") FynxPostAudience.EVERYONE else FynxPostAudience.FRIENDS) }
    var selectedAudienceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var textBackground by remember { mutableStateOf<FynxPostTextBackground?>(null) }
    var postLocation by remember { mutableStateOf<String?>(null) }
    var locationLoading by remember { mutableStateOf(false) }
    var selectedCatalogueMusic by remember { mutableStateOf<FynxMusicCatalogueTrack?>(null) }
    var musicPlaying by remember { mutableStateOf(false) }
    var showMusicPicker by remember { mutableStateOf(false) }
    var musicSearch by remember { mutableStateOf("") }
    var musicCatalogue by remember { mutableStateOf<List<FynxMusicCatalogueTrack>>(emptyList()) }
    var musicLoading by remember { mutableStateOf(false) }
    var selectedFeelingActivity by remember { mutableStateOf<FynxFeelingActivityOption?>(null) }
    var showFeelingActivityPicker by remember { mutableStateOf(false) }
    var feelingActivitySearch by remember { mutableStateOf("") }
    var showPeoplePicker by remember { mutableStateOf(false) }
    var audienceFriends by remember { mutableStateOf<List<FynxFriend>>(emptyList()) }
    var audienceLoading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var posting by remember { mutableStateOf(false) }
    var networkLevel by remember { mutableStateOf(FynxNetworkQuality.current(context)) }

    LaunchedEffect(Unit) {
        while (true) { networkLevel = FynxNetworkQuality.current(context); delay(5_000L) }
    }

    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(12)
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.take(4).forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            val selected = uris.distinct().take(4)
            val nextUris = (capturedUris.filterNot { it in selected } + selected).take(4)
            capturedUris = nextUris
            capturedTypes = nextUris.map { uri -> FynxMultiMediaPostClient.mediaKind(context, uri) }
            selectedVisualIndex = 0
            showComposer = true
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            locationLoading = true
            scope.launch {
                FynxPostLocationClient.currentPlace(context).onSuccess { postLocation = it; notice = null }.onFailure { notice = it.message ?: "Could not determine your location." }
                locationLoading = false
            }
        } else notice = "Location permission was not granted."
    }

    LaunchedEffect(cameraRequest) {
        if (cameraRequest > 0) {
            // Home camera is a fresh "What's on your mind?" entry point.
            // Clear any abandoned composer state before opening it so a previous
            // draft/media selection can never leak into a new camera session.
            if (!posting) {
                capturedUris = emptyList()
                capturedTypes = emptyList()
                selectedVisualIndex = 0
                text = ""
                textBackground = null
                postLocation = null
                selectedCatalogueMusic = null
                musicPlaying = false
                selectedFeelingActivity = null
                feelingActivitySearch = ""
                showFeelingActivityPicker = false
                selectedAudienceIds = emptySet()
                audience = if (configuredPostVisibility == "Everyone") FynxPostAudience.EVERYONE else FynxPostAudience.FRIENDS
                visibility = defaultPostVisibility
                notice = null
            }
            showComposer = true
            showCamera = false
            onCameraRequestConsumed()
        }
    }

    LaunchedEffect(showMusicPicker, musicSearch) {
        if (showMusicPicker) {
            musicLoading = true
            FynxMusicCatalogueClient.listPublished(context, musicSearch)
                .onSuccess { musicCatalogue = it }
                .onFailure { notice = it.message ?: "Music catalogue could not be loaded." }
            musicLoading = false
        }
    }

    LaunchedEffect(showPeoplePicker) {
        if (showPeoplePicker) {
            audienceLoading = true
            notice = null
            val result = withTimeoutOrNull(10_000L) {
                FynxPostAudienceClient.friends(context)
            }
            if (result == null) {
                notice = "People could not be loaded right now. Please try again."
            } else {
                result.onSuccess {
                    audienceFriends = it
                }.onFailure {
                    notice = it.message ?: "Could not load your friends."
                }
            }
            audienceLoading = false
        }
    }

    if (showMusicPicker) {
        AlertDialog(
            onDismissRequest = {
                if (!posting) {
                    showMusicPicker = false
                    musicSearch = ""
                }
            },
            title = { Text("FYNX Music") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = musicSearch,
                        onValueChange = { musicSearch = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search music") },
                        placeholder = { Text("Song or artist") }
                    )
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (musicLoading) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (musicCatalogue.isEmpty()) {
                            Text(
                                "No FYNX music is published yet. Music can only be added by FYNX administrators.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            musicCatalogue.forEach { track ->
                                TextButton(
                                    onClick = {
                                        selectedCatalogueMusic = track
                                        musicPlaying = false
                                        showMusicPicker = false
                                        musicSearch = ""
                                        notice = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.MusicNote, "Music", tint = MaterialTheme.colorScheme.primary)
                                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                            Text(track.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                            Text(track.artist.ifBlank { "FYNX" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                        Text("${track.durationMs.coerceAtLeast(0L) / 60000}:${((track.durationMs.coerceAtLeast(0L) / 1000L) % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMusicPicker = false; musicSearch = "" }) { Text("Close") }
            }
        )
    }

    if (showFeelingActivityPicker) {
        val filteredOptions = FynxFeelingActivityLibrary.options.filter {
            feelingActivitySearch.isBlank() ||
                it.label.contains(feelingActivitySearch.trim(), ignoreCase = true) ||
                it.type.contains(feelingActivitySearch.trim(), ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = {
                showFeelingActivityPicker = false
                feelingActivitySearch = ""
            },
            title = { Text("Feeling / Activity") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = feelingActivitySearch,
                        onValueChange = { feelingActivitySearch = it.take(50) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search") },
                        placeholder = { Text("Find a feeling or activity") }
                    )
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredOptions.forEach { option ->
                            TextButton(
                                onClick = {
                                    selectedFeelingActivity = option
                                    showFeelingActivityPicker = false
                                    feelingActivitySearch = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(option.icon, style = MaterialTheme.typography.titleMedium)
                                    Column(Modifier.weight(1f)) {
                                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            if (option.type == "FEELING") "Feeling" else "Activity",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        if (filteredOptions.isEmpty()) {
                            Text("No matching feeling or activity.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedFeelingActivity = null
                    showFeelingActivityPicker = false
                    feelingActivitySearch = ""
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFeelingActivityPicker = false
                    feelingActivitySearch = ""
                }) { Text("Cancel") }
            }
        )
    }

    selectedFeelingActivity?.let { option ->
        if (showComposer) {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(option.icon, style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            if (option.type == "FEELING") "Feeling ${option.label}" else "Activity: ${option.label}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Attached to this post",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { selectedFeelingActivity = null }, enabled = !posting) {
                        Icon(Icons.Default.Close, "Remove feeling or activity")
                    }
                }
            }
        }
    }

    LaunchedEffect(initialCaption) {
        if (!initialCaption.isNullOrBlank()) { text = initialCaption.trim().take(4000); capturedUris = emptyList(); capturedTypes = emptyList(); selectedVisualIndex = 0; notice = null; showComposer = true; onCaptionConsumed() }
    }


    fun recomputeTypes(nextUris: List<Uri> = capturedUris) {
        capturedTypes = nextUris.map { item ->
            when {
                context.contentResolver.getType(item)?.startsWith("video/") == true -> "video"
                context.contentResolver.getType(item)?.startsWith("audio/") == true -> "audio"
                else -> "image"
            }
        }
        val visualCount = capturedTypes.count { it == "image" || it == "video" }
        selectedVisualIndex = selectedVisualIndex.coerceIn(0, (visualCount - 1).coerceAtLeast(0))
    }

    fun removeCapturedUri(uri: Uri) {
        if (posting) return
        capturedUris = capturedUris.filterNot { it == uri }
        recomputeTypes()
    }

    fun clearComposer() {
        if (!posting) {
            showComposer = false
            capturedUris = emptyList()
            capturedTypes = emptyList()
            selectedVisualIndex = 0
            text = ""
            textBackground = null
            postLocation = null
            selectedCatalogueMusic = null
            musicPlaying = false
            selectedFeelingActivity = null
            feelingActivitySearch = ""
            showFeelingActivityPicker = false
            selectedAudienceIds = emptySet()
            audience = if (configuredPostVisibility == "Everyone") FynxPostAudience.EVERYONE else FynxPostAudience.FRIENDS
            visibility = defaultPostVisibility
            notice = null
        }
    }

    fun finishComposerAfterSuccess() {
        showComposer = false
        capturedUris = emptyList()
        capturedTypes = emptyList()
        selectedVisualIndex = 0
        text = ""
        textBackground = null
        postLocation = null
        selectedCatalogueMusic = null
        musicPlaying = false
        selectedFeelingActivity = null
        feelingActivitySearch = ""
        showFeelingActivityPicker = false
        selectedAudienceIds = emptySet()
        audience = if (configuredPostVisibility == "Everyone") FynxPostAudience.EVERYONE else FynxPostAudience.FRIENDS
        visibility = defaultPostVisibility
        notice = null
    }

    Box(Modifier.fillMaxSize()) {
        HomePanel(currentUsername = currentUsername, onOpenChats = onOpenChats, onOpenStories = onOpenStories, onOpenProfile = onOpenProfile, onOpenMarketplace = onOpenMarketplace, onOpenNotifications = onOpenNotifications, onOpenFindPeople = onOpenFindPeople, onOpenAi = onOpenAi, onCreatePost = { showComposer = true; notice = null }, onOpenAuthorProfile = onOpenAuthorProfile)
    }

    if (showComposer) {
        Dialog(
            onDismissRequest = { clearComposer() },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = !posting, dismissOnClickOutside = !posting)
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { clearComposer() }, enabled = !posting && !false) { Icon(Icons.Default.Close, "Close") }
                        Text("New post", style = MaterialTheme.typography.titleLarge)
                        Button(enabled = !posting && postingAllowed && networkLevel != FynxNetworkQuality.Level.OFFLINE && (text.isNotBlank() || capturedUris.isNotEmpty() || selectedCatalogueMusic != null), onClick = {
                            if (FynxNetworkQuality.current(context) == FynxNetworkQuality.Level.OFFLINE) { notice = "You are offline. Reconnect before publishing this post."; return@Button }
                            posting = true; notice = null
                            scope.launch { val result = withContext(Dispatchers.IO) { FynxMultiMediaPostClient.createPost(context, text, visibility, capturedUris, selectedAudienceIds.toList(), textBackground, postLocation, null, selectedCatalogueMusic, selectedFeelingActivity) }; result.onSuccess { finishComposerAfterSuccess() }.onFailure { notice = it.message ?: "Post could not be published." }; posting = false }
                        }) { Text(if (posting) "Publishing…" else "Post") }
                    }

                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (!postingAllowed) Text("Posting is disabled by your Posts privacy setting.", color = MaterialTheme.colorScheme.error)

                        Row(
                            Modifier.fillMaxWidth().padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FynxProfileImage(
                                currentUsername,
                                FynxPreferencesStore.loadProfilePhoto(context),
                                Modifier.size(44.dp).clip(CircleShape)
                            )
                            Text(currentUsername, style = MaterialTheme.typography.titleMedium)
                        }

                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ComposerQuickChip("Music", Icons.Default.MusicNote, enabled = !posting && postingAllowed) { showMusicPicker = true; notice = null }
                            ComposerQuickChip("People", Icons.Default.People, enabled = !posting && postingAllowed) { showPeoplePicker = true }
                            ComposerQuickChip("Location", Icons.Default.LocationOn, enabled = !posting && postingAllowed) {
                                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (fine || coarse) {
                                    locationLoading = true
                                    scope.launch {
                                        FynxPostLocationClient.currentPlace(context).onSuccess { postLocation = it; notice = null }.onFailure { notice = it.message ?: "Could not determine your location." }
                                        locationLoading = false
                                    }
                                } else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            }
                            ComposerQuickChip("Feeling/Activity", Icons.Default.SentimentSatisfied, enabled = !posting && postingAllowed) { showFeelingActivityPicker = true }
                        }

                        selectedCatalogueMusic?.let { music ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MusicNote, contentDescription = "Selected FYNX music", tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text(music.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                        Text(music.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                    val previewFile = remember(music.id) { mutableStateOf<java.io.File?>(null) }
                                    LaunchedEffect(music.id) {
                                        previewFile.value = withContext(Dispatchers.IO) {
                                            FynxMediaCache.getOrDownload(context, "/api/social/music/catalogue/" + music.id + "/media", "audio")
                                        }
                                    }
                                    val player = remember(music.id, previewFile.value) {
                                        previewFile.value?.let { file ->
                                            runCatching { android.media.MediaPlayer().apply { setDataSource(file.absolutePath); prepare() } }.getOrNull()
                                        }
                                    }
                                    DisposableEffect(player) { onDispose { player?.release() } }
                                    IconButton(onClick = {
                                        runCatching {
                                            if (player?.isPlaying == true) {
                                                player.pause()
                                                musicPlaying = false
                                            } else if (player != null) {
                                                if (player.currentPosition >= player.duration) player.seekTo(0)
                                                player.start()
                                                musicPlaying = true
                                            }
                                        }
                                    }, enabled = !posting && player != null) {
                                        Icon(if (musicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (musicPlaying) "Pause music" else "Preview music")
                                    }
                                    IconButton(onClick = { selectedCatalogueMusic = null; musicPlaying = false }, enabled = !posting) {
                                        Icon(Icons.Default.Close, "Remove music")
                                    }
                                }
                            }
                        }

                        if (postLocation != null || locationLoading) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.LocationOn, contentDescription = "Post location", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text(if (locationLoading) "Getting location…" else postLocation.orEmpty(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                if (!locationLoading) IconButton(onClick = { postLocation = null }, enabled = !posting) { Icon(Icons.Default.Close, "Remove location") }
                            }
                        }

                        BasicTextField(
                            value = text,
                            onValueChange = { value ->
                                text = value.take(4000)
                                if (text.isBlank()) textBackground = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp, max = 420.dp)
                                .padding(top = 8.dp)
                                .background(
                                    color = textBackground?.let { Color(it.color) } ?: MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .padding(horizontal = 2.dp, vertical = 8.dp),
                            enabled = !posting && postingAllowed,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textBackground?.let { Color(it.foregroundColor) } ?: MaterialTheme.colorScheme.onBackground),
                            decorationBox = { innerTextField ->
                                Box(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp)) {
                                    if (text.isEmpty()) {
                                        Text("What's on your mind?", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        if (text.isNotBlank()) {
                            Text(
                                "Text background",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FynxPostTextBackground.entries.forEach { option ->
                                    FilterChip(
                                        selected = textBackground == option,
                                        onClick = { textBackground = if (textBackground == option) null else option },
                                        label = { Text(option.label) },
                                        leadingIcon = {
                                            Box(
                                                Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(option.color))
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ComposerAction("Photo", Icons.Default.Image, { gallery.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                    )
                                ) }, !posting && postingAllowed, Modifier.weight(1f), compact = true)
                            ComposerAction("Video/Camera", Icons.Default.VideoLibrary, { showComposer = false; showCamera = true }, !posting && postingAllowed, Modifier.weight(1f), compact = true)
                            ComposerAction("Voice", Icons.Default.Mic, { showComposer = false; showVoiceRecorder = true }, !posting && postingAllowed, Modifier.weight(1f), compact = true)
                            ComposerAction("Marketplace", Icons.Default.Storefront, { showComposer = false; onOpenMarketplace() }, !posting && postingAllowed, Modifier.weight(1f), compact = true)
                        }

                        if (capturedUris.isNotEmpty()) {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val audioCount = capturedTypes.count { it == "audio" }
                                    val visualItems = capturedUris.mapIndexedNotNull { index, uri ->
                                        capturedTypes.getOrNull(index)?.takeIf { it == "image" || it == "video" }?.let { type -> Triple(index, uri, type) }
                                    }
                                    val selectedVisual = visualItems.getOrNull(selectedVisualIndex.coerceIn(0, (visualItems.size - 1).coerceAtLeast(0)))
                                    Text("Attached media", style = MaterialTheme.typography.titleMedium)
                                    Text("Up to 4 items per FYNX post.", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
                                    if (selectedVisual != null) {
                                        Box(Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 280.dp)) {
                                            if (selectedVisual.third == "video") {
                                                AndroidView(factory = { VideoView(it).apply { setVideoURI(selectedVisual.second); setOnPreparedListener { player -> player.isLooping = true; start() } } }, update = { view -> if (view.tag != selectedVisual.second.toString()) { view.tag = selectedVisual.second.toString(); view.setVideoURI(selectedVisual.second); view.start() } }, modifier = Modifier.fillMaxSize())
                                            } else {
                                                AndroidView(factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } }, update = { view -> view.setImageURI(selectedVisual.second) }, modifier = Modifier.fillMaxSize())
                                            }
                                        }
                                    }
                                    if (visualItems.size > 1) {
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            visualItems.forEachIndexed { visualIndex, (_, uri, type) ->
                                                Box(Modifier.size(76.dp).clickable(enabled = !posting && !false) { selectedVisualIndex = visualIndex }) {
                                                    Card(Modifier.fillMaxSize()) {
                                                        if (type == "video") {
                                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.VideoLibrary, "Video ${visualIndex + 1}", modifier = Modifier.size(28.dp)) }
                                                        } else {
                                                            AndroidView(factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } }, update = { view -> view.setImageURI(uri) }, modifier = Modifier.fillMaxSize())
                                                        }
                                                    }
                                                    IconButton(onClick = { removeCapturedUri(uri) }, enabled = !posting && !false, modifier = Modifier.align(Alignment.TopEnd).size(30.dp)) { Icon(Icons.Default.Close, "Remove media") }
                                                }
                                            }
                                        }
                                    } else if (visualItems.size == 1) {
                                        TextButton(onClick = { removeCapturedUri(visualItems.first().second) }, enabled = !posting && !false) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(4.dp)); Text("Remove photo/video") }
                                    }
                                    Text("${capturedUris.size} item${if (capturedUris.size == 1) "" else "s"} ready${if (audioCount > 0) " • $audioCount audio" else ""}", color = MaterialTheme.colorScheme.primary)
                                    Text("Preview before publishing. Select any thumbnail to inspect it, or remove media you don't want to post.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Text("Audience: ${audience.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (networkLevel == FynxNetworkQuality.Level.OFFLINE) Text("You are offline. Reconnect before publishing this post.", color = MaterialTheme.colorScheme.error)
                        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }


    if (showPeoplePicker) {
        AlertDialog(
            onDismissRequest = { if (!audienceLoading) showPeoplePicker = false },
            title = { Text("Who can see this post?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(FynxPostAudience.EVERYONE, FynxPostAudience.FRIENDS, FynxPostAudience.SELECTED, FynxPostAudience.ONLY_ME).forEach { option ->
                        val allowed = true
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !audienceLoading && allowed) {
                                audience = option
                                visibility = when (option) {
                                    FynxPostAudience.EVERYONE -> FynxPostVisibility.PUBLIC
                                    FynxPostAudience.FRIENDS -> FynxPostVisibility.FRIENDS_ONLY
                                    FynxPostAudience.SELECTED -> FynxPostVisibility.SELECTED_PEOPLE
                                    FynxPostAudience.ONLY_ME -> FynxPostVisibility.ONLY_ME
                                }
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = audience == option, onClick = null, enabled = allowed && !audienceLoading)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(option.label)
                                if (option == FynxPostAudience.SELECTED) Text("Choose specific accepted friends.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (audience == FynxPostAudience.SELECTED) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text("Choose friends", style = MaterialTheme.typography.titleSmall)
                        if (audienceLoading) CircularProgressIndicator(Modifier.size(22.dp))
                        else if (audienceFriends.isEmpty()) Text("You have no accepted friends to select yet.", style = MaterialTheme.typography.bodySmall)
                        else audienceFriends.forEach { friend ->
                            val checked = friend.id in selectedAudienceIds
                            Row(
                                Modifier.fillMaxWidth().clickable { selectedAudienceIds = if (checked) selectedAudienceIds - friend.id else selectedAudienceIds + friend.id }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { value -> selectedAudienceIds = if (value) selectedAudienceIds + friend.id else selectedAudienceIds - friend.id })
                                Spacer(Modifier.width(8.dp))
                                Column { Text(friend.displayName); Text("@" + friend.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (audience == FynxPostAudience.SELECTED && selectedAudienceIds.isEmpty()) notice = "Select at least one friend, or choose another audience."
                        else showPeoplePicker = false
                    },
                    enabled = !audienceLoading
                ) { Text("Done") }
            }
        )
    }

    if (showVoiceRecorder) {
        FynxVoicePostRecorder(
            onRecorded = { uri ->
                capturedUris = (capturedUris.filterNot { it == uri } + uri).take(4)
                recomputeTypes()
                showVoiceRecorder = false
                showComposer = true
            },
            onDismiss = { showVoiceRecorder = false; showComposer = true }
        )
    }

    if (showCamera) {
        Dialog(
            onDismissRequest = { showCamera = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    FynxCameraCapturePanel(
                        onCaptured = { uri, type ->
                            val nextUris = (capturedUris + uri).distinct().take(4)
                            capturedUris = nextUris
                            recomputeTypes(nextUris)
                            selectedVisualIndex = 0
                            showCamera = false
                            showComposer = true
                        },
                        onDismiss = { showCamera = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerQuickChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, maxLines = 1) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
    )
}

@Composable
private fun ComposerAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(if (compact) 62.dp else 76.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(if (compact) 24.dp else 28.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
