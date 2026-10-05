package com.fynx.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * FYNX navigation wrapper. The existing FynxApp uses Material3's wildcard import,
 * so this same-package NavigationBar keeps the existing call site intact while
 * adding the Chat unread badge without replacing the navigation implementation.
 */
@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationBarDefaults.containerColor,
    contentColor: Color = NavigationBarDefaults.contentColor,
    tonalElevation: androidx.compose.ui.unit.Dp = NavigationBarDefaults.Elevation,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable RowScope.() -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val privateUnread = rememberFynxChatUnreadBadgeCount(context)
    val groupUnread = rememberFynxGroupUnreadBadgeCount(context)
    val unread = (privateUnread + groupUnread).coerceAtLeast(0)

    BoxWithConstraints(modifier = modifier) {
        androidx.compose.material3.NavigationBar(
            modifier = Modifier.fillMaxSize(),
            containerColor = containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            windowInsets = windowInsets,
            content = content,
        )
        if (unread > 0) {
            // Five equal navigation slots place Chat at the second slot (30% of
            // the width). The badge scales with the actual navigation surface.
            FynxGreenUnreadBadge(
                count = unread,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = maxWidth * -0.20f, y = 7.dp),
                contentDescription = "$unread unread chats",
            )
        }
    }
}
