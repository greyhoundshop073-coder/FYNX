from pathlib import Path

p = Path('app/src/main/java/com/fynx/app/ui/ConversationPanel.kt')
s = p.read_text()

if 'import android.provider.ContactsContract' not in s:
    s = s.replace('import android.content.pm.PackageManager\n', 'import android.content.pm.PackageManager\nimport android.provider.ContactsContract\n', 1)

anchor = '    val context = LocalContext.current\n'
launcher = r'''    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        conversationScope.launch {
            sending = true
            try {
                var displayName = "Contact"
                var phone = ""
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                    arrayOf(uri.lastPathSegment), null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        displayName = cursor.getString(0)?.trim().orEmpty().ifBlank { "Contact" }
                        phone = cursor.getString(1)?.trim().orEmpty()
                    }
                }
                if (phone.isBlank()) throw IllegalArgumentException("This contact has no phone number to share.")
                val normalized = FynxPeopleDiscovery.normalizePhone(phone)
                val matchedUsername = FynxSocialClient.searchUsers(context, normalized, phoneSearch = true).getOrNull()?.firstOrNull()?.username.orEmpty()
                val payload = buildMap {
                    put("displayName", displayName)
                    put("phone", phone)
                    if (matchedUsername.isNotBlank()) put("username", matchedUsername)
                }
                FynxProductionMessaging.sendStructuredMessage(context, chat.username, "contact", payload)
                    .onSuccess { remote -> messages = (messages + FynxProductionMessaging.toChatMessage(remote, currentUserId ?: "")).distinctBy { it.id }.sortedBy { it.timestamp } }
                    .onFailure { networkError = it.message ?: "Contact could not be sent" }
            } catch (e: Exception) {
                networkError = e.message ?: "Contact could not be shared"
            } finally {
                sending = false
            }
        }
    }
'''
if 'val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact())' not in s:
    if anchor not in s:
        raise SystemExit('Expected ConversationPanel context anchor was not found; refusing to modify source')
    s = s.replace(anchor, anchor + launcher, 1)

old_action = 'Triple("Contact", Icons.Default.ContactPage) { showAttachmentSheet = false; showContactDialog = true },'
new_action = 'Triple("Contact", Icons.Default.ContactPage) { showAttachmentSheet = false; contactPicker.launch(null) },'
if old_action not in s:
    raise SystemExit('Expected Contact attachment action was not found; refusing to modify source')
s = s.replace(old_action, new_action, 1)

start = s.find('    if (showContactDialog) {')
end = s.find('    if (showPollDialog) {', start)
if start < 0 or end < 0:
    raise SystemExit('Expected Contact dialog block was not found; refusing to modify source')
s = s[:start] + s[end:]

p.write_text(s)
