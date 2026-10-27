package com.fynx.app.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Base64

class FynxMarketplaceCheckoutQuoteTest {
    private val now = Instant.parse("2026-10-27T07:00:00Z")

    private fun jwtToken(subject: String, signature: String): String {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(JSONObject().put("sub", subject).toString().toByteArray(Charsets.UTF_8))
        return "e30.$payload.$signature"
    }

    private fun validQuote(edit: (JSONObject) -> Unit = {}): String {
        val quote = JSONObject()
            .put("id", "quote-123")
            .put("expiresAt", "2026-10-27T07:05:00Z")
            .put("listingId", "42")
            .put("sellerDisplayName", "Seller")
            .put("sellerUsername", "selleruser")
            .put("productTitle", "Phone")
            .put("quantity", 2)
            .put("unitPrice", 10.0)
            .put("currency", "NGN")
            .put("fulfillmentMethod", "PICKUP")
            .put("subtotal", 20.0)
            .put("deliveryFee", 0.0)
            .put("marketplaceFee", 1.0)
            .put("marketplaceFeeBuyer", 0.5)
            .put("discountAmount", 0.0)
            .put("total", 20.5)
            .put("protection", JSONObject().put("payout", "RELEASED_AFTER_BUYER_CONFIRMATION"))
        edit(quote)
        return JSONObject().put("quote", quote).toString()
    }

    private fun parse(raw: String): FynxMarketplaceCheckoutQuote =
        parseMarketplaceCheckoutQuoteResponse(raw, "42", "NGN", 2, "PICKUP", now)

    private fun assertInvalid(raw: String) {
        val result = runCatching { parse(raw) }
        assertTrue("Expected checkout quote to be rejected, but it was accepted.", result.isFailure)
    }

    @Test
    fun validServerQuoteMapsAuthoritativeTotals() {
        val quote = parse(validQuote())
        assertEquals("quote-123", quote.id)
        assertEquals("42", quote.listingId)
        assertEquals(2, quote.quantity)
        assertEquals("NGN", quote.currency)
        assertEquals(20.0, quote.subtotal, 0.001)
        assertEquals(0.5, quote.marketplaceFeeBuyer, 0.001)
        assertEquals(20.5, quote.total, 0.001)
    }

    @Test
    fun missingSellerDisplayNameFallsBackToAuthoritativeSellerUsername() {
        val missing = parse(validQuote { it.remove("sellerDisplayName") })
        val nullValue = parse(validQuote { it.put("sellerDisplayName", JSONObject.NULL) })
        assertEquals("selleruser", missing.sellerDisplayName)
        assertEquals("selleruser", nullValue.sellerDisplayName)
    }

    @Test
    fun expiredOrMalformedExpiryIsRejected() {
        assertInvalid(validQuote { it.put("expiresAt", "2026-10-27T06:59:00Z") })
        assertInvalid(validQuote { it.put("expiresAt", "not-a-time") })
    }

    @Test
    fun productQuantityCurrencyAndFulfillmentMustMatchTheRequest() {
        assertInvalid(validQuote { it.put("listingId", "99") })
        assertInvalid(validQuote { it.put("quantity", 3) })
        assertInvalid(validQuote { it.put("currency", "USD") })
        assertInvalid(validQuote { it.put("fulfillmentMethod", "DELIVERY") })
    }

    @Test
    fun missingIdentityAndMoneyFieldsAreRejectedInsteadOfDefaultingToZeroOrLiteralNull() {
        assertInvalid(validQuote { it.remove("id") })
        assertInvalid(validQuote { it.put("id", JSONObject.NULL) })
        assertInvalid(validQuote { it.remove("total") })
        assertInvalid(validQuote { it.put("total", "not-a-number") })
        assertInvalid(validQuote { it.put("deliveryFee", -1.0) })
    }

    @Test
    fun subtotalAndTotalMustReconcileWithQuantityAndFeeBreakdown() {
        assertInvalid(validQuote { it.put("subtotal", 19.0) })
        assertInvalid(validQuote { it.put("total", 22.0) })
        assertInvalid(validQuote { it.put("marketplaceFeeBuyer", 1.5) })
    }

    @Test
    fun idempotencyFingerprintIsStableForTheSameAuthenticatedIntentAcrossTokenRotation() {
        val address = FynxMarketplaceCheckoutAddress("Buyer", "08000000000", "Road 1", "Port Harcourt", "Rivers", "Nigeria")
        val first = marketplaceCheckoutIntentFingerprint(jwtToken("buyer-1", "signature-one"), "42", 2, "DELIVERY", address)
        val retry = marketplaceCheckoutIntentFingerprint(jwtToken("buyer-1", "signature-two"), "42", 2, "DELIVERY", address)
        assertEquals(first, retry)
        assertEquals(64, first.length)
    }

