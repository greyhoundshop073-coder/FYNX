package com.fynx.app.ui

/**
 * Local wallet foundation for FYNX.
 * This is intentionally a demo/local model: it does not move real money
 * and does not connect to a payment provider.
 */
data class FynxWalletTransaction(
    val id: String,
    val title: String,
    val amount: Double,
    val type: FynxWalletTransactionType
)

enum class FynxWalletTransactionType {
    DEPOSIT,
    WITHDRAWAL,
    GIFT_SENT,
    GIFT_RECEIVED,
    REFUND
}

data class FynxWallet(
    val currency: String = "NGN",
    val balance: Double = 0.0,
    val transactions: List<FynxWalletTransaction> = emptyList()
) {
    fun availableBalance(): Double = balance.coerceAtLeast(0.0)

    fun withTransaction(transaction: FynxWalletTransaction): FynxWallet {
        val signedAmount = when (transaction.type) {
            FynxWalletTransactionType.DEPOSIT,
            FynxWalletTransactionType.GIFT_RECEIVED,
            FynxWalletTransactionType.REFUND -> transaction.amount
            FynxWalletTransactionType.WITHDRAWAL,
            FynxWalletTransactionType.GIFT_SENT -> -transaction.amount
        }
        return copy(
            balance = (balance + signedAmount).coerceAtLeast(0.0),
            transactions = transactions + transaction
        )
    }
}

object FynxWalletFoundation {
    fun empty(currency: String = "NGN"): FynxWallet = FynxWallet(currency = currency)
}
