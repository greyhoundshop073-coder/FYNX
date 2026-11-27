package com.fynx.app.ui

import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FynxCampaignArtworkMediaTest {
    @Test
    fun twoFrameGifIsValidatedAndDecodedWithoutNetworkAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gifBytes = Base64.decode(
            "R0lGODlhAgACAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQICgAAACwAAAAAAgACAAAIBgABCAQQEAAh+QQIDwAAACwAAAAAAgACAIEAAP8AAAAAAAAAAAAIBgABCAQQEAA7",
            Base64.DEFAULT
        )
        val file = File.createTempFile("fynx-campaign-", ".gif", context.cacheDir)
        try {
            file.writeBytes(gifBytes)

            val info = FynxCampaignArtworkMedia.inspectFile(file)
            assertEquals("image/gif", info.mimeType)
            assertEquals(2, info.frameCount)
            assertEquals(2, info.width)
            assertEquals(2, info.height)

            val drawable = FynxCampaignArtworkMedia.decodeGifFile(file)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                assertTrue("Android 9+ must retain an animated drawable", drawable is AnimatedImageDrawable)
            } else {
                assertTrue("Android 8 must use the Movie fallback", drawable is FynxLegacyGifDrawable)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun invalidGifSignatureIsRejectedBeforeDecoding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("fynx-campaign-invalid-", ".gif", context.cacheDir)
        try {
            file.writeText("not a GIF")
            val result = runCatching { FynxCampaignArtworkMedia.inspectFile(file) }
            assertTrue("A forged .gif extension must not bypass signature validation", result.isFailure)
        } finally {
            file.delete()
        }
    }
}
