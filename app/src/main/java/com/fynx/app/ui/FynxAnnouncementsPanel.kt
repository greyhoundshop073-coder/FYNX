package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FynxAnnouncementsPanel() {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<FynxAdminClient.Announcement>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        loading = true
        FynxAdminClient.announcements(context).onSuccess { items = it; error = null }.onFailure { error = it.message ?: "Unable to load announcements." }
        loading = false
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Official FYNX Announcements", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Important updates published by the FYNX team.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        when {
            loading -> CircularProgressIndicator()
            error != null -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Could not load announcements"); Spacer(Modifier.height(6.dp)); Text(error!!, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(10.dp)); TextButton(onClick = { refresh++ }) { Text("Retry") } } }
            items.isEmpty() -> Text("No official announcements yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(items, key = { it.id }) { announcement ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(announcement.title, style = MaterialTheme.typography.titleMedium); if (announcement.priority != "NORMAL") AssistChip(onClick = {}, label = { Text(announcement.priority) }) }
                    Spacer(Modifier.height(8.dp)); Text(announcement.body)
                    if (announcement.publishedAt.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(announcement.publishedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } }
            } }
        }
    }
}

@Composable
fun FynxAdminControlCenterPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dashboard by remember { mutableStateOf<FynxAdminClient.Dashboard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        FynxAdminClient.dashboard(context).onSuccess { dashboard = it; error = null }.onFailure { error = it.message ?: "Admin access required." }
        loading = false
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Owner / Admin Control Center", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        if (loading) CircularProgressIndicator()
        else if (dashboard == null) Text(error ?: "Admin access required.", color = MaterialTheme.colorScheme.error)
        else {
            val d = dashboard!!
            Text("Server-authorized administration overview", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCard("Users", d.users); StatCard("Reports", d.openReports) }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCard("Appeals", d.openAppeals); StatCard("Safety / 24h", d.safetyEvents24h) }
            Spacer(Modifier.height(20.dp))
            Text("Publish official announcement", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), label = { Text("Message") }, minLines = 4)
            Spacer(Modifier.height(8.dp))
            Button(enabled = title.isNotBlank() && body.isNotBlank(), onClick = {
                statusMessage = "Publishing..."
                scope.launch { FynxAdminClient.publishAnnouncement(context, title, body, "NORMAL").onSuccess { title = ""; body = ""; statusMessage = "Announcement published." }.onFailure { statusMessage = it.message ?: "Publish failed." } }
            }) { Text("Publish") }
            statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun RowScope.StatCard(label: String, value: Int) { Card(Modifier.weight(1f)) { Column(Modifier.padding(14.dp)) { Text(label, style = MaterialTheme.typography.labelMedium); Text(value.toString(), style = MaterialTheme.typography.headlineSmall) } } }
