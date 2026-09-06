package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Shared other-user profile surface used by people search, friends, chat and marketplace.
 * The backend identity is authoritative; local relationship/profile stores are not used as
 * another user's private profile data.
 */
@Composable
fun OtherUserProfilePanel(username: String, onBack: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    var person by remember(username) { mutableStateOf<FriendProfile?>(null) }
    var loading by remember(username) { mutableStateOf(true) }
    var error by remember(username) { mutableStateOf<String?>(null) }
    var retryNonce by remember(username) { mutableIntStateOf(0) }

    LaunchedEffect(username, retryNonce) {
        loading = true
        error = null
        val target = username.removePrefix("@").trim()
        FynxSocialClient.searchUsers(context, target)
            .onSuccess { users ->
                users.firstOrNull {
                    it.username.removePrefix("@").trim().equals(target, true)
                }?.let { remote ->
                    person = FriendProfile(
                        username = "@${remote.username.removePrefix("@").trim()}",
                        displayName = remote.displayName.ifBlank { target },
                        bio = ""
                    )
                    error = null
                } ?: run {
                    person = null
                    error = "User not found"
                }
            }
            .onFailure {
                person = null
                error = "Unable to load this profile. Check your connection and try again."
            }
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
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error ?: "User not found", color = FynxDesign.TextSecondary)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { retryNonce++ }) { Text("Retry") }
            }
            return@Column
        }

        val profile = person!!
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FynxAvatar(profile.displayName, Modifier.size(104.dp))
            Spacer(Modifier.height(14.dp))
            Text(profile.displayName.ifBlank { profile.username }, style = MaterialTheme.typography.headlineSmall)
            Text("@${profile.username.removePrefix("@").trim()}", color = FynxDesign.TextSecondary)
            Spacer(Modifier.height(20.dp))
            Button(onClick = { onMessage(profile.username) }) {
                Icon(Icons.Default.Message, null)
                Spacer(Modifier.width(8.dp))
                Text("Message")
            }
        }
    }
}
