package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GroupChatPanel(
    group: GroupChat,
    currentUsername: String,
    onBack: () -> Unit,
    onGroupChanged: (GroupChat) -> Unit = {}
) {
    var newMember by remember { mutableStateOf("") }
    var description by remember { mutableStateOf(group.description) }
    val isAdmin = group.isAdmin(currentUsername)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text(group.name, style = MaterialTheme.typography.titleLarge)
        }
        Text(description.ifBlank { "Group conversation" }, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))

        Text("Members (${group.memberUsernames.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(group.memberUsernames) { username ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(username + if (username in group.adminUsernames) "  • Admin" else "")
                    if (isAdmin && username != currentUsername) {
                        TextButton(onClick = { onGroupChanged(group.removeMember(username)) }) { Text("Remove") }
                    }
                }
            }
        }

        if (isAdmin) {
            OutlinedTextField(
                value = newMember,
                onValueChange = { newMember = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Username to add") }
            )
            Row {
                Button(onClick = {
                    val value = newMember.trim()
                    if (value.isNotEmpty()) {
                        onGroupChanged(group.addMember(value)); newMember = ""
                    }
                }, enabled = newMember.isNotBlank()) { Text("Add member") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    val value = newMember.trim()
                    if (value.isNotEmpty() && value in group.memberUsernames) {
                        onGroupChanged(group.promoteToAdmin(value)); newMember = ""
                    }
                }, enabled = newMember.isNotBlank()) { Text("Make admin") }
            }
        }
    }
}
