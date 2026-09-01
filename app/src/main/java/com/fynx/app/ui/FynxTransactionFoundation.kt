package com.fynx.app.ui

/** Secure local transaction state foundation. No real funds are moved here. */
enum class FynxTransactionStatus { PENDING, COMPLETED, FAILED, REFUNDED, CANCELLED }

data class FynxSecureTransaction(
    val id: String,
    val reference: String,
    val amount: Double,
    val currency: String,
    val type: FynxWalletTransactionType,
    val status: FynxTransactionStatus = FynxTransactionStatus.PENDING
)

object FynxTransactionFoundation {
    fun createReference(id: String): String = "FYNX-$id"

    fun canDebit(wallet: FynxWallet, amount: Double): Boolean =
        amount > 0.0 && wallet.availableBalance() >= amount

    fun complete(transaction: FynxSecureTransaction): FynxSecureTransaction =
        transaction.copy(status = FynxTransactionStatus.COMPLETED)

    fun fail(transaction: FynxSecureTransaction): FynxSecureTransaction =
        transaction.copy(status = FynxTransactionStatus.FAILED)

    fun refund(transaction: FynxSecureTransaction): FynxSecureTransaction =
        transaction.copy(status = FynxTransactionStatus.REFUNDED)

    fun cancel(transaction: FynxSecureTransaction): FynxSecureTransaction =
        transaction.copy(status = FynxTransactionStatus.CANCELLED)
}
