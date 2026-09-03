package com.fynx.app.ui

import android.graphics.ColorMatrix

/**
 * Compatibility helper for the camera photo filters.
 * Android ColorMatrix exposes separate operations for saturation and for
 * brightness/contrast; this combines them behind the four-value call used by
 * the existing camera UI.
 */
fun ColorMatrix.set(saturation: Float, brightness: Float, contrast: Float, alpha: Float) {
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

    val saturationMatrix = ColorMatrix().apply { setToSaturation(safeSaturation) }
    postConcat(saturationMatrix)
}
