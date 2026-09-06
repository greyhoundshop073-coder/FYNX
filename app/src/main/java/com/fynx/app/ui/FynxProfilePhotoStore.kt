package com.fynx.app.ui

import android.content.Context
import android.net.Uri

object FynxProfilePhotoStore {
    suspend fun uploadAndSave(context: Context, uri: Uri): Result<String> =
        FynxProfileRemoteClient.uploadProfilePhoto(context, uri).mapCatching { id ->
            val profile = FynxPreferencesStore.loadProfile(context, FynxAuthStore.load(context).username.orEmpty())
            FynxProfileRemoteClient.update(
                context,
                displayName = profile.displayName,
                username = profile.username,
                bio = profile.bio,
                profilePhotoMediaId = id
            ).getOrThrow()
            FynxPreferencesStore.saveProfilePhoto(context, uri.toString())
            id
        }
}
