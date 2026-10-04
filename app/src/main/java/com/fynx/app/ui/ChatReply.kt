package com.fynx.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Chat reply-preview presentation boundary. */
@Composable
fun ChatReply(
    senderLabel: String,
    preview: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(text = senderLabel)
        Text(text = preview)
    }
}
