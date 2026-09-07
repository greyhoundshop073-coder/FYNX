package com.fynx.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class DeviceContact(val name: String, val phone: String)

@Composable
fun FynxContactsPanel(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var permission by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    var contacts by remember { mutableStateOf(emptyList<DeviceContact>()) }
    var matched by remember { mutableStateOf<Map<String, FynxSocialClient.User>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var openChat by remember { mutableStateOf<ChatPreview?>(null) }
    var profileUser by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = granted
        if (!granted) notice = "Contacts permission is needed to find people you already know on FYNX."
    }

    fun loadContacts() {
        scope.launch {
            loading = true
            notice = null
            try {
                val local = readDeviceContacts(context)
                val sim = readSimContacts(context)
                contacts = mergeContacts(local, sim)

                // Keep phone matching bounded and authenticated, while resolving several contacts
                // concurrently so a large address book does not make the Contacts screen feel stuck.
                val candidates = contacts.take(150)
                val found = mutableMapOf<String, FynxSocialClient.User>()
                var failedLookups = 0
                candidates.chunked(4).forEach { batch ->
                    val results = coroutineScope {
                        batch.map { contact ->
                            async {
                                val result = FynxSocialClient.searchUsers(
                                    context,
                                    FynxPeopleDiscovery.normalizePhone(contact.phone),
                                    phoneSearch = true
                                )
                                contact to result
                            }
                        }.awaitAll()
                    }
                    results.forEach { (contact, result) ->
                        result.getOrNull()?.firstOrNull()?.let { found[contact.phone] = it }
                            ?: run { if (result.isFailure) failedLookups += 1 }
                    }
                }
                matched = found.toMap()
                if (failedLookups > 0 && found.isEmpty() && candidates.isNotEmpty()) {
                    notice = "FYNX could not complete contact matching right now. Check your connection and try again."
                } else if (failedLookups > 0) {
                    notice = "Some contacts could not be checked. Showing the matches FYNX found."
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                throw kotlinx.coroutines.CancellationException()
            } catch (_: Exception) {
                matched = emptyMap()
                notice = "FYNX could not load your contacts right now. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(permission) { if (permission) loadContacts() }

    profileUser?.let { username ->
        OtherUserProfilePanel(
            username = username,
            onBack = { profileUser = null },
            onMessage = { targetUsername ->
                profileUser = null
                openChat = ChatPreview(
                    name = targetUsername.removePrefix("@").ifBlank { targetUsername },
                    username = "@${targetUsername.removePrefix("@").lowercase()}",
                    lastMessage = "",
                    time = "Now"
                )
            }
        )
        return
    }

    if (openChat != null) {
        ConversationPanel(
            chat = openChat!!,
            onBack = { openChat = null },
            onOpenProfile = { username -> profileUser = username.removePrefix("@") },
            onVoiceCall = {},
            onVideoCall = {}
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(8.dp))
            Text("Phone Contacts", style = MaterialTheme.typography.headlineSmall)
        }
        Text("Find people from the contacts already saved on your phone. FYNX only checks them after you give permission.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (!permission) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.People, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Find your friends on FYNX", style = MaterialTheme.typography.titleMedium)
                    Text("See which people in your phone contacts are already on FYNX and invite the ones who are not.")
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) { Text("Allow contacts") }
                }
            }
        } else if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Text("${contacts.size} contacts", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, placeholder = { Text("Search contacts") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
            val visibleContacts = remember(contacts, searchQuery) { val q = searchQuery.trim().lowercase(); if (q.isBlank()) contacts else contacts.filter { it.name.lowercase().contains(q) || it.phone.contains(q) } }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(visibleContacts, key = { "${it.name}_${it.phone}" }) { contact ->
                    val user = matched[contact.phone]
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (user != null) {
                                FynxRemoteProfileAvatar(
                                    mediaId = user.profilePhotoMediaId,
                                    contentDescription = user.displayName.ifBlank { user.username },
                                    modifier = Modifier.size(44.dp)
                                )
                            } else {
                                Icon(Icons.Default.People, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(contact.name.ifBlank { "Unknown contact" }, style = MaterialTheme.typography.titleSmall)
                                Text(if (user != null) "@${user.username.removePrefix("@").lowercase()}" else "Not on FYNX", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            if (user != null) {
                                TextButton(onClick = {
                                    profileUser = user.username.removePrefix("@")
                                }) {
                                    Icon(Icons.Default.People, null)
                                    Spacer(Modifier.width(3.dp))
                                    Text("Profile")
                                }
                                TextButton(onClick = {
                                    val username = user.username.removePrefix("@").lowercase()
                                    openChat = ChatPreview(
                                        name = user.displayName.ifBlank { username },
                                        username = "@$username",
                                        lastMessage = "",
                                        time = "Now",
                                        online = false
                                    )
                                }) {
                                    Icon(Icons.Default.ChatBubbleOutline, null)
                                    Spacer(Modifier.width(3.dp))
                                    Text("Chat")
                                }
                            } else {
                                TextButton(onClick = {
                                    val payload = FynxShareActions.invitePayload(contact.name.ifBlank { "A friend" })
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TITLE, payload.title)
                                        putExtra(Intent.EXTRA_TEXT, payload.text)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Invite ${contact.name}"))
                                }) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(3.dp)); Text("Invite") }
                            }
                        }
                    }
                }
            }
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun mergeContacts(primary: List<DeviceContact>, sim: List<DeviceContact>): List<DeviceContact> {
    val merged = linkedMapOf<String, DeviceContact>()
    (primary + sim).forEach { contact ->
        val key = FynxPeopleDiscovery.normalizePhone(contact.phone)
        if (key.length >= 7 && !merged.containsKey(key)) merged[key] = contact
    }
    return merged.values.sortedBy { it.name.lowercase() }
}

private fun readSimContacts(context: Context): List<DeviceContact> {
    if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val output = linkedMapOf<String, DeviceContact>()
    val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.RawContacts.ACCOUNT_TYPE)
    runCatching {
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, "${ContactsContract.RawContacts.ACCOUNT_TYPE} LIKE ?", arrayOf("%SIM%"), ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                val phone = if (phoneIndex >= 0) cursor.getString(phoneIndex).orEmpty() else ""
                val normalized = FynxPeopleDiscovery.normalizePhone(phone)
                if (normalized.length >= 7) output[normalized] = DeviceContact(name, normalized)
            }
        }
    }
    return output.values.toList()
}

private fun readDeviceContacts(context: Context): List<DeviceContact> {
    val output = linkedMapOf<String, DeviceContact>()
    val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
    context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (cursor.moveToNext()) {
            val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
            val phone = if (phoneIndex >= 0) cursor.getString(phoneIndex).orEmpty() else ""
            val normalized = FynxPeopleDiscovery.normalizePhone(phone)
            if (normalized.length >= 7) output[normalized] = DeviceContact(name, normalized)
        }
    }
    return output.values.toList()
}
