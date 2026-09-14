package com.fynx.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier

/**
 * Compatibility bridge for the Marketplace panel's existing scroll call.
 * Delegates directly to the official Compose foundation implementation.
 */
fun Modifier.verticalScroll(state: ScrollState): Modifier =
    androidx.compose.foundation.verticalScroll(this, state)
