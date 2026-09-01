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
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

private data class FynxGroupItem(val name: String, val members: Int, val online: Int, val preview: String)

@Composable
fun FynxGroupsPanel(onOpenGroup: (String) -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    val groups = remember {
        listOf(
            FynxGroupItem("Family", 8, 2, "Good morning everyone"),
            FynxGroupItem("Friends", 12, 4, "Alex: See you later"),
            FynxGroupItem("FYNX Community", 126, 18, "Welcome to FYNX")
        )
    }
    val visible = groups.filter { it.name.contains(query, true) }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                androidx.compose.material3.Text("Groups", style = MaterialTheme.typography.headlineSmall)
                androidx.compose.material3.Text("Your communities in one place", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {}) { Icon(Icons.Default.Settings, "Group settings") }
            FilledIconButton(onClick = {}) { Icon(Icons.Default.Add, "Create group") }
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(visible, key = { it.name }) { group ->
                Card(onClick = { onOpenGroup(group.name) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Group, "Group", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            androidx.compose.material3.Text(group.name, style = MaterialTheme.typography.titleMedium)
                            androidx.compose.material3.Text("${group.members} members • ${group.online} online", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(3.dp))
                            androidx.compose.material3.Text(group.preview, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FynxGroupConversationPanel(groupName: String, onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf("Welcome to $groupName")) }
    Column(Modifier.fillMaxSize().background(FynxDesign.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { androidx.compose.material3.Text("‹") }
            androidx.compose.material3.Text(groupName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = {}) { Icon(Icons.Default.Group, "Members") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { message ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large) {
                    androidx.compose.material3.Text(message, Modifier.padding(12.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { androidx.compose.material3.Text("Message…") }, maxLines = 4, shape = MaterialTheme.shapes.large)
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = { if (text.isNotBlank()) { messages = messages + text.trim(); text = "" } }) { Icon(Icons.Default.Send, "Send") }
        }
    }
}
