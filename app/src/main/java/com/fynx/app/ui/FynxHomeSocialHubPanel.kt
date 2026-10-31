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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.unit.sp
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
    networkReady: Boolean? = null,
    initialCaption: String? = null,
    onCaptionConsumed: () -> Unit = {},
    cameraRequest: Int = 0,
    onCameraRequestConsumed: () -> Unit = {},
    onOpenChats: () -> Unit = {},
    onOpenStories: () -> Unit = {},
    onOpenStatusOwner: (String) -> Unit = {},
    onCreateStatus: () -> Unit = {},
    onOpenStatusWithMedia: (Uri, String) -> Unit = { _, _ -> },
    onOpenProfile: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenFindPeople: () -> Unit = {},
    onOpenAi: () -> Unit = {},
    onOpenAuthorProfile: (String) -> Unit = {},
    onOpenFastCamera: () -> Unit = {},
    initialPostId: String? = null,
    initialCommentId: String? = null,
    onInitialPostConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuredPostVisibility = remember { FynxPreferencesStore.loadVisibility(context, "privacy_posts_visibility") }
    val postingAllowed = configuredPostVisibility != "Nobody"
    val defaultPostVisibility = if (configuredPostVisibility == "Everyone") FynxPostVisibility.PUBLIC else FynxPostVisibility.FRIENDS_ONLY
    var showComposer by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var showFastCamera by remember { mutableStateOf(false) }
    // Tracks whether the camera was opened from an existing composer.
    // Home's header camera starts a fresh camera session; the composer camera
    // returns to the existing draft when dismissed without capturing media.
    var cameraOpenedFromComposer by remember { mutableStateOf(false) }
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
    var musicPreviewId by remember { mutableStateOf<Long?>(null) }
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
    var showComposerPreview by remember { mutableStateOf(false) }
    var showDraftsDialog by remember { mutableStateOf(false) }
    var savedPostDrafts by remember { mutableStateOf<List<FynxLocalPostDraft>>(emptyList()) }
    var editingDraftId by remember { mutableStateOf<String?>(null) }
    var draftSaving by remember { mutableStateOf(false) }
    var draftNotice by remember { mutableStateOf<String?>(null) }
    var showPostLinkDialog by remember { mutableStateOf(false) }
    var postLinkInput by remember { mutableStateOf("") }
    var postLinkError by remember { mutableStateOf<String?>(null) }
    var showDisclosureInfo by remember { mutableStateOf(false) }
    var showCoverInfo by remember { mutableStateOf(false) }
    var showMorePostOptions by remember { mutableStateOf(false) }
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
            // Home camera is a fresh "Add description..." entry point.
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
            cameraOpenedFromComposer = false
            // The Home header camera is the social creation entry point:
            // open the full "Add description..." composer. The separate
            // camera button in the Home content remains the intentional
            // fast/direct camera path.
            showCamera = false
            showFastCamera = false
            showComposer = true
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
                                Card(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.MusicNote, "Music", tint = MaterialTheme.colorScheme.primary)
                                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                            Text(track.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                            Text(track.artist.ifBlank { "FYNX" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                            Text(
                                                "${track.durationMs.coerceAtLeast(0L) / 60000}:${((track.durationMs.coerceAtLeast(0L) / 1000L) % 60).toString().padStart(2, '0')}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (musicPreviewId == track.id) {
                                            FynxRemoteAudio(
                                                mediaUrl = "/api/social/music/catalogue/" + track.id + "/media",
                                                modifier = Modifier.width(108.dp),
                                                maxDurationMs = track.durationMs.coerceAtLeast(1_000L)
                                            )
                                            TextButton(onClick = { musicPreviewId = null }) { Text("Stop") }
                                        } else {
                                            TextButton(onClick = { musicPreviewId = track.id }) { Text("Preview") }
                                        }
                                        Button(
                                            onClick = {
                                                selectedCatalogueMusic = track
                                                musicPlaying = false
                                                musicPreviewId = null
                                                showMusicPicker = false
                                                musicSearch = ""
                                                notice = null
                                            }
                                        ) { Text("Add") }
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
        // Use the same MIME/extension resolver as the real upload client.
        // Camera and voice captures are file:// URIs, where ContentResolver.getType()
        // may be null; falling back to "image" would make a captured video/audio
        // appear as the wrong media type in the composer.
        capturedTypes = nextUris.map { FynxMultiMediaPostClient.mediaKind(context, it) }
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
            showComposerPreview = false
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
            editingDraftId = null
        }
    }

    fun finishComposerAfterSuccess() {
        showComposer = false
        showComposerPreview = false
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
        editingDraftId = null
    }

    fun restoreSavedPostDraft(draft: FynxLocalPostDraft) {
        text = draft.caption
        visibility = runCatching { FynxPostVisibility.valueOf(draft.visibility) }.getOrDefault(FynxPostVisibility.FRIENDS_ONLY)
        audience = runCatching { FynxPostAudience.valueOf(draft.audience) }.getOrDefault(FynxPostAudience.FRIENDS)
        selectedAudienceIds = draft.selectedAudienceIds.toSet()
        postLocation = draft.location
        textBackground = FynxPostTextBackground.entries.firstOrNull { it.key == draft.textBackgroundKey }
        capturedUris = draft.mediaPaths.map { Uri.fromFile(java.io.File(it)) }
        capturedTypes = draft.mediaTypes
        selectedVisualIndex = 0
        selectedCatalogueMusic = if (draft.musicId != null && draft.musicMediaId != null) {
            FynxMusicCatalogueTrack(
                id = draft.musicId,
                mediaId = draft.musicMediaId,
                title = draft.musicTitle.orEmpty(),
                artist = draft.musicArtist.orEmpty(),
                durationMs = draft.musicDurationMs
            )
        } else null
        selectedFeelingActivity = null
        editingDraftId = draft.id
        notice = null
        showComposer = true
        showDraftsDialog = false
    }

    fun saveCurrentPostDraft() {
        if (posting || draftSaving) return
        val media = capturedUris.mapIndexed { index, uri ->
            uri to capturedTypes.getOrElse(index) { FynxMultiMediaPostClient.mediaKind(context, uri) }
        }
        draftSaving = true
        draftNotice = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                FynxLocalPostDraftStore.save(
                    context = context,
                    existingId = editingDraftId,
                    caption = text,
                    visibility = visibility,
                    audience = audience,
                    selectedAudienceIds = selectedAudienceIds,
                    location = postLocation,
                    textBackground = textBackground,
                    media = media,
                    music = selectedCatalogueMusic
                )
            }
            result.onSuccess { draft ->
                editingDraftId = draft.id
                savedPostDrafts = FynxLocalPostDraftStore.list(context)
                draftNotice = "Draft saved on this device."
            }.onFailure { failure ->
                draftNotice = failure.message ?: "Draft could not be saved."
            }
            draftSaving = false
        }
    }

    fun requestComposerLocation() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            locationLoading = true
            scope.launch {
                FynxPostLocationClient.currentPlace(context)
                    .onSuccess { postLocation = it; notice = null }
                    .onFailure { notice = it.message ?: "Could not determine your location." }
                locationLoading = false
            }
        } else {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    fun sharePostCaption() {
        val payload = text.trim().ifBlank {
            if (capturedUris.isNotEmpty()) "A photo or video from FYNX." else ""
        }
        if (payload.isBlank()) {
            notice = "Add a caption before sharing."
            return
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "FYNX post")
            putExtra(Intent.EXTRA_TEXT, payload)
        }
        runCatching { context.startActivity(Intent.createChooser(sendIntent, "Share to")) }
            .onFailure { notice = "No sharing app is available." }
    }

    Box(Modifier.fillMaxSize()) {
        HomePanel(
            currentUsername = currentUsername,
            networkReady = networkReady,
            initialPostId = initialPostId,
            initialCommentId = initialCommentId,
            onInitialPostConsumed = onInitialPostConsumed,
            onOpenChats = onOpenChats,
            onOpenStories = onOpenStories,
            onOpenStatusOwner = onOpenStatusOwner,
            onCreateStatus = onCreateStatus,
            onOpenProfile = onOpenProfile,
            onOpenMarketplace = onOpenMarketplace,
            onOpenNotifications = onOpenNotifications,
            onOpenFindPeople = onOpenFindPeople,
            onOpenAi = onOpenAi,
            onOpenCamera = {
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
                    showFeelingActivityPicker = false
                    selectedAudienceIds = emptySet()
                    audience = if (configuredPostVisibility == "Everyone") FynxPostAudience.EVERYONE else FynxPostAudience.FRIENDS
                    visibility = defaultPostVisibility
                    notice = null
                }
                cameraOpenedFromComposer = false
                showComposer = false
                showCamera = true
            },
            onCreatePost = { showComposer = true; notice = null },
            onOpenFastCamera = {
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
                    showFeelingActivityPicker = false
                    selectedAudienceIds = emptySet()
                    audience = if (configuredPostVisibility == "Everyone") FynxPostAudience.EVERYONE else FynxPostAudience.FRIENDS
                    visibility = defaultPostVisibility
                    notice = null
                }
                cameraOpenedFromComposer = false
                showComposer = false
                showFastCamera = true
            },
            onOpenAuthorProfile = onOpenAuthorProfile
        )
    }

    if (showComposer) {
        Dialog(
            onDismissRequest = { clearComposer() },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = !posting, dismissOnClickOutside = !posting)
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { clearComposer() }, enabled = !posting) { Icon(Icons.Default.ArrowBack, "Back") }
                        Text("New post", style = MaterialTheme.typography.titleLarge)

                    }

                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (!postingAllowed) Text("Posting is disabled by your Posts privacy setting.", color = MaterialTheme.colorScheme.error)

                        val composerVisualItems = capturedUris.mapIndexedNotNull { index, uri ->
                            capturedTypes.getOrNull(index)?.takeIf { it == "image" || it == "video" }?.let { type -> Triple(index, uri, type) }
                        }
                        val selectedComposerVisual = composerVisualItems.getOrNull(
                            selectedVisualIndex.coerceIn(0, (composerVisualItems.size - 1).coerceAtLeast(0))
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                        BasicTextField(
                            value = text,
                            onValueChange = { value ->
                                text = value.take(4000)
                                if (text.isBlank()) textBackground = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 190.dp, max = 420.dp)
                                .padding(top = 4.dp)
                                .background(
                                    color = textBackground?.let { Color(it.color) } ?: MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(0.dp)
                                )
                                .padding(horizontal = if (textBackground != null) 18.dp else 0.dp, vertical = 14.dp),
                            enabled = !posting && postingAllowed,
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                lineHeight = 30.sp,
                                color = textBackground?.let { Color(it.foregroundColor) } ?: MaterialTheme.colorScheme.onBackground,
                                textAlign = if (textBackground != null) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start
                            ),
                            decorationBox = { innerTextField ->
                                Box(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    if (text.isEmpty()) {
                                        Text(
                                            "Add description...",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontSize = 22.sp,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                                lineHeight = 30.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        selectedComposerVisual?.let { media ->
                            Box(
                                Modifier.width(136.dp).height(190.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = !posting) { showComposerPreview = true }
                            ) {
                                if (media.third == "video") {
                                    AndroidView(
                                        factory = { VideoView(it).apply { setVideoURI(media.second); setOnPreparedListener { player -> player.isLooping = true; start() } } },
                                        update = { view ->
                                            if (view.tag != media.second.toString()) {
                                                view.tag = media.second.toString()
                                                view.setVideoURI(media.second)
                                                view.start()
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    AndroidView(
                                        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                                        update = { view -> view.setImageURI(media.second) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Surface(
                                    Modifier.align(Alignment.TopStart).padding(8.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF25364A).copy(alpha = 0.48f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                                ) {
                                    Text("Preview", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
                                }
                                Surface(
                                    onClick = { showCoverInfo = true },
                                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(7.dp),
                                    shape = RoundedCornerShape(9.dp),
                                    color = Color(0xFF25364A).copy(alpha = 0.48f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                                ) {
                                    Text(
                                        "Edit cover",
                                        Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                        }

                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = { text = (text.trimEnd() + if (text.isBlank()) "# " else " # ").take(4000) },
                                enabled = !posting && postingAllowed,
                                label = { Text("# Hashtags") }
                            )
                            AssistChip(
                                onClick = { text = (text.trimEnd() + if (text.isBlank()) "@ " else " @ ").take(4000) },
                                enabled = !posting && postingAllowed,
                                label = { Text("@ Mention") }
                            )
                        }                        
                        selectedCatalogueMusic?.let { music ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = "Selected FYNX music", tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f)) {
                                        Text(music.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                        Text(music.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                    FynxRemoteAudio(
                                        mediaUrl = "/api/social/music/catalogue/" + music.id + "/media",
                                        modifier = Modifier.width(112.dp),
                                        maxDurationMs = music.durationMs.coerceAtLeast(1_000L)
                                    )
                                    IconButton(onClick = { selectedCatalogueMusic = null; musicPlaying = false }, enabled = !posting) {
                                        Icon(Icons.Default.Close, "Remove music")
                                    }
                                }
                            }
                        }




                        if (capturedUris.isNotEmpty()) {
                            val audioCount = capturedTypes.count { it == "audio" }
                            val visualItems = composerVisualItems

                            Column(
                                Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (visualItems.size > 1) {
                                    Row(
                                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        visualItems.forEachIndexed { visualIndex, (_, uri, type) ->
                                            Box(Modifier.size(72.dp).clickable(enabled = !posting) { selectedVisualIndex = visualIndex }) {
                                                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))) {
                                                    if (type == "video") {
                                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.VideoLibrary, "Video " + (visualIndex + 1), modifier = Modifier.size(28.dp))
                                                        }
                                                    } else {
                                                        AndroidView(
                                                            factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                                                            update = { view -> view.setImageURI(uri) },
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                }
                                                IconButton(onClick = { removeCapturedUri(uri) }, enabled = !posting, modifier = Modifier.align(Alignment.TopEnd).size(44.dp)) {
                                                    Icon(Icons.Default.Close, "Remove media")
                                                }
                                            }
                                        }
                                    }
                                } else if (visualItems.size == 1) {
                                    TextButton(onClick = { removeCapturedUri(visualItems.first().second) }, enabled = !posting) {
                                        Icon(Icons.Default.Close, null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Remove photo/video")
                                    }
                                }
                                Text(
                                    capturedUris.size.toString() + " item" + (if (capturedUris.size == 1) "" else "s") + " ready" +
                                        (if (audioCount > 0) " • " + audioCount + " audio" else ""),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))

                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !posting) { showDisclosureInfo = true }.padding(vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(25.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Content disclosure and ads", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                Text("Paid partnership labeling is not supported yet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !posting && !locationLoading) { requestComposerLocation() }.padding(vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(25.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Location", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                Text(
                                    when {
                                        locationLoading -> "Getting your location…"
                                        postLocation != null -> postLocation.orEmpty()
                                        else -> "Add a location to this post"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            if (postLocation != null && !locationLoading) {
                                TextButton(onClick = { postLocation = null }, enabled = !posting) { Text("Remove") }
                            } else {
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (postLocation != null) {
                            Row(Modifier.fillMaxWidth().padding(start = 36.dp, bottom = 8.dp)) {
                                AssistChip(
                                    onClick = { postLocation = null },
                                    enabled = !posting,
                                    label = { Text(postLocation.orEmpty(), maxLines = 1) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove location", modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !posting) { postLinkInput = ""; showPostLinkDialog = true }.padding(vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(25.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Add link", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                Text("Include a URL in your caption", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !posting) { showPeoplePicker = true }.padding(vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(25.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (audience == FynxPostAudience.EVERYONE) "Everyone can view this post" else "Who can view this post",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                                Text(audience.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !posting) { showMorePostOptions = true }.padding(vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = null, modifier = Modifier.size(25.dp))
                            Column(Modifier.weight(1f)) {
                                Text("More options", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                Text("Music, people, media and post styling", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !posting) { sharePostCaption() }.padding(vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(25.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Share to", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                Text("Share your caption with another app", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        if (networkLevel == FynxNetworkQuality.Level.OFFLINE) Text("You are offline. Reconnect before publishing this post.", color = MaterialTheme.colorScheme.error)
                        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                savedPostDrafts = FynxLocalPostDraftStore.list(context)
                                draftNotice = null
                                showDraftsDialog = true
                            },
                            enabled = !posting,
                            modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF292B31),
                                contentColor = Color.White
                            )
                        ) { Text("▣  Drafts", style = MaterialTheme.typography.titleMedium) }
                        Button(
                            enabled = !posting && postingAllowed && networkLevel != FynxNetworkQuality.Level.OFFLINE && (audience != FynxPostAudience.SELECTED || selectedAudienceIds.isNotEmpty()) && (text.isNotBlank() || capturedUris.isNotEmpty() || selectedCatalogueMusic != null),
                            onClick = {
                                if (FynxNetworkQuality.current(context) == FynxNetworkQuality.Level.OFFLINE) {
                                    notice = "You are offline. Reconnect before publishing this post."
                                    return@Button
                                }
                                posting = true
                                notice = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        FynxMultiMediaPostClient.createPost(context, text, visibility, capturedUris, selectedAudienceIds.toList(), textBackground, postLocation, null, selectedCatalogueMusic, selectedFeelingActivity)
                                    }
                                    result.onSuccess {
                                        editingDraftId?.let { savedId ->
                                            withContext(Dispatchers.IO) { runCatching { FynxLocalPostDraftStore.delete(context, savedId) } }
                                        }
                                        finishComposerAfterSuccess()
                                    }.onFailure { notice = it.message ?: "Post could not be published." }
                                    posting = false
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2457), contentColor = Color.White)
                        ) { Text(if (posting) "Publishing…" else "Post", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
    }


    if (showPostLinkDialog) {
        AlertDialog(
            onDismissRequest = { showPostLinkDialog = false },
            title = { Text("Add link") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add a web address to your caption. It will be published as text in the post description.")
                    OutlinedTextField(
                        value = postLinkInput,
                        onValueChange = { postLinkInput = it.take(500); postLinkError = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Website URL") },
                        placeholder = { Text("https://example.com") },
                        isError = postLinkError != null
                    )
                    postLinkError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val candidate = postLinkInput.trim()
                    val parsed = runCatching { Uri.parse(candidate) }.getOrNull()
                    if (parsed == null || parsed.scheme !in listOf("http", "https") || parsed.host.isNullOrBlank()) {
                        postLinkError = "Enter a valid http or https web address."
                    } else {
                        val updated = (text.trimEnd() + if (text.isBlank()) candidate else "\n" + candidate)
                        if (updated.length > 4000) {
                            postLinkError = "The caption and link exceed the 4,000 character limit."
                        } else {
                            text = updated
                            showPostLinkDialog = false
                            postLinkInput = ""
                            postLinkError = null
                        }
                    }
                }, enabled = !posting) { Text("Add to caption") }
            },
            dismissButton = { TextButton(onClick = { showPostLinkDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDisclosureInfo) {
        AlertDialog(
            onDismissRequest = { showDisclosureInfo = false },
            title = { Text("Content disclosure and ads") },
            text = {
                Text("The current FYNX publishing model does not yet support paid-partnership or branded-content disclosure fields. This post will not be marked as sponsored. Do not use this post flow to label a paid promotion until that publishing support is added.")
            },
            confirmButton = { Button(onClick = { showDisclosureInfo = false }) { Text("Got it") } }
        )
    }

    if (showCoverInfo) {
        AlertDialog(
            onDismissRequest = { showCoverInfo = false },
            title = { Text("Edit cover") },
            text = {
                Text("FYNX currently publishes the selected photo or video using its generated media preview. A custom video-cover field is not supported by the current publishing API, so the cover cannot be changed here yet.")
            },
            confirmButton = { Button(onClick = { showCoverInfo = false }) { Text("OK") } }
        )
    }

    if (showMorePostOptions) {
        AlertDialog(
            onDismissRequest = { showMorePostOptions = false },
            title = { Text("More options") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Add to your post", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ComposerAction("Marketplace", Icons.Default.Storefront, onClick = { showMorePostOptions = false; onOpenMarketplace() }, enabled = !posting, compact = true)
                    TextButton(
                        onClick = { showMorePostOptions = false; showMusicPicker = true; notice = null },
                        enabled = !posting && postingAllowed,
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.MusicNote, null); Spacer(Modifier.width(10.dp)); Text(if (selectedCatalogueMusic == null) "Add music" else "Change music") }
                    TextButton(
                        onClick = { showMorePostOptions = false; showPeoplePicker = true },
                        enabled = !posting,
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.People, null); Spacer(Modifier.width(10.dp)); Text("Tag people") }
                    TextButton(
                        onClick = { showMorePostOptions = false; showFeelingActivityPicker = true },
                        enabled = !posting && postingAllowed,
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.SentimentSatisfied, null); Spacer(Modifier.width(10.dp)); Text("Feeling / Activity") }
                    TextButton(
                        onClick = {
                            showMorePostOptions = false
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                        enabled = !posting && postingAllowed && capturedUris.size < 4,
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(10.dp)); Text("Add photo or video") }
                    TextButton(
                        onClick = {
                            showMorePostOptions = false
                            if (!posting && postingAllowed) {
                                cameraOpenedFromComposer = true
                                showComposer = false
                                showCamera = true
                                showFastCamera = false
                                notice = null
                            }
                        },
                        enabled = !posting && postingAllowed && capturedUris.size < 4,
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(10.dp)); Text("Take photo or video") }
                    TextButton(
                        onClick = {
                            showMorePostOptions = false
                            if (capturedUris.isNotEmpty()) notice = "Voice posts must contain only one audio recording. Remove the current media first."
                            else { showComposer = false; showVoiceRecorder = true }
                        },
                        enabled = !posting && postingAllowed && capturedUris.isEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(10.dp)); Text("Record voice post") }
                    TextButton(
                        onClick = { showMorePostOptions = false; requestComposerLocation() },
                        enabled = !posting && !locationLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(10.dp)); Text("Add location") }
                    if (text.isNotBlank()) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text("Text background", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FynxPostTextBackground.entries.forEach { option ->
                                FilterChip(
                                    selected = textBackground == option,
                                    onClick = { textBackground = if (textBackground == option) null else option },
                                    label = { Text(option.label) },
                                    enabled = !posting
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMorePostOptions = false }) { Text("Done") } }
        )
    }

    if (showDraftsDialog) {
        AlertDialog(
            onDismissRequest = { if (!draftSaving) showDraftsDialog = false },
            title = { Text("Drafts") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    draftNotice?.let { Text(it, color = if (it.startsWith("Draft saved")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                    if (savedPostDrafts.isEmpty()) {
                        Text("No saved drafts yet. Save this post to continue editing it later on this device.")
                    } else {
                        savedPostDrafts.forEach { draft ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(draft.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                    Text(
                                        listOf(
                                            draft.mediaTypes.size.takeIf { it > 0 }?.let { "$it media" },
                                            draft.audience.replace('_', ' ').lowercase()
                                        ).filterNotNull().joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = {
                                            restoreSavedPostDraft(draft)
                                            draftNotice = null
                                        }) { Text("Open") }
                                        TextButton(onClick = {
                                            scope.launch {
                                                val result = withContext(Dispatchers.IO) { runCatching { FynxLocalPostDraftStore.delete(context, draft.id) } }
                                                result.onSuccess {
                                                    savedPostDrafts = FynxLocalPostDraftStore.list(context)
                                                    if (editingDraftId == draft.id) editingDraftId = null
                                                }.onFailure { draftNotice = it.message ?: "Draft could not be deleted." }
                                            }
                                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { saveCurrentPostDraft() },
                    enabled = !draftSaving && !posting && (text.isNotBlank() || capturedUris.isNotEmpty() || selectedCatalogueMusic != null)
                ) { Text(if (draftSaving) "Saving…" else if (editingDraftId == null) "Save current draft" else "Update draft") }
            },
            dismissButton = {
                TextButton(onClick = { if (!draftSaving) showDraftsDialog = false }) { Text("Done") }
            }
        )
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
                                if (option != FynxPostAudience.SELECTED) selectedAudienceIds = emptySet()
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
                                Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { selectedAudienceIds = if (checked) selectedAudienceIds - friend.id else selectedAudienceIds + friend.id },
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

    if (showComposerPreview) {
        Dialog(
            onDismissRequest = { showComposerPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Post preview", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { showComposerPreview = false }) { Text("Back to edit") }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(currentUsername, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text("• " + audience.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (text.isNotBlank()) {
                        Text(
                            text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(textBackground?.let { Color(it.color) } ?: Color.Transparent, RoundedCornerShape(0.dp))
                                .padding(horizontal = if (textBackground != null) 18.dp else 0.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                lineHeight = 30.sp,
                                color = textBackground?.let { Color(it.foregroundColor) } ?: MaterialTheme.colorScheme.onBackground,
                                textAlign = if (textBackground != null) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start
                            )
                        )
                    }
                    val previewVisuals = capturedUris.mapIndexedNotNull { index, uri ->
                        capturedTypes.getOrNull(index)?.takeIf { it == "image" || it == "video" }?.let { type -> Triple(index, uri, type) }
                    }
                    val previewVisual = previewVisuals.getOrNull(selectedVisualIndex.coerceIn(0, (previewVisuals.size - 1).coerceAtLeast(0)))
                    if (previewVisual != null) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp).clip(RoundedCornerShape(12.dp))) {
                            if (previewVisual.third == "video") {
                                AndroidView(
                                    factory = { VideoView(it).apply { setVideoURI(previewVisual.second); setOnPreparedListener { player -> player.isLooping = true; start() } } },
                                    update = { view -> if (view.tag != previewVisual.second.toString()) { view.tag = previewVisual.second.toString(); view.setVideoURI(previewVisual.second); view.start() } },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AndroidView(
                                    factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                                    update = { view -> view.setImageURI(previewVisual.second) },
                                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                                )
                            }
                        }
                    }
                    selectedCatalogueMusic?.let {
                        Text("♫ " + it.title + " • " + it.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    postLocation?.let {
                        Text("📍 " + it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    selectedFeelingActivity?.let {
                        Text(it.icon + " " + it.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("This is how your post content will be arranged before you publish.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
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

    if (showCamera || showFastCamera) {
        Dialog(
            onDismissRequest = {
                showCamera = false
                showFastCamera = false
                if (cameraOpenedFromComposer && !posting) {
                    showComposer = true
                }
                cameraOpenedFromComposer = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            // Draw the camera's dark canvas edge-to-edge, including behind the status bar.
            // The camera panel already insets its own controls; padding this parent would leave
            // the system-bar strip outside the dark surface and expose the underlying window.
            Surface(Modifier.fillMaxSize(), color = Color(0xFF080A10)) {
                Box(Modifier.fillMaxSize().background(Color(0xFF080A10))) {
                    FynxCameraCapturePanel(
                        fastCapture = showFastCamera,
                        selectedMusic = selectedCatalogueMusic,
                        onChooseMusic = { showMusicPicker = true; notice = null },
                        onClearMusic = { selectedCatalogueMusic = null; musicPlaying = false },
                        showYourStoryAction = true,
                        onShareToStatus = { uri, type ->
                            showCamera = false
                            showFastCamera = false
                            cameraOpenedFromComposer = false
                            onOpenStatusWithMedia(uri, type)
                        },
                        onCaptured = { uri, type ->
                            val nextUris = (capturedUris + uri).distinct().take(4)
                            capturedUris = nextUris
                            recomputeTypes(nextUris)
                            selectedVisualIndex = 0
                            showCamera = false
                            showFastCamera = false
                            showComposer = true
                        },
                        onDismiss = {
                            showCamera = false
                            showFastCamera = false
                            if (cameraOpenedFromComposer && !posting) {
                                showComposer = true
                            }
                            cameraOpenedFromComposer = false
                        }
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
        modifier = modifier.height(if (compact) 68.dp else 76.dp).minimumInteractiveComponentSize(),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(if (compact) 24.dp else 28.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}