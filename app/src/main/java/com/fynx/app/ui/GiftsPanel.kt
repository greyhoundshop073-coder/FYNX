package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.UUID

data class FynxGift(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val value: Int,
    val rarity: String
)

private val fynxGiftCatalog = listOf(
    FynxGift("fynx_flower", "FYNX Flower", "🌸", "A gentle gesture of friendship", 5, "Common"),
    FynxGift("coffee", "Coffee", "☕", "A warm virtual treat", 10, "Common"),
    FynxGift("love_heart", "Love Heart", "❤️", "Send a little love", 25, "Common"),
    FynxGift("fynx_star", "FYNX Star", "⭐", "Celebrate someone special", 50, "Uncommon"),
    FynxGift("butterfly", "Butterfly", "🦋", "A bright gift for a beautiful moment", 75, "Uncommon"),
    FynxGift("golden_rose", "Golden Rose", "🌹", "A special FYNX gesture of admiration", 100, "Rare"),
    FynxGift("fire_heart", "Fire Heart", "🔥", "Show bold appreciation", 250, "Rare"),
    FynxGift("mystery_box", "Mystery Gift", "🎁", "A surprise for someone special", 500, "Epic"),
    FynxGift("trophy", "Trophy", "🏆", "Celebrate an achievement", 750, "Epic"),
    FynxGift("royal_ring", "Royal Ring", "💍", "A timeless gift for someone special", 1000, "Legendary"),
    FynxGift("crown", "Crown", "👑", "Celebrate someone who stands out", 2500, "Legendary"),
    FynxGift("fynx_diamond", "FYNX Diamond", "💎", "A premium FYNX symbol of appreciation", 5000, "Ultra"),
    FynxGift("fynx_galaxy", "FYNX Galaxy", "💫", "A legendary FYNX gift", 10000, "Ultra")
)

@Composable
fun GiftsPanel(
    recipientName: String = "Your friend",
    onGiftSelected: (FynxGift) -> Unit = {}
) {
    var selectedGift by remember { mutableStateOf<FynxGift?>(null) }
    var sent by remember { mutableStateOf(false) }
    var preparedTransfer by remember { mutableStateOf<FynxGiftTransfer?>(null) }

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
                        preparedTransfer = null
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
                        Text("${gift.value} FYNX", style = MaterialTheme.typography.labelSmall)
                        Text(gift.rarity, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        selectedGift?.let { gift ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${gift.emoji} ${gift.name}", style = MaterialTheme.typography.titleMedium)
                    Text(gift.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Value: ${gift.value} FYNX • ${gift.rarity}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Button(
                        onClick = {
                            val transactionId = UUID.randomUUID().toString()
                            val transfer = FynxGiftTransfer(
                                transaction = FynxSecureTransaction(
                                    id = transactionId,
                                    reference = FynxTransactionFoundation.createReference(transactionId),
                                    amount = gift.value.toDouble(),
                                    currency = "FYNX",
                                    type = FynxWalletTransactionType.GIFT_SENT
                                ),
                                senderName = "You",
                                recipientName = recipientName,
                                gift = gift
                            )
                            preparedTransfer = transfer
                            sent = true
                            onGiftSelected(gift)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (sent) "Gift prepared" else "Prepare gift")
                    }
                }
            }
        }

        preparedTransfer?.let { transfer ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ready for secure delivery", style = MaterialTheme.typography.titleSmall)
                    Text("${transfer.gift.emoji} ${transfer.gift.name} • ${transfer.transaction.amount.toInt()} FYNX")
                    Text("Recipient: ${transfer.recipientName}")
                    Text("Reference: ${transfer.transaction.reference}", style = MaterialTheme.typography.bodySmall)
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
