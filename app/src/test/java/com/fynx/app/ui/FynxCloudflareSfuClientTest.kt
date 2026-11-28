package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxCloudflareSfuClientTest {
    @Test
    fun publicationDiscoveryKeepsOnlyValidServerOwnedSessionsAndTracks() {
        val raw = """
            {
              "publishers": [
                {"sessionId":"session-a","userId":"77","trackNames":["microphone","camera","camera","screen"]},
                {"sessionId":"bad session","userId":"78","trackNames":["camera"]},
                {"sessionId":"session-b","userId":"not-an-id","trackNames":["camera"]},
                {"sessionId":"session-c","userId":"79","trackNames":[]},
                {"sessionId":"session-a","userId":"88","trackNames":["camera"]}
              ]
            }
        """.trimIndent()

        val result = FynxCloudflareSfuClient.parsePublications(raw)

        assertEquals(1, result.size)
        assertEquals("session-a", result.single().sessionId)
        assertEquals("77", result.single().userId)
        assertEquals(setOf("microphone", "camera"), result.single().trackNames.toSet())
    }

    @Test
    fun parsesRequiredSfuRenegotiationOfferAndTrackMids() {
        val raw = """
            {
              "requiresImmediateRenegotiation": true,
              "sessionDescription": {"type":"offer","sdp":"v=0\\r\\na=mid:0"},
              "tracks": [
                {"mid":"3","trackName":"camera"},
                {"mid":"4","trackName":"microphone"},
                {"mid":"bad mid","trackName":"camera"},
                {"mid":"5","trackName":"screen"}
              ]
            }
        """.trimIndent()

        val result = FynxCloudflareSfuClient.parseOperation(raw)

        assertTrue(result.requiresImmediateRenegotiation)
        assertEquals("offer", result.sessionDescriptionType)
        assertEquals("v=0\\r\\na=mid:0", result.sessionDescriptionSdp)
        assertEquals(listOf(FynxCloudflareSfuClient.Track("3", "camera"), FynxCloudflareSfuClient.Track("4", "microphone")), result.tracks)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidSdpDescription() {
        FynxCloudflareSfuClient.parseOperation(
            """{"sessionDescription":{"type":"answer","sdp":""}}"""
        )
    }
}
