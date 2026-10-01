package com.fynx.app.ui

import androidx.compose.runtime.Composable

/** Compatibility entry point: Status/Stories now use one backend-backed implementation. */
@Composable
fun StoriesPanel() {
    FynxStatusTimelinePanel()
}
