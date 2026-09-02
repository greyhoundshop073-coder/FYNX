package com.fynx.app.ui

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

@Composable
fun ProfilePanel(
    session: AuthSession = AuthSession(),
    onSignOut: () -> Unit = {},
    onAppearanceChanged: (String) -> Unit = {},
    onAccentChanged: (FynxAccent) -> Unit = {}
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var profile by remember(session.username) {
        mutableStateOf(FynxPreferencesStore.loadProfile(context, session.username))
    }
    var description by remember(session.username) {
        mutableStateOf(FynxPreferencesStore.loadDescription(context))
    }
    var photo by remember(session.username) {
        mutableStateOf(FynxPreferencesStore.loadProfilePhoto(context))
    }
    var settings by remember { mutableStateOf(FynxPreferencesStore.loadSettings(context)) }

    if (editing) {
        EditProfilePanel(
            profile = profile,
            description = description,
            photoUri = photo,
            onPhotoChanged = { uri ->
                photo = uri
                FynxPreferencesStore.saveProfilePhoto(context, uri)
            },
            onSave = { updatedProfile, updatedDescription ->
                profile = updatedProfile
                description = updatedDescription
                FynxPreferencesStore.saveProfile(context, updatedProfile)
                FynxPreferencesStore.saveDescription(context, updatedDescription)
                editing = false
            },
            onCancel = { editing = false }
        )
        return
    }

    if (settingsOpen) {
        SettingsPanel(
            settings = settings,
            onSettingsChange = {
                settings = it
                FynxPreferencesStore.saveSettings(context, it)
            },
            onBack = { settingsOpen = false },
            onAppearanceChanged = onAppearanceChanged,
            onAccentChanged = onAccentChanged
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = FynxDesign.LargeCardShape,
                colors = CardDefaults.cardColors(FynxDesign.Surface),
                border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FynxProfileImage(profile.displayName, photo, Modifier.size(108.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(profile.displayName, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "@${profile.username.removePrefix("@")}",
                        color = FynxDesign.TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        profile.bio.ifBlank { "Welcome to FYNX" },
                        color = FynxDesign.TextSecondary
                    )
                    if (description.isNotBlank()) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = FynxDesign.TextSecondary
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { editing = true },
                            shape = FynxDesign.ControlShape
                        ) {
                            Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Edit profile")
                        }
                        OutlinedButton(
                            onClick = { settingsOpen = true },
                            shape = FynxDesign.ControlShape
                        ) {
                            Icon(Icons.Default.Settings, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Settings")
                        }
                    }
                }
            }
        }
        item { ProfileInfoCard("Username", "@${profile.username.removePrefix("@")}") }
        item {
            ProfileInfoCard(
                "Profile photo",
                if (photo == null) "Not set" else "Photo selected"
            )
        }
        item {
            ProfileInfoCard(
                "Account",
                if (session.state == AuthState.SIGNED_IN) "Signed in" else "Signed out"
            )
        }
        if (session.state == AuthState.SIGNED_IN) {
            item {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FynxDesign.ControlShape
                ) {
                    Text("Sign out")
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FynxDesign.CardShape,
        colors = CardDefaults.cardColors(FynxDesign.Surface),
        border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(value, color = FynxDesign.TextSecondary)
        }
    }
}

@Composable
private fun EditProfilePanel(
    profile: FynxProfile,
    description: String,
    photoUri: String?,
    onPhotoChanged: (String?) -> Unit,
    onSave: (FynxProfile, String) -> Unit,
    onCancel: () -> Unit
) {
    var displayName by remember(profile) { mutableStateOf(profile.displayName) }
    var username by remember(profile) { mutableStateOf(profile.username) }
    var bio by remember(profile) { mutableStateOf(profile.bio) }
    var about by remember(profile, description) { mutableStateOf(description) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        uri: Uri? ->
        if (uri != null) onPhotoChanged(uri.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Text("Edit profile", style = MaterialTheme.typography.titleLarge)
            TextButton(
                onClick = {
                    onSave(
                        profile.copy(
                            displayName = displayName.trim().ifBlank { profile.displayName },
                            username = username
                                .trim()
                                .removePrefix("@")
                                .replace(" ", "")
                                .ifBlank { profile.username }
                        ),
                        about.trim()
                    )
                }
            ) { Text("Save") }
        }
        HorizontalDivider()
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FynxProfileImage(displayName, photoUri, Modifier.size(112.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { picker.launch("image/*") }) {
                Icon(Icons.Default.AddAPhoto, null, Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (photoUri == null) "Add photo" else "Change photo")
            }
            if (photoUri != null) {
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { onPhotoChanged(null) }) { Text("Remove") }
            }
        }
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Display name") },
            singleLine = true,
            shape = FynxDesign.ControlShape
        )
        OutlinedTextField(
            value = username.removePrefix("@"),
            onValueChange = { username = it.removePrefix("@").replace(" ", "") },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Username") },
            prefix = { Text("@") },
            singleLine = true,
            shape = FynxDesign.ControlShape
        )
        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Bio") },
            minLines = 3,
            shape = FynxDesign.ControlShape
        )
        OutlinedTextField(
            value = about,
            onValueChange = { about = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("About / description") },
            minLines = 3,
            maxLines = 6,
            shape = FynxDesign.ControlShape
        )
    }
}

