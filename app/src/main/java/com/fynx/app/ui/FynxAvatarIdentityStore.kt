package com.fynx.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local avatar invalidation stream. The server remains authoritative;
 * this store only tells already-visible Compose surfaces that a cached avatar
 * identity changed so they can re-read the authoritative cache immediately.
 */
object FynxAvatarIdentityStore {
    private val revisions = MutableStateFlow<Map<String, Long>>(emptyMap())

    fun revisions(): StateFlow<Map<String, Long>> = revisions.asStateFlow()

    fun publish(username: String, mediaId: String?) {
        val normalized = username.trim().removePrefix("@").trim().lowercase()
        if (normalized.isBlank()) return
        revisions.value = revisions.value.toMutableMap().apply {
            put(normalized, System.nanoTime())
        }
    }
}
