package com.fynx.app.ui

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class FynxMarketplaceUserFacingErrorTest {
    @Test
    fun rawHttpBodyAndRequestIdAreReplacedWithHelpfulFallback() {
        val failure = IOException(
            """FYNX backend returned HTTP 404: {"status":"error","code":404,"message":"Application not found","request_id":"private-debug-id"}"""
        )
        assertEquals("Listings could not load. Try again.", marketplaceSafeError(failure, "Listings could not load. Try again."))
    }

    @Test
    fun standaloneRawJsonIsNotShownToUsers() {
        val failure = IOException("""{"error":"internal failure","request_id":"private-debug-id"}""")
        assertEquals("Try again later.", marketplaceSafeError(failure, "Try again later."))
    }

    @Test
    fun usefulDomainValidationMessageIsPreserved() {
        val failure = IllegalArgumentException("Choose delivery or pickup.")
        assertEquals("Choose delivery or pickup.", marketplaceSafeError(failure, "Checkout could not continue."))
    }

    @Test
    fun blankFailureUsesFallback() {
        assertEquals("Orders could not load.", marketplaceSafeError(IOException("  "), "Orders could not load."))
    }
}