@Composable
fun SettingsPanel(
    settings: FynxSettings,
    onSettingsChange: (FynxSettings) -> Unit,
    onBack: () -> Unit,
    onAppearanceChanged: (String) -> Unit = {},
    onAccentChanged: (FynxAccent) -> Unit = {}
) {
    val context = LocalContext.current
    var appearance by remember { mutableStateOf(FynxPreferencesStore.loadAppearance(context)) }
    var accent by remember { mutableStateOf(FynxPreferencesStore.loadAccent(context)) }
    var language by remember { mutableStateOf(FynxPreferencesStore.loadLanguage(context)) }
    var showAppearance by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showAssets by remember { mutableStateOf(false) }

    val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        uri: Uri? ->
        if (uri != null) FynxPreferencesStore.saveAsset(context, uri.toString())
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Spacer(Modifier.width(4.dp))
            Text("Settings & privacy", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SettingSwitchCard("Private profile", settings.privateProfile) {
                    onSettingsChange(settings.copy(privateProfile = it))
                }
            }
            item {
                SettingSwitchCard("Read receipts", settings.readReceipts) {
                    onSettingsChange(settings.copy(readReceipts = it))
                }
            }
            item {
                SettingSwitchCard("Story replies", settings.storyReplies) {
                    onSettingsChange(settings.copy(storyReplies = it))
                }
            }
            item {
                SettingSwitchCard("FYNX notifications", settings.notifications) {
                    onSettingsChange(settings.copy(notifications = it))
                }
            }
            item { SettingsActionCard("Appearance", appearance) { showAppearance = true } }
            item { SettingsActionCard("Colors & accent", accent.name) { showColors = true } }
            item {
                SettingsActionCard(
                    "Assets & media",
                    FynxPreferencesStore.loadAsset(context)?.let { "1 selected asset" }
                        ?: "Choose a device asset"
                ) { showAssets = true }
            }
            item { SettingsActionCard("Language", language) { showLanguage = true } }
        }
    }

    if (showAppearance) {
        AlertDialog(
            onDismissRequest = { showAppearance = false },
            title = { Text("Appearance") },
            text = {
                Column {
                    listOf("System", "Light", "Dark").forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = appearance == option,
                                onClick = {
                                    appearance = option
                                    FynxPreferencesStore.saveAppearance(context, option)
                                    onAppearanceChanged(option)
                                }
                            )
                            Text(option)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppearance = false }) { Text("Done") }
            }
        )
    }

    if (showColors) {
        AlertDialog(
            onDismissRequest = { showColors = false },
            title = { Text("FYNX colors") },
            text = {
                Column {
                    FynxAccent.values().forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = accent == option,
                                onClick = {
                                    accent = option
                                    FynxPreferencesStore.saveAccent(context, option)
                                    onAccentChanged(option)
                                }
                            )
                            Text(option.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColors = false }) { Text("Done") }
            }
        )
    }

    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            title = { Text("Language") },
            text = {
                Column {
                    listOf("Device default", "English", "French", "Arabic", "Portuguese").forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = language == option,
                                onClick = {
                                    language = option
                                    FynxPreferencesStore.saveLanguage(context, option)
                                }
                            )
                            Text(option)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguage = false }) { Text("Done") }
            }
        )
    }

    if (showAssets) {
        AlertDialog(
            onDismissRequest = { showAssets = false },
            title = { Text("Assets & media") },
            text = { Text("Choose a personal image asset for FYNX customization.") },
            confirmButton = {
                TextButton(onClick = { assetPicker.launch("image/*") }) {
                    Text("Choose image")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAssets = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun SettingSwitchCard(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FynxDesign.CardShape,
        colors = CardDefaults.cardColors(FynxDesign.Surface),
        border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun SettingsActionCard(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = FynxDesign.CardShape,
        colors = CardDefaults.cardColors(FynxDesign.Surface),
        border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(value, color = FynxDesign.TextSecondary)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = FynxDesign.TextSecondary
            )
        }
    }
}
