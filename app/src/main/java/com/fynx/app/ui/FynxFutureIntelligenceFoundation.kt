package com.fynx.app.ui

/**
 * FYNX Future Intelligence Layer foundation.
 *
 * This layer deliberately sits above the existing social, messaging,
 * marketplace, media and account features. It describes what AI is allowed
 * to request without giving the AI direct access to private data.
 */
enum class FynxAiCapability {
    ASSISTANT,
    SMART_SEARCH,
    TRANSLATION,
    RECOMMENDATIONS,
    MARKETPLACE_ASSIST,
    MEDIA_ASSIST,
    PERSONAL_CONTEXT
}

enum class FynxAiDataScope {
    NONE,
    PUBLIC_CONTENT,
    MY_PROFILE,
    MY_SOCIAL_ACTIVITY,
    MY_MESSAGES,
    MY_MARKETPLACE,
    MY_MEDIA,
    MY_CALENDAR
}

data class FynxAiPermission(
    val capability: FynxAiCapability,
    val allowedScopes: Set<FynxAiDataScope> = emptySet(),
    val enabled: Boolean = false
)

data class FynxAiRequest(
    val capability: FynxAiCapability,
    val prompt: String,
    val requestedScopes: Set<FynxAiDataScope> = emptySet(),
    val requiresConfirmation: Boolean = false
)

data class FynxAiDecision(
    val allowed: Boolean,
    val scopes: Set<FynxAiDataScope> = emptySet(),
    val reason: String = ""
)

object FynxFutureIntelligencePolicy {
    private const val MAX_PROMPT_LENGTH = 8_000

    fun authorize(
        permissions: List<FynxAiPermission>,
        request: FynxAiRequest
    ): FynxAiDecision {
        val prompt = request.prompt.trim()
        if (prompt.isEmpty()) return FynxAiDecision(false, reason = "empty prompt")
        if (prompt.length > MAX_PROMPT_LENGTH) return FynxAiDecision(false, reason = "prompt too large")

        val permission = permissions.firstOrNull { it.capability == request.capability }
            ?: return FynxAiDecision(false, reason = "capability not granted")
        if (!permission.enabled) return FynxAiDecision(false, reason = "capability disabled")

        val denied = request.requestedScopes - permission.allowedScopes
        if (denied.isNotEmpty()) return FynxAiDecision(false, reason = "requested data is not permitted")

        return FynxAiDecision(true, request.requestedScopes)
    }
}

/**
 * Event vocabulary for the future intelligence/event pipeline.
 * Events contain identifiers and metadata only; sensitive content must stay
 * behind explicit authorization checks.
 */nenum class FynxActivityEventType {
    MESSAGE_SENT,
    POST_CREATED,
    POST_LIKED,
    STATUS_CREATED,
    STATUS_VIEWED,
    STATUS_REPLIED,
    FRIEND_REQUESTED,
    FRIEND_ACCEPTED,
    FOLLOWED,
    LISTING_CREATED,
    MEDIA_UPLOADED,
    GROUP_JOINED,
    CALENDAR_EVENT_CREATED
}

data class FynxActivityEvent(
    val type: FynxActivityEventType,
    val actorUserId: String,
    val targetId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)
