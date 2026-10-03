from pathlib import Path

p = Path('app/src/main/java/com/fynx/app/ui/ConversationPanel.kt')
s = p.read_text()

if 'import android.provider.ContactsContract' not in s:
    s = s.replace('import android.content.pm.PackageManager\n', 'import android.content.pm.PackageManager\nimport android.provider.ContactsContract\n', 1)

# The previous repair added the picker near the top of the composable, before the
# conversation state it uses. Move that exact launcher below the state/functions.
start = s.find('    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact())')
end = s.find('    val locationPermission = rememberLauncherForActivityResult', start)
if start < 0 or end < 0:
    raise SystemExit('Expected existing in-chat contact picker block was not found; refusing to modify source')
launcher = s[start:end]
s = s[:start] + s[end:]

if '    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact())' not in s:
    anchor = '    stopRecordingAction = ::stopRecording\n'
    if anchor not in s:
        raise SystemExit('Expected recording state anchor was not found; refusing to modify source')
    s = s.replace(anchor, anchor + '\n' + launcher, 1)

# Ensure the Plus-menu Contact action launches the in-chat device contact picker.
old_action = 'Triple("Contact", Icons.Default.ContactPage) { showAttachmentSheet = false; showContactDialog = true },'
new_action = 'Triple("Contact", Icons.Default.ContactPage) { showAttachmentSheet = false; contactPicker.launch(null) },'
s = s.replace(old_action, new_action, 1)

# The in-chat contact preview/send UI must remain a real confirmation step.
if 'if (showContactPreview) {' not in s:
    marker = '    if (showPollDialog) {'
    if marker not in s:
        raise SystemExit('Expected poll dialog marker was not found; refusing to modify source')
    preview = '''    if (showContactPreview) {\n        AlertDialog(\n            onDismissRequest = { showContactPreview = false },\n            title = { Text("Share contact") },\n            text = {\n                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {\n                    Icon(Icons.Default.ContactPage, "Contact", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))\n                    Text(pendingContactName, style = MaterialTheme.typography.titleMedium)\n                    Text(pendingContactPhone, style = MaterialTheme.typography.bodyMedium)\n                    if (pendingContactUsername.isNotBlank()) Text("@" + pendingContactUsername.removePrefix("@"), style = MaterialTheme.typography.bodySmall)\n                    Text("This contact will be sent as a contact card to this chat.", style = MaterialTheme.typography.bodySmall)\n                }\n            },\n            confirmButton = {\n                TextButton(enabled = !sending, onClick = {\n                    showContactPreview = false\n                    conversationScope.launch {\n                        sending = true\n                        val payload = buildMap {\n                            put("displayName", pendingContactName)\n                            put("phone", pendingContactPhone)\n                            if (pendingContactUsername.isNotBlank()) put("username", pendingContactUsername)\n                        }\n                        FynxProductionMessaging.sendStructuredMessage(context, chat.username, "contact", payload)\n                            .onSuccess { remote ->\n                                messages = (messages + FynxProductionMessaging.toChatMessage(remote, currentUserId ?: ""))\n                                    .distinctBy { it.id }.sortedBy { it.timestamp }\n                                pendingContactName = ""\n                                pendingContactPhone = ""\n                                pendingContactUsername = ""\n                            }\n                            .onFailure { networkError = it.message ?: "Contact could not be sent" }\n                        sending = false\n                    }\n                }) { Text("Send") }\n            },\n            dismissButton = { TextButton(onClick = { showContactPreview = false }) { Text("Cancel") } }\n        )\n    }\n\n'''
    s = s.replace(marker, preview + marker, 1)

p.write_text(s)
