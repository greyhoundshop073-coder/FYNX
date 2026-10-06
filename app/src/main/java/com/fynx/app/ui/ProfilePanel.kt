package com.fynx.app.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette



import androidx.compose.material.icons.filled.Info




import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
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
        SettingsPanel(settings = settings, onSettingsChange = { settings = it; FynxPreferencesStore.saveSettings(context, it) }, onBack = { settingsOpen = false; onSettingsClosed() }, onAppearanceChanged = onAppearanceChanged, onAccentChanged = onAccentChanged, onOpenPrivacy = onOpenPrivacy, onOpenNotifications = onOpenNotifications, onOpenAccountProfile = { settingsOpen = false; editing = true })
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { OutlinedButton(enabled = !syncing, onClick = { picker.launch("image/*") }, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.AddAPhoto, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(if (photoUri == null) "Add photo" else "Change photo") }; if (@Composable
fun SettingsPanel(
    settings: FynxSettings,
    onSettingsChange: (FynxSettings) -> Unit,
    onBack: () -> Unit,
    onAppearanceChanged: (String) -> Unit = {},
    onAccentChanged: (FynxAccent) -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenAccountProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    var appearance by remember { mutableStateOf(FynxPreferencesStore.loadAppearance(context)) }
    var accent by remember { mutableStateOf(FynxPreferencesStore.loadAccent(context)) }
    var search by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<String?>(null) }

    if (detail != null) {
        when (detail) {
            "account" -> SettingsDetailPanel("Account & Profile", "Profile, account and sign-in controls", "account", onBack = { detail = null }, onOpen = { onOpenAccountProfile(); detail = null })
            "privacy" -> SettingsDetailPanel("Privacy & Security", "Control what other people can see", "privacy", onBack = { detail = null }, onOpen = { onOpenPrivacy(); detail = null })
            "notifications" -> SettingsDetailPanel("Notifications", "Messages, calls, social activity and sounds", "notifications", onBack = { detail = null }, onOpen = { onOpenNotifications(); detail = null })
            "chat" -> SettingsDetailPanel("Chat", "Chat settings stay inside Chat", "chat", onBack = { detail = null }, onOpen = { detail = null })
            "stories" -> SettingsDetailPanel("Stories & Status", "Replies, reactions, mentions and archive", "stories", onBack = { detail = null }, onOpen = { detail = null })
            "appearance" -> AppearanceSettingsDetail(appearance, accent, onBack = { detail = null }, onAppearanceChanged = { appearance = it; FynxPreferencesStore.saveAppearance(context, it); onAppearanceChanged(it) }, onAccentChanged = { accent = it; FynxPreferencesStore.saveAccent(context, it); onAccentChanged(it) })
            "media" -> SettingsDetailPanel("Media & Storage", "Downloads, storage and cache", "media", onBack = { detail = null }, onOpen = { detail = null })
            "data" -> SettingsDetailPanel("Data & Network", "Data usage and upload/download preferences", "data", onBack = { detail = null }, onOpen = { detail = null })
            "language" -> LanguageSelectionPanel(onBack = { detail = null })
            "accessibility" -> SettingsDetailPanel("Accessibility", "Text size and accessible presentation", "accessibility", onBack = { detail = null }, onOpen = { detail = null })
            "help" -> SettingsDetailPanel("Help & Support", "Help center, reports and support", "help", onBack = { detail = null }, onOpen = { detail = null })
            "about" -> SettingsDetailPanel("About FYNX", "Version, terms and privacy", "about", onBack = { detail = null }, onOpen = { detail = null })
        }
        return
    }

    val query = search.trim().lowercase()
    val categories = listOf(
        SettingsCategory("account", "Account & Profile", "Profile, username, phone & email", Icons.Default.Person, Color(0xFF2F8CFF)),
        SettingsCategory("privacy", "Privacy & Security", "Privacy, blocking, security & sessions", Icons.Default.Security, Color(0xFF25B864)),
        SettingsCategory("notifications", "Notifications", "Messages, calls, social activity & sounds", Icons.Default.Notifications, Color(0xFF9B5CFF)),
        SettingsCategory("chat", "Chat", "Chat settings remain inside Chat", Icons.Default.ChatBubbleOutline, Color(0xFF19A9F5)),
        SettingsCategory("stories", "Stories & Status", "Stories, replies, reactions & archive", Icons.Default.AutoStories, Color(0xFFFF8A1F)),
        SettingsCategory("appearance", "Appearance", "Theme, colors & app style", Icons.Default.Palette, Color(0xFF7C5CFF)),
        SettingsCategory("media", "Media & Storage", "Downloads, storage & cache", Icons.Default.Image, Color(0xFF00AFA6)),
        SettingsCategory("data", "Data & Network", "Data usage, upload/download & network", Icons.Default.Language, Color(0xFF0AA7D8)),
        SettingsCategory("language", "Language", "App language", Icons.Default.Language, Color(0xFFFFB000)),
        SettingsCategory("accessibility", "Accessibility", "Text size and accessible presentation", Icons.Default.Settings, Color(0xFF5968D8)),
        SettingsCategory("help", "Help & Support", "Help center, report a problem & support", Icons.Default.Info, Color(0xFF24B96B)),
        SettingsCategory("about", "About FYNX", "Version, terms & privacy", Icons.Default.Info, Color(0xFF2F8CFF))
    )
    val sections = listOf(
        "ACCOUNT" to listOf("account", "privacy"),
        "COMMUNICATION" to listOf("notifications", "chat", "stories"),
        "PERSONALIZATION" to listOf("appearance"),
        "APP & DATA" to listOf("media", "data", "language", "accessibility"),
        "FYNX" to listOf("help", "about")
    )
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp).widthIn(max = 720.dp).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text("Settings", style = MaterialTheme.typography.titleLarge)
                Text("Manage your account, privacy and app preferences", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, "Search settings") }, placeholder = { Text("Search settings...") }, shape = RoundedCornerShape(18.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sections.forEach { (sectionTitle, ids) ->
                val visible = ids.mapNotNull { id -> categories.find { it.id == id } }.filter { query.isBlank() || it.title.lowercase().contains(query) || it.subtitle.lowercase().contains(query) }
                if (visible.isNotEmpty()) {
                    item { Text(sectionTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) }
                    item {
                        Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface.copy(alpha = .98f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .32f))) {
                            Column {
                                visible.forEachIndexed { index, category ->
                                    SettingsCategoryRow(category) { detail = category.id }
                                    if (index < visible.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                                }
                            }
                        }
                    }
                }
            }
            if (query.isNotBlank() && categories.none { it.title.lowercase().contains(query) || it.subtitle.lowercase().contains(query) }) item { Text("No matching settings", Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private data class SettingsCategory(val id: String, val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)

@Composable
private fun SettingsCategoryRow(category: SettingsCategory, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(category.color.copy(alpha = .13f)), contentAlignment = Alignment.Center) { Icon(category.icon, null, tint = category.color, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) { Text(category.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(category.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsDetailPanel(title: String, subtitle: String, kind: String, onBack: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("‹ Back") }; Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)) }
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        Spacer(Modifier.height(8.dp))
        when (kind) {
            "account" -> SettingsDetailCard("Profile & Account", "Username, bio, profile photo and account information", Icons.Default.Person, Color(0xFF2F8CFF), onOpen)
            "privacy" -> SettingsDetailCard("Privacy Settings", "Control profile, online status, posts, Status and photos", Icons.Default.Shield, Color(0xFF25B864), onOpen)
            "notifications" -> SettingsDetailCard("Notification Preferences", "Open the existing notification settings and controls", Icons.Default.Notifications, Color(0xFF9B5CFF), onOpen)
            "chat" -> SettingsDetailCard("Open Chat Settings", "Your existing chat settings remain in Chat and are not duplicated here", Icons.Default.ChatBubble, Color(0xFF19A9F5)) { onBack() }
            "stories" -> { SettingsDetailCard("Stories & Status Preferences", "Replies, reactions, mentions and archive", Icons.Default.AutoStories, Color(0xFFFF8A1F)) { }; Spacer(Modifier.height(8.dp)); SettingsInfoCard("Feature-local controls", "Stories and Status controls remain with the existing feature so we do not create duplicate settings.") }
            "media" -> SettingsInfoCard("Media & Storage", "Downloads, saved media, storage usage and cache controls will be grouped here without changing existing media behavior.")
            "data" -> SettingsInfoCard("Data & Network", "Data usage and upload/download preferences will be grouped here without changing existing network behavior.")
            "accessibility" -> SettingsInfoCard("Accessibility", "Text size, readable contrast and accessible presentation controls will be grouped here.")
            "help" -> SettingsInfoCard("Help & Support", "Help center, report a problem and support entry points.")
            "about" -> SettingsInfoCard("About FYNX", "Version information, terms and privacy information.")
        }
    }
}

@Composable
private fun SettingsDetailCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Card(onClick = onClick, Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .13f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsInfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f))) { Column(Modifier.padding(16.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(4.dp)); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }
}

