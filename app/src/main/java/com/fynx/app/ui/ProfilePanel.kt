package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ProfilePanel(session: AuthSession = AuthSession(), onSignOut: () -> Unit = {}) {
    val context = LocalContext.current
    var settingsOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var description by remember(session.username) { mutableStateOf(FynxPreferencesStore.loadDescription(context)) }
    var profile by remember(session.username) {
        mutableStateOf(FynxPreferencesStore.loadProfile(context, session.username))
    }
    var settings by remember {
        mutableStateOf(FynxPreferencesStore.loadSettings(context))
    }

    if (settingsOpen) {
        SettingsPanel(
            settings = settings,
            onSettingsChange = {
                settings = it
                FynxPreferencesStore.saveSettings(context, it)
            },
            onBack = { settingsOpen = false }
        )
        return
    }

    if (editing) {
        EditProfilePanel(
            profile = profile,
            description = description,
            onSave = { updated, updatedDescription ->
                profile = updated
                description = updatedDescription
                FynxPreferencesStore.saveProfile(context, updated)
                FynxPreferencesStore.saveDescription(context, updatedDescription)
                editing = false
            },
            onCancel = { editing = false }
        )
        return
    }

    val username = session.username?.let { "@${it.removePrefix("@")}" } ?: "@${profile.username.removePrefix("@")}"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = FynxDesign.LargeCardShape,
                colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
                border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FynxAvatar(profile.displayName, Modifier.size(104.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(profile.displayName, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(3.dp))
                    Text(username, color = FynxDesign.TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Text(profile.bio.ifBlank { "Welcome to FYNX" }, color = FynxDesign.TextSecondary)
                    if (description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(description, color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { editing = true },
                            shape = FynxDesign.ControlShape
                        ) { Text("Edit profile") }
                        OutlinedButton(
                            onClick = { settingsOpen = true },
                            shape = FynxDesign.ControlShape,
                            border = BorderStroke(1.dp, FynxDesign.Outline)
                        ) { Text("Settings") }
                    }
                }
            }
        }

        item {
            Text("Account", style = MaterialTheme.typography.titleMedium)
        }

        item {
            ProfileInfoCard(
                icon = Icons.Default.Person,
                title = "Username",
                value = username
            )
        }

        item {
            ProfileInfoCard(
                icon = Icons.Default.Security,
                title = "Account security",
                value = if (session.state == AuthState.SIGNED_IN) "Signed in" else "Signed out"
            )
        }

        item {
            Text("Privacy & preferences", style = MaterialTheme.typography.titleMedium)
        }

        item {
            SettingPreviewCard(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                value = if (settings.notifications) "Enabled" else "Disabled"
            )
        }

        item {
            SettingPreviewCard(
                icon = Icons.Default.Lock,
                title = "Private profile",
                value = if (settings.privateProfile) "On" else "Off"
            )
        }

        item {
            SettingPreviewCard(
                icon = Icons.Default.Visibility,
                title = "Read receipts",
                value = if (settings.readReceipts) "On" else "Off"
            )
        }

        if (session.state == AuthState.SIGNED_IN) {
            item {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FynxDesign.ControlShape,
                    border = BorderStroke(1.dp, FynxDesign.Outline)
                ) { Text("Sign out") }
            }
        }
    }
}

@Composable
private fun ProfileInfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FynxDesign.CardShape,
        colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
        border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SelectedContainer) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(9.dp).size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(value, color = FynxDesign.TextSecondary)
            }
        }
    }
}

@Composable
private fun SettingPreviewCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    ProfileInfoCard(icon = icon, title = title, value = value)
}

