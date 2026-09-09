package com.fynx.app.ui

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxDeepLinkParserTest {
    @Test
    fun marketplaceAppLinkPreservesListingId() {
        val destination = FynxDeepLinkParser.parse(Uri.parse("fynx://marketplace/12345"))
        assertTrue(destination is FynxDeepLinkDestination.Marketplace)
        assertEquals("12345", (destination as FynxDeepLinkDestination.Marketplace).listingId)
    }

    @Test
    fun marketplaceWebLinkPreservesListingId() {
        val destination = FynxDeepLinkParser.parse(Uri.parse("https://fynx.app/marketplace/12345"))
        assertTrue(destination is FynxDeepLinkDestination.Marketplace)
        assertEquals("12345", (destination as FynxDeepLinkDestination.Marketplace).listingId)
    }

    @Test
    fun marketplaceBaseLinkRemainsValid() {
        val destination = FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.marketplaceAppLink()))
        assertTrue(destination is FynxDeepLinkDestination.Marketplace)
        assertEquals(null, (destination as FynxDeepLinkDestination.Marketplace).listingId)
    }
}
