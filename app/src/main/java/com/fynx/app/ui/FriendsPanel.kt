package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FriendsPanel() {
    var query by remember { mutableStateOf("") }
    var people by remember { mutableStateOf(samplePeople) }

    val results = people.filter {
        query.isBlank() ||
            it.username.contains(query.trim(), ignoreCase = true) ||
            it.displayName.contains(query.trim(), ignoreCase = true)
    }

    Column(Modifier.fillMaxSize()) {
        Text("Friends", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search by username") }
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.username }) { person ->
                var requestSent by remember(person.username) { mutableStateOf(person.requestSent) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(person.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(person.username, style = MaterialTheme.typography.bodyMedium)
                        if (person.bio.isNotBlank()) Text(person.bio)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { requestSent = true },
                            enabled = !requestSent && !person.isFriend
                        ) {
                            Text(if (requestSent) "Request sent" else if (person.isFriend) "Friends" else "Add friend")
                        }
                    }
                }
            }
        }
    }
}
