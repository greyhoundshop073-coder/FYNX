package com.fynx.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FynxPostAudienceEditor(postId: String, currentVisibility: FynxPostVisibility, onDismiss: () -> Unit, onSaved: (FynxPostVisibility) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var visibility by remember(postId) { mutableStateOf(currentVisibility) }
    var selectedIds by remember(postId) { mutableStateOf<Set<String>>(emptySet()) }
    var friends by remember(postId) { mutableStateOf<List<FynxFriend>>(emptyList()) }
    var loading by remember(postId) { mutableStateOf(true) }
    var saving by remember(postId) { mutableStateOf(false) }
    var error by remember(postId) { mutableStateOf<String?>(null) }
    LaunchedEffect(postId) {
        loading = true
        FynxRemoteSocialClient.postAudience(context, postId).onSuccess { visibility = it.visibility; selectedIds = it.userIds.toSet() }.onFailure { error = it.message ?: "Unable to load this post audience." }
        FynxPostAudienceClient.friends(context).onSuccess { friends = it }.onFailure { if (visibility == FynxPostVisibility.SELECTED_PEOPLE) error = it.message ?: "Unable to load friends." }
        loading = false
    }
    val options = listOf(FynxPostVisibility.PUBLIC to "Public", FynxPostVisibility.FRIENDS_ONLY to "Friends", FynxPostVisibility.SELECTED_PEOPLE to "Selected people", FynxPostVisibility.ONLY_ME to "Only me")
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Post audience") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (value,label) ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !saving && !loading) { visibility = value; if (value != FynxPostVisibility.SELECTED_PEOPLE) selectedIds = emptySet() }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = visibility == value, onClick = null, enabled = !saving && !loading); Spacer(Modifier.width(8.dp)); Text(label)
                }
            }
            if (visibility == FynxPostVisibility.SELECTED_PEOPLE) {
                HorizontalDivider()
                if (loading) CircularProgressIndicator(Modifier.size(22.dp)) else if (friends.isEmpty()) Text("No accepted friends available.", style = MaterialTheme.typography.bodySmall) else {
                    Text("Choose friends", style = MaterialTheme.typography.titleSmall)
                    friends.forEach { friend ->
                        val checked = friend.id in selectedIds
                        Row(Modifier.fillMaxWidth().clickable(enabled = !saving) { selectedIds = if (checked) selectedIds - friend.id else selectedIds + friend.id }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = { value -> selectedIds = if (value) selectedIds + friend.id else selectedIds - friend.id }, enabled = !saving)
                            Spacer(Modifier.width(6.dp)); Column { Text(friend.displayName.ifBlank { friend.username }); Text("@" + friend.username.removePrefix("@"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = {
        Button(enabled = !saving && !loading && (visibility != FynxPostVisibility.SELECTED_PEOPLE || selectedIds.isNotEmpty()), onClick = {
            saving = true; error = null
            scope.launch { FynxRemoteSocialClient.updatePostAudience(context, postId, visibility, selectedIds.toList()).onSuccess { saving = false; onSaved(visibility) }.onFailure { saving = false; error = it.message ?: "Unable to update this post audience." } }
        }) { Text(if (saving) "Saving…" else "Save") }
    }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } })
}
