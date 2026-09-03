package com.fynx.app.ui

/**
 * Local first-pass safety analysis for Marketplace content.
 *
 * This is deliberately conservative: it identifies strong scam signals without
 * pretending that a client-side score can prove a seller or transaction is safe.
 * Server-side/AI analysis can consume the same signals later.
 */
enum class FynxMarketplaceRiskLevel { LOW, MEDIUM, HIGH }

data class FynxMarketplaceSafetyAssessment(
    val level: FynxMarketplaceRiskLevel,
    val score: Int,
    val signals: List<String>
) {
    val isBlocking: Boolean get() = level == FynxMarketplaceRiskLevel.HIGH
}

object FynxMarketplaceSafety {
    private data class Signal(val weight: Int, val label: String, val patterns: List<String>)

    private val signals = listOf(
        Signal(55, "Payment is being redirected outside FYNX", listOf(
            "pay outside fynx", "payment outside fynx", "pay me outside", "pay outside the app",
            "send money outside", "deal outside fynx", "continue outside fynx"
        )),
        Signal(45, "Gift-card payment request", listOf(
            "gift card", "itunes card", "google play card", "steam card", "voucher code"
        )),
        Signal(45, "Cryptocurrency payment request", listOf(
            "pay in crypto", "pay with crypto", "cryptocurrency", "bitcoin", "btc", "usdt", "ethereum", "crypto wallet"
        )),
        Signal(40, "Wire-transfer payment request", listOf(
            "wire transfer", "western union", "moneygram", "send a wire"
        )),
        Signal(50, "Request for a verification or one-time code", listOf(
            "send your otp", "send otp", "verification code", "one time code", "one-time code", "security code"
        )),
        Signal(30, "Urgency or pressure to pay", listOf(
            "pay now", "pay immediately", "last chance", "act now", "urgent payment", "must pay today", "don't tell anyone"
        )),
        Signal(35, "Advance-fee or release-payment language", listOf(
            "pay release fee", "release fee", "clearance fee", "processing fee first", "deposit before delivery", "fee before refund"
        ))
    )

    fun analyze(title: String, description: String, storeName: String = "", location: String = ""): FynxMarketplaceSafetyAssessment {
        val text = listOf(title, description, storeName, location)
            .joinToString(" ")
            .trim()
            .lowercase()
        if (text.isBlank()) return FynxMarketplaceSafetyAssessment(FynxMarketplaceRiskLevel.LOW, 0, emptyList())

        val matched = signals.filter { signal -> signal.patterns.any { pattern -> containsPhrase(text, pattern) } }
        val score = matched.sumOf { it.weight }.coerceAtMost(100)
        val level = when {
            score >= 55 -> FynxMarketplaceRiskLevel.HIGH
            score >= 25 -> FynxMarketplaceRiskLevel.MEDIUM
            else -> FynxMarketplaceRiskLevel.LOW
        }
        return FynxMarketplaceSafetyAssessment(level, score, matched.map { it.label }.distinct())
    }

    fun publishDecision(assessment: FynxMarketplaceSafetyAssessment): Result<Unit> =
        if (!assessment.isBlocking) Result.success(Unit)
        else Result.failure(IllegalArgumentException(
            "FYNX Safety Shield blocked this listing because it contains high-risk transaction language: ${assessment.signals.joinToString(", ")}. Remove the risky payment or code request before publishing."
        ))

    private fun containsPhrase(text: String, phrase: String): Boolean {
        val normalized = text.replace(Regex("[^a-z0-9@._ -]"), " ").replace(Regex("\\s+"), " ").trim()
        return " $normalized ".contains(" $phrase ")
    }
}
