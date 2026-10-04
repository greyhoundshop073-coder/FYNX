package com.fynx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Stable Chat composer boundary.
 *
 * This component intentionally exposes actions as callbacks. Existing attachment,
 * location, gift, camera, recording and sending implementations stay owned by
 * ConversationPanel until each responsibility is migrated and verified.
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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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
