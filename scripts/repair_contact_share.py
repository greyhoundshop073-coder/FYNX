from pathlib import Path

p = Path('app/src/main/java/com/fynx/app/ui/ConversationPanel.kt')
s = p.read_text()

if 'import android.provider.ContactsContract' not in s:
    s = s.replace('import android.content.pm.PackageManager\n', 'import android.content.pm.PackageManager\nimport android.provider.ContactsContract\n', 1)

state_anchor = '    var showAttachmentSheet by remember { mutableStateOf(false) }\n'
state_block = '''    var pendingContactName by remember { mutableStateOf("") }\n    var pendingContactPhone by remember { mutableStateOf("") }\n    var pendingContactUsername by remember { mutableStateOf("") }\n    var showContactPreview by remember { mutableStateOf(false) }\n'''
if 'var showContactPreview by remember { mutableStateOf(false) }' not in s:
    if state_anchor not in s:
        raise SystemExit('Expected attachment-sheet state anchor was not found; refusing to modify source')
    s = s.replace(state_anchor, state_anchor + state_block, 1)

# Remove any existing contact picker block so it can be placed after all dependent chat state/functions are declared.
start = s.find('    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact())')
end = s.find('    val locationPermission = rememberLauncherForActivityResult', start)
if start >= 0 and end >= 0:
    s = s[:start] + s[end:]

launcher = '''    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->\n        if (uri == null) return@rememberLauncherForActivityResult\n        conversationScope.launch {\n            sending = true\n            try {\n                var displayName = "Contact"\n                var phone = ""\n                context.contentResolver.query(\n                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,\n                    arrayOf(\n                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,\n                        ContactsContract.CommonDataKinds.Phone.NUMBER\n                    ),\n                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",\n                    arrayOf(uri.lastPathSegment),\n                    null\n                )?.use { cursor ->\n                    if (cursor.moveToFirst()) {\n                        displayName = cursor.getString(0)?.trim().orEmpty().ifBlank { "Contact" }\n                        phone = cursor.getString(1)?.trim().orEmpty()\n                    }\n                }\n                if (phone.isBlank()) throw IllegalArgumentException("This contact has no phone number to share.")\n                val normalized = FynxPeopleDiscovery.normalizePhone(phone)\n                val matchedUsername = FynxSocialClient.searchUsers(context, normalized, phoneSearch = true)\n                    .getOrNull()?.firstOrNull()?.username.orEmpty()\n                pendingContactName = displayName\n                pendingContactPhone = phone\n                pendingContactUsername = matchedUsername\n                showContactPreview = true\n            } catch (e: Exception) {\n                networkError = e.message ?: "Contact could not be selected"\n            } finally {\n                sending = false\n            }\n        }\n    }\n'''
anchor = '    var stopRecordingAction: (() -> Unit)? = null\n'
if 'val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact())' not in s:
    if anchor not in s:
        raise SystemExit('Expected recording-action anchor was not found; refusing to modify source')
    s = s.replace(anchor, anchor + '\n' + launcher, 1)

old_action = 'Triple("Contact", Icons.Default.ContactPage) { showAttachmentSheet = false; showContactDialog = true },'
new_action = 'Triple("Contact", Icons.Default.ContactPage) { showAttachmentSheet = false; contactPicker.launch(null) },'
s = s.replace(old_action, new_action, 1)

# Remove any legacy username-only contact dialog if it remains.
start = s.find('    if (showContactDialog) {')
end = s.find('    if (showPollDialog) {', start)
if start >= 0 and end >= 0:
    s = s[:start] + s[end:]

marker = '    if (showPollDialog) {'
if 'if (showContactPreview) {' not in s:
    preview = '''    if (showContactPreview) {\n        AlertDialog(\n            onDismissRequest = { showContactPreview = false },\n            title = { Text("Share contact") },\n            text = {\n                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {\n                    Icon(Icons.Default.ContactPage, "Contact", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))\n                    Text(pendingContactName, style = MaterialTheme.typography.titleMedium)\n                    Text(pendingContactPhone, style = MaterialTheme.typography.bodyMedium)\n                    if (pendingContactUsername.isNotBlank()) {\n                        Text("@" + pendingContactUsername.removePrefix("@"), style = MaterialTheme.typography.bodySmall)\n                    }\n                    Text("This contact will be sent as a contact card to this chat.", style = MaterialTheme.typography.bodySmall)\n                }\n            },\n            confirmButton = {\n                TextButton(enabled = !sending, onClick = {\n                    showContactPreview = false\n                    conversationScope.launch {\n                        sending = true\n                        val payload = buildMap {\n                            put("displayName", pendingContactName)\n                            put("phone", pendingContactPhone)\n                            if (pendingContactUsername.isNotBlank()) put("username", pendingContactUsername)\n                        }\n                        FynxProductionMessaging.sendStructuredMessage(context, chat.username, "contact", payload)\n                            .onSuccess { remote ->\n                                messages = (messages + FynxProductionMessaging.toChatMessage(remote, currentUserId ?: ""))\n                                    .distinctBy { it.id }.sortedBy { it.timestamp }\n                                pendingContactName = ""\n                                pendingContactPhone = ""\n                                pendingContactUsername = ""\n                            }\n                            .onFailure { networkError = it.message ?: "Contact could not be sent" }\n                        sending = false\n                    }\n                }) { Text("Send") }\n            },\n            dismissButton = { TextButton(onClick = { showContactPreview = false }) { Text("Cancel") } }\n        )\n    }\n\n'''
    if marker not in s:
        raise SystemExit('Expected poll dialog marker was not found; refusing to modify source')
    s = s.replace(marker, preview + marker, 1)

p.write_text(s)
