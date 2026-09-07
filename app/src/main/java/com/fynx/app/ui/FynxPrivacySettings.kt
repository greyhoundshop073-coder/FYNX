package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
    var safety by remember { mutableStateOf<FynxSafetyRemoteClient.Safety?>(null) }
    var reports by remember { mutableStateOf<List<FynxSafetyRemoteClient.Report>>(emptyList()) }
    var appeals by remember { mutableStateOf<List<FynxSafetyRemoteClient.Appeal>>(emptyList()) }
    var safetyLoading by remember { mutableStateOf(true) }
    var appealSubject by remember { mutableStateOf("") }
    var appealDetails by remember { mutableStateOf("") }
    var appealReportId by remember { mutableStateOf("") }
    var submittingAppeal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        FynxPrivacyRemoteClient.load(context).onSuccess { remote ->
            profile = remote[KEY_PROFILE]
            online = remote[KEY_ONLINE]
            posts = remote[KEY_POSTS]
            status = remote[KEY_STATUS]
            photo = remote[KEY_PROFILE_PHOTO]
            messages = remote[KEY_MESSAGES]
            listOf(KEY_PROFILE to profile, KEY_ONLINE to online, KEY_POSTS to posts, KEY_STATUS to status, KEY_PROFILE_PHOTO to photo, KEY_MESSAGES to messages).forEach { (key, value) -> FynxPreferencesStore.saveVisibility(context, key, value) }
        }.onFailure { error -> notice = error.message ?: "Privacy settings are currently offline." }
    }

    LaunchedEffect(Unit) {
        safetyLoading = true
        FynxSafetyRemoteClient.load(context).onSuccess { safety = it }.onFailure { error -> notice = error.message ?: "Safety controls are currently unavailable." }
        FynxSafetyRemoteClient.reports(context).onSuccess { reports = it }
        FynxSafetyRemoteClient.appeals(context).onSuccess { appeals = it }
        safetyLoading = false
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

    fun updateSafety(key: String, enabled: Boolean) {
        scope.launch {
            FynxSafetyRemoteClient.update(context, key, enabled).onSuccess { safety = it }.onFailure { error -> notice = error.message ?: "Could not save this safety control." }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Spacer(Modifier.width(4.dp))
            Text("Privacy & Safety", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        }
        HorizontalDivider()
        notice?.let { message -> Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
        LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Safety Center", style = MaterialTheme.typography.titleMedium)
                        val state = safety
                        Text(if (safetyLoading) "Checking your account protection…" else "Account status: ${state?.accountStatus ?: "UNKNOWN"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!state?.statusNote.isNullOrBlank()) Text(state?.statusNote.orEmpty(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(6.dp))
                        SafetyToggle("Message safety", state?.messageSafety ?: true, safetyLoading) { updateSafety("messageSafety", it) }
                        SafetyToggle("Marketplace safety", state?.marketplaceSafety ?: true, safetyLoading) { updateSafety("marketplaceSafety", it) }
                        SafetyToggle("Login alerts", state?.loginAlerts ?: true, safetyLoading) { updateSafety("loginAlerts", it) }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("My reports", style = MaterialTheme.typography.titleMedium)
                        if (reports.isEmpty()) Text("No submitted reports yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        reports.take(5).forEach { report ->
                            Text("@${report.targetUsername} • ${report.reason} • ${report.status}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 7.dp))
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Appeal a decision or report", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(value = appealReportId, onValueChange = { appealReportId = it.filter(Char::isDigit).take(18) }, label = { Text("Report ID (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true)
                        OutlinedTextField(value = appealSubject, onValueChange = { appealSubject = it.take(120) }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true)
                        OutlinedTextField(value = appealDetails, onValueChange = { appealDetails = it.take(4000) }, label = { Text("Explain your appeal") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 4)
                        Button(enabled = !submittingAppeal && appealSubject.trim().length >= 3 && appealDetails.trim().length >= 10, onClick = {
                            submittingAppeal = true
                            scope.launch {
                                FynxSafetyRemoteClient.submitAppeal(context, appealReportId.ifBlank { null }, appealSubject, appealDetails).onSuccess {
                                    appeals = listOf(it) + appeals
                                    appealReportId = ""
                                    appealSubject = ""
                                    appealDetails = ""
                                    notice = "Appeal submitted. You can follow its status here."
                                }.onFailure { error -> notice = error.message ?: "Appeal submission failed." }
                                submittingAppeal = false
                            }
                        }, modifier = Modifier.padding(top = 8.dp)) { Text(if (submittingAppeal) "Submitting…" else "Submit appeal") }
                        if (appeals.isNotEmpty()) {
                            Text("Recent appeals", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                            appeals.take(5).forEach { appeal ->
                                Text("#${appeal.id} • ${appeal.status}${if (appeal.decisionNote.isNotBlank()) " • ${appeal.decisionNote}" else ""}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
                            }
                        }
                    }
                }
            }
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
            text = { Column { FYNX_PRIVACY_OPTIONS.forEach { option -> Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { RadioButton(selected = current == option, enabled = savingKey == null, onClick = { saveSelection(key, option) }); Text(option) } } } },
            confirmButton = { TextButton(enabled = savingKey == null, onClick = { openKey = null }) { Text("Done") } }
        )
    }
}

@Composable
private fun SafetyToggle(title: String, checked: Boolean, disabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, enabled = !disabled, onCheckedChange = onChange)
    }
}

@Composable
private fun PrivacyChoiceCard(title: String, value: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) {
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}
