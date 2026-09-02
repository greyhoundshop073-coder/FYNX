package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    recipientName: String? = null,
    onGiftSelected: (FynxGift) -> Unit = {}
) {
    val context = LocalContext.current
    val authUsername = FynxAuthStore.storedUsername(context)?.let { if (it.startsWith("@")) it else "@$it" }
    val actualProfiles = remember(context) {
        FynxFriendsStore(context).load().filterNot { it in samplePeople }
    }
    val initialRecipient = remember(recipientName, actualProfiles) {
        actualProfiles.firstOrNull { it.displayName.equals(recipientName, ignoreCase = true) || it.username.equals(recipientName, ignoreCase = true) }
    }
    var selectedRecipient by remember { mutableStateOf(initialRecipient) }
    var selectedGift by remember { mutableStateOf<FynxGift?>(null) }
    var confirmationOpen by remember { mutableStateOf(false) }
    var preparedTransfer by remember { mutableStateOf<FynxGiftTransfer?>(null) }
    val historyStore = remember { FynxGiftHistoryStore() }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎁", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Send a gift", style = MaterialTheme.typography.titleLarge)
                Text(
                    selectedRecipient?.let { "To ${it.displayName}" } ?: "Choose a recipient",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text("Recipient", style = MaterialTheme.typography.titleMedium)
        if (actualProfiles.isEmpty()) {
            Text(
                "No real FYNX users are available to select yet. Gifts will appear here when another user account is available.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 150.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(actualProfiles, key = { it.username }) { person ->
                    Card(
                        onClick = {
                            selectedRecipient = person
                            selectedGift = null
                            preparedTransfer = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedRecipient?.username == person.username)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            FynxAvatar(person.username, Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(person.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(person.username, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selectedRecipient?.username == person.username) Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        Text("Choose a gift", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.heightIn(max = 290.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(fynxGiftCatalog, key = { it.id }) { gift ->
                Card(
                    onClick = {
                        selectedGift = gift
                        preparedTransfer = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedGift?.id == gift.id)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(gift.emoji, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(gift.name, style = MaterialTheme.typography.titleSmall)
                            Text(gift.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${gift.value} FYNX", style = MaterialTheme.typography.labelLarge)
                            Text(gift.rarity, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        selectedGift?.let { gift ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${gift.emoji} ${gift.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Value: ${gift.value} FYNX • ${gift.rarity}", color = MaterialTheme.colorScheme.primary)
                    Text(
                        selectedRecipient?.let { "Recipient: ${it.displayName} (${it.username})" } ?: "Select a recipient before continuing",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        enabled = selectedRecipient != null && authUsername != null && preparedTransfer == null,
                        onClick = { confirmationOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Review gift") }
                }
            }
        }

        preparedTransfer?.let { transfer ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Gift prepared", style = MaterialTheme.typography.titleSmall)
                    Text("${transfer.gift.emoji} ${transfer.gift.name} • ${transfer.transaction.amount.toInt()} FYNX")
                    Text("To: ${transfer.recipientName} (${transfer.recipientUsername})")
                    Text("Status: ${transfer.transaction.status}")
                    Text("Reference: ${transfer.transaction.reference}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "This is a virtual FYNX gift record. No real money was charged and no backend delivery has occurred yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val sentHistory = historyStore.sentBy(authUsername.orEmpty())
        if (sentHistory.isNotEmpty()) {
            Text("Sent Gifts", style = MaterialTheme.typography.titleMedium)
            sentHistory.forEach { entry ->
                ListItem(
                    headlineContent = { Text("${entry.transfer.gift.emoji} ${entry.giftName}") },
                    supportingContent = { Text("To ${entry.recipient} • ${entry.amount.toInt()} FYNX • ${entry.status}") },
                    trailingContent = { Text(entry.reference.takeLast(8), style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Text(
            "Real payment, wallet debits, cross-device delivery and received-gift syncing will be connected later through the secure production backend.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (confirmationOpen && selectedGift != null && selectedRecipient != null && authUsername != null) {
        val gift = selectedGift!!
        val recipient = selectedRecipient!!
        AlertDialog(
            onDismissRequest = { confirmationOpen = false },
            title = { Text("Confirm gift") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${gift.emoji} ${gift.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Send to ${recipient.displayName} (${recipient.username})?")
                    Text("Value: ${gift.value} FYNX • ${gift.rarity}")
                    Text("This only prepares a virtual FYNX transaction; it does not move real money.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val transactionId = UUID.randomUUID().toString()
                    val (status, transfer) = FynxGiftFlow.prepare(
                        wallet = FynxWalletFoundation.empty("FYNX"),
                        senderName = authUsername.removePrefix("@").ifBlank { "FYNX user" },
                        senderUsername = authUsername,
                        recipientName = recipient.displayName,
                        recipientUsername = recipient.username,
                        gift = gift,
                        transactionId = transactionId
                    )
                    if (status == FynxGiftFlowStatus.READY && transfer != null) {
                        preparedTransfer = transfer
                        historyStore.add(FynxGiftHistoryEntry(transfer, System.currentTimeMillis()))
                        onGiftSelected(gift)
                    }
                    confirmationOpen = false
                }) { Text("Prepare gift") }
            },
            dismissButton = { TextButton(onClick = { confirmationOpen = false }) { Text("Cancel") } }
        )
    }
}
