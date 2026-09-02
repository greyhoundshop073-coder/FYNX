package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OtherUserProfilePanel(username: String, onBack: () -> Unit, onMessage: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val person = samplePeople.firstOrNull { it.username.equals(username, true) }
    val photoVisibility = FynxPreferencesStore.loadVisibility(context, "photo_visibility")
    val bioVisibility = FynxPreferencesStore.loadVisibility(context, "bio_visibility")
    val descriptionVisibility = FynxPreferencesStore.loadVisibility(context, "description_visibility")
    val show = { setting: String -> setting == "Everyone" || (setting == "Friends" && (person?.isFriend == true)) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Profile", style = MaterialTheme.typography.titleLarge)
        }
        if (person == null) {
            Text("User not found", Modifier.padding(24.dp))
            return@Column
        }
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (show(photoVisibility)) FynxAvatar(person.displayName, Modifier.size(104.dp)) else Text("Photo hidden", color = FynxDesign.TextSecondary)
            Spacer(Modifier.height(14.dp))
            Text(person.displayName, style = MaterialTheme.typography.headlineSmall)
            Text(person.username, color = FynxDesign.TextSecondary)
            if (show(bioVisibility)) {
                Spacer(Modifier.height(12.dp))
                Text("Bio", style = MaterialTheme.typography.labelLarge)
                Text("Available on FYNX", color = FynxDesign.TextSecondary)
            }
            if (show(descriptionVisibility)) {
                Spacer(Modifier.height(12.dp))
                Text("About", style = MaterialTheme.typography.labelLarge)
                Text("This user has not added an about description yet.", color = FynxDesign.TextSecondary)
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { onMessage(person.username) }) {
                Icon(Icons.Default.Message, null); Spacer(Modifier.width(8.dp)); Text("Message")
            }
        }
    }
}
