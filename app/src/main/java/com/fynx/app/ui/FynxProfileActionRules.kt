package com.fynx.app.ui

import java.util.Locale

/** Normalizes FYNX usernames for identity comparisons without changing stored usernames. */
internal fun normalizeFynxProfileUsername(username: String?): String? =
    username
        ?.trim()
        ?.removePrefix("@")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.lowercase(Locale.ROOT)

/** Returns true only when both usernames identify the same non-empty account. */
internal fun isOwnFynxProfile(profileUsername: String, currentUsername: String?): Boolean {
    val profile = normalizeFynxProfileUsername(profileUsername)
    val current = normalizeFynxProfileUsername(currentUsername)
    return profile != null && current != null && profile == current
}

/** Report reasons must contain visible, non-whitespace text before a report is sent. */
internal fun validateProfileReportReason(reason: String): String? =
    if (reason.isBlank()) "Enter a reason before submitting this report." else null
