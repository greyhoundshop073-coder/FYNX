package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Small, local search surface for the FYNX Features hub.
 * It intentionally searches the already-registered feature labels only;
 * it does not create a second navigation system or duplicate destinations.
 */
@Composable
fun FynxFeatureSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Search FYNX tools") },
        leadingIcon = { Text("⌕") }
    )
}
