package com.fynx.app.ui

/**
 * Determines whether a new group peer must receive the local video track.
 *
 * Camera mute is a track-enabled state, not a change to the call's media type.
 * A disabled track must still be attached so a later unmute reaches every peer,
 * including participants who joined while the camera was muted.
 */
internal object FynxGroupCallMediaPolicy {
    fun shouldAttachVideoTrack(isVideoCall: Boolean, hasLocalVideoTrack: Boolean): Boolean =
        isVideoCall && hasLocalVideoTrack
}
