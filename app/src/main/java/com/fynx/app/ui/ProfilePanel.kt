package com.fynx.app.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfilePanel(session: AuthSession = AuthSession(), openSettingsInitially: Boolean = false, onSettingsClosed: () -> Unit = {}, onSignOut: (() -> Unit)? = null, onAppearanceChanged: (String) -> Unit = {}, onAccentChanged: (FynxAccent) -> Unit = {}, onOpenPrivacy: () -> Unit = {}, onOpenNotifications: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(openSettingsInitially) }
    var profile by remember(session.username) { mutableStateOf(FynxPreferencesStore.loadProfile(context, session.username)) }
    var description by remember(session.username) { mutableStateOf(FynxPreferencesStore.loadDescription(context)) }
    var photo by remember(session.username) { mutableStateOf(FynxPreferencesStore.loadProfilePhoto(context)) }
    var remotePhotoId by remember(session.username) { mutableStateOf(session.username?.let { FynxProfileRemoteClient.cachedProfilePhotoId(context, it) }) }
    var remoteProfileLoaded by remember(session.username) { mutableStateOf(false) }
    var remoteVerified by remember(session.username) { mutableStateOf(false) }
    var showProfilePhoto by remember(session.username) { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(FynxPreferencesStore.loadSettings(context)) }
    val cachedStats = remember(session.username) {
        session.username?.let { FynxPreferencesStore.loadRemoteProfileStats(context, it) }
    }
    var postCount by remember(session.username) { mutableStateOf(cachedStats?.postCount ?: 0) }
    var followerCount by remember(session.username) { mutableStateOf(cachedStats?.followerCount) }
    var followingCount by remember(session.username) { mutableStateOf(cachedStats?.followingCount) }
    var connectionType by remember { mutableStateOf<String?>(null) }
    var connections by remember { mutableStateOf<List<FynxProfileRemoteClient.ConnectionUser>>(emptyList()) }
    var connectionsLoading by remember { mutableStateOf(false) }
    var connectionsError by remember { mutableStateOf<String?>(null) }

    fun openConnections(type: String) {
        connectionType = type; connections = emptyList(); connectionsError = null; connectionsLoading = true
        scope.launch {
            val result = if (type == "Followers") FynxProfileRemoteClient.followers(context) else FynxProfileRemoteClient.following(context)
            result.onSuccess { connections = it }.onFailure { connectionsError = it.message ?: "Could not load connections." }
            connectionsLoading = false
        }
    }

    LaunchedEffect(session.username) {
        if (session.state == AuthState.SIGNED_IN && !session.username.isNullOrBlank()) {
            FynxProfileRemoteClient.get(context, session.username).onSuccess { remote ->
                postCount = remote.postCount
                remote.followerCount?.let { followerCount = it }
                remote.followingCount?.let { followingCount = it }
                FynxPreferencesStore.saveRemoteProfileStats(context, remote.username, remote.postCount, remote.followerCount, remote.followingCount)
                // Once the server responds, its profilePhotoMediaId is authoritative.
                // Do not resurrect a stale local/cached photo when the server says null.
                remotePhotoId = remote.profilePhotoMediaId
                remoteVerified = remote.verified
                remoteProfileLoaded = true
                profile = profile.copy(
                    displayName = remote.displayName.ifBlank { profile.displayName },
                    username = remote.username.ifBlank { profile.username },
                    bio = remote.bio.ifBlank { profile.bio }
                )
            }.onFailure { syncError = it.message }
        }
    }

    if (editing) {
        EditProfilePanel(profile, description, photo, syncing, syncError, onPhotoChanged = { photo = it }, onSave = { updatedProfile, updatedDescription, updatedPhoto ->
            scope.launch {
                syncing = true
                syncError = null
                val originalPhoto = FynxPreferencesStore.loadProfilePhoto(context)
                var photoId: String? = null
                if (updatedPhoto != originalPhoto && updatedPhoto != null) {
                    val uri = runCatching { Uri.parse(updatedPhoto) }.getOrNull()
                    if (uri != null) {
                        FynxProfileRemoteClient.uploadProfilePhoto(context, uri)
                            .onSuccess { photoId = it }
                            .onFailure { syncError = it.message ?: "Profile photo upload failed." }
                    }
                }
                val removeRemotePhoto = updatedPhoto == null && (originalPhoto != null || remotePhotoId != null)
                if (syncError == null) {
                    FynxProfileRemoteClient.update(context, updatedProfile.displayName, updatedProfile.username, updatedProfile.bio, profilePhotoMediaId = photoId, removeProfilePhoto = removeRemotePhoto)
                        .onSuccess { remote ->
                            profile = updatedProfile
                            description = updatedDescription
                            photo = updatedPhoto
                            remotePhotoId = remote.profilePhotoMediaId
                            FynxPreferencesStore.saveProfile(context, updatedProfile)
                            FynxPreferencesStore.saveDescription(context, updatedDescription)
                            FynxPreferencesStore.saveProfilePhoto(context, updatedPhoto)
                            editing = false
                        }.onFailure { syncError = it.message ?: "Profile update failed." }
                }
                syncing = false
            }
        }, onCancel = { editing = false })
        return
    }

    if (settingsOpen) {
        SettingsPanel(profile = profile, settings = settings, onSettingsChange = { settings = it; FynxPreferencesStore.saveSettings(context, it) }, onBack = { settingsOpen = false; onSettingsClosed() }, onAppearanceChanged = onAppearanceChanged, onAccentChanged = onAccentChanged, onOpenPrivacy = onOpenPrivacy, onOpenNotifications = onOpenNotifications, onSignOut = { if (onSignOut != null) onSignOut() else { FynxAuthStore.clear(context); (context as? Activity)?.recreate() } }, onOpenAccountProfile = { settingsOpen = false; editing = true })
        return
    }

    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = .45f)
    LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item {
            Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (remotePhotoId != null) {
                        FynxRemoteProfileAvatar(remotePhotoId, profile.displayName, Modifier.size(80.dp).clip(CircleShape).then(if (remoteVerified) Modifier else Modifier).clickable { showProfilePhoto = true }, ownerUsername = profile.username)
                    } else if (!remoteProfileLoaded) {
                        FynxProfileImage(profile.displayName, photo, Modifier.size(80.dp).clip(CircleShape).clickable { showProfilePhoto = true })
                    } else {
                        FynxAvatar(profile.displayName, Modifier.size(80.dp).clip(CircleShape).clickable { showProfilePhoto = true })
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text(
                        profile.displayName.ifBlank { "FYNX User" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                        )
                        if (remoteVerified) {
                            Spacer(Modifier.width(5.dp))
                            Icon(Icons.Default.Verified, contentDescription = "Verified FYNX official account", tint = androidx.compose.ui.graphics.Color(0xFF1877F2), modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        "@${profile.username.removePrefix("@")}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (profile.bio.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            profile.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.9f)
                        )
                    }
                    if (description.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.9f)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileStat("Posts", formatProfileCount(postCount), Modifier.weight(1f))
                        ProfileStat("Followers", formatProfileCount(followerCount ?: 0), Modifier.weight(1f)) { openConnections("Followers") }
                        ProfileStat("Following", formatProfileCount(followingCount ?: 0), Modifier.weight(1f)) { openConnections("Following") }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { editing = true },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit profile")
                        }
                        OutlinedButton(
                            onClick = { settingsOpen = true },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Settings, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Settings")
                        }
                    }
                }
        }
        item {
            session.username?.takeIf { it.isNotBlank() }?.let { username ->
                FynxProfileContentSection(
                    username = username,
                    onError = { if (it != null) syncError = it }
                )
            }
        }
        item {
            Card(onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(surface), border = BorderStroke(1.dp, outline)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Privacy & Safety", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Control who can see your profile, posts, Status and photos", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (session.state == AuthState.SIGNED_IN) item { OutlinedButton(onClick = { if (onSignOut != null) onSignOut() else { FynxAuthStore.clear(context); (context as? Activity)?.recreate() } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Sign out") } }
    }
    connectionType?.let { type ->
        ProfileConnectionsDialog(type, connections, connectionsLoading, connectionsError, onRetry = { openConnections(type) }) { connectionType = null }
    }
    if (showProfilePhoto) FynxProfilePhotoViewer(remotePhotoId, photo, profile.displayName) { showProfilePhoto = false }
}

@Composable private fun ProfileStat(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
    if (onClick != null) TextButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(0.dp)) { content() } else Box(modifier, contentAlignment = Alignment.Center) { content() }
}

private fun formatProfileCount(value: Int): String = when { value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000f).replace(".0M", "M"); value >= 1_000 -> String.format("%.1fK", value / 1_000f).replace(".0K", "K"); else -> value.toString() }

@Composable
// Official-account photo viewer.
private fun FynxProfilePhotoViewer(mediaId: String?, localPhotoUri: String?, name: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(mediaId, localPhotoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(mediaId, localPhotoUri) {
        bitmap = withContext(kotlinx.coroutines.Dispatchers.IO) {
            when {
                !mediaId.isNullOrBlank() -> FynxProductionMessaging.cacheRemoteMedia(context, mediaId, "/api/social/media/$mediaId").getOrNull()?.let { uri ->
                    runCatching { context.contentResolver.openInputStream(uri).use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()
                }
                !localPhotoUri.isNullOrBlank() -> runCatching {
                    context.contentResolver.openInputStream(Uri.parse(localPhotoUri)).use { android.graphics.BitmapFactory.decodeStream(it) }
                }.getOrNull()
                else -> null
            }
        }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) androidx.compose.foundation.Image(
                bitmap!!.asImageBitmap(),
                contentDescription = "Profile photo",
                modifier = Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            ) else FynxAvatar(name, Modifier.size(120.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) { Text("Close", color = androidx.compose.ui.graphics.Color.White) }
        }
    }
}

@Composable private fun ProfileInfoRow(title: String, value: String) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis) } }

