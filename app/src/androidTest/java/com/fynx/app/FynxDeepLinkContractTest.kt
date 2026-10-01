package com.fynx.app

import android.net.Uri
import com.fynx.app.ui.FynxDeepLinkDestination
import com.fynx.app.ui.FynxDeepLinkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxDeepLinkContractTest {
    @Test
    fun webRoutesRoundTripToExpectedDestinations() {
        assertEquals(FynxDeepLinkDestination.Home, FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.homeWebLink())))
        assertEquals(FynxDeepLinkDestination.Profile("alice"), FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.profileWebLink("@alice"))))
        assertEquals(FynxDeepLinkDestination.Chat("bob"), FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.chatWebLink("@bob"))))
        assertEquals(FynxDeepLinkDestination.Group("42"), FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.groupWebLink("42"))))
        assertEquals(FynxDeepLinkDestination.Marketplace("listing-7"), FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.marketplaceWebLink("listing-7"))))
        assertEquals(FynxDeepLinkDestination.Stories, FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.storiesWebLink())))
        assertEquals(FynxDeepLinkDestination.Money, FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.moneyWebLink())))
    }

    @Test
    fun appRoutesRoundTripToExpectedDestinations() {
        assertEquals(FynxDeepLinkDestination.Home, FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.homeAppLink())))
        assertEquals(FynxDeepLinkDestination.Profile("alice"), FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.profileAppLink("@alice"))))
        assertEquals(FynxDeepLinkDestination.Chat("bob"), FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.chatAppLink("@bob"))))
        assertEquals(FynxDeepLinkDestination.Group("42"), FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.groupAppLink("42"))))
        assertEquals(FynxDeepLinkDestination.Marketplace("listing-7"), FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.marketplaceAppLink("listing-7"))))
        assertEquals(FynxDeepLinkDestination.Stories, FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.storiesAppLink())))
        assertEquals(FynxDeepLinkDestination.Money, FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.moneyAppLink())))
    }

    @Test
    fun inviteCodeSurvivesWebRoute() {
        val destination = FynxDeepLinkParser.parse(Uri.parse(FynxDeepLinkParser.inviteWebLink("ABC123")))
        assertEquals(FynxDeepLinkDestination.Invite("ABC123"), destination)
    }

    @Test
    fun invalidExternalHostIsRejected() {
        val destination = FynxDeepLinkParser.parse(Uri.parse("https://example.com/profile/alice"))
        assertTrue(destination == null)
    }

    @Test
    fun malformedKnownRoutesAreRejected() {
        assertTrue(FynxDeepLinkParser.parse(Uri.parse("https://fynx.app/profile")) == null)
        assertTrue(FynxDeepLinkParser.parse(Uri.parse("https://fynx.app/profile/alice/extra")) == null)
        assertTrue(FynxDeepLinkParser.parse(Uri.parse("https://fynx.app/home/extra")) == null)
        assertTrue(FynxDeepLinkParser.parse(Uri.parse("fynx://profile/@")) == null)
        assertTrue(FynxDeepLinkParser.parse(Uri.parse("fynx://unknown/alice")) == null)
    }
}
