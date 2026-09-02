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

@Composable
fun FriendsPanel(onOpenProfile: (String) -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { FynxFriendsStore(context) }
    val privacyStore = remember { FynxPhoneDiscoveryPrivacyStore(context) }
    val currentUsername = remember { FynxAuthStore.storedUsername(context)?.let(::normalizeUsername) ?: "@preview" }
    var query by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("Friends") }
    var searchMethod by remember { mutableStateOf(FynxPeopleSearchMethod.USERNAME) }
    var showPhonePrivacy by remember { mutableStateOf(false) }
    var phonePrivacy by remember { mutableStateOf(privacyStore.load()) }
    var relationships by remember { mutableStateOf(store.load()) }
    var chats by remember { mutableStateOf(FynxChatStore.loadPreviews(context)) }
    fun refresh() { relationships = store.load(); chats = FynxChatStore.loadPreviews(context) }
    val knownProfiles = remember(relationships, chats) {
        val stored = relationships.associateBy { normalizeUsername(it.username) }
        chats.mapNotNull { chat -> normalizeUsername(chat.username).takeIf { !it.equals(currentUsername, true) }?.let { stored[it] ?: FriendProfile(chat.name, it) } }
            .plus(relationships.filter { relationship -> chats.none { normalizeUsername(it.username).equals(normalizeUsername(relationship.username), true) } }).distinctBy { normalizeUsername(it.username) }
    }
    val normalizedQuery = if (searchMethod == FynxPeopleSearchMethod.USERNAME) normalizeUsername(query) else FynxPeopleDiscovery.normalizePhone(query)
    val filtered = if (query.isBlank()) knownProfiles else knownProfiles.filter { searchMethod == FynxPeopleSearchMethod.USERNAME && (it.username.contains(query.trim(), true) || it.displayName.contains(query.trim(), true)) }
    val friends = filtered.filter { it.status == FynxFriendStatus.FRIENDS }
    val incoming = filtered.filter { it.status == FynxFriendStatus.INCOMING_PENDING }
    val outgoing = filtered.filter { it.status == FynxFriendStatus.OUTGOING_PENDING }
    val discover = filtered.filter { it.status == FynxFriendStatus.NONE || it.status == FynxFriendStatus.DECLINED }
    val blocked = filtered.filter { it.status == FynxFriendStatus.BLOCKED }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text("Find People", style = MaterialTheme.typography.headlineSmall)
        Text("Connect using a FYNX username or phone number.", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(searchMethod == FynxPeopleSearchMethod.USERNAME, { searchMethod = FynxPeopleSearchMethod.USERNAME; query = "" }, label = { Text("Username") })
            FilterChip(searchMethod == FynxPeopleSearchMethod.PHONE, { searchMethod = FynxPeopleSearchMethod.PHONE; query = "" }, label = { Text("Phone") })
            OutlinedButton(onClick = { showPhonePrivacy = true }, shape = FynxDesign.ControlShape, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text("Privacy") }
            OutlinedButton(onClick = { shareFynx(context) }, shape = FynxDesign.ControlShape, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Icon(Icons.Default.PersonAdd, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Invite") }
        }
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(query, { query = it.take(80) }, Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, "Search") }, placeholder = { Text(if (searchMethod == FynxPeopleSearchMethod.USERNAME) "@username" else "+234 801 234 5678") }, shape = FynxDesign.ControlShape)
        if (searchMethod == FynxPeopleSearchMethod.PHONE && query.isNotBlank()) {
            val validation = FynxPeopleDiscovery.validate(FynxPeopleSearchRequest(searchMethod, normalizedQuery))
            if (validation != null) Text(validation, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
            else Text("Secure phone lookup is ready; server-wide account matching activates with the social backend.", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("Connections", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("Friends", "Requests", "Sent", "Discover", "Blocked").forEach { tab -> FilterChip(section == tab, { section = tab }, label = { Text(tab) }) }
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
            when (section) {
                "Friends" -> { if (friends.isEmpty()) emptyState("No friends yet", "Accepted FYNX connections will appear here."); items(friends, key = { "friend_${it.username}" }) { person -> FriendRow(person, "Remove", onOpenProfile, onAction = { store.removeFriend(person.username); refresh() }) } }
                "Requests" -> { if (incoming.isEmpty()) emptyState("No incoming requests", "Friend requests from other accounts will appear here."); items(incoming, key = { "incoming_${it.username}" }) { person -> FriendRow(person, "Confirm", onOpenProfile, "Delete", { store.acceptRequest(person.username); refresh() }, { store.declineRequest(person.username); refresh() }) } }
                "Sent" -> { if (outgoing.isEmpty()) emptyState("No sent requests", "Requests you send remain here until their status changes."); items(outgoing, key = { "outgoing_${it.username}" }) { person -> FriendRow(person, "Cancel", onOpenProfile, onAction = { store.cancelRequest(person.username); refresh() }) } }
                "Discover" -> { if (searchMethod == FynxPeopleSearchMethod.PHONE) emptyState("Phone discovery is backend-ready", "A secure account lookup will return a matching FYNX profile when server sync is connected.") else if (discover.isEmpty()) emptyState("No people to discover", "Search a known username or invite someone to FYNX.") else items(discover, key = { "discover_${it.username}" }) { person -> FriendRow(person, "Add", onOpenProfile, onAction = { store.sendRequest(person); refresh() }) } }
                else -> { if (blocked.isEmpty()) emptyState("No blocked accounts", "Blocked accounts stay out of normal connection lists."); items(blocked, key = { "blocked_${it.username}" }) { person -> FriendRow(person, "Unblock", onOpenProfile, onAction = { store.unblock(person.username); refresh() }) } }
            }
        }
    }
    if (showPhonePrivacy) AlertDialog(onDismissRequest = { showPhonePrivacy = false }, title = { Text("Phone discovery privacy") }, text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Choose who can use your verified phone number to find your FYNX account."); FynxPhoneDiscoveryVisibility.values().forEach { option -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(phonePrivacy == option, { phonePrivacy = option; privacyStore.save(option) }); Text(when (option) { FynxPhoneDiscoveryVisibility.EVERYONE -> "Everyone"; FynxPhoneDiscoveryVisibility.CONTACTS_ONLY -> "Contacts only"; FynxPhoneDiscoveryVisibility.NOBODY -> "Nobody" }) } }; Text("Stored locally until account privacy sync is connected.", color = FynxDesign.TextSecondary) } }, confirmButton = { TextButton(onClick = { showPhonePrivacy = false }) { Text("Done") } })
}

@Composable
private fun FriendRow(person: FriendProfile, actionText: String, onOpenProfile: (String) -> Unit, secondaryAction: String? = null, onAction: () -> Unit, onSecondaryAction: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onOpenProfile(person.username) }, modifier = Modifier.size(48.dp)) { FynxAvatar(person.displayName, Modifier.size(42.dp)) }
            Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(person.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1); Text(person.username, color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            if (secondaryAction == null) OutlinedButton(onClick = onAction, shape = FynxDesign.ControlShape, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)) { Text(actionText) }
            else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Button(onClick = onAction, shape = FynxDesign.ControlShape, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text(actionText) }; OutlinedButton(onClick = onSecondaryAction, shape = FynxDesign.ControlShape, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text(secondaryAction) } }
        }
    }
}

private fun LazyListScope.emptyState(title: String, body: String) { item { Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall) } } } }
private fun normalizeUsername(value: String): String = value.trim().let { if (it.startsWith("@")) it else "@$it" }
