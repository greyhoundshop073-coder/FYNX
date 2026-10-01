package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FynxVerifiedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(18.dp),
        shape = CircleShape,
        color = Color(0xFF168BFF),
        contentColor = Color.White
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("✓", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FynxAvatar(name: String, modifier: Modifier = Modifier, ownerUsername: String? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val normalizedOwner = ownerUsername?.removePrefix("@")?.trim()?.takeIf { it.isNotBlank() }
    var resolvedMediaId by remember(normalizedOwner) {
        mutableStateOf(normalizedOwner?.let { FynxProfileRemoteClient.cachedProfilePhotoId(context, it) })
    }

    LaunchedEffect(normalizedOwner) {
        if (normalizedOwner != null) {
            FynxProfileRemoteClient.get(context, normalizedOwner)
                .onSuccess { profile -> resolvedMediaId = profile.profilePhotoMediaId }
        } else {
            resolvedMediaId = null
        }
    }

    if (normalizedOwner != null) {
        FynxRemoteProfileAvatar(
            mediaId = resolvedMediaId,
            contentDescription = name,
            modifier = modifier,
            ownerUsername = normalizedOwner
        )
    } else {
        FynxAvatarContent(name, null, modifier)
    }
}

@Composable
fun FynxAvatar(name: String, avatarUri: String?, modifier: Modifier = Modifier, ownerUsername: String? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val normalizedOwner = ownerUsername?.removePrefix("@")?.trim()?.takeIf { it.isNotBlank() }
    var resolvedMediaId by remember(normalizedOwner, avatarUri) {
        mutableStateOf(
            normalizedOwner?.let { FynxProfileRemoteClient.cachedProfilePhotoId(context, it) }
                ?: avatarUri?.substringAfterLast("/api/media/")?.takeIf { it != avatarUri }
        )
    }

    LaunchedEffect(normalizedOwner) {
        if (normalizedOwner != null) {
            FynxProfileRemoteClient.get(context, normalizedOwner)
                .onSuccess { profile -> resolvedMediaId = profile.profilePhotoMediaId }
        }
    }

    if (normalizedOwner != null) {
        FynxRemoteProfileAvatar(
            mediaId = resolvedMediaId,
            contentDescription = name,
            modifier = modifier,
            ownerUsername = normalizedOwner
        )
    } else {
        FynxAvatarContent(name, avatarUri, modifier)
    }
}

@Composable
private fun FynxAvatarContent(name: String, avatarUri: String?, modifier: Modifier) {
    val initials = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }

    Box(
        modifier = modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUri.isNullOrBlank()) {
            FynxRemoteMedia(
                mediaUrl = avatarUri,
                type = "image",
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Text(
                text = initials.ifBlank { "F" },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
