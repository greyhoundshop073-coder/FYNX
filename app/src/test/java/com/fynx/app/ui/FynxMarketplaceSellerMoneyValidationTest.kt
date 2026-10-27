package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FynxMarketplaceSellerMoneyValidationTest {
    @Test
    fun acceptsRealNumericAmountsIncludingNegativeAdjustmentRecords() {
        assertEquals(125.5, marketplaceRequiredAmount(125.5), 0.0001)
        assertEquals(-4.25, marketplaceRequiredAmount(-4.25), 0.0001)
    }

    @Test
    fun missingOrStringAmountsCannotBeInterpretedAsZero() {
        expectInvalid { marketplaceRequiredAmount(null) }
        expectInvalid { marketplaceRequiredAmount("0.00") }
    }

    @Test
    fun nonFiniteAmountsAreRejected() {
        expectInvalid { marketplaceRequiredAmount(Double.NaN) }
        expectInvalid { marketplaceRequiredAmount(Double.POSITIVE_INFINITY) }
        expectInvalid { marketplaceRequiredAmount(Double.NEGATIVE_INFINITY) }
    }

    @Test
    fun balanceBucketsMustBeFiniteAndNonNegative() {
        assertEquals(0.0, marketplaceRequiredBalance(0), 0.0)
        assertEquals(12.75, marketplaceRequiredBalance(12.75), 0.0001)
        expectInvalid { marketplaceRequiredBalance(-0.01) }
        expectInvalid { marketplaceRequiredBalance(Double.NaN) }
    }

    @Test
    fun currencyMustBePresentAndAnExplicitThreeLetterCode() {
        assertEquals("NGN", marketplaceRequiredCurrency("ngn"))
        assertEquals("USD", marketplaceRequiredCurrency(" USD "))
        expectInvalid { marketplaceRequiredCurrency(null) }
        expectInvalid { marketplaceRequiredCurrency("") }
        expectInvalid { marketplaceRequiredCurrency("N") }
        expectInvalid { marketplaceRequiredCurrency("USDD") }
    }

    private fun expectInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected malformed seller financial data to be rejected.")
        } catch (_: IllegalStateException) {
            // Correct: invalid financial data must trigger the unavailable/error state.
        }
    }
}
