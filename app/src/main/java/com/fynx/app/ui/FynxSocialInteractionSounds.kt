package com.fynx.app.ui

import android.content.Context

/**
 * Compatibility facade for social feedback.
 *
 * All social feedback goes through the shared controller so the master/category
 * switches, silent/vibrate-mode rules, stream-volume checks and event throttling
 * are applied consistently.
 */
object FynxSocialInteractionSounds {
    fun postPublished(context: Context) =
        FynxInteractionSound.play(context, FynxInteractionSound.Event.SOCIAL_INTERACTION)

    fun liked(context: Context) =
        FynxInteractionSound.play(context, FynxInteractionSound.Event.SOCIAL_REACTION)

    fun reaction(context: Context) =
        FynxInteractionSound.play(context, FynxInteractionSound.Event.SOCIAL_REACTION)
}
