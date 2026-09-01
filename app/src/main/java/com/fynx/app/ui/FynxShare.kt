package com.fynx.app.ui

import android.content.Context
import android.content.Intent

private const val FYNX_SHARE_URL = "https://fynx.app"

fun shareFynx(context: Context) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "Join me on FYNX — one place for your social life, tools and everyday organization. $FYNX_SHARE_URL"
        )
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share FYNX"))
}
