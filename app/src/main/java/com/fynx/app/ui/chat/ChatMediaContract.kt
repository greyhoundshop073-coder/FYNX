package com.fynx.app.ui.chat

/**
 * Boundary for Chat-specific media presentation.
 *
 * Keep Chat photo/video presentation changes in the chat package instead of
 * extending the shared media loader or the ConversationPanel coordinator.
 * Shared loading, caching, decoding, and playback remain owned by the existing
 * FynxRemoteMedia component.
 */
object ChatMediaContract {
    const val PREVIEW_ASPECT_RATIO = 9f / 16f
    const val VIDEO_NOTE_SIZE_DP = 170
}
