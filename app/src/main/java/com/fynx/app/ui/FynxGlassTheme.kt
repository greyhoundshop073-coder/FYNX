package com.fynx.app.ui

import androidx.compose.ui.graphics.Color

enum class FynxGlassThemeId(val label: String) {
    PURE_BLACK("Pure Black Glass"),
    AURORA("Aurora Glass"),
    LIGHT("Light Glass"),
    EMERALD("Deep Emerald Glass")
}

data class FynxGlassThemePalette(
    val id: FynxGlassThemeId,
    val background: Color,
    val backgroundMid: Color,
    val backgroundGlow: Color,
    val doodlePrimary: Color,
    val doodleSecondary: Color,
    val doodleHighlight: Color,
    val incomingGlass: Color,
    val outgoingStart: Color,
    val outgoingEnd: Color,
    val bubbleRim: Color,
    val messageText: Color,
    val messageMuted: Color
)

fun fynxGlassPalette(id: FynxGlassThemeId): FynxGlassThemePalette = when (id) {
    FynxGlassThemeId.PURE_BLACK -> FynxGlassThemePalette(
        id, Color(0xFF000000), Color(0xFF080B18), Color(0xFF182B52),
        Color(0xFF6F8CFF), Color(0xFF8B6DFF), Color(0xFF45D9FF),
        Color(0xFF17202B), Color(0xFF163B70), Color(0xFF176A7D), Color(0xFF67DFFF),
        Color(0xFFF7F9FF), Color(0xFFCBD4E6)
    )
    FynxGlassThemeId.AURORA -> FynxGlassThemePalette(
        id, Color(0xFF07101F), Color(0xFF101A3B), Color(0xFF153F55),
        Color(0xFF58D9E8), Color(0xFF8D7CFF), Color(0xFFB6F4FF),
        Color(0xFF1B2A3D), Color(0xFF244A82), Color(0xFF176C72), Color(0xFF7EEBFF),
        Color(0xFFF7FBFF), Color(0xFFC7D5E7)
    )
    FynxGlassThemeId.LIGHT -> FynxGlassThemePalette(
        id, Color(0xFFF5F8FB), Color(0xFFEAF1F7), Color(0xFFDDECF0),
        Color(0xFF6D94AE), Color(0xFF8B80B8), Color(0xFF66BFAF),
        Color(0xFFE8F0F4), Color(0xFFDDEAF5), Color(0xFFDDEFEA), Color(0xFF79B6C7),
        Color(0xFF10212B), Color(0xFF58717E)
    )
    FynxGlassThemeId.EMERALD -> FynxGlassThemePalette(
        id, Color(0xFF020B08), Color(0xFF063D32), Color(0xFF087A62),
        Color(0xFF22C7A5), Color(0xFF54E2C4), Color(0xFF9BFFE9),
        Color(0xFF10231F), Color(0xFF0A5D4B), Color(0xFF119D84), Color(0xFF76F5D8),
        Color(0xFFF4FFFB), Color(0xFFB8D8CE)
    )
}
