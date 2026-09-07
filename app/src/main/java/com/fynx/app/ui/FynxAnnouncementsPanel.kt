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
    LaunchedEffect(refresh) { loading = true; FynxAdminClient.announcements(context).onSuccess { items = it; error = null }.onFailure { error = it.message ?: "Unable to load announcements." }; loading = false }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Official FYNX Announcements", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp)); Text("Important updates published by the FYNX team.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp))
        when { loading -> CircularProgressIndicator(); error != null -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Could not load announcements"); Spacer(Modifier.height(6.dp)); Text(error!!, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(10.dp)); TextButton(onClick = { refresh++ }) { Text("Retry") } } }; items.isEmpty() -> Text("No official announcements yet.", color = MaterialTheme.colorScheme.onSurfaceVariant); else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(items, key = { it.id }) { announcement -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(announcement.title, style = MaterialTheme.typography.titleMedium); if (announcement.priority != "NORMAL") AssistChip(onClick = {}, label = { Text(announcement.priority) }) }; Spacer(Modifier.height(8.dp)); Text(announcement.body); if (announcement.publishedAt.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(announcement.publishedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } } }
    }
}

@Composable
fun FynxAdminControlCenterPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dashboard by remember { mutableStateOf<FynxAdminClient.Dashboard?>(null) }
    var admins by remember { mutableStateOf<List<FynxAdminClient.Admin>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var targetQuery by remember { mutableStateOf("") }
    var targets by remember { mutableStateOf<List<FynxSocialClient.User>>(emptyList()) }
    var targetMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var refreshAdmins by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { FynxAdminClient.dashboard(context).onSuccess { dashboard = it; error = null }.onFailure { error = it.message ?: "Admin access required." }; loading = false }
    LaunchedEffect(refreshAdmins, dashboard?.role) { if (dashboard != null) FynxAdminClient.admins(context).onSuccess { admins = it } }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Owner / Admin Control Center", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp))
        if (loading) CircularProgressIndicator() else if (dashboard == null) Text(error ?: "Admin access required.", color = MaterialTheme.colorScheme.error) else {
            val d = dashboard!!
            Text("Role: ${d.role}", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCard("Users", d.users); StatCard("Reports", d.openReports) }; Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCard("Appeals", d.openAppeals); StatCard("Safety / 24h", d.safetyEvents24h) }
            if (d.role == "OWNER") { Spacer(Modifier.height(20.dp)); Text("Current Admins", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); if (admins.isEmpty()) Text("No additional Admins yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) else admins.forEach { admin -> Card(Modifier.fillMaxWidth().padding(top = 6.dp)) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(admin.displayName.ifBlank { "FYNX Admin" }, style = MaterialTheme.typography.titleMedium); Text("@${admin.username}", color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton(onClick = { statusMessage = "Removing @${admin.username}..."; scope.launch { FynxAdminClient.revokeAdmin(context, admin.id).onSuccess { statusMessage = "Admin access removed."; refreshAdmins++ }.onFailure { statusMessage = it.message ?: "Admin removal failed." } } }) { Text("Remove") } } } } }
            Spacer(Modifier.height(20.dp)); Text("Manage an account", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); OutlinedTextField(targetQuery, { targetQuery = it }, Modifier.fillMaxWidth(), label = { Text("Search username") }, singleLine = true); Spacer(Modifier.height(6.dp)); Button(enabled = targetQuery.isNotBlank(), onClick = { targetMessage = "Searching..."; scope.launch { FynxSocialClient.searchUsers(context, targetQuery).onSuccess { targets = it; targetMessage = if (it.isEmpty()) "No users found." else null }.onFailure { targetMessage = it.message ?: "User search failed." } } }) { Text("Find user") }; targetMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            targets.take(5).forEach { user -> Card(Modifier.fillMaxWidth().padding(top = 8.dp)) { Column(Modifier.padding(12.dp)) { Text(user.displayName.ifBlank { "FYNX user" }, style = MaterialTheme.typography.titleMedium); Text("@${user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { scope.launch { FynxAdminClient.setAccountStatus(context, user.id, "LOCKED").onSuccess { statusMessage = "@${user.username} locked." }.onFailure { statusMessage = it.message ?: "Action failed." } } }) { Text("Lock") }; OutlinedButton(onClick = { scope.launch { FynxAdminClient.setAccountStatus(context, user.id, "ACTIVE").onSuccess { statusMessage = "@${user.username} restored." }.onFailure { statusMessage = it.message ?: "Action failed." } } }) { Text("Activate") }; if (d.role == "OWNER") OutlinedButton(onClick = { scope.launch { FynxAdminClient.grantAdmin(context, user.id).onSuccess { statusMessage = "@${user.username} is now an Admin."; refreshAdmins++ }.onFailure { statusMessage = it.message ?: "Admin grant failed." } } }) { Text("Make Admin") } } } } }
            Spacer(Modifier.height(20.dp)); Text("Publish official announcement", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), label = { Text("Message") }, minLines = 4); Spacer(Modifier.height(8.dp)); Button(enabled = title.isNotBlank() && body.isNotBlank(), onClick = { statusMessage = "Publishing..."; scope.launch { FynxAdminClient.publishAnnouncement(context, title, body, "NORMAL").onSuccess { title = ""; body = ""; statusMessage = "Announcement published." }.onFailure { statusMessage = it.message ?: "Publish failed." } } }) { Text("Publish") }; statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun RowScope.StatCard(label: String, value: Int) { Card(Modifier.weight(1f)) { Column(Modifier.padding(14.dp)) { Text(label, style = MaterialTheme.typography.labelMedium); Text(value.toString(), style = MaterialTheme.typography.headlineSmall) } } }
