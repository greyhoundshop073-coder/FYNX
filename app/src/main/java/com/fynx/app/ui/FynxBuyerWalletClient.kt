package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class FynxBuyerWalletSummary(
    val walletStatus: String,
    val currency: String,
    val availableBalance: Double?,
    val reservedBalance: Double?,
    val withdrawalPendingBalance: Double?,
    val canFund: Boolean,
    val canSpend: Boolean,
    val canWithdraw: Boolean,
    val notice: String
)

internal data class FynxBuyerWalletActivity(
    val id: String,
    val type: String,
    val status: String,
    val amount: Double,
    val fee: Double,
    val walletDelta: Double,
    val currency: String,
    val reference: String,
    val description: String,
    val createdAt: String
)

internal object FynxBuyerWalletClient {
    suspend fun loadSummary(context: Context): Result<FynxBuyerWalletSummary> =
        FynxBackendClient.get(context, "/api/wallet/summary").mapCatching(::parseSummary)

    suspend fun loadTransactions(context: Context, limit: Int = 20): Result<List<FynxBuyerWalletActivity>> {
        val boundedLimit = limit.coerceIn(1, 50)
        return FynxBackendClient.get(context, "/api/wallet/transactions?limit=" + boundedLimit)
            .mapCatching(::parseTransactions)
    }

    internal fun parseSummary(raw: String): FynxBuyerWalletSummary {
        val json = JSONObject(raw)
        val status = json.optString("walletStatus", "NOT_SETUP").trim().uppercase(Locale.US)
        val currency = json.optString("currency", "NGN").trim().uppercase(Locale.US)
        require(status in setOf("NOT_SETUP", "PENDING_PROVIDER", "ACTIVE", "RESTRICTED", "CLOSED")) {
            "Wallet returned an unsupported status."
        }
        require(currency == "NGN") { "The buyer wallet currently supports NGN only." }
        return FynxBuyerWalletSummary(
            walletStatus = status,
            currency = currency,
            availableBalance = nullableAmount(json, "availableBalance"),
            reservedBalance = nullableAmount(json, "reservedBalance"),
            withdrawalPendingBalance = nullableAmount(json, "withdrawalPendingBalance"),
            canFund = json.optBoolean("canFund", false),
            canSpend = json.optBoolean("canSpend", false),
            canWithdraw = json.optBoolean("canWithdraw", false),
            notice = json.optString("notice").trim()
        )
    }

    internal fun parseTransactions(raw: String): List<FynxBuyerWalletActivity> {
        val rows = JSONObject(raw).optJSONArray("transactions") ?: JSONArray()
        require(rows.length() <= 50) { "Wallet activity response is too large." }
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("id").trim()
                val type = row.optString("type").trim().uppercase(Locale.US)
                val status = row.optString("status").trim().uppercase(Locale.US)
                val amount = row.optDouble("amount", Double.NaN)
                val fee = row.optDouble("fee", 0.0)
                val walletDelta = row.optDouble("walletDelta", 0.0)
                val currency = row.optString("currency", "NGN").trim().uppercase(Locale.US)
                if (id.isBlank() || amount <= 0.0 || !amount.isFinite() ||
                    fee < 0.0 || !fee.isFinite() || !walletDelta.isFinite() || currency != "NGN" ||
                    type !in setOf("FUNDING", "MARKETPLACE_PAYMENT", "WITHDRAWAL", "REFUND", "REVERSAL", "ADJUSTMENT") ||
                    status !in setOf("PENDING", "PROCESSING", "SUCCEEDED", "FAILED", "REVERSED", "BLOCKED")
                ) {
                    throw IllegalStateException("Wallet activity contains an invalid transaction.")
                }
                add(
                    FynxBuyerWalletActivity(
                        id = id,
                        type = type,
                        status = status,
                        amount = amount,
                        fee = fee,
                        walletDelta = walletDelta,
                        currency = currency,
                        reference = row.optString("reference").trim(),
                        description = row.optString("description").trim(),
                        createdAt = row.optString("createdAt").trim()
                    )
                )
            }
        }
    }

    private fun nullableAmount(json: JSONObject, field: String): Double? {
        if (!json.has(field) || json.isNull(field)) return null
        val value = json.optDouble(field, Double.NaN)
        require(value.isFinite() && value >= 0.0) { "Wallet returned an invalid balance." }
        return value
    }
}
