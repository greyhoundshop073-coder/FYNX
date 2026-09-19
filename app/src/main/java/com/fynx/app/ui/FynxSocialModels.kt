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
