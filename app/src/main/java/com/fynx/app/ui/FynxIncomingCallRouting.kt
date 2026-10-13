package com.fynx.app.ui

/** Keeps incoming-call navigation separate from WebRTC/media session ownership. */
object FynxIncomingCallRouting {
    fun shouldRouteInvite(
        signalType: String,
        currentScreen: String,
        hasPendingIncomingCall: Boolean
    ): Boolean =
        signalType.equals("invite", ignoreCase = true) &&
            currentScreen != "Calls" &&
            !hasPendingIncomingCall
}
