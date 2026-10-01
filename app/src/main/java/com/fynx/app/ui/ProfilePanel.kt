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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val profileListState = rememberSaveable(session.username, saver = LazyListState.Saver) { LazyListState() }
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
    val cachedStats = remember(session.username) { session.username?.let { FynxPreferencesStore.loadRemoteProfileStats(context, it) } }
    var postCount by remember(session.username) { mutableStateOf(cachedStats?.postCount ?: 0) }
    var followerCount by remember(session.username) { mutableStateOf(cachedStats?.followerCount) }
    var followingCount by remember(session.username) { mutableStateOf(cachedStats?.followingCount) }
    var connectionType by remember { mutableStateOf<String?>(null) }
    var connections by remember { mutableStateOf<List<FynxProfileRemoteClient.ConnectionUser>>(emptyList()) }
    var connectionsLoading by remember { mutableStateOf(false) }
    var connectionsError by remember { mutableStateOf<String?>(null) }

    fun openConnections(type: String) {
        if (connectionsLoading && connectionType == type) return
        connectionType = type; connections = emptyList(); connectionsError = null; connectionsLoading = true
        scope.launch {
            try {
                val result = if (type == "Followers") FynxProfileRemoteClient.followers(context) else FynxProfileRemoteClient.following(context)
                result.onSuccess { connections = it }.onFailure { connectionsError = it.message ?: "Could not load connections." }
            } finally { connectionsLoading = false }
        }
    }

    LaunchedEffect(session.username) {
        if (session.state == AuthState.SIGNED_IN && !session.username.isNullOrBlank()) {
            FynxProfileRemoteClient.get(context, session.username).onSuccess { remote ->
                postCount = remote.postCount
                remote.followerCount?.let { followerCount = it }
                remote.followingCount?.let { followingCount = it }
                FynxPreferencesStore.saveRemoteProfileStats(context, remote.username, remote.postCount, remote.followerCount, remote.followingCount)
                remotePhotoId = remote.profilePhotoMediaId
                remoteVerified = remote.verified
                remoteProfileLoaded = true
                profile = profile.copy(displayName = remote.displayName.ifBlank { profile.displayName }, username = remote.username.ifBlank { profile.username }, bio = remote.bio.ifBlank { profile.bio })
            }.onFailure { syncError = it.message }
        }
    }

    if (editing) {
        EditProfilePanel(profile, description, photo, syncing, syncError, onPhotoChanged = { photo = it }, onSave = { updatedProfile, updatedDescription, updatedPhoto ->
            scope.launch {
                syncing = true; syncError = null
                val originalPhoto = FynxPreferencesStore.loadProfilePhoto(context)
                var photoId: String? = null
                if (updatedPhoto != originalPhoto && updatedPhoto != null) {
                    val uri = runCatching { Uri.parse(updatedPhoto) }.getOrNull()
                    if (uri != null) FynxProfileRemoteClient.uploadProfilePhoto(context, uri).onSuccess { photoId = it }.onFailure { syncError = it.message ?: "Profile photo upload failed." }
                }
                val removeRemotePhoto = updatedPhoto == null && (originalPhoto != null || remotePhotoId != null)
                if (syncError == null) FynxProfileRemoteClient.update(context, updatedProfile.displayName, updatedProfile.username, updatedProfile.bio, profilePhotoMediaId = photoId, removeProfilePhoto = removeRemotePhoto)
                    .onSuccess { remote ->
                        profile = updatedProfile; description = updatedDescription; photo = updatedPhoto; remotePhotoId = remote.profilePhotoMediaId
                        FynxPreferencesStore.saveProfile(context, updatedProfile); FynxPreferencesStore.saveDescription(context, updatedDescription); FynxPreferencesStore.saveProfilePhoto(context, updatedPhoto); editing = false
                    }.onFailure { syncError = it.message ?: "Profile update failed." }
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
    LazyColumn(state = profileListState, modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (remotePhotoId != null) FynxRemoteProfileAvatar(remotePhotoId, profile.displayName, Modifier.size(80.dp).clip(CircleShape).clickable { showProfilePhoto = true }, ownerUsername = profile.username)
                else if (!remoteProfileLoaded) FynxProfileImage(profile.displayName, photo, Modifier.size(80.dp).clip(CircleShape).clickable { showProfilePhoto = true })
                else FynxAvatar(profile.displayName, Modifier.size(80.dp).clip(CircleShape).clickable { showProfilePhoto = true })
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(profile.displayName.ifBlank { "FYNX User" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (remoteVerified) { Spacer(Modifier.width(5.dp)); Icon(Icons.Default.Verified, contentDescription = "Verified FYNX official account", tint = androidx.compose.ui.graphics.Color(0xFF1877F2), modifier = Modifier.size(18.dp)) }
                }
                Text("@${profile.username.removePrefix("@")}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (profile.bio.isNotBlank()) { Spacer(Modifier.height(5.dp)); Text(profile.bio, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.9f)) }
                if (description.isNotBlank()) { Spacer(Modifier.height(3.dp)); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.9f)) }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    ProfileStat("Posts", formatProfileCount(postCount), Modifier.weight(1f))
                    ProfileStat("Followers", formatProfileCount(followerCount ?: 0), Modifier.weight(1f)) { openConnections("Followers") }
                    ProfileStat("Following", formatProfileCount(followingCount ?: 0), Modifier.weight(1f)) { openConnections("Following") }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { editing = true }, modifier = Modifier.weight(1f).heightIn(min = 44.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Edit profile") }
                    OutlinedButton(onClick = { settingsOpen = true }, modifier = Modifier.weight(1f).heightIn(min = 44.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Settings, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Settings") }
                }
            }
        }
        item { session.username?.takeIf { it.isNotBlank() }?.let { username -> FynxProfileContentSection(username = username, onError = { if (it != null) syncError = it }) } }
        item { Card(onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(surface), border = BorderStroke(1.dp, outline)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Privacy & Safety", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Control who can see your profile, posts, Status and photos", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }; Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        if (session.state == AuthState.SIGNED_IN) item { OutlinedButton(onClick = { if (onSignOut != null) onSignOut() else { FynxAuthStore.clear(context); (context as? Activity)?.recreate() } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Sign out") } }
    }
    connectionType?.let { type -> ProfileConnectionsDialog(type, connections, connectionsLoading, connectionsError, onRetry = { openConnections(type) }) { connectionType = null } }
    if (showProfilePhoto) FynxProfilePhotoViewer(remotePhotoId, photo, profile.displayName) { showProfilePhoto = false }
}

@Composable private fun ProfileStat(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = { Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center); Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) } }
    if (onClick != null) TextButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(0.dp)) { content() } else Box(modifier, contentAlignment = Alignment.Center) { content() }
}

private fun formatProfileCount(value: Int): String = when { value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000f).replace(".0M", "M"); value >= 1_000 -> String.format("%.1fK", value / 1_000f).replace(".0K", "K"); else -> value.toString() }

@Composable
private fun FynxProfilePhotoViewer(mediaId: String?, localPhotoUri: String?, name: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(mediaId, localPhotoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(mediaId, localPhotoUri) { bitmap = withContext(kotlinx.coroutines.Dispatchers.IO) { when { !mediaId.isNullOrBlank() -> FynxProductionMessaging.cacheRemoteMedia(context, mediaId, "/api/social/media/$mediaId").getOrNull()?.let { uri -> runCatching { context.contentResolver.openInputStream(uri).use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull() }; !localPhotoUri.isNullOrBlank() -> runCatching { context.contentResolver.openInputStream(Uri.parse(localPhotoUri)).use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull(); else -> null } } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) { Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) { if (bitmap != null) androidx.compose.foundation.Image(bitmap!!.asImageBitmap(), contentDescription = "Profile photo", modifier = Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Fit) else FynxAvatar(name, Modifier.size(120.dp)); TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) { Text("Close", color = androidx.compose.ui.graphics.Color.White) } } }
}

@Composable private fun ProfileInfoRow(title: String, value: String) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis) } }

@Composable private fun EditProfilePanel(profile: FynxProfile, description: String, photoUri: String?, syncing: Boolean, syncError: String?, onPhotoChanged: (String?) -> Unit, onSave: (FynxProfile, String, String?) -> Unit, onCancel: () -> Unit) {
    var displayName by remember(profile) { mutableStateOf(profile.displayName) }
    var username by remember(profile) { mutableStateOf(profile.username) }
    var bio by remember(profile) { mutableStateOf(profile.bio) }
    var descriptionText by remember(description) { mutableStateOf(description) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) onPhotoChanged(uri.toString()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Edit profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(displayName, { displayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(bio, { bio = it }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        OutlinedTextField(descriptionText, { descriptionText = it }, label = { Text("About me") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { launcher.launch("image/*") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AddAPhoto, null); Spacer(Modifier.width(6.dp)); Text("Photo") }; Button(onClick = { onSave(profile.copy(displayName = displayName, username = username, bio = bio), descriptionText, photoUri) }, enabled = !syncing, modifier = Modifier.weight(1f)) { if (syncing) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Save") } }
        OutlinedButton(onClick = onCancel, enabled = !syncing, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        syncError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
