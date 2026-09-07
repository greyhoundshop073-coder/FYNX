package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FriendsPanel(onOpenProfile: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val privacyStore = remember { FynxPhoneDiscoveryPrivacyStore(context) }
    var query by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("Friends") }
    var searchMethod by remember { mutableStateOf(FynxPeopleSearchMethod.USERNAME) }
    var showPhonePrivacy by remember { mutableStateOf(false) }
    var showUniversalSearch by remember { mutableStateOf(false) }
    var phonePrivacy by remember { mutableStateOf(privacyStore.load()) }
    var friends by remember { mutableStateOf(emptyList<FynxSocialClient.User>()) }
    var incoming by remember { mutableStateOf(emptyList<FynxSocialClient.FriendRequest>()) }
    var outgoing by remember { mutableStateOf(emptyList<FynxSocialClient.FriendRequest>()) }
    var blocked by remember { mutableStateOf(emptyList<FynxSocialClient.User>()) }
    var searchResults by remember { mutableStateOf(emptyList<FynxSocialClient.User>()) }
    var loading by remember { mutableStateOf(true) }
    var busyUsername by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    fun refresh() { scope.launch { loading = true; message = null; val fr = FynxSocialClient.friends(context); val rr = FynxSocialClient.requests(context); val br = FynxSocialClient.blocked(context); friends = fr.getOrElse { emptyList() }; val rs = rr.getOrElse { emptyList() }; incoming = rs.filter { it.status.equals("incoming", true) }; outgoing = rs.filter { it.status.equals("outgoing", true) }; blocked = br.getOrElse { emptyList() }; val e = fr.exceptionOrNull() ?: rr.exceptionOrNull() ?: br.exceptionOrNull(); if (e != null) message = e.message ?: "Could not load your connections."; loading = false } }
    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(query, searchMethod) { val trimmed = query.trim(); val normalizedPhone = FynxPeopleDiscovery.normalizePhone(trimmed); val ready = if (searchMethod == FynxPeopleSearchMethod.PHONE) normalizedPhone.length >= 7 else trimmed.removePrefix("@").length >= 2; if (!ready) { searchResults = emptyList(); return@LaunchedEffect }; FynxSocialClient.searchUsers(context, if (searchMethod == FynxPeopleSearchMethod.PHONE) normalizedPhone else trimmed.removePrefix("@"), phoneSearch = searchMethod == FynxPeopleSearchMethod.PHONE).onSuccess { searchResults = it; section = "Discover" }.onFailure { searchResults = emptyList(); message = it.message ?: "Search failed." } }
    val normalizedQuery = if (searchMethod == FynxPeopleSearchMethod.USERNAME) query.trim().removePrefix("@") else FynxPeopleDiscovery.normalizePhone(query)
    val friendNames = friends.map { it.username.lowercase() }.toSet(); val blockedNames = blocked.map { it.username.lowercase() }.toSet(); val incomingNames = incoming.map { it.username.lowercase() }.toSet(); val outgoingNames = outgoing.map { it.username.lowercase() }.toSet()
    val discover = searchResults.filter { val n = it.username.lowercase(); n !in friendNames && n !in blockedNames && n !in incomingNames && n !in outgoingNames }
    fun userFromRequest(r: FynxSocialClient.FriendRequest) = FynxSocialClient.User(r.username, r.displayName, "")
    fun runAction(username: String, action: suspend () -> Result<Unit>) { scope.launch { busyUsername = username; message = null; action().onSuccess { refresh() }.onFailure { message = it.message ?: "That action could not be completed." }; busyUsername = null } }
    if (showUniversalSearch) { FynxUniversalSearchPanel(onOpenProfile = onOpenProfile); return }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Find People", style = MaterialTheme.typography.headlineSmall); Text("Connect with real FYNX accounts.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; OutlinedButton(onClick = { showUniversalSearch = true }, shape = FynxDesign.ControlShape) { Icon(Icons.Default.Search, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Search all FYNX") } }
        Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(searchMethod == FynxPeopleSearchMethod.USERNAME, { searchMethod = FynxPeopleSearchMethod.USERNAME; query = "" }, label = { Text("Username") }); FilterChip(searchMethod == FynxPeopleSearchMethod.PHONE, { searchMethod = FynxPeopleSearchMethod.PHONE; query = "" }, label = { Text("Phone") }); OutlinedButton(onClick = { showPhonePrivacy = true }, shape = FynxDesign.ControlShape, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text("Privacy") }; OutlinedButton(onClick = { shareFynx(context) }, shape = FynxDesign.ControlShape, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Icon(Icons.Default.PersonAdd, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Invite") } }
        Spacer(Modifier.height(7.dp)); OutlinedTextField(query, { query = it.take(80) }, Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, "Search") }, placeholder = { Text(if (searchMethod == FynxPeopleSearchMethod.USERNAME) "Search @username or name" else "+234 801 234 5678") }, shape = FynxDesign.ControlShape)
        if (searchMethod == FynxPeopleSearchMethod.PHONE && query.isNotBlank()) { val validation = FynxPeopleDiscovery.validate(FynxPeopleSearchRequest(searchMethod, normalizedQuery)); if (validation != null) Text(validation, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp)) else Text("Exact phone matching only. Your phone number is never shown in search results.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp)) }
        message?.let { Text(it, color = if (it.contains("could not", true) || it.contains("failed", true) || it.contains("error", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(10.dp)); Text("Connections", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(5.dp)); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("Friends", "Requests", "Sent", "Discover", "Blocked").forEach { tab -> FilterChip(section == tab, { section = tab }, label = { Text(tab) }) } }; Spacer(Modifier.height(6.dp))
        if (loading) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
            when (section) {
                "Friends" -> { if (friends.isEmpty()) emptyState("No friends yet", "Accepted FYNX connections will appear here."); items(friends, key = { "friend_${it.username}" }) { p -> RemoteFriendRow(p, "Remove", busyUsername == p.username, onOpenProfile, onAction = { runAction(p.username) { FynxSocialClient.removeFriend(context, p.username) } }) } }
                "Requests" -> { if (incoming.isEmpty()) emptyState("No incoming requests", "Friend requests from other FYNX accounts will appear here."); items(incoming, key = { "incoming_${it.id}" }) { r -> RemoteFriendRow(userFromRequest(r), "Confirm", busyUsername == r.username, onOpenProfile, secondaryAction = "Delete", onAction = { runAction(r.username) { FynxSocialClient.acceptRequest(context, r.id) } }, onSecondaryAction = { runAction(r.username) { FynxSocialClient.rejectRequest(context, r.id) } }) } }
                "Sent" -> { if (outgoing.isEmpty()) emptyState("No sent requests", "Requests you send will appear here until they are accepted or rejected."); items(outgoing, key = { "outgoing_${it.id}" }) { r -> RemoteFriendRow(userFromRequest(r), "Cancel", busyUsername == r.username, onOpenProfile, onAction = { runAction(r.username) { FynxSocialClient.cancelRequest(context, r.id) } }) } }
                "Discover" -> { if (searchMethod == FynxPeopleSearchMethod.PHONE && normalizedQuery.length < 7) emptyState("Phone discovery", "Enter a valid phone number with country code.") else if (searchMethod == FynxPeopleSearchMethod.USERNAME && normalizedQuery.length < 2) emptyState("Search for a FYNX user", "Type at least two characters of a username or display name.") else if (discover.isEmpty()) emptyState("No matching people", "No available FYNX account matched that search.") else items(discover, key = { "discover_${it.username}" }) { p -> RemoteFriendRow(p, "Add", busyUsername == p.username, onOpenProfile, onAction = { runAction(p.username) { FynxSocialClient.sendRequest(context, p.username) } }) } }
                else -> { if (blocked.isEmpty()) emptyState("No blocked accounts", "Blocked accounts stay out of normal connection lists."); items(blocked, key = { "blocked_${it.username}" }) { p -> RemoteFriendRow(p, "Unblock", busyUsername == p.username, onOpenProfile, onAction = { runAction(p.username) { FynxSocialClient.unblock(context, p.username) } }) } }
            }
        }
    }
    if (showPhonePrivacy) AlertDialog(onDismissRequest = { showPhonePrivacy = false }, title = { Text("Phone discovery privacy") }, text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Choose who can use your verified phone number to find your FYNX account."); FynxPhoneDiscoveryVisibility.values().forEach { option -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(phonePrivacy == option, { phonePrivacy = option; privacyStore.save(option) }); Text(when (option) { FynxPhoneDiscoveryVisibility.EVERYONE -> "Everyone"; FynxPhoneDiscoveryVisibility.CONTACTS_ONLY -> "Contacts only"; FynxPhoneDiscoveryVisibility.NOBODY -> "Nobody" }) } }; Text("This preference is ready for server-side privacy sync.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }, confirmButton = { TextButton(onClick = { showPhonePrivacy = false }) { Text("Done") } })
}

@Composable private fun RemoteFriendRow(person: FynxSocialClient.User, actionText: String, busy: Boolean, onOpenProfile: (String) -> Unit, secondaryAction: String? = null, onAction: () -> Unit, onSecondaryAction: () -> Unit = {}) { Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) { Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { onOpenProfile(person.username) }, modifier = Modifier.size(48.dp)) { FynxRemoteProfileAvatar(mediaId = person.profilePhotoMediaId, contentDescription = person.displayName.ifBlank { person.username }, modifier = Modifier.size(42.dp)) }; Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(person.displayName.ifBlank { person.username }, style = MaterialTheme.typography.titleSmall, maxLines = 1); Text(if (person.username.startsWith("@")) person.username else "@${person.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1) }; if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else if (secondaryAction == null) OutlinedButton(onClick = onAction, shape = FynxDesign.ControlShape, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)) { Text(actionText) } else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Button(onClick = onAction, shape = FynxDesign.ControlShape, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text(actionText) }; OutlinedButton(onClick = onSecondaryAction, shape = FynxDesign.ControlShape, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text(secondaryAction) } } } } }
private fun LazyListScope.emptyState(title: String, body: String) { item { Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } } } }
