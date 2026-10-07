package com.fynx.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Buy Together uses the existing real FYNX Group infrastructure.
 * It does not create a second shopping-room or Marketplace data source:
 * the canonical listing is posted to a real group through R6-G integration,
 * then the existing group destination is opened.
 */
@Composable
internal fun FynxMarketplaceBuyTogether(
    listing: FynxRemoteSocialClient.MarketplaceListing,
    onStart: (listingId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showGroups by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<List<FynxGroup>>(emptyList()) }
    var sendingGroupId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Surface(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Buy Together", style = MaterialTheme.typography.titleMedium)
            Text("Share this real Marketplace item into a FYNX Group so friends can discuss it together.")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = {
                    groups = FynxGroupsStore.load(context)
                    message = null
                    showGroups = true
                }) {
                    Text("Start with friends")
                }
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showGroups) {
        AlertDialog(
            onDismissRequest = { if (sendingGroupId == null) showGroups = false },
            title = { Text("Choose a FYNX Group") },
            text = {
                if (groups.isEmpty()) {
                    Text("Create or join a FYNX Group first, then you can start a Buy Together conversation for this item.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(groups, key = { it.id }) { group ->
                            Surface(
                                onClick = {
                                    if (sendingGroupId != null) return@Surface
                                    sendingGroupId = group.id
                                    message = null
                                    scope.launch {
                                        FynxR6GIntegrationClient
                                            .shareListingToGroup(
                                                context,
                                                group.id,
                                                listing.id,
                                                "Let's buy this together: ${listing.title}"
                                            )
                                            .onSuccess {
                                                message = "Product shared to ${group.name}."
                                                showGroups = false
                                                sendingGroupId = null
                                                onStart(listing.id)
                                                openExistingGroup(context, group.id)
                                            }
                                            .onFailure {
                                                message = it.message ?: "The product could not be shared to ${group.name}."
                                                sendingGroupId = null
                                            }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(group.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${group.members.size} members",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (sendingGroupId == group.id) {
                                        CircularProgressIndicator(Modifier.padding(4.dp))
                                    } else {
                                        Text("Share", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = sendingGroupId == null,
                    onClick = { showGroups = false }
                ) { Text("Cancel") }
            }
        )
    }
}

private fun openExistingGroup(context: Context, groupId: String) {
    runCatching {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse(FynxDeepLinkParser.groupAppLink(groupId))
            )
        )
    }
}
