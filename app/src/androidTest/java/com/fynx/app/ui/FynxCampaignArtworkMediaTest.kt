package com.fynx.app.ui

import android.graphics.Bitmap
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

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
    fun staticPhotoCanBeCopiedAndPreviewedWithoutNetworkAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("fynx-campaign-photo-", ".png", context.cacheDir)
        var storedArtwork: File? = null
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        try {
            FileOutputStream(source).use { output ->
                assertTrue("The local photo fixture must encode successfully", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            // This exercises the same app-private copy path used by Save offline draft.
            // No backend client, media upload or network transport is involved.
            val storedUri = FynxMarketplaceLocalCampaignStore.importArtwork(context, Uri.fromFile(source)).getOrThrow()
            storedArtwork = File(Uri.parse(storedUri).path.orEmpty())
            assertTrue("The private photo copy must exist", storedArtwork!!.isFile)
            val info = FynxCampaignArtworkMedia.inspectFile(storedArtwork!!)
            assertEquals("image/png", info.mimeType)
            assertEquals(320, info.width)
            assertEquals(180, info.height)

            val preview = FynxCampaignArtworkMedia.decodePreview(context, Uri.fromFile(storedArtwork!!))
            assertTrue("A static photo preview must remain a bitmap drawable", preview is BitmapDrawable)
            assertEquals(320, preview.intrinsicWidth)
            assertEquals(180, preview.intrinsicHeight)
        } finally {
            bitmap.recycle()
            storedArtwork?.delete()
            source.delete()
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