@Composable
private fun AppearanceSettingsDetail(currentAppearance: String, currentAccent: FynxAccent, onBack: () -> Unit, onAppearanceChanged: (String) -> Unit, onAccentChanged: (FynxAccent) -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("‹ Back") }; Text("Appearance", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)) }
        Text("Theme, colors and app style", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(12.dp))
        Text("Theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
        Card(Modifier.fillMaxWidth().padding(top = 6.dp), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f))) {
            listOf("System", "Light", "Dark", "Black AMOLED").forEach { option ->
                Row(Modifier.fillMaxWidth().clickable { onAppearanceChanged(option) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (option == "Light") Icons.Default.Settings else if (option == "Dark") Icons.Default.Settings else if (option == "Black AMOLED") Icons.Default.Settings else Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp)); Text(option, Modifier.weight(1f)); RadioButton(selected = currentAppearance == option, onClick = { onAppearanceChanged(option) })
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Accent Color", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
        Card(Modifier.fillMaxWidth().padding(top = 6.dp), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f))) {
            FynxAccent.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().clickable { onAccentChanged(option) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(option.primary)); Spacer(Modifier.width(12.dp)); Text(if (option == FynxAccent.Blue) "FYNX Blue" else if (option == FynxAccent.Purple) "FYNX Purple" else "Charcoal", Modifier.weight(1f)); RadioButton(selected = currentAccent == option, onClick = { onAccentChanged(option) })
                }
            }
        }
    }
}
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
