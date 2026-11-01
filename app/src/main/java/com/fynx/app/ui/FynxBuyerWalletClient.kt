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

internal data class FynxWalletSellerTotal(
    val currency: String,
    val protectedFunds: Double,
    val availableForPayout: Double,
    val payoutPending: Double,
    val paidOut: Double,
    val grossSales: Double,
    val marketplaceFees: Double,
    val netEarnings: Double
)

internal data class FynxWalletSellerSettlement(
    val orderId: String,
    val title: String,
    val currency: String,
    val sellerNetAmount: Double,
    val marketplaceFeeSeller: Double,
    val funds: String,
    val payoutStatus: String,
    val updatedAt: String
)

internal data class FynxWalletSellerSummary(
    val totals: List<FynxWalletSellerTotal>,
    val settlements: List<FynxWalletSellerSettlement>
)

internal data class FynxWalletPayoutBank(
    val name: String,
    val code: String,
    val slug: String
)

internal data class FynxWalletSecurityStatus(
    val hasWithdrawalPin: Boolean,
    val isLocked: Boolean,
    val lockedUntil: String?
)

internal data class FynxWalletWithdrawalResult(
    val status: String,
    val reference: String,
    val amount: Double,
    val bankName: String,
    val accountLast4: String,
    val message: String
)

