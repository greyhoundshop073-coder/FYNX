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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

private fun findFynxGift(id: String): FynxGift? = fynxGiftCatalog.firstOrNull { it.id == id }

@Composable
fun GiftsPanel(
    recipientName: String? = null,
    onGiftSelected: (FynxGift) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authUsername = FynxAuthStore.storedUsername(context)?.let { if (it.startsWith("@")) it else "@$it" }
    val actualProfiles = remember(context) {
        FynxFriendsStore(context).load().filterNot { it.username.equals(authUsername, ignoreCase = true) }
    }
    var remoteRecipient by remember(recipientName) { mutableStateOf<FriendProfile?>(null) }
    var recipientLoading by remember(recipientName) { mutableStateOf(false) }
    LaunchedEffect(recipientName) {
        val query = recipientName?.trim().orEmpty()
        if (query.isBlank()) return@LaunchedEffect
        val localMatch = actualProfiles.firstOrNull {
            it.displayName.equals(query, ignoreCase = true) || it.username.equals(query, ignoreCase = true)
        }
        if (localMatch == null) {
            recipientLoading = true
            remoteRecipient = withContext(Dispatchers.IO) {
                FynxSocialClient.searchUsers(context, query).getOrNull()?.firstOrNull {
                    it.displayName.equals(query, ignoreCase = true) || it.username.removePrefix("@").equals(query.removePrefix("@"), ignoreCase = true)
                }?.let { user ->
                    FriendProfile(
                        displayName = user.displayName.ifBlank { user.username.removePrefix("@") },
                        username = user.username.let { if (it.startsWith("@")) it else "@$it" },
                        hasProfilePhoto = !user.profilePhotoMediaId.isNullOrBlank()
                    )
                }
            }
            recipientLoading = false
        }
    }
    val initialRecipient = remember(recipientName, actualProfiles, remoteRecipient) {
        actualProfiles.firstOrNull {
            it.displayName.equals(recipientName, ignoreCase = true) || it.username.equals(recipientName, ignoreCase = true)
        } ?: remoteRecipient
    }
    var selectedRecipient by remember(recipientName) { mutableStateOf<FriendProfile?>(initialRecipient) }
    LaunchedEffect(initialRecipient) { if (selectedRecipient == null) selectedRecipient = initialRecipient }
    var selectedGift by remember { mutableStateOf<FynxGift?>(null) }
    var confirmationOpen by remember { mutableStateOf(false) }
    var preparedTransfer by remember { mutableStateOf<FynxGiftTransfer?>(null) }
    var deliveryMessage by remember { mutableStateOf<String?>(null) }
    var historyVersion by remember { mutableIntStateOf(0) }
    var historyTab by remember { mutableStateOf("Sent") }
    val historyStore = remember(context) { FynxGiftHistoryStore(context, ::findFynxGift) }
    val recipients = buildList {
        addAll(actualProfiles)
        remoteRecipient?.let { remote -> if (none { it.username.equals(remote.username, true) }) add(remote) }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎁", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Send a gift", style = MaterialTheme.typography.titleLarge)
                Text(selectedRecipient?.let { "To ${it.displayName}" } ?: if (recipientLoading) "Finding recipient…" else "Choose a recipient", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        deliveryMessage?.let { message ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (message.startsWith("Gift sent")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
                Text(message, Modifier.padding(12.dp))
            }
        }

        Text("Recipient", style = MaterialTheme.typography.titleMedium)
        if (recipients.isEmpty()) {
            Text("No real FYNX user could be found for this chat yet. Keep the chat connected and try again.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 150.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recipients, key = { it.username }) { person ->
                    Card(
                        onClick = { selectedRecipient = person; selectedGift = null; preparedTransfer = null; deliveryMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = if (selectedRecipient?.username == person.username) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            FynxAvatar(person.username, Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(person.displayName, style = MaterialTheme.typography.titleSmall); Text(person.username, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (selectedRecipient?.username == person.username) Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        Text("Choose a gift", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.heightIn(max = 290.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(fynxGiftCatalog, key = { it.id }) { gift ->
                Card(
                    onClick = { selectedGift = gift; preparedTransfer = null; deliveryMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (selectedGift?.id == gift.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(gift.emoji, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(gift.name, style = MaterialTheme.typography.titleSmall); Text(gift.description, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Column(horizontalAlignment = Alignment.End) { Text("${gift.value} FYNX", style = MaterialTheme.typography.labelLarge); Text(gift.rarity, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }

        selectedGift?.let { gift ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${gift.emoji} ${gift.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Value: ${gift.value} FYNX • ${gift.rarity}", color = MaterialTheme.colorScheme.primary)
                    Text(selectedRecipient?.let { "Recipient: ${it.displayName} (${it.username})" } ?: "Select a recipient before continuing", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(enabled = selectedRecipient != null && authUsername != null && preparedTransfer == null, onClick = { confirmationOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Review gift") }
                }
            }
        }

        preparedTransfer?.let { transfer ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Gift sent", style = MaterialTheme.typography.titleSmall)
                    Text("${transfer.gift.emoji} ${transfer.gift.name} • ${transfer.transaction.amount.toInt()} FYNX")
                    Text("To: ${transfer.recipientName} (${transfer.recipientUsername})")
                    Text("Reference: ${transfer.transaction.reference}", style = MaterialTheme.typography.bodySmall)
                    Text("Virtual gift delivered as a FYNX chat message. No real-money charge was made.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        @Suppress("UNUSED_VARIABLE") val _historyVersion = historyVersion
        val sentHistory = historyStore.sentBy(authUsername.orEmpty())
        val receivedHistory = historyStore.receivedBy(authUsername.orEmpty())
        val visibleHistory = if (historyTab == "Sent") sentHistory else receivedHistory
        Text("My Gifts", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = historyTab == "Sent", onClick = { historyTab = "Sent" }, shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)) { Text("Sent (${sentHistory.size})") }
            SegmentedButton(selected = historyTab == "Received", onClick = { historyTab = "Received" }, shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)) { Text("Received (${receivedHistory.size})") }
        }
        if (visibleHistory.isEmpty()) {
            Text(if (historyTab == "Sent") "Your sent gifts will stay here on this device." else "Received gifts will appear here after cross-device delivery.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            visibleHistory.forEach { entry ->
                ListItem(
                    headlineContent = { Text("${entry.transfer.gift.emoji} ${entry.giftName}") },
                    supportingContent = { Text(if (historyTab == "Sent") "To ${entry.recipient} • ${entry.amount.toInt()} FYNX • ${entry.status}" else "From ${entry.sender} • ${entry.amount.toInt()} FYNX • ${entry.status}") },
                    trailingContent = { Text(entry.reference.takeLast(8), style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Text("Virtual gifts currently use FYNX chat delivery. Wallet debits and real-money gifting remain disabled until a secure payment provider is connected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("This sends a virtual gift message in the chat. It does not move real money.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(enabled = !recipientLoading, onClick = {
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
                        scope.launch {
                            deliveryMessage = null
                            val result = withContext(Dispatchers.IO) {
                                FynxProductionMessaging.sendText(
                                    context,
                                    recipient.username.removePrefix("@"),
                                    "${gift.emoji} ${gift.name} — a virtual gift from ${authUsername.removePrefix("@")}"
                                )
                            }
                            result.onSuccess {
                                preparedTransfer = transfer
                                historyStore.add(FynxGiftHistoryEntry(transfer, System.currentTimeMillis()))
                                historyVersion++
                                deliveryMessage = "Gift sent to ${recipient.displayName}."
                                onGiftSelected(gift)
                            }.onFailure { error ->
                                deliveryMessage = error.message?.takeIf { it.isNotBlank() }?.let { "Gift could not be sent: $it" } ?: "Gift could not be sent. Please try again."
                            }
                            confirmationOpen = false
                        }
                    }
                }) { Text("Send gift") }
            },
            dismissButton = { TextButton(onClick = { confirmationOpen = false }) { Text("Cancel") } }
        )
    }
}
