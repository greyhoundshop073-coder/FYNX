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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Compact FYNX unread badge shared by notification surfaces and bottom navigation. */
fun fynxUnreadBadgeLabel(count: Int): String = when {
    count <= 0 -> ""
    count > 99 -> "99+"
    else -> count.toString()
}

/** Aggregate unread private-chat messages from the existing ChatPreview unread state. */
fun fynxChatUnreadCount(previews: List<ChatPreview>): Int =
    previews.sumOf { it.unreadCount.coerceAtLeast(0) }

@Composable
fun FynxGreenUnreadBadge(
    count: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (count <= 0) return

    val label = fynxUnreadBadgeLabel(count)
    Box(
        modifier = modifier
            .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
            .background(Color(0xFF18B957), CircleShape)
            .padding(horizontal = 5.dp, vertical = 2.dp)
            .semantics {
                this.contentDescription = contentDescription ?: "$label unread"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}