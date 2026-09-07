package com.fynx.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ProfilePanel(session: AuthSession = AuthSession(), openSettingsInitially: Boolean = false, onSettingsClosed: () -> Unit = {}, onSignOut: () -> Unit = {}, onAppearanceChanged: (String) -> Unit = {}, onAccentChanged: (FynxAccent) -> Unit = {}, onOpenPrivacy: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(openSettingsInitially) }
    var profile by remember(session.username) { mutableStateOf(FynxPreferencesStore.loadProfile(context, session.username)) }
    var description by remember(session.username) { mutableStateOf(FynxPreferencesStore.loadDescription(context)) }
    var photo by remember(session.username) { mutableStateOf(FynxPreferencesStore.loadProfilePhoto(context)) }
    var syncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(FynxPreferencesStore.loadSettings(context)) }
    var postCount by remember { mutableStateOf(0) }
    var followerCount by remember { mutableStateOf<Int?>(null) }
    var followingCount by remember { mutableStateOf<Int?>(null) }
    var connectionType by remember { mutableStateOf<String?>(null) }
    var connections by remember { mutableStateOf<List<FynxProfileRemoteClient.ConnectionUser>>(emptyList()) }
    var connectionsLoading by remember { mutableStateOf(false) }
    var connectionsError by remember { mutableStateOf<String?>(null) }
    fun openConnections(type: String) {
        connectionType = type; connections = emptyList(); connectionsError = null; connectionsLoading = true
        scope.launch { val result = if (type == "Followers") FynxProfileRemoteClient.followers(context) else FynxProfileRemoteClient.following(context); result.onSuccess { connections = it }.onFailure { connectionsError = it.message ?: "Could not load connections." }; connectionsLoading = false }
    }
    LaunchedEffect(session.username) {
        if (session.state == AuthState.SIGNED_IN && !session.username.isNullOrBlank()) {
            FynxProfileRemoteClient.get(context, session.username).onSuccess { remote ->
                postCount = remote.postCount; followerCount = remote.followerCount; followingCount = remote.followingCount
                profile = profile.copy(displayName = remote.displayName.ifBlank { profile.displayName }, username = remote.username.ifBlank { profile.username }, bio = remote.bio.ifBlank { profile.bio })
            }.onFailure { syncError = it.message }
        }
    }
    if (editing) { EditProfilePanel(profile, description, photo, syncing, syncError, onPhotoChanged = { uri -> photo = uri }, onSave = { updatedProfile, updatedDescription, updatedPhoto ->
        scope.launch {
            syncing = true; syncError = null
            var photoId: String? = null
            if (updatedPhoto != photo || (updatedPhoto != null && updatedPhoto != FynxPreferencesStore.loadProfilePhoto(context))) {
                val uri = updatedPhoto?.let(Uri::parse)
                if (uri != null) FynxProfileRemoteClient.uploadProfilePhoto(context, uri).onSuccess { photoId = it }.onFailure { syncError = it.message }
            }
            if (syncError == null) FynxProfileRemoteClient.update(context, updatedProfile.displayName, updatedProfile.username, updatedProfile.bio, profilePhotoMediaId = photoId).onSuccess {
                profile = updatedProfile; description = updatedDescription; photo = updatedPhoto
                FynxPreferencesStore.saveProfile(context, updatedProfile); FynxPreferencesStore.saveDescription(context, updatedDescription); FynxPreferencesStore.saveProfilePhoto(context, updatedPhoto); editing = false
            }.onFailure { syncError = it.message }
            syncing = false
        }
    }, onCancel = { editing = false }); return }
    if (settingsOpen) { SettingsPanel(settings = settings, onSettingsChange = { settings = it; FynxPreferencesStore.saveSettings(context, it) }, onBack = { settingsOpen = false; onSettingsClosed() }, onAppearanceChanged = onAppearanceChanged, onAccentChanged = onAccentChanged, onOpenPrivacy = onOpenPrivacy); return }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .6f))) { Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { FynxProfileImage(profile.displayName, photo, Modifier.size(108.dp)); Spacer(Modifier.height(12.dp)); Text(profile.displayName, style = MaterialTheme.typography.headlineSmall); Text("@${profile.username.removePrefix("@")}", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)); Text(profile.bio.ifBlank { "Welcome to FYNX" }, color = MaterialTheme.colorScheme.onSurfaceVariant); if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)); if (session.state == AuthState.SIGNED_IN) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { ProfileStat("Posts", formatProfileCount(postCount)); ProfileStat("Followers", formatProfileCount(followerCount ?: 0)) { openConnections("Followers") }; ProfileStat("Following", formatProfileCount(followingCount ?: 0)) { openConnections("Following") } } } Spacer(Modifier.height(14.dp)); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Button(onClick = { editing = true }, shape = FynxDesign.ControlShape) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Edit profile") }; OutlinedButton(onClick = { settingsOpen = true }, shape = FynxDesign.ControlShape) { Icon(Icons.Default.Settings, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Settings") } } } } }
        item { ProfileInfoCard("Username", "@${profile.username.removePrefix("@")}") }
        item { ProfileInfoCard("Profile photo", if (photo == null) "Not set" else "Photo selected") }
        item { ProfileInfoCard("Account", if (session.state == AuthState.SIGNED_IN) "Signed in" else "Signed out") }
        if (session.state == AuthState.SIGNED_IN) item { OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape) { Text("Sign out") } }
    }
    connectionType?.let { type -> ProfileConnectionsDialog(type, connections, connectionsLoading, connectionsError) { connectionType = null } }
}


