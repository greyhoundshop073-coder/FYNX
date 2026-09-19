package com.fynx.app.ui

/**
 * Server-supported visibility values for social posts.
 * Kept as a shared model so the live remote social clients and UI do not
 * depend on the removed legacy local Home store.
 */
enum class FynxPostVisibility {
    PUBLIC,
    FRIENDS_ONLY,
    SELECTED_PEOPLE,
    ONLY_ME
}

enum class FynxPostAudience(val label: String) {
    EVERYONE("Everyone"),
    FRIENDS("Friends"),
    SELECTED("Selected people"),
    ONLY_ME("Only me")
}

enum class FynxPostTextBackground(
    val key: String,
    val label: String,
    val color: Long,
    val foregroundColor: Long
) {
    OCEAN("OCEAN", "Ocean", 0xFF1565C0, 0xFFFFFFFF),
    VIOLET("VIOLET", "Violet", 0xFF6A1B9A, 0xFFFFFFFF),
    EMERALD("EMERALD", "Emerald", 0xFF00695C, 0xFFFFFFFF),
    SUNSET("SUNSET", "Sunset", 0xFFE65100, 0xFFFFFFFF),
    CHARCOAL("CHARCOAL", "Charcoal", 0xFF263238, 0xFFFFFFFF)
}
