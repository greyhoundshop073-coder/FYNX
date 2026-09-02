package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ProfilePanel(session: AuthSession = AuthSession(), onSignOut: () -> Unit = {}, onAppearanceChanged: (String) -> Unit = {}, onAccentChanged: (FynxAccent) -> Unit = {}) {
    val context = LocalContext.current
    var settingsOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var description by remember(session.username) { mutableStateOf(FynxPreferencesStore.loadDescription(context)) }
    var profile by remember(session.username) { mutableStateOf(FynxPreferencesStore.loadProfile(context, session.username)) }
    var profilePhoto by remember(session.username) { mutableStateOf(FynxPreferencesStore.loadProfilePhoto(context)) }
    var settings by remember { mutableStateOf(FynxPreferencesStore.loadSettings(context)) }

    if (settingsOpen) {
        SettingsPanel(settings, { settings = it; FynxPreferencesStore.saveSettings(context, it) }, { settingsOpen = false }, onAppearanceChanged, onAccentChanged)
        return
    }
    if (editing) {
        EditProfilePanel(profile, description, profilePhoto,
            onPhotoChanged = { profilePhoto = it; FynxPreferencesStore.saveProfilePhoto(context, it) },
            onSave = { updated, updatedDescription -> profile = updated; description = updatedDescription; FynxPreferencesStore.saveProfile(context, updated); FynxPreferencesStore.saveDescription(context, updatedDescription); editing = false },
            onCancel = { editing = false })
        return
    }
    val username = session.username?.let { "@${it.removePrefix("@")}" } ?: "@${profile.username.removePrefix("@")}"
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .6f))) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FynxProfileImage(profile.displayName, profilePhoto, Modifier.size(108.dp)); Spacer(Modifier.height(12.dp))
                    Text(profile.displayName, style = MaterialTheme.typography.headlineSmall); Text(username, color = FynxDesign.TextSecondary); Spacer(Modifier.height(8.dp)); Text(profile.bio.ifBlank { "Welcome to FYNX" }, color = FynxDesign.TextSecondary, maxLines = 3)
                    if (description.isNotBlank()) Text(description, color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(14.dp)); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { editing = true }, shape = FynxDesign.ControlShape) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Edit profile") }; OutlinedButton(onClick = { settingsOpen = true }, shape = FynxDesign.ControlShape) { Icon(Icons.Default.Settings, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Settings") } }
                }
            }
        }
        item { Text("Account", style = MaterialTheme.typography.titleMedium) }
        item { ProfileInfoCard(Icons.Default.Person, "Username", username) }
        item { ProfileInfoCard(Icons.Default.Security, "Account security", if (session.state == AuthState.SIGNED_IN) "Signed in" else "Signed out") }
        item { Text("Privacy & preferences", style = MaterialTheme.typography.titleMedium) }
        item { ProfileInfoCard(Icons.Default.Notifications, "Notifications", if (settings.notifications) "Enabled" else "Disabled") }
        item { ProfileInfoCard(Icons.Default.Lock, "Private profile", if (settings.privateProfile) "On" else "Off") }
        item { ProfileInfoCard(Icons.Default.Visibility, "Read receipts", if (settings.readReceipts) "On" else "Off") }
        if (session.state == AuthState.SIGNED_IN) item { OutlinedButton(onClick = onSignOut, Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape) { Text("Sign out") } }
    }
}

@Composable private fun ProfileInfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) { Row(Modifier.fillMaxWidth().padding(13.dp), Alignment.CenterVertically) { Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SelectedContainer) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, Modifier.padding(9.dp).size(21.dp)) }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.labelLarge); Text(value, color = FynxDesign.TextSecondary) } } }
}

