package com.fynx.app.ui

/** Coordinates the local gift flow without moving real money. */
enum class FynxGiftFlowStatus { READY, INSUFFICIENT_BALANCE, CONFIRMED }

data class FynxGiftTransfer(
    val transaction: FynxSecureTransaction,
    val senderName: String,
    val recipientName: String,
    val gift: FynxGift,
    val senderUsername: String = "",
    val recipientUsername: String = ""
)

object FynxGiftFlow {
    fun prepare(
        wallet: FynxWallet,
        senderName: String,
        recipientName: String,
        gift: FynxGift,
        amount: Double = gift.value.toDouble(),
        transactionId: String,
        senderUsername: String = "",
        recipientUsername: String = ""
    ): Pair<FynxGiftFlowStatus, FynxGiftTransfer?> {
        // Gift values are virtual FYNX units at this stage. The wallet argument is
        // retained for API compatibility, but no real-money wallet balance is debited.
        if (amount <= 0.0 || amount != gift.value.toDouble()) {
            return FynxGiftFlowStatus.INSUFFICIENT_BALANCE to null
        }

        val transaction = FynxSecureTransaction(
            id = transactionId,
            reference = FynxTransactionFoundation.createReference(transactionId),
            amount = amount,
            currency = "FYNX",
            type = FynxWalletTransactionType.GIFT_SENT
        )

        return FynxGiftFlowStatus.READY to FynxGiftTransfer(
            transaction = transaction,
            senderName = senderName,
            recipientName = recipientName,
            gift = gift,
            senderUsername = senderUsername,
            recipientUsername = recipientUsername
        )
    }

    fun confirm(transfer: FynxGiftTransfer): FynxGiftTransfer =
        transfer.copy(transaction = FynxTransactionFoundation.complete(transfer.transaction))
}
