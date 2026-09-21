package com.fynx.app.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
        SettingsPanel(settings = settings, onSettingsChange = { settings = it; FynxPreferencesStore.saveSettings(context, it) }, onBack = { settingsOpen = false; onSettingsClosed() }, onAppearanceChanged = onAppearanceChanged, onAccentChanged = onAccentChanged, onOpenPrivacy = onOpenPrivacy, onOpenNotifications = onOpenNotifications)
        return
    }

    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = .45f)
    LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = surface),
                border = BorderStroke(1.dp, outline)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (remotePhotoId != null) {
                        FynxRemoteProfileAvatar(remotePhotoId, profile.displayName, Modifier.size(80.dp).clip(CircleShape))
                    } else if (!remoteProfileLoaded) {
                        FynxProfileImage(profile.displayName, photo, Modifier.size(80.dp).clip(CircleShape))
                    } else {
                        FynxAvatar(profile.displayName, Modifier.size(80.dp).clip(CircleShape))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        profile.displayName.ifBlank { "FYNX User" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
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
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(surface), border = BorderStroke(1.dp, outline)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    ProfileInfoRow("Username", "@${profile.username.removePrefix("@")}")
                    ProfileInfoRow("Profile photo", if (remotePhotoId == null && (remoteProfileLoaded || photo == null)) "Not set" else "Set")
                    ProfileInfoRow("Account", if (session.state == AuthState.SIGNED_IN) "Signed in" else "Signed out")
                }
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
    connectionType?.let { type -> ProfileConnectionsDialog(type, connections, connectionsLoading, connectionsError) { connectionType = null } }
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
    settings: FynxSettings,
    onSettingsChange: (FynxSettings) -> Unit,
    onBack: () -> Unit,
    onAppearanceChanged: (String) -> Unit = {},
    onAccentChanged: (FynxAccent) -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenNotifications: () -> Unit = {}
) {
    val context = LocalContext.current
    var appearance by remember { mutableStateOf(FynxPreferencesStore.loadAppearance(context)) }
    var accent by remember { mutableStateOf(FynxPreferencesStore.loadAccent(context)) }
    var showAppearance by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }
    var showChatPersonalization by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }

    val query = search.trim().lowercase()
    fun visible(title: String, description: String): Boolean =
        query.isBlank() || title.lowercase().contains(query) || description.lowercase().contains(query)

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .widthIn(max = 720.dp)
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Spacer(Modifier.width(4.dp))
            Text(
                "Settings & privacy",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search settings") },
            placeholder = { Text("Search settings") },
            shape = RoundedCornerShape(16.dp)
        )
        HorizontalDivider()
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (visible("Account", "Username bio profile account")) {
                item { SettingsSectionTitle("Account") }
                item { SettingsActionCard("Account & profile", "Username, bio and profile information") { onBack(); onSettingsChange(settings) } }
            }
            if (visible("Privacy & Safety", "Profile online posts Status photo visibility")) {
                item { SettingsSectionTitle("Privacy & Security") }
                item { SettingsActionCard("Privacy & Safety", "Profile, online, posts, Status and photo visibility") { onOpenPrivacy() } }
            }
            if (visible("Notifications", "Sounds calls badges message alerts")) {
                item { SettingsSectionTitle("Notifications") }
                item { SettingsActionCard("Notifications", "Message alerts, sounds and notification controls") { onOpenNotifications() } }
            }
            if (visible("Chat & personalization", "Wallpapers night mode animations stickers emoji read receipts")) {
                item { SettingsSectionTitle("Chat Settings") }
                item { SettingsActionCard("Chat & personalization", "Wallpapers, night mode, animations, stickers and emoji") { showChatPersonalization = true } }
                item { SettingsActionCard("Read receipts", if (settings.readReceipts) "On • managed in Chat settings" else "Off • managed in Chat settings") { showChatPersonalization = true } }
                item { SettingsActionCard("Story replies", if (settings.storyReplies) "On • managed in Privacy & Safety" else "Off • managed in Privacy & Safety") { onOpenPrivacy() } }
            }
            if (visible("Appearance", "Light dark system theme")) {
                item { SettingsSectionTitle("Appearance") }
                item { SettingsActionCard("Appearance", appearance) { showAppearance = true } }
                item { SettingsActionCard("Colors & accent", accent.name) { showColors = true } }
            }
            if (visible("Language", "App language English")) {
                item { SettingsSectionTitle("General") }
                item { SettingsActionCard("Language", "English") { } }
            }
            if (query.isNotBlank() && !listOf("Account","Privacy & Safety","Notifications","Chat & personalization","Appearance","Language").any { visible(it, "") }) {
                item {
                    Text(
                        "No matching settings",
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    if (showAppearance) AppearanceDialog(appearance, { appearance = it; FynxPreferencesStore.saveAppearance(context, it); onAppearanceChanged(it); showAppearance = false }, { showAppearance = false })
    if (showColors) AccentDialog(accent, { accent = it; FynxPreferencesStore.saveAccent(context, it); onAccentChanged(it); showColors = false }, { showColors = false })
    if (showChatPersonalization) ChatPersonalizationDialog(settings, onSettingsChange, { showChatPersonalization = false })
}

@Composable private fun SettingsSectionTitle(title: String) { Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)) }
@Composable private fun SettingsActionCard(title: String, value: String, onClick: () -> Unit) { Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }; Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun ProfileConnectionsDialog(type:String,users:List<FynxProfileRemoteClient.ConnectionUser>,loading:Boolean,error:String?,onDismiss:()->Unit){AlertDialog(onDismissRequest={if(!loading)onDismiss()},title={Text(type)},text={Box(Modifier.fillMaxWidth().heightIn(min=80.dp,max=420.dp)){when{loading->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};error!=null->Text(error,color=MaterialTheme.colorScheme.error);users.isEmpty()->Text("No ${type.lowercase()} yet.",color=MaterialTheme.colorScheme.onSurfaceVariant);else->LazyColumn(verticalArrangement=Arrangement.spacedBy(2.dp)){items(users){user->ListItem(headlineContent={Text(user.displayName.ifBlank{user.username})},supportingContent={Text("@${user.username.removePrefix("@").trim()}")})}}}}},confirmButton={TextButton(onClick=onDismiss,enabled=!loading){Text("Done")}})}
