package com.fynx.app.ui

import android.content.Context

/** Bridges completed FYNX events to the existing notification foundation. */
object FynxNotificationIntegration {
    fun giftSent(context: Context, recipientName: String, gift: FynxGift) {
        FynxNotificationFoundation.show(context, FynxNotificationFoundation.GIFTS_CHANNEL, gift.id.hashCode(), "Gift sent 🎁", "Your "+gift.name+" was sent to "+recipientName+".")
    }

    fun giftReceived(context: Context, senderName: String, gift: FynxGift) {
        FynxNotificationFoundation.show(context, FynxNotificationFoundation.GIFTS_CHANNEL, (gift.id + senderName).hashCode(), "Gift received 🎁", senderName+" sent you a "+gift.name+".")
    }

    fun transactionCompleted(context: Context, transaction: FynxSecureTransaction) {
        FynxNotificationFoundation.show(context, FynxNotificationFoundation.MONEY_CHANNEL, transaction.id.hashCode(), "Transaction completed 💰", transaction.reference+" completed successfully.")
    }

    fun transactionFailed(context: Context, transaction: FynxSecureTransaction) {
        FynxNotificationFoundation.show(context, FynxNotificationFoundation.MONEY_CHANNEL, transaction.id.hashCode(), "Transaction failed", transaction.reference+" could not be completed.")
    }

    fun transactionRefunded(context: Context, transaction: FynxSecureTransaction) {
        FynxNotificationFoundation.show(context, FynxNotificationFoundation.MONEY_CHANNEL, transaction.id.hashCode(), "Refund processed ↩️", transaction.reference+" was refunded.")
    }
}
