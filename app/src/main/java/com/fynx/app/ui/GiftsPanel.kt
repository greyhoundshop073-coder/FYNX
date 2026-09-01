package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class FynxGift(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String
)

private val fynxGiftCatalog = listOf(
    FynxGift("rose", "Rose", "🌹", "A simple gesture of appreciation"),
    FynxGift("heart", "Heart", "💖", "Send a little love to a friend"),
    FynxGift("star", "Star", "⭐", "Celebrate someone special"),
    FynxGift("coffee", "Coffee", "☕", "A warm virtual treat"),
    FynxGift("cake", "Cake", "🎂", "Celebrate a special moment")
)

@Composable
fun GiftsPanel(
    recipientName: String = "Your friend",
    onGiftSelected: (FynxGift) -> Unit = {}
) {
    var selectedGift by remember { mutableStateOf<FynxGift?>(null) }
    var sent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CardGiftcard, contentDescription = "Gifts", tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Send a gift", style = MaterialTheme.typography.titleLarge)
                Text("To $recipientName", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("Choose a gift", style = MaterialTheme.typography.titleMedium)

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(fynxGiftCatalog, key = { it.id }) { gift ->
                Card(
                    onClick = {
                        selectedGift = gift
                        sent = false
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedGift?.id == gift.id)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        Modifier.width(92.dp).padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(gift.emoji, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(gift.name, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        selectedGift?.let { gift ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${gift.emoji} ${gift.name}", style = MaterialTheme.typography.titleMedium)
                    Text(gift.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            sent = true
                            onGiftSelected(gift)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (sent) "Gift selected" else "Continue")
                    }
                }
            }
        }

        Text(
            "Gift payments and real delivery will be connected later through the secure production backend. This foundation does not charge money.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
