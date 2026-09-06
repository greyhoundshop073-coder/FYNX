package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun OtherUserProfilePanel(username: String, onBack: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember(username) { mutableStateOf<FynxProfileRemoteClient.Profile?>(null) }
    var loading by remember(username) { mutableStateOf(true) }
    var error by remember(username) { mutableStateOf<String?>(null) }
    var following by remember(username) { mutableStateOf(false) }
    var busy by remember(username) { mutableStateOf(false) }
    var reportOpen by remember(username) { mutableStateOf(false) }
    var reportReason by remember(username) { mutableStateOf("Safety or spam") }
    var reportDetails by remember(username) { mutableStateOf("") }
    var reportMessage by remember(username) { mutableStateOf<String?>(null) }

    LaunchedEffect(username) {
        loading = true; error = null
        FynxProfileRemoteClient.get(context, username).onSuccess { profile = it }.onFailure { error = it.message ?: "Unable to load this profile." }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Profile", style = MaterialTheme.typography.titleLarge)
        }
        if (loading && profile == null) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (profile == null) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "User not found", color = FynxDesign.TextSecondary)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { scope.launch { loading = true; FynxProfileRemoteClient.get(context, username).onSuccess { profile = it; error = null }.onFailure { error = it.message ?: "Unable to load this profile." }; loading = false } }) { Text("Retry") }
            }
        } else {
            val person = profile!!
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                item {
                    FynxAvatar(person.displayName, Modifier.size(104.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(person.displayName.ifBlank { person.username }, style = MaterialTheme.typography.headlineSmall)
                    Text("@${person.username.removePrefix("@").trim()}", color = FynxDesign.TextSecondary)
                    if (person.verified) Text("Verified", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    if (person.bio.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(person.bio, color = FynxDesign.TextSecondary) }
                    if (person.country.isNotBlank()) Text(person.country, color = FynxDesign.TextSecondary)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                FynxRemoteSocialClient.follow(context, person.username, following).onSuccess { following = it }
                                busy = false
                            }
                        }) { Text(if (following) "Following" else "Follow") }
                        OutlinedButton(enabled = !busy, onClick = { onMessage(person.username) }) {
                            Icon(Icons.Default.Message, null); Spacer(Modifier.width(8.dp)); Text("Message")
                        }
                    }
                    TextButton(enabled = !busy, onClick = { reportOpen = true }) { Text("Report") }
                    if (person.postCount > 0) Text("${person.postCount} posts", color = FynxDesign.TextSecondary)
                    if (person.mutualFriends > 0) Text("${person.mutualFriends} mutual friends", color = FynxDesign.TextSecondary)
                    if (!person.activityVisible) Text("Activity is hidden", color = FynxDesign.TextSecondary)
                }
            }
        }
    }
    if (reportOpen && profile != null) AlertDialog(
        onDismissRequest = { if (!busy) reportOpen = false },
        title = { Text("Report profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(reportReason, { reportReason = it }, label = { Text("Reason") }, singleLine = true)
                OutlinedTextField(reportDetails, { reportDetails = it }, label = { Text("Details (optional)") }, minLines = 3)
                reportMessage?.let { Text(it, color = FynxDesign.TextSecondary) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            scope.launch {
                busy = true; reportMessage = null
                FynxProfileRemoteClient.report(context, profile!!.username, reportReason, reportDetails).onSuccess { reportMessage = "Report submitted (${it.status.lowercase()})." }.onFailure { reportMessage = it.message ?: "Report failed. Try again." }
                busy = false
            }
        }) { Text("Submit") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { reportOpen = false }) { Text("Cancel") } }
    )
}
