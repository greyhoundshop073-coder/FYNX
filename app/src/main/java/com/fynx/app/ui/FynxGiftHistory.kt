package com.fynx.app.ui

import android.content.Context
import android.util.Base64

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

/**
 * Device-persistent gift history. This is local preparation/history only; the
 * production backend will become the source of truth for delivery and sync.
 */
class FynxGiftHistoryStore(
    context: Context,
    private val giftResolver: (String) -> FynxGift?
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val entries = mutableListOf<FynxGiftHistoryEntry>()

    init {
        loadPersisted()
    }

    fun add(entry: FynxGiftHistoryEntry) {
        if (entries.none { it.transfer.transaction.id == entry.transfer.transaction.id }) {
            entries += entry
            persist()
        }
    }

    fun all(): List<FynxGiftHistoryEntry> = entries.sortedByDescending { it.timestampMillis }

    fun sentBy(username: String): List<FynxGiftHistoryEntry> =
        all().filter { it.transfer.senderUsername.equals(username, ignoreCase = true) }

    fun receivedBy(username: String): List<FynxGiftHistoryEntry> =
        all().filter { it.transfer.recipientUsername.equals(username, ignoreCase = true) }

    fun findByReference(reference: String): FynxGiftHistoryEntry? =
        entries.firstOrNull { it.reference == reference }

    private fun loadPersisted() {
        entries.clear()
        val records = prefs.getStringSet(KEY_ENTRIES, emptySet()).orEmpty()
        records.mapNotNull { decode(it) }
            .forEach { entry ->
                if (entries.none { it.transfer.transaction.id == entry.transfer.transaction.id }) entries += entry
            }
    }

    private fun persist() {
        prefs.edit()
            .putStringSet(KEY_ENTRIES, entries.map(::encode).toSet())
            .apply()
    }

    private fun encode(entry: FynxGiftHistoryEntry): String {
        val t = entry.transfer
        val tx = t.transaction
        val fields = listOf(
            entry.timestampMillis.toString(),
            tx.id,
            tx.reference,
            tx.amount.toString(),
            tx.currency,
            tx.type.name,
            tx.status.name,
            t.senderName,
            t.recipientName,
            t.gift.id,
            t.senderUsername,
            t.recipientUsername
        )
        return fields.joinToString(DELIMITER) { Base64.encodeToString(it.toByteArray(Charsets.UTF_8), Base64.NO_WRAP) }
    }

    private fun decode(value: String): FynxGiftHistoryEntry? = runCatching {
        val fields = value.split(DELIMITER).map { String(Base64.decode(it, Base64.NO_WRAP), Charsets.UTF_8) }
        if (fields.size != 12) return null
        val gift = giftResolver(fields[9]) ?: return null
        val transaction = FynxSecureTransaction(
            id = fields[1],
            reference = fields[2],
            amount = fields[3].toDouble(),
            currency = fields[4],
            type = FynxWalletTransactionType.valueOf(fields[5]),
            status = FynxTransactionStatus.valueOf(fields[6])
        )
        FynxGiftHistoryEntry(
            transfer = FynxGiftTransfer(
                transaction = transaction,
                senderName = fields[7],
                recipientName = fields[8],
                gift = gift,
                senderUsername = fields[10],
                recipientUsername = fields[11]
            ),
            timestampMillis = fields[0].toLong()
        )
    }.getOrNull()

    companion object {
        private const val PREFS_NAME = "fynx_gift_history"
        private const val KEY_ENTRIES = "entries"
        private const val DELIMITER = "."
    }
}
