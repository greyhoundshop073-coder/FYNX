package com.fynx.app.ui

/** Coordinates the local gift flow without moving real money. */
enum class FynxGiftFlowStatus { READY, INSUFFICIENT_BALANCE, CONFIRMED }

data class FynxGiftTransfer(
    val transaction: FynxSecureTransaction,
    val senderName: String,
    val recipientName: String,
    val gift: FynxGift
)

object FynxGiftFlow {
    fun prepare(
        wallet: FynxWallet,
        senderName: String,
        recipientName: String,
        gift: FynxGift,
        amount: Double,
        transactionId: String
    ): Pair<FynxGiftFlowStatus, FynxGiftTransfer?> {
        if (!FynxTransactionFoundation.canDebit(wallet, amount)) {
            return FynxGiftFlowStatus.INSUFFICIENT_BALANCE to null
        }

        val transaction = FynxSecureTransaction(
            id = transactionId,
            reference = FynxTransactionFoundation.createReference(transactionId),
            amount = amount,
            currency = wallet.currency,
            type = FynxWalletTransactionType.GIFT_SENT
        )

        return FynxGiftFlowStatus.READY to FynxGiftTransfer(
            transaction = transaction,
            senderName = senderName,
            recipientName = recipientName,
            gift = gift
        )
    }

    fun confirm(transfer: FynxGiftTransfer): FynxGiftTransfer =
        transfer.copy(transaction = FynxTransactionFoundation.complete(transfer.transaction))
}
