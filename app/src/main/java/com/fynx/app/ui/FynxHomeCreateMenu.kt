package com.fynx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Story
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FynxHomeCreateMenu(
    onDismiss: () -> Unit,
    onPost: () -> Unit,
    onStatus: () -> Unit,
    onMarketplace: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CreateMenuAction(
                    icon = Icons.Default.PostAdd,
                    title = "Post",
                    onClick = onPost
                )
                CreateMenuAction(
                    icon = Icons.Default.Story,
                    title = "Status",
                    onClick = onStatus
                )
                CreateMenuAction(
                    icon = Icons.Default.ShoppingBag,
                    title = "Marketplace",
                    onClick = onMarketplace
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CreateMenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = {
            onClick()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Text(title, modifier = Modifier.padding(start = 14.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}