@Composable private fun EditProfilePanel(profile: FynxProfile, description: String, photoUri: String?, syncing: Boolean, syncError: String?, onPhotoChanged: (String?) -> Unit, onSave: (FynxProfile, String, String?) -> Unit, onCancel: () -> Unit) {
    var displayName by remember(profile) { mutableStateOf(profile.displayName) }
    var username by remember(profile) { mutableStateOf(profile.username) }
    var bio by remember(profile) { mutableStateOf(profile.bio) }
    var about by remember(profile, description) { mutableStateOf(description) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if (uri != null) onPhotoChanged(uri.toString()) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { TextButton(enabled = !syncing, onClick = onCancel) { Text("Cancel") }; Text("Edit profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); TextButton(enabled = !syncing, onClick = { onSave(profile.copy(displayName = displayName.trim().ifBlank { profile.displayName }, username = username.trim().removePrefix("@").replace(" ", "").ifBlank { profile.username }, bio = bio.trim()), about.trim(), photoUri) }) { Text(if (syncing) "Saving…" else "Save") } }
        syncError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        HorizontalDivider()
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { FynxProfileImage(displayName, photoUri, Modifier.size(112.dp).clip(CircleShape)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { OutlinedButton(enabled = !syncing, onClick = { picker.launch("image/*") }, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.AddAPhoto, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(if (photoUri == null) "Add photo" else "Change photo") }; if (photoUri != null) { Spacer(Modifier.width(6.dp)); TextButton(enabled = !syncing, onClick = { onPhotoChanged(null) }) { Text("Remove") } } }
        OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), label = { Text("Display name") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        OutlinedTextField(username.removePrefix("@"), { username = it.removePrefix("@").replace(" ", "") }, Modifier.fillMaxWidth(), label = { Text("Username") }, prefix = { Text("@") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text("Bio") }, minLines = 3, shape = RoundedCornerShape(14.dp))
        OutlinedTextField(about, { about = it }, Modifier.fillMaxWidth(), label = { Text("About / description") }, minLines = 3, maxLines = 6, shape = RoundedCornerShape(14.dp))
    }
}

@Composable
fun SettingsPanel(
    profile: FynxProfile,
    settings: FynxSettings, 
    onSettingsChange: (FynxSettings) -> Unit,
    onBack: () -> Unit,
    onAppearanceChanged: (String) -> Unit = {},
    onAccentChanged: (FynxAccent) -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onOpenAccountProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsScope = rememberCoroutineScope()
    var storySaveNotice by remember { mutableStateOf<String?>(null) }
    var storySaving by remember { mutableStateOf(false) }
    var appearance by remember { mutableStateOf(FynxPreferencesStore.loadAppearance(context)) }
    var accent by remember { mutableStateOf(FynxPreferencesStore.loadAccent(context)) }
    var showAppearance by remember { mutableStateOf(false) }
    var showAppearancePage by remember { mutableStateOf(false) }
    var showAccountProfile by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }
    var showChatPersonalization by remember { mutableStateOf(false) }
    var showStoriesStatus by remember { mutableStateOf(false) }
    var storyVisibility by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, "privacy_status_visibility")) }
    var search by remember { mutableStateOf("") }
    var showLanguage by remember { mutableStateOf(false) }
    var showMediaStoragePage by remember { mutableStateOf(false) }
    var showNetworkStatusPage by remember { mutableStateOf(false) }
    var infoTitle by remember { mutableStateOf<String?>(null) }
    var infoDescription by remember { mutableStateOf("") }

    fun saveStoryVisibility(option: String) {
        if (storySaving) return
        storyVisibility = option
        FynxPreferencesStore.saveVisibility(context, "privacy_status_visibility", option)
        storySaveNotice = null
        storySaving = true
        settingsScope.launch {
            FynxPrivacyRemoteClient.update(context, "status_visibility", option)
                .onSuccess { remote ->
                    val saved = remote["status_visibility"].ifBlank { option }
                    storyVisibility = saved
                    FynxPreferencesStore.saveVisibility(context, "privacy_status_visibility", saved)
                    storySaveNotice = null
                    showStoriesStatus = false
                }
                .onFailure { error ->
                    // Keep the local choice, but clearly report that server sync failed.
                    storySaveNotice = "Saved on this device, but server sync failed: ${error.message ?: "try again later"}"
                }
            storySaving = false
        }
    }

    val query = search.trim().lowercase()
    if (showAccountProfile) {
        AccountProfileSettingsPanel(
            profile = profile,
            onBack = { showAccountProfile = false },
            onEditProfile = { showAccountProfile = false; onOpenAccountProfile() },
            onOpenPrivacy = { showAccountProfile = false; onOpenPrivacy() },
            onSignOut = onSignOut
        )
        return
    }
    if (showLanguage) {
        LanguageSelectionPanel(onBack = { showLanguage = false })
        return
    }
    if (showMediaStoragePage) {
        MediaStorageSettingsPanel(onBack = { showMediaStoragePage = false })
        return
    }
    if (showNetworkStatusPage) {
        DataNetworkSettingsPanel(onBack = { showNetworkStatusPage = false })
        return
    }
    if (showAppearancePage) {
        AppearanceSettingsPanel(
            appearance = appearance,
            accentName = accent.name,
            onBack = { showAppearancePage = false },
            onThemeSelected = {
                appearance = it
                FynxPreferencesStore.saveAppearance(context, it)
                onAppearanceChanged(it)
            },
            onAccentClick = { showColors = true }
        )
        if (showColors) AccentDialog(accent, {
            accent = it
            FynxPreferencesStore.saveAccent(context, it)
            onAccentChanged(it)
            showColors = false
        }, { showColors = false })
        return
    }
    fun visible(title: String, description: String): Boolean =
        query.isBlank() || title.lowercase().contains(query) || description.lowercase().contains(query)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .widthIn(max = 720.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to profile",
                    modifier = Modifier.size(25.dp).then(Modifier), tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f)) {
                Text("FYNX", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary)
                Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Manage your account, privacy, and app preferences.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(10.dp).size(22.dp))
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search settings") },
            placeholder = { Text("Search settings") },
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (visible("Account & Profile", "account profile username bio phone email account controls") ||
                visible("Privacy & Security", "profile privacy online status blocked users security people discovery")) {
                item { SettingsSectionTitle("ACCOUNT") }
                if (visible("Account & Profile", "account profile username bio phone email account controls")) {
                    item {
                        SettingsReferenceRow(Icons.Default.AccountCircle, "Account & Profile",
                            "Profile, username, phone & email", androidx.compose.ui.graphics.Color(0xFF4F7BFF)) {
                            showAccountProfile = true
                        }
                    }
                }
                if (visible("Privacy & Security", "profile privacy online status blocked users security people discovery")) {
                    item {
                        SettingsReferenceRow(Icons.Default.Security, "Privacy & Security",
                            "Privacy, blocking, security & sessions", androidx.compose.ui.graphics.Color(0xFF8B5CF6)) {
                            onOpenPrivacy()
                        }
                    }
                }
            }
            if (visible("Notifications", "chat alerts social activity stories calls marketplace money sounds vibration")) {
                item { SettingsSectionTitle("COMMUNICATION") }
                item {
                    SettingsReferenceRow(Icons.Default.Notifications, "Notifications",
                        "Messages, calls, social & more", androidx.compose.ui.graphics.Color(0xFFEF8B42)) {
                        onOpenNotifications()
                    }
                }
            }
            if (visible("Chat", "chat settings read receipts wallpapers messages personalization")) {
                item {
                    SettingsReferenceRow(Icons.Default.Chat, "Chat",
                        "Chat settings are in the Chat section", androidx.compose.ui.graphics.Color(0xFF20A88A)) {
                        showChatPersonalization = true
                    }
                }
            }
            if (visible("Stories & Status", "stories status replies reactions privacy")) {
                item {
                    SettingsReferenceRow(Icons.Default.AutoStories, "Stories & Status",
                        "Stories, replies, reactions, archive", androidx.compose.ui.graphics.Color(0xFFE45B91)) {
                        storyVisibility = FynxPreferencesStore.loadVisibility(context, "privacy_status_visibility")
                        showStoriesStatus = true
                    }
                }
            }
            if (visible("Appearance", "theme system default light dark amoled accent color font size message style")) {
                item { SettingsSectionTitle("PERSONALIZATION") }
                item {
                    SettingsReferenceRow(Icons.Default.Palette, "Appearance",
                        "Theme, colors, app style", androidx.compose.ui.graphics.Color(0xFF9B6BFF)) {
                        showAppearancePage = true
                    }
                }
            }
            if (visible("Media & Storage", "media storage cache downloads photos videos")) {
                item { SettingsSectionTitle("APP & DATA") }
                item {
                    SettingsReferenceRow(Icons.Default.Storage, "Media & Storage",
                        "Downloads, storage, cache", androidx.compose.ui.graphics.Color(0xFF2C9BCB)) {
                        showMediaStoragePage = true
                    }
                }
            }
            if (visible("Data & Network", "data network mobile data wifi usage uploads downloads")) {
                item {
                    SettingsReferenceRow(Icons.Default.DataUsage, "Data & Network",
                        "Data usage, upload/download, network", androidx.compose.ui.graphics.Color(0xFF39A96B)) {
                        showNetworkStatusPage = true
                    }
                }
            }
            if (visible("Language & Accessibility", "language accessibility english font size")) {
                item {
                    SettingsReferenceRow(Icons.Default.Language, "Language & Accessibility",
                        "Language, text size, accessibility", androidx.compose.ui.graphics.Color(0xFFCB8A35)) {
                        showLanguage = true
                    }
                }
            }
            if (visible("Help & Support", "help support contact troubleshooting")) {
                item { SettingsSectionTitle("FYNX") }
                item {
                    SettingsReferenceRow(Icons.Default.HelpOutline, "Help & Support",
                        "Help center, report, support", androidx.compose.ui.graphics.Color(0xFF477FEA)) {
                        infoTitle = "Help & Support"
                        infoDescription = "Try these steps for common problems:\n\nConnection problems: Check that your internet is working, then reopen the screen and retry.\n\nSign-in problems: Check your sign-in details and never share your password or verification codes.\n\nCamera, microphone or media problems: Review FYNX permissions in Android Settings and make sure the app is up to date.\n\nPrivacy or account safety: Open Privacy & Security to review visibility controls, submit a report or follow an appeal.\n\nIf the problem continues, note which FYNX screen you were using and the exact error message before asking for support. Do not include passwords or verification codes."
                    }
                }
            }
            if (visible("About FYNX", "about version app information")) {
                item {
                    SettingsReferenceRow(Icons.Default.Info, "About FYNX",
                        "Version, terms, privacy", androidx.compose.ui.graphics.Color(0xFF8B69D6)) {
                        infoTitle = "About FYNX"
                        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "Unknown"
                        infoDescription = "FYNX brings communication, social sharing and marketplace tools together in one app.\n\nVersion: $version"
                    }
                }
            }
            if (query.isNotBlank() && !listOf(
                "Account & Profile" to "account profile username bio phone email account controls",
                "Privacy & Security" to "profile privacy online status blocked users security people discovery",
                "Notifications" to "chat alerts social activity stories calls marketplace money sounds vibration",
                "Chat" to "chat settings read receipts wallpapers messages personalization",
                "Stories & Status" to "stories status replies reactions privacy",
                "Appearance" to "theme system default light dark amoled accent color font size message style",
                "Media & Storage" to "media storage cache downloads photos videos",
                "Data & Network" to "data network mobile data wifi usage uploads downloads",
                "Language & Accessibility" to "language accessibility english font size",
                "Help & Support" to "help support contact troubleshooting",
                "About FYNX" to "about version app information"
            ).any { (title, description) -> visible(title, description) }) {
                item {
                    Text("No matching settings", modifier = Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (showAppearance) AppearanceDialog(appearance, {
        appearance = it
        FynxPreferencesStore.saveAppearance(context, it)
        onAppearanceChanged(it)
        showAppearance = false
    }, { showAppearance = false })
    if (showColors) AccentDialog(accent, {
        accent = it
        FynxPreferencesStore.saveAccent(context, it)
        onAccentChanged(it)
        showColors = false
    }, { showColors = false })
    if (showChatPersonalization) ChatPersonalizationDialog(settings, onSettingsChange, { showChatPersonalization = false })
    if (showStoriesStatus) {
        AlertDialog(
            onDismissRequest = { if (!storySaving) showStoriesStatus = false },
            title = { Text("Stories & Status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Who can view your status?")
                    storySaveNotice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    listOf("Everyone", "My friends", "Nobody").forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !storySaving) {
                                saveStoryVisibility(option)
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = storyVisibility == option,
                                enabled = !storySaving,
                                onClick = { saveStoryVisibility(option) }
                            )
                            Text(option, modifier = Modifier.weight(1f))
                        }
                    }
                    if (storySaving) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Saving…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !storySaving, onClick = { showStoriesStatus = false }) { Text("Done") }
            }
        )
    }
    if (infoTitle != null) AlertDialog(
        onDismissRequest = { infoTitle = null },
        title = { Text(infoTitle!!) },
        text = { Text(infoDescription) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (infoTitle == "Help & Support") {
                    TextButton(onClick = { infoTitle = null; onOpenPrivacy() }) {
                        Text("Open Privacy & Security")
                    }
                }
                TextButton(onClick = { infoTitle = null }) { Text("OK") }
            }
        }
    )
}