@Composable private fun ProfileStat(label:String,value:String,onClick:(()->Unit)?=null){Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){if(onClick!=null)TextButton(onClick=onClick,contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(value,style=MaterialTheme.typography.titleLarge);Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelMedium)}}else{Text(value,style=MaterialTheme.typography.titleLarge);Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelMedium)}}}
private fun formatProfileCount(value:Int):String=when{value>=1_000_000->String.format("%.1fM",value/1_000_000f).replace(".0M","M");value>=1_000->String.format("%.1fK",value/1_000f).replace(".0K","K");else->value.toString()}
@Composable private fun ProfileInfoCard(title: String, value: String) { Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

    connectionType?.let { type -> ProfileConnectionsDialog(type, connections, connectionsLoading, connectionsError) { connectionType = null } }

@Composable private fun EditProfilePanel(profile: FynxProfile, description: String, photoUri: String?, syncing: Boolean, syncError: String?, onPhotoChanged: (String?) -> Unit, onSave: (FynxProfile, String, String?) -> Unit, onCancel: () -> Unit) {
    var displayName by remember(profile) { mutableStateOf(profile.displayName) }; var username by remember(profile) { mutableStateOf(profile.username) }; var bio by remember(profile) { mutableStateOf(profile.bio) }; var about by remember(profile, description) { mutableStateOf(description) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if (uri != null) onPhotoChanged(uri.toString()) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { TextButton(enabled = !syncing, onClick = onCancel) { Text("Cancel") }; Text("Edit profile", style = MaterialTheme.typography.titleLarge); TextButton(enabled = !syncing, onClick = { onSave(profile.copy(displayName = displayName.trim().ifBlank { profile.displayName }, username = username.trim().removePrefix("@").replace(" ", "").ifBlank { profile.username }, bio = bio.trim()), about.trim(), photoUri) }) { Text(if (syncing) "Saving…" else "Save") } }
        syncError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        HorizontalDivider(); Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { FynxProfileImage(displayName, photoUri, Modifier.size(112.dp)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { OutlinedButton(enabled = !syncing, onClick = { picker.launch("image/*") }) { Icon(Icons.Default.AddAPhoto, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(if (photoUri == null) "Add photo" else "Change photo") }; if (photoUri != null) { Spacer(Modifier.width(6.dp)); TextButton(enabled = !syncing, onClick = { onPhotoChanged(null) }) { Text("Remove") } } }
        OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), label = { Text("Display name") }, singleLine = true, shape = FynxDesign.ControlShape)
        OutlinedTextField(username.removePrefix("@"), { username = it.removePrefix("@").replace(" ", "") }, Modifier.fillMaxWidth(), label = { Text("Username") }, prefix = { Text("@") }, singleLine = true, shape = FynxDesign.ControlShape)
        OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text("Bio") }, minLines = 3, shape = FynxDesign.ControlShape)
        OutlinedTextField(about, { about = it }, Modifier.fillMaxWidth(), label = { Text("About / description") }, minLines = 3, maxLines = 6, shape = FynxDesign.ControlShape)
    }
}

