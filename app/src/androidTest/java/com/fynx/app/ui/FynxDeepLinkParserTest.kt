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
    fun marketplaceStatusDraftPreservesListingReferenceAndCaption() {
        val draft = FynxDeepLinkParser.marketplaceStatusDraftText("12345", "Blue Shoes")
        assertTrue(draft.startsWith("Check out \"Blue Shoes\" on FYNX Marketplace"))
        assertEquals("12345", FynxDeepLinkParser.marketplaceListingIdFromStatusText(draft))
        assertEquals("Check out \"Blue Shoes\" on FYNX Marketplace", FynxDeepLinkParser.marketplaceStatusCaption(draft))
        assertTrue(draft.contains(FynxDeepLinkParser.marketplaceWebLink("12345")))
    }

    @Test
    fun marketplaceStatusAttachmentRejectsInvalidOrUnrelatedReferences() {
        assertEquals("", FynxDeepLinkParser.marketplaceStatusDraftText("0", "Invalid product"))
        assertEquals("", FynxDeepLinkParser.marketplaceStatusDraftText("not-an-id", "Invalid product"))
        assertEquals(null, FynxDeepLinkParser.marketplaceListingIdFromStatusText("Visit https://fynx.app/profile/alice"))
        assertEquals(null, FynxDeepLinkParser.marketplaceListingIdFromStatusText("https://fynx.app/marketplace/0"))
    }

    @Test
    fun marketplaceBaseLinkRemainsValid() {
        val destination = FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.marketplaceAppLink()))
        assertTrue(destination is FynxDeepLinkDestination.Marketplace)
        assertEquals(null, (destination as FynxDeepLinkDestination.Marketplace).listingId)
    }
}
