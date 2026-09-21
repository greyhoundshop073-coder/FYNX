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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { settingsOpen = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(24.dp))
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(surface), border = BorderStroke(1.dp, outline)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (remotePhotoId != null) FynxRemoteProfileAvatar(remotePhotoId, profile.displayName, Modifier.size(92.dp).clip(CircleShape)) else if (!remoteProfileLoaded) FynxProfileImage(profile.displayName, photo, Modifier.size(92.dp).clip(CircleShape)) else FynxAvatar(profile.displayName, Modifier.size(92.dp).clip(CircleShape))
                        Spacer(Modifier.width(18.dp))
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            ProfileStat("Posts", formatProfileCount(postCount), Modifier.weight(1f))
                            ProfileStat("Followers", formatProfileCount(followerCount ?: 0), Modifier.weight(1f)) { openConnections("Followers") }
                            ProfileStat("Following", formatProfileCount(followingCount ?: 0), Modifier.weight(1f)) { openConnections("Following") }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(profile.displayName.ifBlank { "FYNX User" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("@${profile.username.removePrefix("@")}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(profile.bio.ifBlank { "Welcome to FYNX" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
                    if (description.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth()) }
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { editing = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit profile")
                    }
                }
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
    var language by remember { mutableStateOf(FynxPreferencesStore.loadLanguage(context)) }
    var showAppearance by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }
    var showChatPersonalization by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showSimpleInfo by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }

    val query = search.trim().lowercase()
    fun visible(title: String, description: String): Boolean =
        query.isBlank() || title.lowercase().contains(query) || description.lowercase().contains(query)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Spacer(Modifier.width(4.dp))
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search settings") },
            placeholder = { Text("Search settings") },
            shape = RoundedCornerShape(16.dp)
        )

        HorizontalDivider()

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (visible("Account", "username bio profile account phone email password")) {
                item { SettingsSectionTitle("Account") }
                item {
                    SettingsActionCard(
                        "Account",
                        "Profile, username, bio and account information"
                    ) { showSimpleInfo = "Account" }
                }
            }

            if (visible("Privacy & Security", "privacy safety last seen online posts status profile photo messages blocking security")) {
                item { SettingsSectionTitle("Privacy & Security") }
                item {
                    SettingsActionCard(
                        "Privacy & Security",
                        "Visibility, blocking, messages and account privacy"
                    ) { onOpenPrivacy() }
                }
            }

            if (visible("Notifications", "notifications sounds calls badges messages stories groups marketplace money")) {
                item { SettingsSectionTitle("Notifications & Sounds") }
                item {
                    SettingsActionCard(
                        "Notifications",
                        "Messages, calls, groups, Stories, Marketplace and Money alerts"
                    ) { onOpenNotifications() }
                }
            }

            if (visible("Chat Settings", "chat wallpaper night mode animations stickers emoji read receipts")) {
                item { SettingsSectionTitle("Chat Settings") }
                item {
                    SettingsActionCard(
                        "Chat & personalization",
                        "Wallpaper, night mode, animations, stickers and emoji"
                    ) { showChatPersonalization = true }
                }
                item {
                    SettingsActionCard(
                        "Read receipts",
                        if (settings.readReceipts) "On" else "Off"
                    ) { showChatPersonalization = true }
                }
            }

            if (visible("Calls", "voice video calls ringtone data privacy")) {
                item { SettingsSectionTitle("Calls") }
                item {
                    SettingsActionCard(
                        "Calls",
                        "Voice and video call preferences"
                    ) { showSimpleInfo = "Calls" }
                }
            }

            if (visible("Friends & Groups", "friends groups requests invitations permissions")) {
                item { SettingsSectionTitle("Social") }
                item {
                    SettingsActionCard(
                        "Friends & Groups",
                        "Friend requests, group invitations and social controls"
                    ) { showSimpleInfo = "Friends & Groups" }
                }
            }

            if (visible("Stories & Status", "stories status audience replies sharing")) {
                item {
                    SettingsActionCard(
                        "Stories & Status",
                        "Audience, replies and sharing controls"
                    ) { onOpenPrivacy() }
                }
            }

            if (visible("Camera & Media", "camera photos videos uploads downloads quality")) {
                item { SettingsSectionTitle("Camera & Media") }
                item {
                    SettingsActionCard(
                        "Camera & Media",
                        "Camera, photos, videos and media handling"
                    ) { showSimpleInfo = "Camera & Media" }
                }
            }

            if (visible("Marketplace", "marketplace buying selling orders shipping returns disputes")) {
                item { SettingsSectionTitle("FYNX Features") }
                item {
                    SettingsActionCard(
                        "Marketplace",
                        "Buying, selling, orders, shipping, returns and disputes"
                    ) { showSimpleInfo = "Marketplace" }
                }
            }

            if (visible("Payments & Money", "money wallet payments transactions alerts")) {
                item {
                    SettingsActionCard(
                        "Payments & Money",
                        "Wallet, payments, transactions and money alerts"
                    ) { showSimpleInfo = "Payments & Money" }
                }
            }

            if (visible("FYNX AI", "assistant search recommendations translation media ai")) {
                item {
                    SettingsActionCard(
                        "FYNX AI",
                        "Assistant, search, recommendations and AI tools"
                    ) { showSimpleInfo = "FYNX AI" }
                }
            }

            if (visible("Search", "search history suggestions discovery")) {
                item {
                    SettingsActionCard(
                        "Search",
                        "Search and discovery preferences"
                    ) { showSimpleInfo = "Search" }
                }
            }

            if (visible("Appearance", "light dark system theme colors accent")) {
                item { SettingsSectionTitle("Appearance") }
                item {
                    SettingsActionCard("Appearance", appearance) { showAppearance = true }
                }
                item {
                    SettingsActionCard("Colors & accent", accent.name) { showColors = true }
                }
            }

            if (visible("Data & Storage", "storage cache downloads mobile data wifi")) {
                item { SettingsSectionTitle("Data & Storage") }
                item {
                    SettingsActionCard(
                        "Data & Storage",
                        "Downloads, storage and media usage"
                    ) { showSimpleInfo = "Data & Storage" }
                }
            }

            if (visible("Language", "app language translation")) {
                item { SettingsSectionTitle("General") }
                item {
                    SettingsActionCard("Language", language) { showLanguage = true }
                }
            }

            if (visible("Accessibility", "text size contrast motion accessibility")) {
                item {
                    SettingsActionCard(
                        "Accessibility",
                        "Text, contrast, motion and accessibility preferences"
                    ) { showSimpleInfo = "Accessibility" }
                }
            }

            if (visible("Devices & Sessions", "devices sessions logins connected devices")) {
                item { SettingsSectionTitle("Security") }
                item {
                    SettingsActionCard(
                        "Devices & Sessions",
                        "Manage where your FYNX account is signed in"
                    ) { showSimpleInfo = "Devices & Sessions" }
                }
            }

            if (visible("Connected Accounts", "google connected accounts integrations")) {
                item {
                    SettingsActionCard(
                        "Connected Accounts",
                        "Manage accounts and integrations connected to FYNX"
                    ) { showSimpleInfo = "Connected Accounts" }
                }
            }

            if (visible("Help & Support", "help support report problem")) {
                item { SettingsSectionTitle("Support") }
                item {
                    SettingsActionCard(
                        "Help & Support",
                        "Get help or report a problem"
                    ) { showSimpleInfo = "Help & Support" }
                }
            }

            if (visible("About FYNX", "version terms privacy")) {
                item {
                    SettingsActionCard(
                        "About FYNX",
                        "Version, terms and privacy information"
                    ) { showSimpleInfo = "About FYNX" }
                }
            }

            if (query.isNotBlank() && !listOf(
                    "Account", "Privacy & Security", "Notifications", "Chat Settings",
                    "Calls", "Friends & Groups", "Stories & Status", "Camera & Media",
                    "Marketplace", "Payments & Money", "FYNX AI", "Search", "Appearance",
                    "Data & Storage", "Language", "Accessibility", "Devices & Sessions",
                    "Connected Accounts", "Help & Support", "About FYNX"
                ).any { visible(it, "") }
            ) {
                item {
                    Text(
                        "No matching settings",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Settings are grouped here so users do not have to search Home for controls.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    if (showAppearance) {
        AppearanceDialog(
            appearance,
            {
                appearance = it
                FynxPreferencesStore.saveAppearance(context, it)
                onAppearanceChanged(it)
                showAppearance = false
            },
            { showAppearance = false }
        )
    }
    if (showColors) {
        AccentDialog(
            accent,
            {
                accent = it
                FynxPreferencesStore.saveAccent(context, it)
                onAccentChanged(it)
                showColors = false
            },
            { showColors = false }
        )
    }
    if (showChatPersonalization) {
        ChatPersonalizationDialog(settings, onSettingsChange) { showChatPersonalization = false }
    }
    if (showLanguage) {
        LanguageDialog(
            language,
            {
                language = it
                FynxPreferencesStore.saveLanguage(context, it)
                showLanguage = false
            },
            { showLanguage = false }
        )
    }
    showSimpleInfo?.let { title ->
        SettingsInfoDialog(title) { showSimpleInfo = null }
    }
}

@Composable
private fun LanguageDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf("Device default", "English")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Language") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = current == option,
                            onClick = { onSelect(option) }
                        )
                        Text(option)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun SettingsInfoDialog(title: String, onDismiss: () -> Unit) {
    val description = when (title) {
        "Account" -> "Account controls are kept with your FYNX profile identity. Use Edit profile for profile details."
        "Calls" -> "Call controls belong to the Calls system. Privacy and notification controls remain available from Privacy & Security and Notifications."
        "Friends & Groups" -> "Friend and group privacy controls are account-scoped and are kept with Privacy & Security and Notifications."
        "Camera & Media" -> "Camera and media access follows Android's permission model. FYNX requests sensitive camera or microphone access only when the related feature needs it."
        "Marketplace" -> "Marketplace controls cover buying, selling, orders, shipping, returns and disputes. Transaction authorization remains server-side."
        "Payments & Money" -> "Money controls cover wallet, payments, transactions and alerts. Financial actions remain server-authoritative."
        "FYNX AI" -> "FYNX AI controls cover the Assistant, search, recommendations, translation and AI media tools. Provider credentials stay off the Android client."
        "Search" -> "Search preferences cover discovery behavior and search history."
        "Data & Storage" -> "Data and storage controls cover media downloads, cache and network usage."
        "Accessibility" -> "Accessibility controls are grouped here so they can be applied consistently across FYNX."
        "Devices & Sessions" -> "Device and session management is reserved for authenticated server-backed session controls."
        "Connected Accounts" -> "Connected-account controls belong here when an account integration is enabled."
        "Help & Support" -> "Help, support and problem reporting are available from one central place."
        else -> "FYNX keeps this area in the central Settings surface so related controls have one predictable home."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable private fun SettingsSectionTitle(title: String) { Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)) }
@Composable private fun SettingsActionCard(title: String, value: String, onClick: () -> Unit) { Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }; Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun ProfileConnectionsDialog(type:String,users:List<FynxProfileRemoteClient.ConnectionUser>,loading:Boolean,error:String?,onDismiss:()->Unit){AlertDialog(onDismissRequest={if(!loading)onDismiss()},title={Text(type)},text={Box(Modifier.fillMaxWidth().heightIn(min=80.dp,max=420.dp)){when{loading->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};error!=null->Text(error,color=MaterialTheme.colorScheme.error);users.isEmpty()->Text("No ${type.lowercase()} yet.",color=MaterialTheme.colorScheme.onSurfaceVariant);else->LazyColumn(verticalArrangement=Arrangement.spacedBy(2.dp)){items(users){user->ListItem(headlineContent={Text(user.displayName.ifBlank{user.username})},supportingContent={Text("@${user.username.removePrefix("@").trim()}")})}}}}},confirmButton={TextButton(onClick=onDismiss,enabled=!loading){Text("Done")}})}
