package com.fynx.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxMarketplaceCheckoutUrlTest {
    @Test
    fun acceptsPaystackHostedCheckoutUrls() {
        assertTrue(isTrustedMarketplaceCheckoutUrl("https://checkout.paystack.com/abc123"))
        assertTrue(isTrustedMarketplaceCheckoutUrl("https://paystack.com/pay/abc123"))
        assertTrue(isTrustedMarketplaceCheckoutUrl("https://checkout.paystack.com:443/abc123"))
    }

    @Test
    fun rejectsUntrustedOrMalformedCheckoutUrls() {
        listOf(
            "http://checkout.paystack.com/abc123",
            "https://checkout.paystack.com.evil.example/abc123",
            "https://evil-checkout.paystack.com/abc123",
            "https://checkout.paystack.com@evil.example/abc123",
            "https://user@checkout.paystack.com/abc123",
            "https://checkout.paystack.com:444/abc123",
            "javascript:alert(1)",
            "https://",
            "not a URL"
        ).forEach { url ->
            assertFalse("Expected checkout URL to be rejected: $url", isTrustedMarketplaceCheckoutUrl(url))
        }
    }
}
