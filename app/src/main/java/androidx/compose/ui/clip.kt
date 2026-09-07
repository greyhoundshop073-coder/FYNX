package androidx.compose.ui

import androidx.compose.ui.draw.clip as drawClip
import androidx.compose.ui.graphics.Shape

/** Compatibility bridge for legacy FYNX screens that imported androidx.compose.ui.clip. */
fun Modifier.clip(shape: Shape): Modifier = this.drawClip(shape)
