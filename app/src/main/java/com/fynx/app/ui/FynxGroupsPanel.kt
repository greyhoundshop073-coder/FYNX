package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable
fun FynxGroupsPanel(onOpenGroup: (String) -> Unit = {}) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf(FynxGroupsStore.load(context)) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val visible = groups.filter { group ->
        group.name.contains(query, true) || group.description.contains(query, true)
    }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                androidx.compose.material3.Text("Groups", style = MaterialTheme.typography.headlineSmall)
                androidx.compose.material3.Text(
                    "Your communities in one place",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { /* Group settings will use the persisted group model in a later Stage 4 batch. */ }) {
                Icon(Icons.Default.Settings, "Group settings")
            }
            FilledIconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, "Create group")
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, "Search groups") },
            placeholder = { androidx.compose.material3.Text("Search groups…") },
            shape = MaterialTheme.shapes.large
        )
        Spacer(Modifier.height(14.dp))
        if (visible.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                androidx.compose.material3.Text(
                    "No groups found. Try another search or create a group.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visible, key = { it.id }) { group ->
                    Card(onClick = { onOpenGroup(group.name) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Group, "Group", tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                androidx.compose.material3.Text(group.name, style = MaterialTheme.typography.titleMedium)
                                androidx.compose.material3.Text(
                                    "${group.members.size} members • ${group.visibility.name.lowercase().replaceFirstChar { it.uppercase() }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(3.dp))
                                androidx.compose.material3.Text(
                                    group.description,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        FynxCreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description, visibility ->
                val group = FynxGroup(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    description = description.trim(),
                    visibility = visibility,
                    ownerUsername = "@username",
                    members = listOf(FynxGroupMember("@username", FynxGroupRole.ADMIN))
                )
                if (FynxGroupsStore.add(context, group)) {
                    groups = FynxGroupsStore.load(context)
                    showCreateDialog = false
                }
            }
        )
    }
}

@Composable
private fun FynxCreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, FynxGroupVisibility) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(FynxGroupVisibility.PRIVATE) }
    val canCreate = name.trim().length >= 2 && description.trim().length >= 2

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Create group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { androidx.compose.material3.Text("Group name") }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    label = { androidx.compose.material3.Text("Description") }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = visibility == FynxGroupVisibility.PRIVATE,
                        onClick = { visibility = FynxGroupVisibility.PRIVATE }
                    )
                    androidx.compose.material3.Text("Private")
                    Spacer(Modifier.width(8.dp))
                    RadioButton(
                        selected = visibility == FynxGroupVisibility.PUBLIC,
                        onClick = { visibility = FynxGroupVisibility.PUBLIC }
                    )
                    androidx.compose.material3.Text("Public")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canCreate, onClick = { onCreate(name, description, visibility) }) {
                androidx.compose.material3.Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Cancel") } }
    )
}

@Composable
fun FynxGroupConversationPanel(groupName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val fallback = remember(groupName) {
        ChatMessage(
            text = "Welcome to $groupName",
            fromMe = false,
            id = "welcome-${groupName.hashCode()}"
        )
    }
    var messages by remember(groupName) {
        mutableStateOf(FynxChatStore.load(context, "group_$groupName", fallback))
    }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { androidx.compose.material3.Text("‹") }
            androidx.compose.material3.Text(
                groupName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { /* Member management is wired in the next safe Stage 4 batch. */ }) {
                Icon(Icons.Default.Group, "Members")
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large
                ) {
                    androidx.compose.material3.Text(message.text, Modifier.padding(12.dp))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { androidx.compose.material3.Text("Message…") },
                maxLines = 4,
                shape = MaterialTheme.shapes.large
            )
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = {
                val trimmed = text.trim()
                if (trimmed.isNotEmpty()) {
                    val next = messages + ChatMessage(
                        text = trimmed,
                        fromMe = true,
                        id = UUID.randomUUID().toString(),
                        delivered = true,
                        read = true
                    )
                    messages = next
                    FynxChatStore.save(context, "group_$groupName", next)
                    text = ""
                }
            }) {
                Icon(Icons.Default.Send, "Send")
            }
        }
    }
}