internal data class FynxWalletFundingAccount(
    val status: String,
    val currency: String,
    val canRequest: Boolean,
    val bankName: String?,
    val accountName: String?,
    val accountNumber: String?
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

    suspend fun loadFundingAccount(context: Context): Result<FynxWalletFundingAccount> =
        FynxBackendClient.get(context, "/api/wallet/funding-account")
            .mapCatching(::parseFundingAccount)

    suspend fun requestFundingAccount(
        context: Context,
        email: String,
        firstName: String,
        lastName: String,
        phone: String,
        explicitConsent: Boolean
    ): Result<FynxWalletFundingAccount> {
        val body = JSONObject()
            .put("email", email.trim())
            .put("firstName", firstName.trim())
            .put("lastName", lastName.trim())
            .put("phone", phone.trim())
            .put("explicitConsent", explicitConsent)
        return FynxBackendClient.postJson(context, "/api/wallet/funding-account", body.toString())
            .mapCatching(::parseFundingAccount)
    }

    suspend fun loadWalletSecurity(context: Context): Result<FynxWalletSecurityStatus> =
        FynxBackendClient.get(context, "/api/wallet/security").mapCatching(::parseWalletSecurity)

    internal fun parseWalletSecurity(raw: String): FynxWalletSecurityStatus {
        val json = JSONObject(raw)
        val isLocked = json.optBoolean("isLocked", false)
        return FynxWalletSecurityStatus(
            hasWithdrawalPin = json.optBoolean("hasWithdrawalPin", false),
            isLocked = isLocked,
            lockedUntil = json.optString("lockedUntil").takeIf { isLocked && it.isNotBlank() && it != "null" }
        )
    }

    suspend fun setWithdrawalPin(
        context: Context,
        currentPassword: String,
        pin: String,
        confirmPin: String
    ): Result<FynxWalletSecurityStatus> {
        require(currentPassword.length in 8..128) { "Enter your current FYNX password." }
        require(pin.matches(Regex("^\\d{6}$")) && pin == confirmPin) { "Enter and confirm the same six-digit wallet PIN." }
        val body = JSONObject()
            .put("currentPassword", currentPassword)
            .put("pin", pin)
            .put("confirmPin", confirmPin)
        return FynxBackendClient.postJson(context, "/api/wallet/security/pin", body.toString())
            .mapCatching { raw ->
                val json = JSONObject(raw)
                require(json.optBoolean("hasWithdrawalPin", false)) { "Wallet PIN setup was not confirmed." }
                FynxWalletSecurityStatus(true, false, null)
            }
    }

    suspend fun loadPayoutBanks(context: Context): Result<List<FynxWalletPayoutBank>> =
        FynxBackendClient.get(context, "/api/wallet/payout-banks").mapCatching(::parsePayoutBanks)

    internal fun parsePayoutBanks(raw: String): List<FynxWalletPayoutBank> {
        val rows = JSONObject(raw).optJSONArray("banks") ?: JSONArray()
        require(rows.length() <= 250) { "The payout bank list is too large." }
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val name = row.optString("name").trim()
                val code = row.optString("code").trim()
                val slug = row.optString("slug").trim()
                if (name.isNotBlank() && code.matches(Regex("^\\d{3,10}$"))) {
                    add(FynxWalletPayoutBank(name, code, slug))
                }
            }
        }
    }

    suspend fun verifyPayoutAccount(
        context: Context,
        bankCode: String,
        accountNumber: String
    ): Result<String> {
        val body = JSONObject()
            .put("bankCode", bankCode.trim())
            .put("accountNumber", accountNumber.filter(Char::isDigit))
        return FynxBackendClient.postJson(context, "/api/wallet/payout-account/verify", body.toString())
            .mapCatching { raw ->
                val json = JSONObject(raw)
                require(json.optBoolean("verified", false)) { "The bank account has not been verified." }
                val accountName = json.optString("accountName").trim()
                require(accountName.length >= 2) { "The verified bank account name is missing." }
                accountName
            }
    }

    suspend fun requestWithdrawal(
        context: Context,
        amount: Double,
        bankCode: String,
        bankName: String,
        accountNumber: String,
        idempotencyKey: String,
        pin: String
    ): Result<FynxWalletWithdrawalResult> {
        require(amount.isFinite() && amount > 0.0) { "Enter a valid withdrawal amount." }
        require(bankCode.matches(Regex("^\\d{3,10}$"))) { "Choose a valid bank." }
        require(accountNumber.filter(Char::isDigit).matches(Regex("^\\d{10}$"))) { "Enter a valid 10-digit account number." }
        require(idempotencyKey.matches(Regex("^[A-Za-z0-9_-]{8,100}$"))) { "Withdrawal request reference is invalid." }
        require(pin.matches(Regex("^\\d{6}$"))) { "Enter your six-digit wallet PIN." }
        val body = JSONObject()
            .put("amount", amount)
            .put("bankCode", bankCode)
            .put("bankName", bankName)
            .put("accountNumber", accountNumber.filter(Char::isDigit))
            .put("idempotencyKey", idempotencyKey)
            .put("pin", pin)
        return FynxBackendClient.postJson(context, "/api/wallet/withdrawals", body.toString())
            .mapCatching(::parseWithdrawalResult)
    }

    internal fun parseWithdrawalResult(raw: String): FynxWalletWithdrawalResult {
        val json = JSONObject(raw)
        val status = json.optString("status", "PENDING").trim().uppercase(Locale.US)
        require(status in setOf("PENDING", "PROCESSING", "SUCCEEDED", "FAILED", "REVERSED", "BLOCKED")) {
            "Wallet withdrawal returned an unsupported status."
        }
        val amount = json.optDouble("amount", Double.NaN)
        require(amount.isFinite() && amount > 0.0) { "Wallet withdrawal amount is invalid." }
        return FynxWalletWithdrawalResult(
            status = status,
            reference = json.optString("reference").trim(),
            amount = amount,
            bankName = json.optString("bankName").trim(),
            accountLast4 = json.optString("accountLast4").trim(),
            message = json.optString("message").trim()
        )
    }

    suspend fun loadStatement(context: Context): Result<String> =
        FynxBackendClient.get(context, "/api/wallet/statement.csv")

    internal fun parseFundingAccount(raw: String): FynxWalletFundingAccount {
        val json = JSONObject(raw)
        val status = json.optString("status", "NOT_CREATED").trim().uppercase(Locale.US)
        require(status in setOf("NOT_SETUP","NOT_CREATED","REQUESTING","ASSIGNING","ACTIVE","FAILED","RESTRICTED")) {
            "Wallet funding account returned an unsupported status."
        }
        val currency = json.optString("currency", "NGN").trim().uppercase(Locale.US)
        require(currency == "NGN") { "Wallet funding account currency is not supported." }
        val account = json.optJSONObject("account")
        val accountNumber = account?.optString("accountNumber")?.trim()?.takeIf { it.isNotBlank() }
        if (status == "ACTIVE") {
            require(accountNumber != null && accountNumber.matches(Regex("^\\d{10}$"))) {
                "Active wallet funding account has invalid account details."
            }
        }
        return FynxWalletFundingAccount(
            status = status,
            currency = currency,
            canRequest = json.optBoolean("canRequest", false),
            bankName = account?.optString("bankName")?.trim()?.takeIf { it.isNotBlank() },
            accountName = account?.optString("accountName")?.trim()?.takeIf { it.isNotBlank() },
            accountNumber = accountNumber
        )
    }

    suspend fun loadSellerSummary(context: Context): Result<FynxWalletSellerSummary> =
        FynxBackendClient.get(context, "/api/marketplace/settlement/seller/summary")
            .mapCatching(::parseSellerSummary)

    internal fun parseSellerSummary(raw: String): FynxWalletSellerSummary {
        val response = JSONObject(raw)
        val totalRows = response.optJSONArray("totals") ?: JSONArray()
        val settlementRows = response.optJSONArray("settlements") ?: JSONArray()
        require(totalRows.length() <= 3) { "Seller earnings returned too many currency totals." }
        require(settlementRows.length() <= 100) { "Seller earnings returned too many settlement rows." }

        fun amount(row: JSONObject, key: String): Double {
            val value = row.optDouble(key, Double.NaN)
            require(value.isFinite() && value >= 0.0) { "Seller earnings contain an invalid amount." }
            return value
        }

        val totals = buildList {
            for (index in 0 until totalRows.length()) {
                val row = totalRows.optJSONObject(index) ?: continue
                val currency = row.optString("currency").trim().uppercase(Locale.US)
                require(currency in setOf("NGN", "USD")) { "Seller earnings contain an unsupported currency." }
                add(
                    FynxWalletSellerTotal(
                        currency = currency,
                        protectedFunds = amount(row, "protectedFunds"),
                        availableForPayout = amount(row, "availableForPayout"),
                        payoutPending = amount(row, "payoutPending"),
                        paidOut = amount(row, "paidOut"),
                        grossSales = amount(row, "grossSales"),
                        marketplaceFees = amount(row, "marketplaceFees"),
                        netEarnings = amount(row, "netEarnings")
                    )
                )
            }
        }
        val settlements = buildList {
            for (index in 0 until settlementRows.length()) {
                val row = settlementRows.optJSONObject(index) ?: continue
                val orderId = row.optString("orderId").trim()
                val title = row.optString("title").trim()
                val currency = row.optString("currency").trim().uppercase(Locale.US)
                require(orderId.isNotBlank() && title.isNotBlank()) { "Seller settlement is missing its order identity." }
                require(currency in setOf("NGN", "USD")) { "Seller settlement contains an unsupported currency." }
                add(
                    FynxWalletSellerSettlement(
                        orderId = orderId,
                        title = title,
                        currency = currency,
                        sellerNetAmount = amount(row, "sellerNetAmount"),
                        marketplaceFeeSeller = amount(row, "marketplaceFeeSeller"),
                        funds = row.optString("funds", "UNKNOWN").trim().uppercase(Locale.US),
                        payoutStatus = row.optString("payoutStatus", "UNKNOWN").trim().uppercase(Locale.US),
                        updatedAt = row.optString("updatedAt").trim()
                    )
                )
            }
        }
        return FynxWalletSellerSummary(totals, settlements)
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
