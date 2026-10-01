package com.fynx.app.ui

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
