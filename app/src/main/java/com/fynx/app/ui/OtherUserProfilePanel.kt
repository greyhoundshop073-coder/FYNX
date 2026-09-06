package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared other-user profile surface used by people search, friends, chat and marketplace.
 * It resolves the requested identity from the authenticated backend search contract first,
 * then uses the local relationship store for richer locally-known profile fields.
 */
@Composable
fun OtherUserProfilePanel(username: String, onBack: () -> Unit, onMessage: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var person by remember(username) {
        mutableStateOf(FynxFriendsStore(context).load().firstOrNull { it.username.equals(username, true) })
    }
    var loading by remember(username) { mutableStateOf(true) }
    var error by remember(username) { mutableStateOf<String?>(null) }

    val localPerson = person
    val photoVisibility = FynxPreferencesStore.loadVisibility(context, "photo_visibility")
    val bioVisibility = FynxPreferencesStore.loadVisibility(context, "bio_visibility")
    val descriptionVisibility = FynxPreferencesStore.loadVisibility(context, "description_visibility")
    val show = { setting: String ->
        setting == "Everyone" || (setting == "Friends" && (localPerson?.isFriend == true))
    }

    LaunchedEffect(username) {
        loading = true
        error = null
        FynxSocialClient.searchUsers(context, username.removePrefix("@"))
            .onSuccess { users ->
                users.firstOrNull { it.username.equals(username.removePrefix("@"), true) }?.let { remote ->
                    val known = FynxFriendsStore(context).load()
                        .firstOrNull { it.username.equals(remote.username, true) }
                    person = known ?: FriendProfile(
                        username = "@${remote.username.removePrefix("@").trim()}",
                        displayName = remote.displayName.ifBlank { remote.username },
                        bio = ""
                    )
                } ?: run { error = "User not found" }
            }
            .onFailure { error = "Unable to load this profile. Check your connection and try again." }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Profile", style = MaterialTheme.typography.titleLarge)
        }

        if (loading && person == null) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (person == null) {
            Text(error ?: "User not found", Modifier.padding(24.dp))
            return@Column
        }

        val profile = person!!
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (show(photoVisibility)) {
                FynxAvatar(profile.displayName, Modifier.size(104.dp))
            } else {
                Text("Photo hidden", color = FynxDesign.TextSecondary)
            }
            Spacer(Modifier.height(14.dp))
            Text(profile.displayName.ifBlank { profile.username }, style = MaterialTheme.typography.headlineSmall)
            Text("@${profile.username.removePrefix("@").trim()}", color = FynxDesign.TextSecondary)

            if (show(bioVisibility)) {
                Spacer(Modifier.height(12.dp))
                Text("Bio", style = MaterialTheme.typography.labelLarge)
                Text(profile.bio.ifBlank { "No bio added yet." }, color = FynxDesign.TextSecondary)
            }
            if (show(descriptionVisibility)) {
                Spacer(Modifier.height(12.dp))
                Text("About", style = MaterialTheme.typography.labelLarge)
                Text("More public profile information will appear here when available.", color = FynxDesign.TextSecondary)
            }

            Spacer(Modifier.height(20.dp))
            Button(onClick = { onMessage(profile.username) }) {
                Icon(Icons.Default.Message, null)
                Spacer(Modifier.width(8.dp))
                Text("Message")
            }
        }
    }
}