    @Test
    fun fingerprintChangesWhenAccountOrOrderIntentChanges() {
        val address = FynxMarketplaceCheckoutAddress("Buyer", "08000000000", "Road 1", "Port Harcourt", "Rivers", "Nigeria")
        val base = marketplaceCheckoutIntentFingerprint(jwtToken("buyer-1", "signature-one"), "42", 2, "DELIVERY", address)
        assertNotEquals(base, marketplaceCheckoutIntentFingerprint(jwtToken("buyer-2", "signature-two"), "42", 2, "DELIVERY", address))
        assertNotEquals(base, marketplaceCheckoutIntentFingerprint(jwtToken("buyer-1", "signature-two"), "42", 3, "DELIVERY", address))
        assertNotEquals(base, marketplaceCheckoutIntentFingerprint(jwtToken("buyer-1", "signature-two"), "42", 2, "DELIVERY", address.copy(address = "Road 2")))
        assertNotEquals(base, marketplaceCheckoutIntentFingerprint(jwtToken("buyer-1", "signature-two"), "42", 2, "PICKUP", null))
    }
    private fun validOrder(edit: (JSONObject) -> Unit = {}): String {
        val order = JSONObject()
            .put("id", "550e8400-e29b-41d4-a716-446655440000")
            .put("buyerId", "buyer-1")
            .put("sellerId", "seller-1")
            .put("listingId", "42")
            .put("quantity", 2)
            .put("unitPrice", 10.0)
            .put("productSubtotal", 20.0)
            .put("deliveryFee", 0.0)
            .put("marketplaceFee", 1.0)
            .put("marketplaceFeeBuyer", 0.5)
            .put("marketplaceFeeSeller", 0.5)
            .put("discountAmount", 0.0)
            .put("buyerTotal", 20.5)
            .put("totalAmount", 20.5)
            .put("currency", "NGN")
            .put("product", JSONObject().put("title", "Phone").put("sellerUsername", "selleruser"))
            .put("status", "PAYMENT_PENDING")
            .put("fulfillmentMethod", "PICKUP")
        edit(order)
        return JSONObject().put("order", order).toString()
    }

    private fun parseOrder(raw: String): FynxRemoteSocialClient.MarketplaceOrder =
        parseProtectedMarketplaceCheckoutOrderResponse(raw, "42", "NGN", 2, "PICKUP")

    private fun assertInvalidOrder(raw: String) {
        val result = runCatching { parseOrder(raw) }
        assertTrue("Expected protected order to be rejected, but it was accepted.", result.isFailure)
    }

    @Test
    fun validProtectedOrderMapsServerIdentityAndPayableTotal() {
        val order = parseOrder(validOrder())
        assertEquals("550e8400-e29b-41d4-a716-446655440000", order.id)
        assertEquals("42", order.listingId)
        assertEquals(2, order.quantity)
        assertEquals("NGN", order.currency)
        assertEquals("PAYMENT_PENDING", order.status)
        assertEquals(20.5, order.totalAmount, 0.001)
    }

    @Test
    fun protectedOrderMustHaveValidIdentityAndStateBeforePayment() {
        assertInvalidOrder(validOrder { it.remove("id") })
        assertInvalidOrder(validOrder { it.put("id", "not-a-uuid") })
        assertInvalidOrder(validOrder { it.remove("status") })
        assertInvalidOrder(validOrder { it.put("status", "PAID") })
        assertInvalidOrder(validOrder { it.put("status", "CANCELLED") })
        assertInvalidOrder(validOrder { it.put("status", "DISPUTED") })
        assertInvalidOrder(JSONObject().put("unexpected", true).toString())
    }

    @Test
    fun protectedOrderMustMatchTheRequestedListingQuantityCurrencyAndFulfillment() {
        assertInvalidOrder(validOrder { it.put("listingId", "99") })
        assertInvalidOrder(validOrder { it.put("quantity", 3) })
        assertInvalidOrder(validOrder { it.put("currency", "USD") })
        assertInvalidOrder(validOrder { it.put("fulfillmentMethod", "DELIVERY") })
    }

    @Test
    fun protectedOrderRejectsMissingMalformedAndInconsistentFinancialValues() {
        assertInvalidOrder(validOrder { it.remove("productSubtotal") })
        assertInvalidOrder(validOrder { it.put("unitPrice", "not-a-number") })
        assertInvalidOrder(validOrder { it.put("productSubtotal", 19.0) })
        assertInvalidOrder(validOrder { it.put("buyerTotal", 22.0) })
        assertInvalidOrder(validOrder { it.put("marketplaceFeeBuyer", 1.5) })
        assertInvalidOrder(validOrder { it.put("marketplaceFeeSeller", 0.1) })
        assertInvalidOrder(validOrder { it.put("deliveryFee", -1.0) })
    }

    @Test
    fun protectedOrderRejectsValidButRepricedResponse() {
        val reviewedQuote = parse(validQuote())
        val repricedOrder = validOrder {
            it.put("unitPrice", 11.0)
                .put("productSubtotal", 22.0)
                .put("marketplaceFee", 1.1)
                .put("marketplaceFeeBuyer", 0.55)
                .put("marketplaceFeeSeller", 0.55)
                .put("buyerTotal", 22.55)
                .put("totalAmount", 22.55)
        }
        val result = runCatching {
            parseProtectedMarketplaceCheckoutOrderResponse(
                raw = repricedOrder,
                expectedListingId = "42",
                expectedCurrency = "NGN",
                expectedQuantity = 2,
                expectedFulfillmentMethod = "PICKUP",
                expectedQuote = reviewedQuote
            )
        }
        assertTrue("A repriced order must not pass to payment after the buyer reviewed an earlier amount.", result.isFailure)
    }

}
