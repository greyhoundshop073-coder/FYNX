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

/** Returns true only when social/relationship actions for another profile should be rendered. */
internal fun shouldShowOtherProfileSocialActions(profileUsername: String, currentUsername: String?): Boolean =
    !isOwnFynxProfile(profileUsername, currentUsername)

/** Returns true when a call target refers to the currently signed-in FYNX account. */
internal fun isSelfCallTarget(targetUsername: String?, currentUsername: String?): Boolean {
    val target = normalizeFynxProfileUsername(targetUsername)
    val current = normalizeFynxProfileUsername(currentUsername)
    return target != null && current != null && target == current
}

/** Returns true when a call target is valid and does not target the current signed-in account. */
internal fun canCallTarget(targetUsername: String?, currentUsername: String?): Boolean {
    val target = normalizeFynxProfileUsername(targetUsername) ?: return false
    return !isSelfCallTarget(target, currentUsername)
}

/** Returns true when an incoming navigation outgoing-call request should be consumed once. */
internal fun shouldConsumeOutgoingCallTrigger(initialOutgoing: Boolean, initialName: String?): Boolean =
    initialOutgoing && !initialName.isNullOrBlank()

/** Returns the backend route that authorizes and serves uploaded profile avatars. */
internal fun resolveProfilePhotoMediaPath(mediaId: String?): String? =
    mediaId?.trim()?.takeIf { it.isNotEmpty() }?.let { "/api/media/$it" }

/** Maps privacy Status visibility labels to canonical FynxStatusAudience enum names. */
internal fun privacyStatusVisibilityToAudienceName(privacyVisibility: String?): String =
    when (privacyVisibility?.trim()?.lowercase(Locale.ROOT)) {
        "my friends", "friends", "friends_only" -> "FRIENDS"
        "nobody", "only me", "only_me", "private" -> "PRIVATE"
        else -> "EVERYONE"
    }

/** Resolves effective Status composer audience, honoring Privacy Settings when unset. */
internal fun effectiveStatusAudienceName(explicitAudience: String?, privacyVisibility: String?): String {
    val explicit = explicitAudience?.trim()?.uppercase(Locale.ROOT)
    return when (explicit) {
        "EVERYONE", "FRIENDS", "PRIVATE" -> explicit
        else -> privacyStatusVisibilityToAudienceName(privacyVisibility)
    }
}

/** Computes whether Edit Profile currently has a visible photo (local override or non-removed remote photo). */
internal fun hasEffectiveEditProfilePhoto(
    localPhotoUri: String?,
    remotePhotoId: String?,
    photoRemoved: Boolean
): Boolean = !localPhotoUri.isNullOrBlank() || (!photoRemoved && !remotePhotoId.isNullOrBlank())
