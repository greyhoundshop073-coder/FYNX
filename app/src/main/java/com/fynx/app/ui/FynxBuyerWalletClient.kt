package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class FynxBuyerWalletSummary(
    val walletStatus: String,
    val currency: String,
    val availableBalance: Double?,
    val reservedBalance: Double?,
    val withdrawalPendingBalance: Double?,
    val withdrawalFee: Double,
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

internal data class FynxBuyerWalletActivityCursor(
    val createdAt: String,
    val id: String
)

internal data class FynxBuyerWalletActivityPage(
    val transactions: List<FynxBuyerWalletActivity>,
    val hasMore: Boolean,
    val nextCursor: FynxBuyerWalletActivityCursor?
)

internal data class FynxWalletBankTransfer(
    val bankName: String,
    val accountName: String,
    val accountNumber: String,
    val reference: String,
    val amount: Double,
    val currency: String,
    val accountExpiresAt: String
)

internal data class FynxWalletFundingResult(
    val reference: String,
    val status: String,
    val amount: Double,
    val currency: String,
    val bankTransfer: FynxWalletBankTransfer?,
    val checkoutUrl: String?
)

internal data class FynxWalletBank(val code: String, val name: String)
internal data class FynxWalletPayoutAccount(
    val id: String,
    val bankName: String,
    val accountName: String,
    val accountLast4: String,
    val verified: Boolean
)
internal data class FynxWalletWithdrawal(
    val id: String,
    val reference: String,
    val status: String,
    val amount: Double,
    val fee: Double,
    val currency: String,
    val bankName: String = "",
    val accountLast4: String = "",
    val otpRequired: Boolean = false
)

internal object FynxBuyerWalletClient {
    internal fun normalizeActivityDirection(direction: String): String {
        val normalized = direction.trim().uppercase(Locale.US)
        require(normalized in setOf("ALL", "IN", "OUT")) {
            "Wallet activity direction is not supported."
        }
        return normalized
    }

    suspend fun loadSummary(context: Context): Result<FynxBuyerWalletSummary> =
        FynxBackendClient.get(context, "/api/wallet/summary").mapCatching(::parseSummary)

    suspend fun loadTransactions(
        context: Context,
        limit: Int = 20,
        before: FynxBuyerWalletActivityCursor? = null,
        direction: String = "ALL"
    ): Result<FynxBuyerWalletActivityPage> {
        val boundedLimit = limit.coerceIn(1, 50)
        val normalizedDirection = normalizeActivityDirection(direction)
        val query = buildString {
            append("&direction=").append(Uri.encode(normalizedDirection))
            before?.let {
                append("&beforeCreatedAt=").append(Uri.encode(it.createdAt))
                append("&beforeId=").append(Uri.encode(it.id))
            }
        }
        return FynxBackendClient.get(
            context,
            "/api/wallet/transactions?limit=" + boundedLimit + query
        ).mapCatching(::parseTransactions)
    }


    suspend fun startBankTransfer(context: Context, amount: Double, email: String, idempotencyKey: String): Result<FynxWalletFundingResult> {
        val body = JSONObject().put("amount", amount).put("email", email.trim())
        return FynxBackendClient.postJsonWithHeaders(
            context, "/api/wallet/funding/transfer", body.toString(),
            mapOf("Idempotency-Key" to idempotencyKey)
        ).mapCatching(::parseFundingResult)
    }

    suspend fun startFundingCheckout(context: Context, amount: Double, email: String, method: String, idempotencyKey: String): Result<FynxWalletFundingResult> {
        val body = JSONObject().put("amount", amount).put("email", email.trim()).put("method", method.lowercase(Locale.US))
        return FynxBackendClient.postJsonWithHeaders(
            context, "/api/wallet/funding/checkout", body.toString(),
            mapOf("Idempotency-Key" to idempotencyKey)
        ).mapCatching(::parseFundingResult)
    }

    suspend fun loadFundingStatus(context: Context, reference: String): Result<FynxWalletFundingResult> =
        FynxBackendClient.get(context, "/api/wallet/funding/" + Uri.encode(reference)).mapCatching(::parseFundingResult)

    suspend fun loadBanks(context: Context): Result<List<FynxWalletBank>> =
        FynxBackendClient.get(context, "/api/wallet/banks").mapCatching { raw ->
            val rows = JSONObject(raw).optJSONArray("banks") ?: JSONArray()
            buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val code = row.optString("code").trim()
                    val name = row.optString("name").trim()
                    if (code.isNotBlank() && name.isNotBlank()) add(FynxWalletBank(code, name))
                }
            }
        }

    suspend fun resolveBankAccount(context: Context, accountNumber: String, bankCode: String): Result<String> =
        FynxBackendClient.postJson(context, "/api/wallet/banks/resolve",
            JSONObject().put("accountNumber", accountNumber).put("bankCode", bankCode).toString()
        ).mapCatching { raw ->
            val json = JSONObject(raw)
            require(json.optBoolean("verified", false)) { "The bank account could not be verified." }
            json.optString("accountName").trim().also { require(it.isNotBlank()) { "Bank account name is missing." } }
        }

    suspend fun savePayoutAccount(context: Context, accountNumber: String, bankCode: String, bankName: String): Result<FynxWalletPayoutAccount> =
        FynxBackendClient.postJson(context, "/api/wallet/payout-accounts",
            JSONObject().put("accountNumber", accountNumber).put("bankCode", bankCode).put("bankName", bankName).toString()
        ).mapCatching { raw ->
            val row = JSONObject(raw).getJSONObject("payoutAccount")
            FynxWalletPayoutAccount(
                id = row.getString("id"),
                bankName = row.optString("bankName"),
                accountName = row.optString("accountName"),
                accountLast4 = row.optString("accountLast4"),
                verified = row.optBoolean("verified", false)
            )
        }

    suspend fun loadPayoutAccounts(context: Context): Result<List<FynxWalletPayoutAccount>> =
        FynxBackendClient.get(context, "/api/wallet/payout-accounts").mapCatching { raw ->
            val rows = JSONObject(raw).optJSONArray("payoutAccounts") ?: JSONArray()
            buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    add(FynxWalletPayoutAccount(
                        id = row.optString("id"),
                        bankName = row.optString("bankName"),
                        accountName = row.optString("accountName"),
                        accountLast4 = row.optString("accountLast4"),
                        verified = row.optBoolean("verified", false)
                    ))
                }
            }
        }

    suspend fun walletPinConfigured(context: Context): Result<Boolean> =
        FynxBackendClient.get(context, "/api/wallet/security").mapCatching {
            JSONObject(it).optBoolean("pinConfigured", false)
        }

    suspend fun saveWalletPin(context: Context, pin: String, currentPin: String?): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/wallet/security/pin",
            JSONObject().put("pin", pin).put("confirmPin", pin).apply {
                if (!currentPin.isNullOrBlank()) put("currentPin", currentPin)
            }.toString()
        ).mapCatching { raw -> require(JSONObject(raw).optBoolean("pinConfigured", false)) { "Wallet PIN was not saved." }; Unit }

    suspend fun startWithdrawal(context: Context, amount: Double, payoutAccountId: String, pin: String, idempotencyKey: String): Result<FynxWalletWithdrawal> =
        FynxBackendClient.postJsonWithHeaders(
            context, "/api/wallet/withdrawals",
            JSONObject().put("amount", amount).put("payoutAccountId", payoutAccountId).put("pin", pin).toString(),
            mapOf("Idempotency-Key" to idempotencyKey)
        ).mapCatching { raw ->
            val row = JSONObject(raw).getJSONObject("withdrawal")
            FynxWalletWithdrawal(
                id = row.optString("id"), reference = row.getString("reference"),
                status = row.optString("status"), amount = row.optDouble("amount", amount),
                fee = row.optDouble("fee", 0.0), currency = row.optString("currency", "NGN"),
                bankName = row.optString("bankName"), accountLast4 = row.optString("accountLast4"),
                otpRequired = JSONObject(raw).optBoolean("otpRequired", false)
            )
        }

    suspend fun finalizeWithdrawal(context: Context, reference: String, otp: String): Result<FynxWalletWithdrawal> =
        FynxBackendClient.postJson(context, "/api/wallet/withdrawals/" + Uri.encode(reference) + "/finalize",
            JSONObject().put("otp", otp).toString()
        ).mapCatching { raw ->
            val row = JSONObject(raw)
            FynxWalletWithdrawal(
                id = "", reference = row.optString("reference", reference), status = row.optString("status"),
                amount = 0.0, fee = 0.0, currency = "NGN"
            )
        }

    suspend fun loadWithdrawalStatus(context: Context, reference: String): Result<FynxWalletWithdrawal> =
        FynxBackendClient.get(context, "/api/wallet/withdrawals/" + Uri.encode(reference)).mapCatching { raw ->
            val row = JSONObject(raw).getJSONObject("withdrawal")
            FynxWalletWithdrawal(
                id = row.optString("id"), reference = row.getString("reference"), status = row.optString("status"),
                amount = row.optDouble("amount"), fee = row.optDouble("fee"), currency = row.optString("currency", "NGN"),
                bankName = row.optString("bankName"), accountLast4 = row.optString("accountLast4")
            )
        }

    private fun parseFundingResult(raw: String): FynxWalletFundingResult {
        val json = JSONObject(raw)
        val tx = json.optJSONObject("transaction") ?: JSONObject()
        val transfer = json.optJSONObject("bankTransfer")
        val bankTransfer = transfer?.let {
            FynxWalletBankTransfer(
                bankName = it.optString("bankName"), accountName = it.optString("accountName"),
                accountNumber = it.optString("accountNumber"), reference = it.optString("reference"),
                amount = it.optDouble("amount", tx.optDouble("amount")), currency = it.optString("currency", "NGN"),
                accountExpiresAt = it.optString("accountExpiresAt")
            )
        }
        val reference = tx.optString("reference", json.optString("reference"))
        require(reference.isNotBlank()) { "Wallet funding response did not include a reference." }
        return FynxWalletFundingResult(
            reference = reference, status = tx.optString("status", json.optString("status", "PENDING")),
            amount = tx.optDouble("amount", json.optDouble("amount", bankTransfer?.amount ?: 0.0)),
            currency = tx.optString("currency", json.optString("currency", bankTransfer?.currency ?: "NGN")),
            bankTransfer = bankTransfer, checkoutUrl = json.optString("checkoutUrl").takeIf { it.startsWith("https://checkout.paystack.com/") }
        )
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
            withdrawalFee = json.optDouble("withdrawalFee", 0.0).also { require(it.isFinite() && it >= 0.0) { "Wallet returned an invalid withdrawal fee." } },
            canFund = json.optBoolean("canFund", false),
            canSpend = json.optBoolean("canSpend", false),
            canWithdraw = json.optBoolean("canWithdraw", false),
            notice = json.optString("notice").trim()
        )
    }

    internal fun parseTransactions(raw: String): FynxBuyerWalletActivityPage {
        val response = JSONObject(raw)
        val rows = response.optJSONArray("transactions") ?: JSONArray()
        require(rows.length() <= 50) { "Wallet activity response is too large." }
        val transactions = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("id").trim()
                val type = row.optString("type").trim().uppercase(Locale.US)
                val status = row.optString("status").trim().uppercase(Locale.US)
                val amount = row.optDouble("amount", Double.NaN)
                val fee = row.optDouble("fee", 0.0)
                val walletDelta = row.optDouble("walletDelta", 0.0)
                val currency = row.optString("currency", "NGN").trim().uppercase(Locale.US)
                val reference = row.optString("reference").trim()
                val createdAt = row.optString("createdAt").trim()
                if (id.isBlank() || reference.isBlank() || createdAt.isBlank() ||
                    amount <= 0.0 || !amount.isFinite() ||
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
                        reference = reference,
                        description = row.optString("description").trim(),
                        createdAt = createdAt
                    )
                )
            }
        }
        val hasMore = response.optBoolean("hasMore", false)
        val cursorJson = response.optJSONObject("nextCursor")
        val cursor = cursorJson?.let {
            val createdAt = it.optString("createdAt").trim()
            val id = it.optString("id").trim()
            require(createdAt.isNotBlank() && id.isNotBlank()) {
                "Wallet activity returned an invalid page cursor."
            }
            FynxBuyerWalletActivityCursor(createdAt, id)
        }
        require(!hasMore || cursor != null) {
            "Wallet activity says more transactions exist but did not return a cursor."
        }
        return FynxBuyerWalletActivityPage(transactions, hasMore, cursor)
    }

    private fun nullableAmount(json: JSONObject, field: String): Double? {
        if (!json.has(field) || json.isNull(field)) return null
        val value = json.optDouble(field, Double.NaN)
        require(value.isFinite() && value >= 0.0) { "Wallet returned an invalid balance." }
        return value
    }
}
