package com.fynx.app.ui

import android.graphics.ColorMatrix

/**
 * Applies the FYNX camera filter values using the Android ColorMatrix API.
 * The explicit name avoids colliding with ColorMatrix.set(FloatArray).
 */
fun ColorMatrix.setFynxFilter(
    saturation: Float,
    brightness: Float,
    contrast: Float,
    alpha: Float
) {
    val safeSaturation = saturation.coerceIn(0f, 4f)
    val safeContrast = contrast.coerceIn(0f, 4f)
    val safeAlpha = alpha.coerceIn(0f, 1f)
    val offset = brightness.coerceIn(-1f, 1f) * 255f

    set(floatArrayOf(
        safeContrast, 0f, 0f, 0f, offset,
        0f, safeContrast, 0f, 0f, offset,
        0f, 0f, safeContrast, 0f, offset,
        0f, 0f, 0f, safeAlpha, 0f
    ))

    val saturationMatrix = ColorMatrix().apply {
        setToSaturation(safeSaturation)
    }
    postConcat(saturationMatrix)
}
