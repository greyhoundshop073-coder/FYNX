package com.fynx.app.ui

/**
 * Delivery is persisted server-side when the recipient socket is connected.
 * This compatibility bridge keeps the existing ConversationPanel callback
 * source-compatible while read receipts are handled by the authenticated API.
 */
object realtimeClient {
    fun acknowledgeMessage(@Suppress("UNUSED_PARAMETER") messageId: String) = Unit
}
