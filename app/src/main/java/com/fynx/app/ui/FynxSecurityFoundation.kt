package com.fynx.app.ui

/**
 * Security rules shared by the future FYNX intelligence layer.
 * The Android client is never the final authority: these rules are mirrored
 * server-side before sensitive data is returned to an AI capability.
 */
object FynxSecurityFoundation {
    const val MAX_AI_PROMPT_LENGTH = 8_000
    const val MAX_SEARCH_QUERY_LENGTH = 120
    const val MAX_AI_CONTEXT_ITEMS = 25

    fun sanitizeSearchQuery(value: String): String =
        value.trim().replace(Regex("[\\u0000-\\u001F]"), "").take(MAX_SEARCH_QUERY_LENGTH)

    fun canUseSensitiveScope(
        scope: FynxAiDataScope,
        explicitUserPermission: Boolean,
        requiresConfirmation: Boolean
    ): Boolean {
        if (scope == FynxAiDataScope.NONE || scope == FynxAiDataScope.PUBLIC_CONTENT) return true
        return explicitUserPermission && (!requiresConfirmation || explicitUserPermission)
    }

    fun allowedContextSize(items: Int): Int = items.coerceIn(0, MAX_AI_CONTEXT_ITEMS)
}

data class FynxSecurityAuditEvent(
    val action: String,
    val actorUserId: String,
    val allowed: Boolean,
    val reason: String,
    val timestampMillis: Long = System.currentTimeMillis()
)
