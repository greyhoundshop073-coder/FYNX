package com.fynx.app.ui

import android.graphics.ColorMatrix

/**
 * Applies the FYNX camera filter values using only the ColorMatrix APIs
 * available to the project's Android/Kotlin toolchain.
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

    // Equivalent to ColorMatrix.setToSaturation(), expressed directly so
    // compilation does not depend on that helper being exposed by the
    // project's Android SDK stubs.
    val inverseSaturation = 1f - safeSaturation
    val red = 0.213f * inverseSaturation
    val green = 0.715f * inverseSaturation
    val blue = 0.072f * inverseSaturation

    val saturationMatrix = ColorMatrix(floatArrayOf(
        red + safeSaturation, green, blue, 0f, 0f,
        red, green + safeSaturation, blue, 0f, 0f,
        red, green, blue + safeSaturation, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))
    postConcat(saturationMatrix)
}