@Composable
private fun EditProfilePanel(profile: FynxProfile, description: String, onSave: (FynxProfile, String) -> Unit, onCancel: () -> Unit) {
    var displayName by remember(profile) { mutableStateOf(profile.displayName) }
    var username by remember(profile) { mutableStateOf(profile.username) }
    var bio by remember(profile) { mutableStateOf(profile.bio) }
    var descriptionText by remember(profile, description) { mutableStateOf(description) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            displayName = displayName.trim().ifEmpty { profile.displayName },
                            username = username.trim().removePrefix("@").replace(" ", "").ifEmpty { profile.username.removePrefix("@") },
                            bio = bio.trim()
                        ),
                        descriptionText.trim()
                    )
                }
            ) { Text("Save") }
        }

        HorizontalDivider(color = FynxDesign.Outline.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display name") },
            singleLine = true,
            shape = FynxDesign.ControlShape,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = username.removePrefix("@"),
            onValueChange = { username = it.removePrefix("@").replace(" ", "") },
            label = { Text("Username") },
            prefix = { Text("@") },
            singleLine = true,
            shape = FynxDesign.ControlShape,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text("Bio") },
            minLines = 3,
            shape = FynxDesign.ControlShape,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = descriptionText,
            onValueChange = { descriptionText = it },
            label = { Text("About / description") },
            placeholder = { Text("Tell people a little about yourself…") },
            minLines = 4,
            maxLines = 6,
            shape = FynxDesign.ControlShape,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingsPanel(settings: FynxSettings, onSettingsChange: (FynxSettings) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("Settings & privacy", style = MaterialTheme.typography.titleLarge)
                Text("Control your FYNX experience", color = FynxDesign.TextSecondary)
            }
        }

        HorizontalDivider(color = FynxDesign.Outline.copy(alpha = 0.6f))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SettingsSectionTitle("Account & security") }
            item {
                SettingsInfoCard(
                    icon = Icons.Default.Security,
                    title = "Authentication",
                    description = "Your current FYNX sign-in session is protected by the existing authentication foundation."
                )
            }

            item { SettingsSectionTitle("Privacy") }
            item {
                SettingSwitchCard(
                    icon = Icons.Default.Lock,
                    label = "Private profile",
                    description = "Limit profile visibility when the production privacy backend is connected.",
                    checked = settings.privateProfile,
                    onCheckedChange = { onSettingsChange(settings.copy(privateProfile = it)) }
                )
            }
            item {
                SettingSwitchCard(
                    icon = Icons.Default.Visibility,
                    label = "Read receipts",
                    description = "Control whether conversations can show that you have read a message.",
                    checked = settings.readReceipts,
                    onCheckedChange = { onSettingsChange(settings.copy(readReceipts = it)) }
                )
            }
            item {
                SettingSwitchCard(
                    icon = Icons.Default.Person,
                    label = "Story replies",
                    description = "Allow replies to your FYNX stories when that feature is available.",
                    checked = settings.storyReplies,
                    onCheckedChange = { onSettingsChange(settings.copy(storyReplies = it)) }
                )
            }

            item { SettingsSectionTitle("Notifications") }
            item {
                SettingSwitchCard(
                    icon = Icons.Default.Notifications,
                    label = "FYNX notifications",
                    description = "Enable or disable FYNX notification preferences. Android notification permission remains controlled by the system.",
                    checked = settings.notifications,
                    onCheckedChange = { onSettingsChange(settings.copy(notifications = it)) }
                )
            }

            item { SettingsSectionTitle("Data & account") }
            item {
                SettingsInfoCard(
                    icon = Icons.Default.Lock,
                    title = "Account data",
                    description = "Server-side export, recovery, and deletion will be connected when the secure production backend is ready."
                )
            }
            item {
                OutlinedButton(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = FynxDesign.ControlShape,
                    border = BorderStroke(1.dp, FynxDesign.Outline)
                ) { Text("Delete account") }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete account?") },
            text = { Text("This is a safe confirmation foundation only. No account will be deleted until the secure backend supports the operation.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("I understand") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FynxDesign.CardShape,
        colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
        border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SelectedContainer) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp).size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(description, color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingSwitchCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FynxDesign.CardShape,
        colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
        border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SelectedContainer) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp).size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(description, color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
