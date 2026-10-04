package com.fynx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Presentation boundary for the Chat composer.
 *
 * The composer emits user intents through callbacks. Ownership of message state,
 * uploads, recording, permissions, realtime delivery and attachment completion
 * remains with the existing Chat state owner until each path is migrated and
 * verified. This keeps the component reusable and prevents feature code from
 * leaking back into ConversationPanel.
 */
@Composable
fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachment: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCamera: (() -> Unit)? = null,
    onLocation: (() -> Unit)? = null,
    onGift: (() -> Unit)? = null,
    onDocument: (() -> Unit)? = null,
    onContact: (() -> Unit)? = null,
    onVideoNote: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The existing Plus/attachment implementation remains the source of
        // truth until its individual actions are migrated into this boundary.
        IconButton(onClick = onAttachment, enabled = enabled) {
            Text("+")
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            singleLine = false,
        )
        IconButton(onClick = onVoice, enabled = enabled && text.isBlank()) {
            Text("🎙")
        }
        IconButton(onClick = onSend, enabled = enabled && text.isNotBlank()) {
            Text("➤")
        }
    }
}