@Composable private fun EditProfilePanel(profile: FynxProfile, description: String, photoUri: String?, onPhotoChanged: (String?) -> Unit, onSave: (FynxProfile, String) -> Unit, onCancel: () -> Unit) {
    var displayName by remember(profile) { mutableStateOf(profile.displayName) }; var username by remember(profile) { mutableStateOf(profile.username) }; var bio by remember(profile) { mutableStateOf(profile.bio) }; var descriptionText by remember(profile, description) { mutableStateOf(description) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if (uri != null) onPhotoChanged(uri.toString()) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { TextButton(onClick = onCancel) { Text("Cancel") }; Text("Edit profile", style = MaterialTheme.typography.titleLarge); TextButton(onClick = { onSave(profile.copy(displayName = displayName.trim().ifEmpty { profile.displayName }, username = username.trim().removePrefix("@").replace(" ", "").ifEmpty { profile.username.removePrefix("@") }, bio = bio.trim()), descriptionText.trim()) }) { Text("Save") } }
        HorizontalDivider()
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { FynxProfileImage(displayName, photoUri, Modifier.size(112.dp)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { OutlinedButton(onClick = { picker.launch("image/*") }) { Icon(Icons.Default.AddAPhoto, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(if (photoUri == null) "Add profile photo" else "Change photo") }; if (photoUri != null) { Spacer(Modifier.width(7.dp)); TextButton(onClick = { onPhotoChanged(null) }) { Text("Remove") } } }
        OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), label = { Text("Display name") }, singleLine = true, shape = FynxDesign.ControlShape)
        OutlinedTextField(username.removePrefix("@"), { username = it.removePrefix("@").replace(" ", "") }, Modifier.fillMaxWidth(), label = { Text("Username") }, prefix = { Text("@") }, singleLine = true, shape = FynxDesign.ControlShape)
        OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text("Bio") }, minLines = 3, shape = FynxDesign.ControlShape)
        OutlinedTextField(descriptionText, { descriptionText = it }, Modifier.fillMaxWidth(), label = { Text("About / description") }, minLines = 3, maxLines = 6, shape = FynxDesign.ControlShape)
    }
}

