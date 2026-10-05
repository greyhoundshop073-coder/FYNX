package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * FYNX unread badge used for in-app notification surfaces and bottom navigation.
 *
 * The count is deliberately capped at 99+ so navigation remains compact and
 * predictable on small screens. Zero is represented by an absent badge at the
 * call site rather than rendering an empty shape.
 */
fun fynxUnreadBadgeLabel(count: Int): String = when {
    count <= 0 -> ""
    count > 99 -> "99+"
    else -> count.toString()
}

/** Aggregate unread private-chat messages for the Chat bottom-navigation badge. */
fun fynxChatUnreadCount(previews: List<ChatPreview>): Int =
    previews.sumOf { it.unreadCount.coerceAtLeast(0) }

@Composable
fun FynxGreenUnreadBadge(
    count: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (count <= 0) return

    val green = Color(0xFF18B957)
    Box(
        modifier = modifier
            .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
            .background(green, CircleShape)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = fynxUnreadBadgeLabel(count),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
