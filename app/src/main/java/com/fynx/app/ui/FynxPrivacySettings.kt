package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val FYNX_PRIVACY_OPTIONS = listOf("Everyone", "My friends", "Nobody")

private const val KEY_PROFILE = "privacy_profile_visibility"
private const val KEY_ONLINE = "privacy_online_visibility"
private const val KEY_POSTS = "privacy_posts_visibility"
private const val KEY_STATUS = "privacy_status_visibility"
private const val KEY_PROFILE_PHOTO = "privacy_profile_photo_visibility"
private const val KEY_MESSAGES = "privacy_messages_visibility"

@Composable
fun FynxPrivacySettingsPanel(onBack: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var profile by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, KEY_PROFILE)) }
    var online by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, KEY_ONLINE)) }
    var posts by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, KEY_POSTS)) }
    var status by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, KEY_STATUS)) }
    var photo by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, KEY_PROFILE_PHOTO)) }
    var messages by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, KEY_MESSAGES)) }
    var openKey by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Spacer(Modifier.width(4.dp))
            Text("Privacy", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        }
        HorizontalDivider()
        LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { PrivacyChoiceCard("Who can see my profile", profile) { openKey = KEY_PROFILE } }
            item { PrivacyChoiceCard("Who can see me online", online) { openKey = KEY_ONLINE } }
            item { PrivacyChoiceCard("Who can see my posts", posts) { openKey = KEY_POSTS } }
            item { PrivacyChoiceCard("Who can view my Status", status) { openKey = KEY_STATUS } }
            item { PrivacyChoiceCard("Who can see my profile photo", photo) { openKey = KEY_PROFILE_PHOTO } }
            item { PrivacyChoiceCard("Who can message me", messages) { openKey = KEY_MESSAGES } }
        }
    }

    openKey?.let { key ->
        val current = when (key) {
            KEY_PROFILE -> profile
            KEY_ONLINE -> online
            KEY_POSTS -> posts
            KEY_STATUS -> status
            else -> photo
        }
        AlertDialog(
            onDismissRequest = { openKey = null },
            title = { Text("Who can see this?") },
            text = {
                Column {
                    FYNX_PRIVACY_OPTIONS.forEach { option ->
                        Row(Modifier.fillMaxWidth()) {
                            RadioButton(selected = current == option, onClick = {
                                when (key) {
                                    KEY_PROFILE -> { profile = option; FynxPreferencesStore.saveVisibility(context, key, option) }
                                    KEY_ONLINE -> { online = option; FynxPreferencesStore.saveVisibility(context, key, option) }
                                    KEY_POSTS -> { posts = option; FynxPreferencesStore.saveVisibility(context, key, option) }
                                    KEY_STATUS -> { status = option; FynxPreferencesStore.saveVisibility(context, key, option) }
                                    KEY_PROFILE_PHOTO -> { photo = option; FynxPreferencesStore.saveVisibility(context, key, option) }
                                    KEY_MESSAGES -> { messages = option; FynxPreferencesStore.saveVisibility(context, key, option) }
                                }
                                openKey = null
                            })
                            Text(option, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { openKey = null }) { Text("Done") } }
        )
    }
}

@Composable
private fun PrivacyChoiceCard(title: String, value: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = FynxDesign.CardShape,
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}
