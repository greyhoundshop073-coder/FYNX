package com.fynx.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.collectLatest

/**
 * Reads the existing ChatStore message/read state and exposes the aggregate
 * unread private-chat count for the Chat bottom-navigation badge.
 *
 * No second unread counter is introduced: the same message/read flags used by
 * ChatsPanel remain the source of truth, while preview updates invalidate the
 * aggregate so the badge follows realtime/read changes without polling.
 */
@Composable
fun rememberFynxChatUnreadBadgeCount(context: Context): Int {
    var unreadCount by remember(context) { mutableIntStateOf(0) }

    LaunchedEffect(context) {
        suspend fun refresh() {
            val previews = FynxChatStore.loadPreviews(context)
            val refreshed = previews.map { preview ->
                val unread = FynxChatStore.load(context, preview.username)
                    .count { !it.fromMe && !it.read }
                preview.copy(unreadCount = unread)
            }
            unreadCount = fynxChatUnreadCount(refreshed)
        }

        refresh()
        FynxChatStore.previewUpdates.collectLatest {
            refresh()
        }
    }

    return unreadCount
}