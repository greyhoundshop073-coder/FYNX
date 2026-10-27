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
}
