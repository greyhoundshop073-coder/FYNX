package com.fynx.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * Marketplace-specific seller avatar binding.
 *
 * Marketplace's listing/profile requests already provide a server-authoritative media ID.
 * Prefer that ID when there is no cached identity yet, but follow the shared avatar revision
 * stream so a seller photo change/removal also refreshes already-visible Marketplace cards.
 */
@Composable
internal fun FynxMarketplaceSellerAvatar(
    fetchedMediaId: String?,
    ownerUsername: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val normalizedOwner = ownerUsername.trim().removePrefix("@").trim().lowercase()
    val avatarRevisions by FynxAvatarIdentityStore.revisions().collectAsState()
    val revision = avatarRevisions[normalizedOwner]
    val hasCachedEntry = normalizedOwner.isNotBlank() &&
        FynxProfileRemoteClient.hasCachedProfilePhotoEntry(context, normalizedOwner)
    val cachedMediaId = normalizedOwner.takeIf { it.isNotBlank() }?.let {
        FynxProfileRemoteClient.cachedProfilePhotoId(context, it)
    }
    val resolvedMediaId = if (hasCachedEntry) cachedMediaId else fetchedMediaId?.takeIf { it.isNotBlank() }

    // revision is intentionally read in composition: publication forces this card/store to
    // re-read the account-scoped cache, including authoritative null/removal updates.
    @Suppress("UNUSED_VARIABLE")
    val avatarRevision = revision
    FynxRemoteProfileAvatar(
        mediaId = resolvedMediaId,
        contentDescription = contentDescription,
        modifier = modifier
    )
}
