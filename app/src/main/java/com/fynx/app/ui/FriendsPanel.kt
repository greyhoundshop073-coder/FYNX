package com.fynx.app.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FriendsPanel(onOpenProfile: (String) -> Unit = {}, onOpenChat: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf("Friends") }
    var searchMethod by rememberSaveable { mutableStateOf(FynxPeopleSearchMethod.USERNAME) }
    var filterOpen by remember { mutableStateOf(false) }
    var friends by remember { mutableStateOf(emptyList<FynxSocialClient.User>()) }
    var incoming by remember { mutableStateOf(emptyList<FynxSocialClient.FriendRequest>()) }
    var outgoing by remember { mutableStateOf(emptyList<FynxSocialClient.FriendRequest>()) }
    var blocked by remember { mutableStateOf(emptyList<FynxSocialClient.User>()) }
    var results by remember { mutableStateOf(emptyList<FynxSocialClient.User>()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuUser by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    suspend fun refresh() {
        loading = true
        error = null
        val fr = FynxSocialClient.friends(context)
        val rr = FynxSocialClient.requests(context)
        val br = FynxSocialClient.blocked(context)
        friends = fr.getOrElse { emptyList() }
        val requests = rr.getOrElse { emptyList() }
        incoming = requests.filter { it.status.equals("incoming", true) }
        outgoing = requests.filter { it.status.equals("outgoing", true) }
        blocked = br.getOrElse { emptyList() }
        error = fr.exceptionOrNull()?.message ?: rr.exceptionOrNull()?.message ?: br.exceptionOrNull()?.message
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    LaunchedEffect(query, searchMethod) {
        val text = query.trim()
        val phone = FynxPeopleDiscovery.normalizePhone(text)
        val ready = if (searchMethod == FynxPeopleSearchMethod.PHONE) phone.length >= 7 else text.removePrefix("@").length >= 2
        if (!ready) {
            results = emptyList()
            return@LaunchedEffect
        }
        FynxSocialClient.searchUsers(
            context,
            if (searchMethod == FynxPeopleSearchMethod.PHONE) phone else text.removePrefix("@"),
            phoneSearch = searchMethod == FynxPeopleSearchMethod.PHONE
        ).onSuccess { results = it; section = "Discover" }
            .onFailure { results = emptyList(); error = it.message ?: "Search failed." }
    }

    val friendNames = friends.map { it.username.lowercase() }.toSet()
    val blockedNames = blocked.map { it.username.lowercase() }.toSet()
    val incomingNames = incoming.map { it.username.lowercase() }.toSet()
    val outgoingNames = outgoing.map { it.username.lowercase() }.toSet()
    val discover = results.filter {
        val name = it.username.lowercase()
        name !in friendNames && name !in blockedNames && name !in incomingNames && name !in outgoingNames
    }

    fun act(username: String, action: suspend () -> Result<Unit>) {
        scope.launch {
            busy = username
            val result = action()
            if (result.isSuccess) refresh() else error = result.exceptionOrNull()?.message ?: "Action failed."
            busy = null
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { (context as? Activity)?.onBackPressed() },
                modifier = Modifier.requiredSize(44.dp).semantics { contentDescription = "Back" }
            ) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(
                "Friends",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.requiredSize(44.dp))
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(56.dp), RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.People, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Find People", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Connect with people on FYNX", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Box {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(80) },
                        modifier = Modifier.fillMaxWidth().padding(end = 54.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, "Search") },
                        placeholder = { Text(if (searchMethod == FynxPeopleSearchMethod.USERNAME) "Search username or name" else "Search phone number") },
                        shape = RoundedCornerShape(28.dp)
                    )
                    IconButton(
                        onClick = { filterOpen = true },
                        modifier = Modifier.align(Alignment.CenterEnd).size(52.dp).semantics { contentDescription = "Search filters" }
                    ) { Icon(Icons.Default.Tune, "Search filters") }
                    DropdownMenu(expanded = filterOpen, onDismissRequest = { filterOpen = false }) {
                        DropdownMenuItem(text = { Text("Username or name") }, onClick = { searchMethod = FynxPeopleSearchMethod.USERNAME; query = ""; filterOpen = false })
                        DropdownMenuItem(text = { Text("Phone number") }, onClick = { searchMethod = FynxPeopleSearchMethod.PHONE; query = ""; filterOpen = false })
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), RoundedCornerShape(28.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf("Friends", "Requests", "Sent", "Discover").forEach { tab ->
                        val selected = section == tab
                        Surface(
                            modifier = Modifier.weight(1f).height(48.dp).clickable { section = tab },
                            shape = RoundedCornerShape(24.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(tab, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (tab == "Requests" && incoming.isNotEmpty()) {
                                        Surface(Modifier.size(20.dp), CircleShape, color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = .18f) else MaterialTheme.colorScheme.primary) {
                                            Box(contentAlignment = Alignment.Center) { Text(incoming.size.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (section == "Friends") {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Your Friends", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(friends.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
                if (loading) item { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                else if (friends.isEmpty()) emptyState("No friends yet", "Accepted FYNX connections will appear here.")
                else items(friends, key = { "friend_${it.username}" }) { person ->
                    FriendRow(person, busy == person.username, menuUser == person.username, { open -> menuUser = if (open) person.username else null }, onOpenProfile, onOpenChat) {
                        act(person.username) { FynxSocialClient.removeFriend(context, person.username) }
                    }
                }
            } else {
                item { error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
                when (section) {
                    "Requests" -> if (incoming.isEmpty()) emptyState("No incoming requests", "Friend requests from other FYNX accounts will appear here.") else items(incoming, key = { "in_${it.id}" }) { request ->
                        RequestRow(FynxSocialClient.User(request.username, request.displayName, ""), "Confirm", "Delete", busy == request.username, onOpenProfile, onOpenChat,
                            { act(request.username) { FynxSocialClient.acceptRequest(context, request.id) } },
                            { act(request.username) { FynxSocialClient.rejectRequest(context, request.id) } })
                    }
                    "Sent" -> if (outgoing.isEmpty()) emptyState("No sent requests", "Requests you send will appear here until they are accepted or rejected.") else items(outgoing, key = { "out_${it.id}" }) { request ->
                        RequestRow(FynxSocialClient.User(request.username, request.displayName, ""), "Cancel", null, busy == request.username, onOpenProfile, onOpenChat) {
                            act(request.username) { FynxSocialClient.cancelRequest(context, request.id) }
                        }
                    }
                    else -> {
                        val enough = if (searchMethod == FynxPeopleSearchMethod.PHONE) FynxPeopleDiscovery.normalizePhone(query).length >= 7 else query.trim().removePrefix("@").length >= 2
                        if (!enough) emptyState("Search for a FYNX user", "Type at least two characters of a username or display name.")
                        else if (discover.isEmpty()) emptyState("No matching people", "No available FYNX account matched that search.")
                        else items(discover, key = { "discover_${it.username}" }) { person ->
                            RequestRow(person, "Add", null, busy == person.username, onOpenProfile, onOpenChat) {
                                act(person.username) { FynxSocialClient.sendRequest(context, person.username) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRow(person: FynxSocialClient.User, busy: Boolean, menuOpen: Boolean, onMenuChange: (Boolean) -> Unit, onOpenProfile: (String) -> Unit, onOpenChat: (String) -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onOpenProfile(person.username) }, modifier = Modifier.size(54.dp)) {
            FynxRemoteProfileAvatar(person.profilePhotoMediaId, person.displayName.ifBlank { person.username }, Modifier.size(48.dp), ownerUsername = person.username)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(person.displayName.ifBlank { person.username }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(if (person.username.startsWith("@")) person.username else "@${person.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else {
            IconButton(onClick = { onOpenChat(person.username) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Open chat" }) { Icon(Icons.Default.ChatBubbleOutline, "Open chat") }
            Box {
                IconButton(onClick = { onMenuChange(true) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Friend options" }) { Icon(Icons.Default.MoreVert, "Friend options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuChange(false) }) {
                    DropdownMenuItem(text = { Text("View profile") }, onClick = { onMenuChange(false); onOpenProfile(person.username) })
                    DropdownMenuItem(text = { Text("Remove friend") }, onClick = { onMenuChange(false); onRemove() })
                }
            }
        }
    }
}

@Composable
private fun RequestRow(person: FynxSocialClient.User, primaryText: String, secondaryText: String?, busy: Boolean, onOpenProfile: (String) -> Unit, onOpenChat: (String) -> Unit, onPrimary: () -> Unit, onSecondary: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f))) {
        Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onOpenProfile(person.username) }, modifier = Modifier.size(50.dp)) {
                FynxRemoteProfileAvatar(person.profilePhotoMediaId, person.displayName.ifBlank { person.username }, Modifier.size(44.dp), ownerUsername = person.username)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(person.displayName.ifBlank { person.username }, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("@${person.username.removePrefix("@").trim()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else {
                if (secondaryText != null) OutlinedButton(onClick = onSecondary, shape = FynxDesign.ControlShape) { Text(secondaryText) }
                Spacer(Modifier.width(5.dp))
                Button(onClick = onPrimary, shape = FynxDesign.ControlShape) { Text(primaryText) }
                if (primaryText == "Add") IconButton(onClick = { onOpenChat(person.username) }, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.ChatBubbleOutline, "Open chat") }
            }
        }
    }
}

private fun LazyListScope.emptyState(title: String, body: String) {
    item {
        Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
