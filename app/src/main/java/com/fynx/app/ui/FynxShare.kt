package com.fynx.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

private const val FYNX_SHARE_URL = "https://fynx.app"
private const val FYNX_SHARE_TEXT =
    "Join me on FYNX — one place for your social life, tools and everyday organization."

data class FynxSharePayload(
    val title: String,
    val message: String,
    val link: String = FYNX_SHARE_URL
) {
    val text: String
        get() = listOf(message, link).filter { it.isNotBlank() }.joinToString("\n\n")
}

object FynxShareActions {
    fun defaultPayload(): FynxSharePayload = FynxSharePayload(
        title = "Join me on FYNX",
        message = FYNX_SHARE_TEXT
    )

    fun invitePayload(username: String): FynxSharePayload = FynxSharePayload(
        title = "Join me on FYNX",
        message = if (username.isBlank()) {
            FYNX_SHARE_TEXT
        } else {
            "$username invited you to join FYNX — one place for your social life, tools and everyday organization."
        }
    )

    fun share(context: Context, payload: FynxSharePayload = defaultPayload()): Boolean {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, payload.title)
            putExtra(Intent.EXTRA_TEXT, payload.text)
        }

        if (sendIntent.resolveActivity(context.packageManager) == null) {
            Toast.makeText(context, "No sharing app is available", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            context.startActivity(Intent.createChooser(sendIntent, payload.title))
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No sharing app is available", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun copy(context: Context, payload: FynxSharePayload = defaultPayload()): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(payload.title, payload.text))
        Toast.makeText(context, "FYNX invite copied", Toast.LENGTH_SHORT).show()
        return true
    }
}

fun shareFynx(context: Context) {
    FynxShareActions.share(context)
}

fun copyFynxInvite(context: Context) {
    FynxShareActions.copy(context)
}
