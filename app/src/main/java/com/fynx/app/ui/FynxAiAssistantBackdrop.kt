package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** AI surface that follows the active FYNX Appearance theme edge-to-edge. */
@Composable
fun FynxAiAssistantBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        content()
    }
}
