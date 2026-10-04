package com.fynx.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Chat presentation shell. Message state, sending, realtime and persistence remain
 * owned by the existing chat layer; this component only provides a stable place
 * for message content, reply preview and reaction presentation.
 */
@Composable
fun ChatMessageBubble(
    modifier: Modifier = Modifier,
    replyContent: (@Composable (() -> Unit))? = null,
    messageContent: @Composable () -> Unit,
    reactionContent: (@Composable (() -> Unit))? = null,
) {
    Column(
        modifier = modifier.padding(vertical = 2.dp),
    ) {
        replyContent?.invoke()
        Row(modifier = Modifier.fillMaxWidth()) {
            messageContent()
        }
        reactionContent?.invoke()
    }
}
