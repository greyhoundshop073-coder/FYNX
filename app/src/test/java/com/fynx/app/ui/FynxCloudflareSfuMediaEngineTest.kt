package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FynxCloudflareSfuMediaEngineTest {
    @Test
    fun extractsAudioAndVideoMidsFromUnifiedPlanSdp() {
        val sdp = """
            v=0
            o=- 1 2 IN IP4 127.0.0.1
            s=-
            t=0 0
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            a=mid:0
            a=sendrecv
            m=application 9 UDP/DTLS/SCTP webrtc-datachannel
            a=mid:data
            m=video 9 UDP/TLS/RTP/SAVPF 96
            a=mid:1
            a=sendrecv
        """.trimIndent()

        assertEquals(
            mapOf("microphone" to "0", "camera" to "1"),
            parseFynxSfuMediaMids(sdp)
        )
    }

    @Test
    fun ignoresMissingOrUnsafeMediaIdentifiers() {
        val sdp = """
            v=0
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            a=mid:invalid mid
            m=video 9 UDP/TLS/RTP/SAVPF 96
        """.trimIndent()

        assertEquals(emptyMap<String, String>(), parseFynxSfuMediaMids(sdp))
    }
}