@Composable
private fun SettingsReferenceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(13.dp), color = tint.copy(alpha = .14f)) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(11.dp).size(23.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun AppearanceSettingsPanel(
    appearance: String,
    accentName: String,
    onBack: () -> Unit,
    onThemeSelected: (String) -> Unit,
    onAccentClick: () -> Unit
) {
    val context = LocalContext.current
    var fontSize by remember { mutableStateOf(FynxPreferencesStore.loadSettingsFontSize(context)) }
    var messageStyle by remember { mutableStateOf(FynxPreferencesStore.loadSettingsMessageStyle(context)) }
    var chooseFontSize by remember { mutableStateOf(false) }
    var chooseMessageStyle by remember { mutableStateOf(false) }
    // Keep the global Appearance choices aligned with the three agreed options.
    // Legacy stored values remain readable by the theme runtime, but are not offered as new choices.
    val themes = listOf(
        Triple("System", "System Default", Color(0xFF64748B)),
        Triple("Light", "Soft white surfaces with subtle FYNX blue accents", Color(0xFFF5F7FB)),
        Triple("Dark", "Deep navy surfaces with subtle blue reflections", Color(0xFF101827))
    )
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back to settings") }
            Column(Modifier.weight(1f)) {
                Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Make FYNX feel like yours", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SettingsSectionTitle("THEME") }
            items(themes) { (value, description, swatch) ->
                val selected = appearance == value
                Card(
                    onClick = { onThemeSelected(value) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        if (selected) 1.5.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = .4f)
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(width = 38.dp, height = 46.dp)
                                .background(swatch, RoundedCornerShape(9.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f), RoundedCornerShape(9.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (value == "System") "System Default" else value,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RadioButton(selected = selected, onClick = { onThemeSelected(value) })
                    }
                }
            }
            item { SettingsSectionTitle("ACCENT COLOR") }
            item {
                SettingsReferenceRow(Icons.Default.Palette, "Accent Color", accentName,
                    androidx.compose.ui.graphics.Color(0xFF5677E8), onAccentClick)
            }
            item { SettingsSectionTitle("APP APPEARANCE") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .4f))
                ) {
                    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SettingsActionCard("Font Size", fontSize) { chooseFontSize = true }
                        SettingsActionCard("Message Style", messageStyle) { chooseMessageStyle = true }
                    }
                }
            }
        }
    }
    if (chooseFontSize) {
        AlertDialog(
            onDismissRequest = { chooseFontSize = false },
            title = { Text("Font Size") },
            text = {
                Column {
                    listOf("Small", "Default", "Large", "Extra large").forEach { option ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = fontSize == option, onClick = {
                                fontSize = option
                                FynxPreferencesStore.saveSettingsFontSize(context, option)
                                chooseFontSize = false
                            })
                            Text(option, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chooseFontSize = false }) { Text("Done") } }
        )
    }
    if (chooseMessageStyle) {
        AlertDialog(
            onDismissRequest = { chooseMessageStyle = false },
            title = { Text("Message Style") },
            text = {
                Column {
                    listOf("FYNX Default", "Compact", "Comfortable").forEach { option ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = messageStyle == option, onClick = {
                                messageStyle = option
                                FynxPreferencesStore.saveSettingsMessageStyle(context, option)
                                chooseMessageStyle = false
                            })
                            Text(option, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chooseMessageStyle = false }) { Text("Done") } }
        )
    }
}

