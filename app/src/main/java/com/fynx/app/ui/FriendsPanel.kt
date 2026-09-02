package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    val currentUsername = remember { FynxAuthStore.storedUsername(context)?.let(::normalizeUsername) ?: "@preview" }
    var query by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("Friends") }
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
    val filtered = knownProfiles.filter { query.isBlank() || it.username.contains(query.trim(), true) || it.displayName.contains(query.trim(), true) }
    val friends = filtered.filter { it.status == FynxFriendStatus.FRIENDS }
    val incoming = filtered.filter { it.status == FynxFriendStatus.INCOMING_PENDING }
    val outgoing = filtered.filter { it.status == FynxFriendStatus.OUTGOING_PENDING }
    val discover = filtered.filter { it.status == FynxFriendStatus.NONE || it.status == FynxFriendStatus.DECLINED }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it.take(80) }, modifier = Modifier.weight(1f), singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") }, placeholder = { Text("Search known FYNX usernames") }, shape = FynxDesign.ControlShape)
            OutlinedButton(onClick = { shareFynx(context) }, shape = FynxDesign.ControlShape, border = BorderStroke(1.dp, FynxDesign.Outline)) { Text("Invite") }
        }
        Spacer(Modifier.height(14.dp))
        Text("Friends", style = MaterialTheme.typography.headlineSmall)
        Text("Manage real people you have connected with. No demo accounts are added.", color = FynxDesign.TextSecondary)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Friends", "Requests", "Sent", "Discover").forEach { tab -> FilterChip(selected = section == tab, onClick = { section = tab }, label = { Text(tab) }) }
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
                else -> {
                    if (discover.isEmpty()) emptyState("No people to discover", "Open a real conversation first or use Invite to bring someone to FYNX.")
                    items(discover, key = { "discover_${it.username}" }) { person ->
                        FriendRow(person, "Add Friend", onOpenProfile, onAction = { store.sendRequest(person); refresh() })
                    }
                }
            }
        }
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
