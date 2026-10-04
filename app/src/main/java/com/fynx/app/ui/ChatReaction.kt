package com.fynx.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Chat message reaction presentation boundary. */
@Composable
fun ChatReaction(
    emoji: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = emoji,
        modifier = modifier.size(32.dp),
    )
}