@Composable private fun SettingsSectionTitle(title: String) { Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)) }
@Composable private fun SettingsActionCard(title: String, value: String, onClick: () -> Unit) { Card(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }; Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun ProfileConnectionsDialog(type:String,users:List<FynxProfileRemoteClient.ConnectionUser>,loading:Boolean,error:String?,onRetry:()->Unit,onDismiss:()->Unit){AlertDialog(onDismissRequest={if(!loading)onDismiss()},title={Text(type)},text={Box(Modifier.fillMaxWidth().heightIn(min=80.dp,max=420.dp)){when{loading->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};error!=null->Column(horizontalAlignment=Alignment.CenterHorizontally){Text(error,color=MaterialTheme.colorScheme.error,textAlign=TextAlign.Center);OutlinedButton(onClick=onRetry){Text("Retry")}};users.isEmpty()->Text("No ${type.lowercase()} yet.",color=MaterialTheme.colorScheme.onSurfaceVariant);else->LazyColumn(verticalArrangement=Arrangement.spacedBy(2.dp)){items(users){user->ListItem(headlineContent={Text(user.displayName.ifBlank{user.username})},supportingContent={Text("@${user.username.removePrefix("@").trim()}")})}}}}},confirmButton={TextButton(onClick=onDismiss,enabled=!loading){Text("Done")}})}


