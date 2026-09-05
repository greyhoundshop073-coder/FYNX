package com.fynx.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = granted
        if (!granted) notice = "Contacts permission is needed to find people you already know on FYNX."
    }

    fun loadContacts() {
        scope.launch {
            loading = true
            notice = null
            val local = readDeviceContacts(context)
            contacts = local
            val found = mutableMapOf<String, FynxSocialClient.User>()
            local.take(150).forEach { contact ->
                FynxSocialClient.searchUsers(context, FynxPeopleDiscovery.normalizePhone(contact.phone), phoneSearch = true)
                    .getOrNull()?.firstOrNull()?.let { found[contact.phone] = it }
            }
            matched = found
            loading = false
        }
    }

    LaunchedEffect(permission) { if (permission) loadContacts() }

    if (openChat != null) {
        ConversationPanel(chat = openChat!!, onBack = { openChat = null }, onOpenProfile = {}, onVoiceCall = {}, onVideoCall = {})
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(contacts, key = { "${it.name}_${it.phone}" }) { contact ->
                    val user = matched[contact.phone]
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.People, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(contact.name.ifBlank { "Unknown contact" }, style = MaterialTheme.typography.titleSmall)
                                Text(if (user != null) "On FYNX" else "Not on FYNX", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            if (user != null) {
                                TextButton(onClick = {
                                    val username = user.username.removePrefix("@")
                                    openChat = FynxChatStore.loadPreviews(context).firstOrNull { it.username.equals("@$username", true) }
                                        ?: ChatPreview(user.displayName.ifBlank { username }, "@$username", "Start a conversation", "Now")
                                }) { Icon(Icons.Default.ChatBubbleOutline, null); Spacer(Modifier.width(3.dp)); Text("Chat") }
                            } else {
                                TextButton(onClick = {
                                    val message = "🚀 I'm on FYNX! Join me so we can chat, connect, share moments, discover businesses, and find products—all in one place. 🤝💬🛍️\n\nJoin FYNX and let's connect there!"
                                    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }
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
