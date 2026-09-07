package com.fynx.app.ui

import android.net.Uri

sealed interface FynxDeepLinkDestination {
    data class Invite(val code: String?) : FynxDeepLinkDestination
    data object Home : FynxDeepLinkDestination
}

object FynxDeepLinkParser {
    private const val FYNX_HOST = "fynx.app"
    private const val INVITE_PATH = "/invite"
    private const val HOME_PATH = "/home"

    fun parse(uri: Uri?): FynxDeepLinkDestination? {
        if (uri == null) return null

        val isFynxScheme = uri.scheme.equals("fynx", ignoreCase = true)
        val isFynxWeb = uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(FYNX_HOST, ignoreCase = true)
        if (!isFynxScheme && !isFynxWeb) return null

        val normalizedPath = uri.path.orEmpty().trimEnd('/').ifBlank { "/" }
        val isHome = normalizedPath == "/" || normalizedPath.equals(HOME_PATH, ignoreCase = true) ||
            (isFynxScheme && uri.host.equals("home", ignoreCase = true))
        if (isHome) return FynxDeepLinkDestination.Home

        val isInvite = if (isFynxScheme) {
            normalizedPath.equals(INVITE_PATH, ignoreCase = true) ||
                uri.host.equals("invite", ignoreCase = true)
        } else {
            normalizedPath.equals(INVITE_PATH, ignoreCase = true) ||
                normalizedPath.startsWith("$INVITE_PATH/", ignoreCase = true)
        }
        if (!isInvite) return null

        val pathCode = normalizedPath.removePrefix(INVITE_PATH).trim('/').takeIf { it.isNotBlank() }
        val queryCode = uri.getQueryParameter("code")?.trim()?.takeIf { it.isNotBlank() }
        return FynxDeepLinkDestination.Invite(queryCode ?: pathCode)
    }
}
