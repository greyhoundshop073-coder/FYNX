package com.fynx.app.ui

import android.content.Context

/** Compatibility facade: all social feedback now routes through the central FYNX sound policy. */
object FynxSocialInteractionSounds {
    fun postPublished(context: Context) =
        FynxInteractionSound.play(context, FynxInteractionSound.Event.SOCIAL_POST_SUCCESS)

    fun liked(context: Context) =
        FynxInteractionSound.play(context, FynxInteractionSound.Event.SOCIAL_LIKE)

    fun reaction(context: Context) =
        FynxInteractionSound.play(context, FynxInteractionSound.Event.SOCIAL_REACTION)
}
