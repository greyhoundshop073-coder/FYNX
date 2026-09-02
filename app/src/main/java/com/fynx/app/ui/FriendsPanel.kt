package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun FriendsPanel(onOpenProfile: (String) -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val results = samplePeople.filter {
        query.isBlank() ||
            it.username.contains(query.trim(), ignoreCase = true) ||
            it.displayName.contains(query.trim(), ignoreCase = true)
    }
    val requests = results.take(2)
    val suggestions = results.drop(2)

    Column(
        Modifier
            .fillMaxSize()
            .background(FynxDesign.Background)
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                placeholder = { Text("Search by username") },
                shape = FynxDesign.ControlShape
            )
            OutlinedButton(
                onClick = { shareFynx(context) },
                shape = FynxDesign.ControlShape,
                border = BorderStroke(1.dp, FynxDesign.Outline)
            ) { Text("Invite") }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Friend Requests", style = MaterialTheme.typography.titleMedium)
            Text("${requests.size}", color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(requests, key = { "request_${it.username}" }) { person ->
                var accepted by remember(person.username) { mutableStateOf(false) }
                Card(
                    Modifier.fillMaxWidth(),
                    shape = FynxDesign.CardShape,
                    colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
                    border = BorderStroke(1.dp, FynxDesign.Outline)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onOpenProfile(person.username) }) { FynxAvatar(person.displayName, Modifier.size(48.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(person.username, color = FynxDesign.TextSecondary)
                        }
                        if (accepted) {
                            Text("Friends", color = MaterialTheme.colorScheme.primary)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = { accepted = true }, shape = FynxDesign.ControlShape) { Text("Confirm") }
                                OutlinedButton(onClick = {}, shape = FynxDesign.ControlShape) { Text("Delete") }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("People You May Know", style = MaterialTheme.typography.titleMedium)
                    Text("View All", color = MaterialTheme.colorScheme.primary)
                }
            }

            items(suggestions, key = { "suggestion_${it.username}" }) { person ->
                var requestSent by remember(person.username) { mutableStateOf(person.requestSent) }
                Card(
                    Modifier.fillMaxWidth(),
                    shape = FynxDesign.CardShape,
                    colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
                    border = BorderStroke(1.dp, FynxDesign.Outline)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FynxAvatar(person.displayName, Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(person.username, color = FynxDesign.TextSecondary)
                        }
                        Button(
                            onClick = { requestSent = true },
                            enabled = !requestSent && !person.isFriend,
                            shape = FynxDesign.ControlShape
                        ) {
                            Text(if (requestSent) "Sent" else if (person.isFriend) "Friends" else "Add Friend")
                        }
                    }
                }
            }
        }
    }
}
