package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FYNX_PRIVACY_OPTIONS = listOf("Everyone", "My friends", "Nobody")

private const val KEY_PROFILE = "privacy_profile_visibility"
private const val KEY_ONLINE = "privacy_online_visibility"
private const val KEY_POSTS = "privacy_posts_visibility"
private const val KEY_STATUS = "privacy_status_visibility"
private const val KEY_PROFILE_PHOTO = "privacy_profile_photo_visibility"
private const val KEY_MESSAGES = "privacy_messages_visibility"

@Composable
fun FynxPrivacySettingsPanel(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, KEY_PROFILE)) }
    var online by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, KEY_ONLINE)) }
    var posts by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, KEY_POSTS)) }
    var status by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, KEY_STATUS)) }
    var photo by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, KEY_PROFILE_PHOTO)) }
    var messages by remember { mutableStateOf(FynxPreferencesStore.loadVisibility(context, KEY_MESSAGES)) }
    var openKey by remember { mutableStateOf<String?>(null) }
    var savingKey by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        FynxPrivacyRemoteClient.load(context).onSuccess { remote ->
            profile = remote[KEY_PROFILE]
            online = remote[KEY_ONLINE]
            posts = remote[KEY_POSTS]
            status = remote[KEY_STATUS]
            photo = remote[KEY_PROFILE_PHOTO]
            messages = remote[KEY_MESSAGES]
            listOf(
                KEY_PROFILE to profile,
                KEY_ONLINE to online,
                KEY_POSTS to posts,
                KEY_STATUS to status,
                KEY_PROFILE_PHOTO to photo,
                KEY_MESSAGES to messages
            ).forEach { (key, value) -> FynxPreferencesStore.saveVisibility(context, key, value) }
        }.onFailure { error ->
            notice = error.message ?: "Privacy settings are currently offline."
        }
    }

    fun saveSelection(key: String, option: String) {
        val previous = when (key) {
            KEY_PROFILE -> profile
            KEY_ONLINE -> online
            KEY_POSTS -> posts
            KEY_STATUS -> status
            KEY_PROFILE_PHOTO -> photo
            KEY_MESSAGES -> messages
            else -> option
        }
        savingKey = key
        notice = null
        scope.launch {
            FynxPrivacyRemoteClient.update(context, key, option).onSuccess { remote ->
                profile = remote[KEY_PROFILE]
                online = remote[KEY_ONLINE]
                posts = remote[KEY_POSTS]
                status = remote[KEY_STATUS]
                photo = remote[KEY_PROFILE_PHOTO]
                messages = remote[KEY_MESSAGES]
                remote.values.forEach { }
                FynxPreferencesStore.saveVisibility(context, key, option)
                openKey = null
            }.onFailure { error ->
                when (key) {
                    KEY_PROFILE -> profile = previous
                    KEY_ONLINE -> online = previous
                    KEY_POSTS -> posts = previous
                    KEY_STATUS -> status = previous
                    KEY_PROFILE_PHOTO -> photo = previous
                    KEY_MESSAGES -> messages = previous
                }
                notice = error.message ?: "Could not save this privacy setting."
            }
            savingKey = null
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Spacer(Modifier.width(4.dp))
            Text("Privacy", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        }
        HorizontalDivider()
        notice?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
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
            KEY_PROFILE_PHOTO -> photo
            KEY_MESSAGES -> messages
            else -> null
        }
        AlertDialog(
            onDismissRequest = { if (savingKey == null) openKey = null },
            title = { Text("Privacy setting") },
            text = {
                Column {
                    FYNX_PRIVACY_OPTIONS.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = current == option,
                                enabled = savingKey == null,
                                onClick = { saveSelection(key, option) }
                            )
                            Text(option)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = savingKey == null, onClick = { openKey = null }) { Text("Done") }
            }
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
