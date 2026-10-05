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
 * Reads group conversations from the same ChatStore used by FynxGroupConversationPanel.
 * This keeps group unread state on the existing message/read source of truth instead of
 * introducing a second notification counter.
 */
@Composable
fun rememberFynxGroupUnreadBadgeCount(context: Context): Int {
    var unreadCount by remember(context) { mutableIntStateOf(0) }

    LaunchedEffect(context) {
        suspend fun refresh() {
            val groups = FynxGroupsStore.load(context)
            val unreadByGroup = groups.associate { group ->
                group.id to FynxChatStore.load(context, "group_${group.id}", null)
                    .count { !it.fromMe && !it.read }
            }
            unreadCount = fynxGroupUnreadCount(unreadByGroup)
        }

        refresh()
        FynxChatStore.previewUpdates.collectLatest {
            refresh()
        }
    }

    return unreadCount
}

fun fynxGroupUnreadCount(unreadByGroup: Map<String, Int>): Int =
    unreadByGroup.values.sumOf { it.coerceAtLeast(0) }
