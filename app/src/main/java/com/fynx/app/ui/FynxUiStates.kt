package com.fynx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shared FYNX empty / loading / error states.
 *
 * SAFE: Adding this file does not change any existing screen.
 * Screens keep their current empty/error UI until you intentionally
 * replace a local empty block with one of these helpers.
 *
 * Uses existing MaterialTheme colours and a Material3 button so it can
 * compile independently of the optional shared-controls package.
 */
@Composable
fun FynxLoadingState(
    message: String = "Loading…",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun FynxEmptyState(
    title: String,
    message: String? = null,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    FynxStatusState(
        title = title,
        message = message,
        icon = icon,
        actionLabel = actionLabel,
        onAction = onAction,
        modifier = modifier
    )
}

@Composable
fun FynxErrorState(
    title: String = "Something went wrong",
    message: String? = "Please try again.",
    actionLabel: String = "Retry",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    FynxStatusState(
        title = title,
        message = message,
        icon = Icons.Outlined.ErrorOutline,
        actionLabel = if (onRetry != null) actionLabel else null,
        onAction = onRetry,
        modifier = modifier
    )
}

@Composable
fun FynxOfflineState(
    title: String = "You're offline",
    message: String? = "Check your connection and try again.",
    actionLabel: String = "Retry",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    FynxStatusState(
        title = title,
        message = message,
        icon = Icons.Outlined.CloudOff,
        actionLabel = if (onRetry != null) actionLabel else null,
        onAction = onRetry,
        modifier = modifier
    )
}

@Composable
private fun FynxStatusState(
    title: String,
    message: String?,
    icon: ImageVector,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (!message.isNullOrBlank ()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(0.6f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
