package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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

    fun refresh() {
        relationships = store.load()
        chats = FynxChatStore.loadPreviews(context)
    }

    val knownProfiles = remember(relationships, chats) {
        val stored = relationships.associateBy { normalizeUsername(it.username) }
        chats.mapNotNull { chat ->
            val username = normalizeUsername(chat.username)
            if (username.equals(currentUsername, ignoreCase = true)) null else stored[username] ?: FriendProfile(chat.name, username)
        }.plus(relationships.filter { relationship ->
            chats.none { normalizeUsername(it.username).equals(normalizeUsername(relationship.username), ignoreCase = true) }
        }).distinctBy { normalizeUsername(it.username) }
    }

    val normalizedQuery = if (searchMethod == FynxPeopleSearchMethod.USERNAME) {
        normalizeUsername(query)
    } else {
        FynxPeopleDiscovery.normalizePhone(query)
    }
    val filtered = if (query.isBlank()) knownProfiles else knownProfiles.filter {
        if (searchMethod == FynxPeopleSearchMethod.USERNAME) {
            it.username.contains(query.trim(), true) || it.displayName.contains(query.trim(), true)
        } else false
    }
    val friends = filtered.filter { it.status == FynxFriendStatus.FRIENDS }
    val incoming = filtered.filter { it.status == FynxFriendStatus.INCOMING_PENDING }
    val outgoing = filtered.filter { it.status == FynxFriendStatus.OUTGOING_PENDING }
    val discover = filtered.filter { it.status == FynxFriendStatus.NONE || it.status == FynxFriendStatus.DECLINED }
    val blocked = filtered.filter { it.status == FynxFriendStatus.BLOCKED }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background).padding(16.dp)) {
        Text("Find People", style = MaterialTheme.typography.headlineSmall)
        Text("Connect using a FYNX username or phone number.", color = FynxDesign.TextSecondary)
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = searchMethod == FynxPeopleSearchMethod.USERNAME,
                onClick = { searchMethod = FynxPeopleSearchMethod.USERNAME; query = "" },
                label = { Text("Username") }
            )
            FilterChip(
                selected = searchMethod == FynxPeopleSearchMethod.PHONE,
                onClick = { searchMethod = FynxPeopleSearchMethod.PHONE; query = "" },
                label = { Text("Phone number") }
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { showPhonePrivacy = true }, shape = FynxDesign.ControlShape, border = BorderStroke(1.dp, FynxDesign.Outline)) {
                Text("Privacy")
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                placeholder = { Text(if (searchMethod == FynxPeopleSearchMethod.USERNAME) "@username" else "+234 801 234 5678") },
                shape = FynxDesign.ControlShape
            )
            OutlinedButton(onClick = { shareFynx(context) }, shape = FynxDesign.ControlShape, border = BorderStroke(1.dp, FynxDesign.Outline)) { Text("Invite") }
        }

        if (searchMethod == FynxPeopleSearchMethod.PHONE && query.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            val validation = FynxPeopleDiscovery.validate(FynxPeopleSearchRequest(searchMethod, normalizedQuery))
            if (validation != null) {
                Text(validation, color = MaterialTheme.colorScheme.error)
            } else {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = FynxDesign.CardShape,
                    colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
                    border = BorderStroke(1.dp, FynxDesign.Outline)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Phone-number search is ready for secure account lookup", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "FYNX will match a verified phone number to an account without revealing the number. Server-wide lookup will activate when the social backend is connected.",
                            color = FynxDesign.TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Friends", style = MaterialTheme.typography.titleLarge)
        Text("Manage real people you have connected with. No demo accounts are added.", color = FynxDesign.TextSecondary)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Friends", "Requests", "Sent", "Discover", "Blocked").forEach { tab ->
                FilterChip(selected = section == tab, onClick = { section = tab }, label = { Text(tab) })
            }
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            when (section) {
                "Friends" -> {
                    if (friends.isEmpty()) emptyState("No friends yet", "When a real FYNX connection is accepted, it will appear here.")
                    items(friends, key = { "friend_${it.username}" }) { person ->
                        FriendRow(person, "Remove", onOpenProfile, onAction = { store.removeFriend(person.username); refresh() })
                    }
                }
                "Requests" -> {
                    if (incoming.isEmpty()) emptyState("No incoming requests", "Friend requests from other accounts will appear here after server sync is connected.")
                    items(incoming, key = { "incoming_${it.username}" }) { person ->
                        FriendRow(person, "Confirm", onOpenProfile, secondaryAction = "Delete",
                            onAction = { store.acceptRequest(person.username); refresh() },
                            onSecondaryAction = { store.declineRequest(person.username); refresh() })
                    }
                }
                "Sent" -> {
                    if (outgoing.isEmpty()) emptyState("No sent requests", "Requests you send stay visible here until accepted, declined, or cancelled.")
                    items(outgoing, key = { "outgoing_${it.username}" }) { person ->
                        FriendRow(person, "Cancel", onOpenProfile, onAction = { store.cancelRequest(person.username); refresh() })
                    }
                }
                "Discover" -> {
                    if (searchMethod == FynxPeopleSearchMethod.PHONE) {
                        emptyState("Phone discovery is backend-ready", "Enter a phone number above. A secure FYNX account lookup will return the matching profile once server account sync is connected.")
                    } else if (discover.isEmpty()) {
                        emptyState("No people to discover", "Search a known FYNX username or use Invite to bring someone to FYNX. Server-wide discovery will be connected with the social backend.")
                    } else {
                        items(discover, key = { "discover_${it.username}" }) { person ->
                            FriendRow(person, "Add Friend", onOpenProfile, onAction = { store.sendRequest(person); refresh() })
                        }
                    }
                }
                else -> {
                    if (blocked.isEmpty()) emptyState("No blocked accounts", "Accounts you block will stay out of your normal friend and discovery lists.")
                    items(blocked, key = { "blocked_${it.username}" }) { person ->
                        FriendRow(person, "Unblock", onOpenProfile, onAction = { store.unblock(person.username); refresh() })
                    }
                }
            }
        }
    }

    if (showPhonePrivacy) {
        AlertDialog(
            onDismissRequest = { showPhonePrivacy = false },
            title = { Text("Phone discovery privacy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Choose who can use your verified phone number to find your FYNX account.")
                    FynxPhoneDiscoveryVisibility.values().forEach { option ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = phonePrivacy == option, onClick = {
                                phonePrivacy = option
                                privacyStore.save(option)
                            })
                            Text(
                                when (option) {
                                    FynxPhoneDiscoveryVisibility.EVERYONE -> "Everyone"
                                    FynxPhoneDiscoveryVisibility.CONTACTS_ONLY -> "Contacts only"
                                    FynxPhoneDiscoveryVisibility.NOBODY -> "Nobody"
                                }
                            )
                        }
                    }
                    Text("This preference is stored locally until account privacy settings are synchronized with the FYNX backend.", color = FynxDesign.TextSecondary)
                }
            },
            confirmButton = { TextButton(onClick = { showPhonePrivacy = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun FriendRow(
    person: FriendProfile,
    actionText: String,
    onOpenProfile: (String) -> Unit,
    secondaryAction: String? = null,
    onAction: () -> Unit,
    onSecondaryAction: () -> Unit = {}
) {
    Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onOpenProfile(person.username) }) { FynxAvatar(person.displayName, Modifier.size(48.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(person.displayName, style = MaterialTheme.typography.titleMedium)
                Text(person.username, color = FynxDesign.TextSecondary)
                if (person.bio.isNotBlank()) Text(person.bio, color = FynxDesign.TextSecondary, maxLines = 1)
            }
            if (secondaryAction == null) {
                OutlinedButton(onClick = onAction, shape = FynxDesign.ControlShape) { Text(actionText) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = onAction, shape = FynxDesign.ControlShape) { Text(actionText) }
                    OutlinedButton(onClick = onSecondaryAction, shape = FynxDesign.ControlShape) { Text(secondaryAction) }
                }
            }
        }
    }
}

private fun LazyListScope.emptyState(title: String, body: String) {
    item {
        Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, color = FynxDesign.TextSecondary) }
        }
    }
}

private fun normalizeUsername(value: String): String = value.trim().let { if (it.startsWith("@")) it else "@$it" }
