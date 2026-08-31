package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProfilePanel(
    session: AuthSession = AuthSession(),
    onSignOut: () -> Unit = {}
) {
    var settingsOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf(FynxProfile()) }
    var settings by remember { mutableStateOf(FynxSettings()) }

    if (settingsOpen) {
        SettingsPanel(settings = settings, onSettingsChange = { settings = it }, onBack = { settingsOpen = false })
        return
    }

    if (editing) {
        EditProfilePanel(
            profile = profile,
            onSave = { profile = it; editing = false },
            onCancel = { editing = false }
        )
        return
    }

    val username = session.username?.let { "@$it" } ?: profile.username
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        FynxAvatar(profile.displayName, Modifier.size(104.dp))
        Spacer(Modifier.height(12.dp))
        Text(profile.displayName, style = MaterialTheme.typography.titleLarge)
        Text(username, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Text(profile.bio, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { editing = true }) { Text("Edit profile") }
        OutlinedButton(onClick = { settingsOpen = true }) { Text("Settings & privacy") }
        if (session.state == AuthState.SIGNED_IN) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
    }
}

@Composable
private fun EditProfilePanel(
    profile: FynxProfile,
    onSave: (FynxProfile) -> Unit,
    onCancel: () -> Unit
) {
    var displayName by remember(profile) { mutableStateOf(profile.displayName) }
    var username by remember(profile) { mutableStateOf(profile.username) }
    var bio by remember(profile) { mutableStateOf(profile.bio) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Text("Edit profile", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { onSave(profile.copy(displayName = displayName.trim().ifEmpty { profile.displayName }, username = username.trim().ifEmpty { profile.username }, bio = bio.trim())) }) { Text("Save") }
        }
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = username, onValueChange = { username = it.removePrefix("@").replace(" ", "") }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") }, minLines = 3, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun SettingsPanel(settings: FynxSettings, onSettingsChange: (FynxSettings) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Settings & privacy", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        LazyColumn {
            item {
                SettingSwitch("Notifications", settings.notifications) { onSettingsChange(settings.copy(notifications = it)) }
                SettingSwitch("Private profile", settings.privateProfile) { onSettingsChange(settings.copy(privateProfile = it)) }
                SettingSwitch("Read receipts", settings.readReceipts) { onSettingsChange(settings.copy(readReceipts = it)) }
                SettingSwitch("Story replies", settings.storyReplies) { onSettingsChange(settings.copy(storyReplies = it)) }
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
