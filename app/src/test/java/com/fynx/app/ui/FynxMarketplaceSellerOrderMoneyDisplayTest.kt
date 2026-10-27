package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FynxMarketplaceSellerOrderMoneyDisplayTest {
    @Test
    fun validZeroAndPositiveAmountsAreFormatted() {
        assertEquals("NGN 0.00", marketplaceMoneyOrUnavailable(0.0, "NGN"))
        assertEquals("USD 12.50", marketplaceMoneyOrUnavailable(12.5, "usd"))
    }

    @Test
    fun missingStringNonFiniteAndNegativeAmountsNeverBecomeZero() {
        assertEquals("Not available", marketplaceMoneyOrUnavailable(null, "NGN"))
        assertEquals("Not available", marketplaceMoneyOrUnavailable("0.00", "NGN"))
        assertEquals("Not available", marketplaceMoneyOrUnavailable(Double.NaN, "NGN"))
        assertEquals("Not available", marketplaceMoneyOrUnavailable(Double.POSITIVE_INFINITY, "NGN"))
        assertEquals("Not available", marketplaceMoneyOrUnavailable(-0.01, "NGN"))
    }

    @Test
    fun invalidCurrencyCannotProduceMisleadingAccountingText() {
        assertEquals("Not available", marketplaceMoneyOrUnavailable(10.0, ""))
        assertEquals("Not available", marketplaceMoneyOrUnavailable(10.0, "USDD"))
    }
}
