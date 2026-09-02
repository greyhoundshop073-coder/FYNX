package com.fynx.app.ui

/** Read-only history model for gifts and their transaction states. */
data class FynxGiftHistoryEntry(
    val transfer: FynxGiftTransfer,
    val timestampMillis: Long
) {
    val status: FynxTransactionStatus get() = transfer.transaction.status
    val reference: String get() = transfer.transaction.reference
    val amount: Double get() = transfer.transaction.amount
    val currency: String get() = transfer.transaction.currency
    val giftName: String get() = transfer.gift.name
    val sender: String get() = transfer.senderUsername.ifBlank { transfer.senderName }
    val recipient: String get() = transfer.recipientUsername.ifBlank { transfer.recipientName }
}

class FynxGiftHistoryStore {
    private val entries = mutableListOf<FynxGiftHistoryEntry>()

    fun add(entry: FynxGiftHistoryEntry) {
        if (entries.none { it.transfer.transaction.id == entry.transfer.transaction.id }) {
            entries += entry
        }
    }

    fun all(): List<FynxGiftHistoryEntry> = entries.sortedByDescending { it.timestampMillis }

    fun sentBy(username: String): List<FynxGiftHistoryEntry> =
        all().filter { it.transfer.senderUsername.equals(username, ignoreCase = true) }

    fun receivedBy(username: String): List<FynxGiftHistoryEntry> =
        all().filter { it.transfer.recipientUsername.equals(username, ignoreCase = true) }

    fun findByReference(reference: String): FynxGiftHistoryEntry? =
        entries.firstOrNull { it.reference == reference }
}
