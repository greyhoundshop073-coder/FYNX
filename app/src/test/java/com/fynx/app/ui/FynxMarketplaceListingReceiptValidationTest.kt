package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class FynxMarketplaceListingReceiptValidationTest {
    private fun expectInvalid(block: () -> Any?) {
        try {
            block()
            fail("Expected malformed Marketplace data to be rejected.")
        } catch (_: IllegalStateException) {
            // Expected: the client must fail closed instead of inventing a fallback value.
        }
    }

    @Test
    fun listingIdsMustBePositiveDatabaseIdentifiers() {
        assertEquals("12", marketplaceRequiredListingId("12"))
        assertEquals("7", marketplaceRequiredListingId(7))
        expectInvalid { marketplaceRequiredListingId(null) }
        expectInvalid { marketplaceRequiredListingId("") }
        expectInvalid { marketplaceRequiredListingId("0") }
        expectInvalid { marketplaceRequiredListingId(-1) }
        expectInvalid { marketplaceRequiredListingId(1.5) }
    }

    @Test
    fun listingTitlePriceAndCurrencyMustBeExplicitAndValid() {
        assertEquals("Phone", marketplaceRequiredListingTitle(" Phone "))
        assertEquals(1200.5, marketplaceRequiredListingPrice(1200.5), 0.0)
        assertEquals("NGN", marketplaceRequiredMarketplaceCurrency("ngn"))
        assertEquals("USD", marketplaceRequiredMarketplaceCurrency(" USD "))
        expectInvalid { marketplaceRequiredListingTitle("") }
        expectInvalid { marketplaceRequiredListingTitle("x".repeat(MAX_MARKETPLACE_LISTING_RESPONSE_TITLE_LENGTH + 1)) }
        expectInvalid { marketplaceRequiredListingPrice(null) }
        expectInvalid { marketplaceRequiredListingPrice("1200") }
        expectInvalid { marketplaceRequiredListingPrice(0.0) }
        expectInvalid { marketplaceRequiredListingPrice(-1.0) }
        expectInvalid { marketplaceRequiredListingPrice(Double.NaN) }
        expectInvalid { marketplaceRequiredListingPrice(Double.POSITIVE_INFINITY) }
        expectInvalid { marketplaceRequiredMarketplaceCurrency(null) }
        expectInvalid { marketplaceRequiredMarketplaceCurrency("EUR") }
        expectInvalid { marketplaceRequiredMarketplaceCurrency("NGNN") }
    }

    @Test
    fun listingAvailabilityMustBeExplicitAndBoolean() {
        assertEquals(true, marketplaceRequiredListingActive(true))
        assertEquals(false, marketplaceRequiredListingActive(false))
        expectInvalid { marketplaceRequiredListingActive(null) }
        expectInvalid { marketplaceRequiredListingActive("true") }
        expectInvalid { marketplaceRequiredListingActive(1) }
    }

    @Test
    fun inventoryMustBeAnIntegerWithinTheSupportedRange() {
        assertEquals(0, marketplaceRequiredInventoryQuantity(0))
        assertEquals(15, marketplaceRequiredInventoryQuantity(15.0))
        assertEquals(FynxMarketplaceSellerFlowSupport.MAX_QUANTITY,
            marketplaceRequiredInventoryQuantity(FynxMarketplaceSellerFlowSupport.MAX_QUANTITY))
        expectInvalid { marketplaceRequiredInventoryQuantity(null) }
        expectInvalid { marketplaceRequiredInventoryQuantity("5") }
        expectInvalid { marketplaceRequiredInventoryQuantity(-1) }
        expectInvalid { marketplaceRequiredInventoryQuantity(1.25) }
        expectInvalid { marketplaceRequiredInventoryQuantity(Double.NaN) }
        expectInvalid { marketplaceRequiredInventoryQuantity(FynxMarketplaceSellerFlowSupport.MAX_QUANTITY + 1) }
    }

    @Test
    fun optionalDeliveryFeesAllowNullOrZeroButRejectMalformedValues() {
        assertNull(marketplaceOptionalListingFee(null))
        assertEquals(0.0, marketplaceOptionalListingFee(0.0)!!, 0.0)
        assertEquals(12.5, marketplaceOptionalListingFee(12.5)!!, 0.0)
        expectInvalid { marketplaceOptionalListingFee("12.5") }
        expectInvalid { marketplaceOptionalListingFee(-0.01) }
        expectInvalid { marketplaceOptionalListingFee(Double.NaN) }
        expectInvalid { marketplaceOptionalListingFee(Double.POSITIVE_INFINITY) }
    }

    @Test
    fun receiptAmountsPreserveGenuineZeroAndRejectMissingOrInvalidMoney() {
        assertEquals(0.0, marketplaceRequiredReceiptAmount(0.0), 0.0)
        assertEquals(125.75, marketplaceRequiredReceiptAmount(125.75), 0.0)
        expectInvalid { marketplaceRequiredReceiptAmount(null) }
        expectInvalid { marketplaceRequiredReceiptAmount("0.00") }
        expectInvalid { marketplaceRequiredReceiptAmount(-0.01) }
        expectInvalid { marketplaceRequiredReceiptAmount(Double.NaN) }
        expectInvalid { marketplaceRequiredReceiptAmount(Double.NEGATIVE_INFINITY) }
    }
}
