package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProfilePanel() {
    var settingsOpen by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf(FynxProfile()) }
    var settings by remember { mutableStateOf(FynxSettings()) }

    if (settingsOpen) {
        SettingsPanel(settings = settings, onSettingsChange = { settings = it }, onBack = { settingsOpen = false })
        return
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        FynxAvatar(profile.displayName, Modifier.size(104.dp))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(profile.displayName, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(6.dp))
            FynxVerifiedBadge()
        }
        Text(profile.username, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Text(profile.bio, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            profile = profile.copy(bio = if (profile.bio == "Welcome to FYNX") "Making everyday life simpler." else "Welcome to FYNX")
        }) {
            Text("Edit profile")
        }
        OutlinedButton(onClick = { settingsOpen = true }) {
            Text("Settings & privacy")
        }
    }
}

@Composable
fun SettingsPanel(
    settings: FynxSettings,
    onSettingsChange: (FynxSettings) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Settings & privacy", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        LazyColumn {
            item {
                SettingSwitch("Notifications", settings.notifications) {
                    onSettingsChange(settings.copy(notifications = it))
                }
                SettingSwitch("Private profile", settings.privateProfile) {
                    onSettingsChange(settings.copy(privateProfile = it))
                }
                SettingSwitch("Read receipts", settings.readReceipts) {
                    onSettingsChange(settings.copy(readReceipts = it))
                }
                SettingSwitch("Story replies", settings.storyReplies) {
                    onSettingsChange(settings.copy(storyReplies = it))
                }
                Spacer(Modifier.height(12.dp))
                Text("Account", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                Text("Username and account details", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Text("Privacy and safety", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Text("Help & support", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
