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
    var phonePrivacy by remember { mutableStateOf(privacyStore.load()) }
    var friends by remember { mutableStateOf(emptyList<FynxSocialClient.User>()) }
    var incoming by remember { mutableStateOf(emptyList<FynxSocialClient.FriendRequest>()) }
    var outgoing by remember { mutableStateOf(emptyList<FynxSocialClient.FriendRequest>()) }
    var blocked by remember { mutableStateOf(emptyList<FynxSocialClient.User>()) }
    var searchResults by remember { mutableStateOf(emptyList<FynxSocialClient.User>()) }
    var loading by remember { mutableStateOf(true) }
    var busyUsername by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            message = null
            val friendsResult = FynxSocialClient.friends(context)
            val requestResult = FynxSocialClient.requests(context)
            val blockedResult = FynxSocialClient.blocked(context)
            friends = friendsResult.getOrElse { emptyList() }
            val requests = requestResult.getOrElse { emptyList() }
            incoming = requests.filter { it.status.equals("incoming", true) }
            outgoing = requests.filter { it.status.equals("outgoing", true) }
            blocked = blockedResult.getOrElse { emptyList() }
            val error = friendsResult.exceptionOrNull() ?: requestResult.exceptionOrNull() ?: blockedResult.exceptionOrNull()
            if (error != null) message = error.message ?: "Could not load your connections."
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LaunchedEffect(query, searchMethod) {
        if (searchMethod != FynxPeopleSearchMethod.USERNAME || query.trim().length < 2) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        val result = FynxSocialClient.searchUsers(context, query)
        result.onSuccess { searchResults = it }.onFailure { searchResults = emptyList(); message = it.message ?: "Search failed." }
    }

    val normalizedQuery = if (searchMethod == FynxPeopleSearchMethod.USERNAME) query.trim() else FynxPeopleDiscovery.normalizePhone(query)
    val friendNames = friends.map { it.username.lowercase() }.toSet()
    val blockedNames = blocked.map { it.username.lowercase() }.toSet()
    val incomingNames = incoming.map { it.username.lowercase() }.toSet()
    val outgoingNames = outgoing.map { it.username.lowercase() }.toSet()
    val discover = searchResults.filter {
        val name = it.username.lowercase()
        name !in friendNames && name !in blockedNames && name !in incomingNames && name !in outgoingNames
    }

    fun userFromRequest(request: FynxSocialClient.FriendRequest) = FynxSocialClient.User(request.username, request.displayName, "")
    fun runAction(username: String, action: suspend () -> Result<Unit>, success: String? = null) {
        scope.launch {
            busyUsername = username
            message = null
            action().onSuccess { if (success != null) message = success; refresh() }
                .onFailure { message = it.message ?: "That action could not be completed." }
            busyUsername = null
        }
    }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text("Find People", style = MaterialTheme.typography.headlineSmall)
        Text("Connect with real FYNX accounts.", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(searchMethod == FynxPeopleSearchMethod.USERNAME, { searchMethod = FynxPeopleSearchMethod.USERNAME; query = "" }, label = { Text("Username") })
            FilterChip(searchMethod == FynxPeopleSearchMethod.PHONE, { searchMethod = FynxPeopleSearchMethod.PHONE; query = "" }, label = { Text("Phone") })
            OutlinedButton(onClick = { showPhonePrivacy = true }, shape = FynxDesign.ControlShape, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text("Privacy") }
            OutlinedButton(onClick = { shareFynx(context) }, shape = FynxDesign.ControlShape, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Icon(Icons.Default.PersonAdd, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Invite") }
        }
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(query, { query = it.take(80) }, Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, "Search") }, placeholder = { Text(if (searchMethod == FynxPeopleSearchMethod.USERNAME) "Search @username" else "+234 801 234 5678") }, shape = FynxDesign.ControlShape)
        if (searchMethod == FynxPeopleSearchMethod.PHONE && query.isNotBlank()) {
            val validation = FynxPeopleDiscovery.validate(FynxPeopleSearchRequest(searchMethod, normalizedQuery))
            if (validation != null) Text(validation, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
            else Text("Phone discovery is reserved for the secured account-matching service.", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
        }
        message?.let { Text(it, color = if (it.contains("could not", true) || it.contains("failed", true) || it.contains("error", true)) MaterialTheme.colorScheme.error else FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(10.dp))
        Text("Connections", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("Friends", "Requests", "Sent", "Discover", "Blocked").forEach { tab -> FilterChip(section == tab, { section = tab }, label = { Text(tab) }) }
        }
        Spacer(Modifier.height(6.dp))
        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
                when (section) {
                    "Friends" -> {
                        if (friends.isEmpty()) emptyState("No friends yet", "Accepted FYNX connections will appear here.")
                        items(friends, key = { "friend_${it.username}" }) { person ->
                            RemoteFriendRow(person, "Remove", busyUsername == person.username, onOpenProfile, onAction = { runAction(person.username) { FynxSocialClient.removeFriend(context, person.username) } })
                        }
                    }
                    "Requests" -> {
                        if (incoming.isEmpty()) emptyState("No incoming requests", "Friend requests from other FYNX accounts will appear here.")
                        items(incoming, key = { "incoming_${it.id}" }) { request ->
                            RemoteFriendRow(userFromRequest(request), "Confirm", busyUsername == request.username, onOpenProfile, secondaryAction = "Delete", onAction = { runAction(request.username) { FynxSocialClient.acceptRequest(context, request.id) } }, onSecondaryAction = { runAction(request.username) { FynxSocialClient.rejectRequest(context, request.id) } })
                        }
                    }
                    "Sent" -> {
                        if (outgoing.isEmpty()) emptyState("No sent requests", "Requests you send will appear here until they are accepted or rejected.")
                        items(outgoing, key = { "outgoing_${it.id}" }) { request ->
                            RemoteFriendRow(userFromRequest(request), "Cancel", busyUsername == request.username, onOpenProfile, onAction = { runAction(request.username) { FynxSocialClient.cancelRequest(context, request.id) } })
                        }
                    }
                    "Discover" -> {
                        if (searchMethod == FynxPeopleSearchMethod.PHONE) emptyState("Phone discovery", "Enter a username to search real FYNX accounts. Phone matching will be enabled with the secured account lookup service.")
                        else if (query.trim().length < 2) emptyState("Search for a FYNX user", "Type at least two characters of a username or display name.")
                        else if (discover.isEmpty()) emptyState("No matching people", "No available FYNX account matched that search.")
                        else items(discover, key = { "discover_${it.username}" }) { person ->
                            RemoteFriendRow(person, "Add", busyUsername == person.username, onOpenProfile, onAction = { runAction(person.username) { FynxSocialClient.sendRequest(context, person.username) } })
                        }
                    }
                    else -> {
                        if (blocked.isEmpty()) emptyState("No blocked accounts", "Blocked accounts stay out of normal connection lists.")
                        items(blocked, key = { "blocked_${it.username}" }) { person ->
                            RemoteFriendRow(person, "Unblock", busyUsername == person.username, onOpenProfile, onAction = { runAction(person.username) { FynxSocialClient.unblock(context, person.username) } })
                        }
                    }
                }
            }
        }
    }

    if (showPhonePrivacy) AlertDialog(onDismissRequest = { showPhonePrivacy = false }, title = { Text("Phone discovery privacy") }, text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Choose who can use your verified phone number to find your FYNX account."); FynxPhoneDiscoveryVisibility.values().forEach { option -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(phonePrivacy == option, { phonePrivacy = option; privacyStore.save(option) }); Text(when (option) { FynxPhoneDiscoveryVisibility.EVERYONE -> "Everyone"; FynxPhoneDiscoveryVisibility.CONTACTS_ONLY -> "Contacts only"; FynxPhoneDiscoveryVisibility.NOBODY -> "Nobody" }) } }; Text("Stored locally until account privacy sync is connected.", color = FynxDesign.TextSecondary) } }, confirmButton = { TextButton(onClick = { showPhonePrivacy = false }) { Text("Done") } })
}

@Composable
private fun RemoteFriendRow(person: FynxSocialClient.User, actionText: String, busy: Boolean, onOpenProfile: (String) -> Unit, secondaryAction: String? = null, onAction: () -> Unit, onSecondaryAction: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onOpenProfile(person.username) }, modifier = Modifier.size(48.dp)) { FynxAvatar(person.displayName.ifBlank { person.username }, Modifier.size(42.dp)) }
            Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(person.displayName.ifBlank { person.username }, style = MaterialTheme.typography.titleSmall, maxLines = 1); Text(if (person.username.startsWith("@")) person.username else "@${person.username}", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else if (secondaryAction == null) OutlinedButton(onClick = onAction, shape = FynxDesign.ControlShape, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)) { Text(actionText) }
            else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Button(onClick = onAction, shape = FynxDesign.ControlShape, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text(actionText) }; OutlinedButton(onClick = onSecondaryAction, shape = FynxDesign.ControlShape, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text(secondaryAction) } }
        }
    }
}

private fun LazyListScope.emptyState(title: String, body: String) { item { Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall) } } } }
