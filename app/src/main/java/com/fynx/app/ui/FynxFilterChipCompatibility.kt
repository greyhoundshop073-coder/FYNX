package com.fynx.app.ui

import androidx.compose.material3.FilterChip as Material3FilterChip
import androidx.compose.runtime.Composable

/**
 * Compatibility overload for existing FYNX UI code that uses the older
 * Boolean-parameter FilterChip callback shape. The callback parameter is
 * intentionally ignored; Material3's current FilterChip uses a no-arg click.
 */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: (Boolean) -> Unit,
    label: @Composable () -> Unit
) {
    Material3FilterChip(
        selected = selected,
        onClick = { onClick(selected) },
        label = label
    )
}