@Composable
private fun LanguageSelectionPanel(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp).statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Spacer(Modifier.width(4.dp))
            Text("Language", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text("App language", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Choose the language used throughout FYNX.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("English", style = MaterialTheme.typography.titleMedium)
                        Text("Current language", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("English is currently the supported FYNX app language.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}


@Composable
private fun AccountProfileSettingsPanel(
    profile: FynxProfile,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var infoTitle by remember { mutableStateOf<String?>(null) }
    var infoText by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to settings")
            }
            Column(Modifier.weight(1f)) {
                Text("Account & Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Manage your personal information", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FynxAvatar(profile.displayName.ifBlank { "FYNX User" }, Modifier.size(58.dp).clip(CircleShape))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.displayName.ifBlank { "FYNX User" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("@${profile.username.removePrefix("@")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = onEditProfile) { Text("Edit") }
                    }
                }
            }
            item { SettingsSectionTitle("ACCOUNT INFORMATION") }
            item {
                SettingsReferenceRow(Icons.Default.AccountCircle, "Profile Information",
                    "Display name, bio, profile photo and description",
                    androidx.compose.ui.graphics.Color(0xFF4F7BFF), onEditProfile)
            }
            item {
                SettingsReferenceRow(Icons.Default.Edit, "Username",
                    "@${profile.username.removePrefix("@")}",
                    androidx.compose.ui.graphics.Color(0xFF20A88A), onEditProfile)
            }
            item {
                SettingsReferenceRow(Icons.Default.Info, "Phone & Email",
                    "View the phone number saved with this account",
                    androidx.compose.ui.graphics.Color(0xFFCB8A35)) {
                    val phone = FynxAuthStore.storedPhone(context)
                    infoTitle = "Phone & Email"
                    infoText = buildString {
                        append("Phone number: ")
                        append(phone ?: "No phone number is saved on this device.")
                        append("\n\nEmail: No email address is currently available in the saved account details.")
                        append("\n\nChanging contact details requires a verified account flow, which is not yet available here. Your details have not been changed.")
                    }
                }
            }
            item { SettingsSectionTitle("ACCOUNT CONTROLS") }
            item {
                SettingsReferenceRow(Icons.Default.Security, "Account Controls",
                    "Privacy, sign out and account access",
                    androidx.compose.ui.graphics.Color(0xFF8B5CF6)) {
                    infoTitle = "Account Controls"
                    infoText = "Choose a real account action. Privacy & Security contains the available visibility and blocked-user controls. Sign out ends this session on this device."
                }
            }
        }
    }
    if (infoTitle != null) {
        AlertDialog(
            onDismissRequest = { infoTitle = null },
            title = { Text(infoTitle!!) },
            text = { Text(infoText) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (infoTitle == "Account Controls") {
                        TextButton(onClick = { infoTitle = null; onOpenPrivacy() }) { Text("Privacy & Security") }
                        TextButton(onClick = { infoTitle = null; onSignOut() }) { Text("Sign out") }
                    } else {
                        TextButton(onClick = { infoTitle = null }) { Text("OK") }
                    }
                }
            }
        )
    }
}
