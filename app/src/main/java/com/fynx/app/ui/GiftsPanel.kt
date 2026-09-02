package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    FynxGift("fynx_diamond", "FYNX Diamond", "💎", "A premium FYNX symbol of appreciation"),
    FynxGift("royal_ring", "Royal Ring", "💍", "A timeless gift for someone special"),
    FynxGift("golden_rose", "Golden Rose", "🌹", "A special FYNX gesture of admiration"),
    FynxGift("love_heart", "Love Heart", "❤️", "Send a little love to a friend"),
    FynxGift("crown", "Crown", "👑", "Celebrate someone who stands out"),
    FynxGift("butterfly", "Butterfly", "🦋", "A bright gift for a beautiful moment"),
    FynxGift("fynx_star", "FYNX Star", "⭐", "Celebrate someone special"),
    FynxGift("galaxy", "FYNX Galaxy", "💫", "A memorable gift with a little magic"),
    FynxGift("fire_heart", "Fire Heart", "🔥", "Show bold appreciation"),
    FynxGift("mystery_box", "Mystery Gift", "🎁", "A surprise for someone special"),
    FynxGift("flower", "Flower", "🌸", "A gentle gesture of friendship"),
    FynxGift("trophy", "Trophy", "🏆", "Celebrate an achievement")
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
            Text("🎁", style = MaterialTheme.typography.headlineMedium)
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
