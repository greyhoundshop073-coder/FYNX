package com.fynx.app.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxMarketplacePaymentResponseTest {
    private fun validResponse(edit: (JSONObject) -> Unit = {}): String {
        val response = JSONObject()
            .put("authorizationUrl", "https://checkout.paystack.com/session-123")
            .put("accessCode", "access-123")
            .put("reference", "FYNX-ORDER-REFERENCE-123")
            .put("amountSubunit", 125050)
            .put("currency", "NGN")
        edit(response)
        return response.toString()
    }

    @Test
    fun validPaymentMustMatchTheReviewedOrderCurrencyAndAmount() {
        val payment = parseMarketplacePaymentResponse(validResponse(), 1250.50, "NGN")
        assertEquals(1250.50, payment.amount, 0.001)
        assertEquals("NGN", payment.currency)
        assertEquals("FYNX-ORDER-REFERENCE-123", payment.reference)
    }

    @Test
    fun missingOrUnsupportedCurrencyIsRejectedInsteadOfDefaultingToNaira() {
        assertTrue(runCatching {
            parseMarketplacePaymentResponse(validResponse { it.remove("currency") }, 1250.50, "NGN")
        }.isFailure)
        assertTrue(runCatching {
            parseMarketplacePaymentResponse(validResponse { it.put("currency", "EUR") }, 1250.50, "NGN")
        }.isFailure)
    }

    @Test
    fun paymentCurrencyMustMatchTheOrderCurrency() {
        assertTrue(runCatching {
            parseMarketplacePaymentResponse(validResponse { it.put("currency", "USD") }, 1250.50, "NGN")
        }.isFailure)
    }

    @Test
    fun paymentAmountMustMatchTheReviewedOrderTotalToTheKoboOrCent() {
        assertTrue(runCatching {
            parseMarketplacePaymentResponse(validResponse { it.put("amountSubunit", 125049) }, 1250.50, "NGN")
        }.isFailure)
        assertTrue(runCatching {
            parseMarketplacePaymentResponse(validResponse { it.put("amountSubunit", 125150) }, 1250.50, "NGN")
        }.isFailure)
    }

    @Test
    fun untrustedCheckoutUrlIsRejectedAlongWithOtherwiseValidMoney() {
        assertTrue(runCatching {
            parseMarketplacePaymentResponse(
                validResponse { it.put("authorizationUrl", "https://checkout.paystack.com.attacker.example/session") },
                1250.50,
                "NGN"
            )
        }.isFailure)
    }

    @Test
    fun usdPaymentsRemainSupportedWhenTheOrderAndProviderAgree() {
        val payment = parseMarketplacePaymentResponse(
            validResponse { it.put("amountSubunit", 9900).put("currency", "USD") },
            99.0,
            "USD"
        )
        assertEquals("USD", payment.currency)
        assertEquals(99.0, payment.amount, 0.001)
    }
}