@Composable
fun SettingsPanel(settings: FynxSettings, onSettingsChange: (FynxSettings) -> Unit, onBack: () -> Unit, onAppearanceChanged: (String) -> Unit = {}, onAccentChanged: (FynxAccent) -> Unit = {}) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("fynx_preferences", Context.MODE_PRIVATE)
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var darkMode by remember { mutableStateOf(FynxPreferencesStore.loadAppearance(context) == "Dark") }
    var appearance by remember { mutableStateOf(FynxPreferencesStore.loadAppearance(context)) }
    var accent by remember { mutableStateOf(FynxPreferencesStore.loadAccent(context)) }
    var language by remember { mutableStateOf(FynxPreferencesStore.loadLanguage(context)) }
    var showColors by remember { mutableStateOf(false) }; var showLanguage by remember { mutableStateOf(false) }; var showAppearance by remember { mutableStateOf(false) }; var showAssets by remember { mutableStateOf(false) }
    var saveMedia by remember { mutableStateOf(prefs.getBoolean("save_media", true)) }; var sounds by remember { mutableStateOf(prefs.getBoolean("chat_sounds", true)) }
    var photoVisibility by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, "photo_visibility")) }; var bioVisibility by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, "bio_visibility")) }; var descriptionVisibility by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, "description_visibility")) }; var friendsVisibility by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, "friends_visibility", "Friends")) }; var findability by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, "findability")) }; var messagesVisibility by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, "messages_visibility", "Everyone")) }; var groupsVisibility by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, "groups_visibility", "Friends")) }; var callsVisibility by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, "calls_visibility", "Everyone")) }; var onlineVisibility by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, "online_visibility", "Friends")) }; var lastActiveVisibility by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, "last_active_visibility", "Friends")) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("‹ Back") }; Column(Modifier.weight(1f).padding(start = 4.dp)) { Text("Settings & privacy", style = MaterialTheme.typography.titleLarge); Text("Control your FYNX experience", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall) } }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SettingsSectionTitle("Account & security") }; item { SettingsInfoCard(Icons.Default.Security, "Authentication", "Your existing FYNX sign-in session remains protected by the authentication foundation.") }
            item { SettingsSectionTitle("Privacy") }; item { SettingSwitchCard(Icons.Default.Lock, "Private profile", "Limit profile visibility when production privacy sync is connected.", settings.privateProfile) { onSettingsChange(settings.copy(privateProfile = it)) } }; item { SettingSwitchCard(Icons.Default.Visibility, "Read receipts", "Control read status in conversations.", settings.readReceipts) { onSettingsChange(settings.copy(readReceipts = it)) } }; item { SettingSwitchCard(Icons.Default.Person, "Story replies", "Allow replies to your stories.", settings.storyReplies) { onSettingsChange(settings.copy(storyReplies = it)) } }
            item { SettingsSectionTitle("Profile visibility") }; item { VisibilitySettingCard("Profile photo", photoVisibility) { photoVisibility = it; FynxPreferencesStore.saveVisibility(context, "photo_visibility", it) } }; item { VisibilitySettingCard("Bio", bioVisibility) { bioVisibility = it; FynxPreferencesStore.saveVisibility(context, "bio_visibility", it) } }; item { VisibilitySettingCard("About / description", descriptionVisibility) { descriptionVisibility = it; FynxPreferencesStore.saveVisibility(context, "description_visibility", it) } }; item { VisibilitySettingCard("Friends list", friendsVisibility) { friendsVisibility = it; FynxPreferencesStore.saveVisibility(context, "friends_visibility", it) } }; item { VisibilitySettingCard("Who can find me", findability) { findability = it; FynxPreferencesStore.saveVisibility(context, "findability", it) } }
            item { SettingsSectionTitle("Contact & activity privacy") }; item { VisibilitySettingCard("Who can message me", messagesVisibility) { messagesVisibility = it; FynxPreferencesStore.saveVisibility(context, "messages_visibility", it) } }; item { VisibilitySettingCard("Who can add me to groups", groupsVisibility) { groupsVisibility = it; FynxPreferencesStore.saveVisibility(context, "groups_visibility", it) } }; item { VisibilitySettingCard("Who can call me", callsVisibility) { callsVisibility = it; FynxPreferencesStore.saveVisibility(context, "calls_visibility", it) } }; item { VisibilitySettingCard("Online status", onlineVisibility) { onlineVisibility = it; FynxPreferencesStore.saveVisibility(context, "online_visibility", it) } }; item { VisibilitySettingCard("Last active", lastActiveVisibility) { lastActiveVisibility = it; FynxPreferencesStore.saveVisibility(context, "last_active_visibility", it) } }
            item { SettingsSectionTitle("Notifications") }; item { SettingSwitchCard(Icons.Default.Notifications, "FYNX notifications", "Enable FYNX notification preferences.", settings.notifications) { onSettingsChange(settings.copy(notifications = it)) } }
            item { SettingsSectionTitle("Chats & media") }; item { SettingSwitchCard(Icons.Default.VolumeOff, "Chat sounds", "Play chat sound feedback.", sounds) { sounds = it; prefs.edit().putBoolean("chat_sounds", it).apply() } }; item { SettingSwitchCard(Icons.Default.Storage, "Save media", "Keep received media available when supported.", saveMedia) { saveMedia = it; prefs.edit().putBoolean("save_media", it).apply() } }
            item { SettingsSectionTitle("Appearance & app") }
            item { SettingsActionCard(Icons.Default.DarkMode, "Appearance", appearance, { showAppearance = true }) }
            item { SettingsActionCard(Icons.Default.Palette, "Colors & accent", accent.name, { showColors = true }) }
            item { SettingsActionCard(Icons.Default.Folder, "Assets & media", FynxPreferencesStore.loadAsset(context)?.let { "1 selected asset" } ?: "Choose a device asset", { showAssets = true }) }
            item { SettingsActionCard(Icons.Default.Language, "Language", language, { showLanguage = true }) }
            item { SettingsInfoCard(Icons.Default.DataUsage, "Data usage", "Review media and network behavior before connecting FYNX to production data services.") }
            item { SettingsSectionTitle("Data & account") }; item { SettingsInfoCard(Icons.Default.Lock, "Account data", "Server-side export, recovery, and deletion will be connected with the secure backend.") }; item { OutlinedButton(onClick = { showDeleteConfirmation = true }, Modifier.fillMaxWidth(), shape = FynxDesign.ControlShape) { Text("Delete account") } }
        }
    }
    if (showAppearance) AlertDialog(onDismissRequest = { showAppearance = false }, title = { Text("Appearance") }, text = { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { listOf("System", "Light", "Dark").forEach { option -> Row(Modifier.fillMaxWidth(), Alignment.CenterVertically) { RadioButton(appearance == option, { appearance = option; darkMode = option == "Dark"; FynxPreferencesStore.saveAppearance(context, option); onAppearanceChanged(option) }); Text(option) } } } }, confirmButton = { TextButton(onClick = { showAppearance = false }) { Text("Done") } })
    if (showColors) AlertDialog(onDismissRequest = { showColors = false }, title = { Text("FYNX colors") }, text = { Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { FynxAccent.values().forEach { option -> Row(Modifier.fillMaxWidth(), Alignment.CenterVertically) { RadioButton(accent == option, { accent = option; FynxPreferencesStore.saveAccent(context, option); onAccentChanged(option) }); Text(option.name) } } } }, confirmButton = { TextButton(onClick = { showColors = false }) { Text("Done") } })
    if (showLanguage) AlertDialog(onDismissRequest = { showLanguage = false }, title = { Text("Language") }, text = { Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { listOf("Device default", "English", "French", "Arabic", "Portuguese").forEach { option -> Row(Modifier.fillMaxWidth(), Alignment.CenterVertically) { RadioButton(language == option, { language = option; FynxPreferencesStore.saveLanguage(context, option) }); Text(option) } }; Text("Language preference is saved now; full translated string resources will be added with the localization phase.", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton(onClick = { showLanguage = false }) { Text("Done") } })
    if (showAssets) {
        val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if (uri != null) FynxPreferencesStore.saveAsset(context, uri.toString()) }
        AlertDialog(onDismissRequest = { showAssets = false }, title = { Text("Assets & media") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Choose a personal image asset for future FYNX customization."); OutlinedButton(onClick = { assetPicker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text("Choose image") }; Text("The selected asset stays on this account's device until secure sync is available.", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton(onClick = { showAssets = false }) { Text("Done") } })
    }
    if (showDeleteConfirmation) AlertDialog(onDismissRequest = { showDeleteConfirmation = false }, title = { Text("Delete account?") }, text = { Text("This is a safe confirmation foundation only. No account will be deleted until the secure backend supports the operation.") }, confirmButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("I understand") } }, dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") } })
}

@Composable private fun SettingsSectionTitle(title: String) { Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 5.dp, bottom = 1.dp)) }
@Composable private fun SettingsInfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) { Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) { Row(Modifier.fillMaxWidth().padding(12.dp), Alignment.CenterVertically) { Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SelectedContainer) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, Modifier.padding(8.dp).size(21.dp)) }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(description, color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall) } } } }
@Composable private fun SettingsActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, onClick: () -> Unit) { Card(onClick = onClick, Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) { Row(Modifier.fillMaxWidth().padding(12.dp), Alignment.CenterVertically) { Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SelectedContainer) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, Modifier.padding(8.dp).size(21.dp)) }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(value, color = FynxDesign.TextSecondary) }; Icon(Icons.Default.ChevronRight, null, tint = FynxDesign.TextSecondary) } } }
@Composable private fun SettingSwitchCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) { Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) { Row(Modifier.fillMaxWidth().padding(12.dp), Alignment.CenterVertically) { Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SelectedContainer) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, Modifier.padding(8.dp).size(21.dp)) }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.titleMedium); Text(description, color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.width(6.dp)); Switch(checked, onCheckedChange) } } }
@Composable private fun VisibilitySettingCard(title: String, value: String, onChange: (String) -> Unit) { var open by remember { mutableStateOf(false) }; val options = listOf("Everyone", "Friends", "Nobody"); Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) { Column(Modifier.fillMaxWidth().padding(12.dp)) { Row(Modifier.fillMaxWidth(), Alignment.CenterVertically) { Icon(Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(value, color = FynxDesign.TextSecondary) }; TextButton(onClick = { open = !open }) { Text("Change") } }; if (open) options.forEach { option -> TextButton(onClick = { onChange(option); open = false }, Modifier.fillMaxWidth()) { Text(option) } } } }
