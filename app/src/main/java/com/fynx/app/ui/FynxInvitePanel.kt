package com.fynx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FynxInvitePanel(
    code: String?,
    onShare: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("FYNX Invite", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.GroupAdd, contentDescription = "Invite")
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    if (code.isNullOrBlank()) {
                        "You opened a FYNX invite link."
                    } else {
                        "You opened a FYNX invite link with code: $code"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "This safe local foundation does not automatically add friends or accept memberships. A future FYNX account service can validate the invite before any relationship is created.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onShare, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("Share FYNX")
                }
            }
        }
        OutlinedButton(onClick = onBack, Modifier.fillMaxWidth()) {
            Text("Back to FYNX")
        }
    }
}