@Composable
fun SettingsPanel(settings: FynxSettings, onSettingsChange: (FynxSettings) -> Unit, onBack: () -> Unit, onAppearanceChanged: (String) -> Unit = {}, onAccentChanged: (FynxAccent) -> Unit = {}, onOpenPrivacy: () -> Unit = {}) {
    val context = LocalContext.current
    var appearance by remember { mutableStateOf(FynxPreferencesStore.loadAppearance(context)) }
    var accent by remember { mutableStateOf(FynxPreferencesStore.loadAccent(context)) }
    var language by remember { mutableStateOf(FynxPreferencesStore.loadLanguage(context)) }
    var showAppearance by remember { mutableStateOf(false) }; var showColors by remember { mutableStateOf(false) }; var showLanguage by remember { mutableStateOf(false) }; var showAssets by remember { mutableStateOf(false) }
    val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if (uri != null) FynxPreferencesStore.saveAsset(context, uri.toString()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("‹ Back") }; Spacer(Modifier.width(4.dp)); Text("Settings & privacy", style = MaterialTheme.typography.titleLarge) }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SettingSwitchCard("Private profile", settings.privateProfile) { onSettingsChange(settings.copy(privateProfile = it)) } }
            item { SettingSwitchCard("Read receipts", settings.readReceipts) { onSettingsChange(settings.copy(readReceipts = it)) } }
            item { SettingSwitchCard("Story replies", settings.storyReplies) { onSettingsChange(settings.copy(storyReplies = it)) } }
            item { SettingSwitchCard("FYNX notifications", settings.notifications) { onSettingsChange(settings.copy(notifications = it)) } }
            item { SettingsActionCard("Privacy & Safety", "Profile, online, posts, Status and photo visibility") { onOpenPrivacy() } }
            item { SettingsActionCard("Appearance", appearance) { showAppearance = true } }
            item { SettingsActionCard("Colors & accent", accent.name) { showColors = true } }
            item { SettingsActionCard("Assets & media", FynxPreferencesStore.loadAsset(context)?.let { "1 selected asset" } ?: "Choose a device asset") { showAssets = true } }
            item { SettingsActionCard("Language", language) { showLanguage = true } }
        }
    }
    if (showAppearance) AlertDialog(onDismissRequest = { showAppearance = false }, title = { Text("Appearance") }, text = { Column { listOf("System", "Light", "Dark").forEach { option -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = appearance == option, onClick = { appearance = option; FynxPreferencesStore.saveAppearance(context, option); onAppearanceChanged(option) }); Text(option) } } } }, confirmButton = { TextButton(onClick = { showAppearance = false }) { Text("Done") } })
    if (showColors) AlertDialog(onDismissRequest = { showColors = false }, title = { Text("FYNX colors") }, text = { Column { FynxAccent.values().forEach { option -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = accent == option, onClick = { accent = option; FynxPreferencesStore.saveAccent(context, option); onAccentChanged(option) }); Text(option.name) } } } }, confirmButton = { TextButton(onClick = { showColors = false }) { Text("Done") } })
    if (showLanguage) AlertDialog(onDismissRequest = { showLanguage = false }, title = { Text("Language") }, text = { Column { listOf("Device default", "English", "French", "Arabic", "Portuguese").forEach { option -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = language == option, onClick = { language = option; FynxPreferencesStore.saveLanguage(context, option) }); Text(option) } } } }, confirmButton = { TextButton(onClick = { showLanguage = false }) { Text("Done") } })
    if (showAssets) AlertDialog(onDismissRequest = { showAssets = false }, title = { Text("Assets & media") }, text = { Text("Choose a personal image asset for FYNX customization.") }, confirmButton = { TextButton(onClick = { assetPicker.launch("image/*") }) { Text("Choose image") } }, dismissButton = { TextButton(onClick = { showAssets = false }) { Text("Done") } })
}

@Composable private fun SettingSwitchCard(title: String, checked: Boolean, onChange: (Boolean) -> Unit) { Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange) } } }
@Composable private fun SettingsActionCard(title: String, value: String, onClick: () -> Unit) { Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable private fun ProfileConnectionsDialog(type:String,users:List<FynxProfileRemoteClient.ConnectionUser>,loading:Boolean,error:String?,onDismiss:()->Unit){AlertDialog(onDismissRequest={if(!loading)onDismiss()},title={Text(type)},text={Box(Modifier.fillMaxWidth().heightIn(min=80.dp,max=420.dp)){when{loading->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};error!=null->Text(error,color=MaterialTheme.colorScheme.error);users.isEmpty()->Text("No ${type.lowercase()} yet.",color=MaterialTheme.colorScheme.onSurfaceVariant);else->LazyColumn(verticalArrangement=Arrangement.spacedBy(2.dp)){items(users){user->ListItem(headlineContent={Text(user.displayName.ifBlank{user.username})},supportingContent={Text("@${user.username.removePrefix("@").trim()}")})}}}}},confirmButton={TextButton(onClick=onDismiss,enabled=!loading){Text("Done")}})}
