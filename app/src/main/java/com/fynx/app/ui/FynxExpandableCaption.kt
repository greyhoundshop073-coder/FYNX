package com.fynx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FynxExpandableCaption(text: String, modifier: Modifier = Modifier, collapsedMaxLines: Int = 4, textStyle: TextStyle = MaterialTheme.typography.bodyLarge) {
    if (text.isBlank()) return
    var expanded by remember(text) { mutableStateOf(false) }
    var hasOverflow by remember(text, collapsedMaxLines) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(text = text, style = textStyle, maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines, overflow = TextOverflow.Ellipsis, onTextLayout = { result -> if (!expanded) hasOverflow = result.hasVisualOverflow })
        if (hasOverflow || expanded) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Less" else "More") } }
    }
}
