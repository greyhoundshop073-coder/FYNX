package com.fynx.app.ui

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val FYNX_SHARE_TEXT =
    "Join me on FYNX — one place for your social life, tools and everyday organization."

data class FynxSharePayload(
    val title: String,
    val message: String,
    val link: String = FynxDeepLinkParser.homeWebLink(),
    val marketplaceListingId: String? = null
) {
    val text: String
        get() = listOf(message, link).filter { it.isNotBlank() }.joinToString("\n\n")
}

object FynxShareActions {
    fun defaultPayload(): FynxSharePayload = FynxSharePayload(
        title = "Join me on FYNX",
        message = FYNX_SHARE_TEXT
    )

    fun invitePayload(username: String, code: String? = null): FynxSharePayload = FynxSharePayload(
        title = "Join me on FYNX",
        message = if (username.isBlank()) FYNX_SHARE_TEXT else
            "$username invited you to join FYNX — one place for your social life, tools and everyday organization.",
        link = FynxDeepLinkParser.inviteWebLink(code)
    )

    fun profilePayload(username: String): FynxSharePayload = FynxSharePayload(
        title = "FYNX profile",
        message = "Check out @$username on FYNX.",
        link = FynxDeepLinkParser.profileWebLink(username)
    )

    fun chatPayload(username: String): FynxSharePayload = FynxSharePayload(
        title = "Message on FYNX",
        message = "Open this FYNX conversation with @$username.",
        link = FynxDeepLinkParser.chatWebLink(username)
    )

    fun marketplacePayload(listingId: String? = null, title: String = "FYNX Marketplace"): FynxSharePayload = FynxSharePayload(
        title = title,
        message = "See this on FYNX Marketplace.",
        link = FynxDeepLinkParser.marketplaceWebLink(listingId),
        marketplaceListingId = listingId
    )

    fun statusPayload(status: FynxStatus): FynxSharePayload = FynxSharePayload(
        title = "FYNX Status",
        message = "${status.ownerDisplayName.ifBlank { status.ownerUsername }} shared a ${status.type.name.lowercase()} Status on FYNX. Open FYNX Stories to view active Status updates.",
        link = FynxDeepLinkParser.storiesWebLink()
    )

    fun groupPayload(groupId: String): FynxSharePayload = FynxSharePayload(
        title = "FYNX group",
        message = "Join this FYNX group.",
        link = FynxDeepLinkParser.groupWebLink(groupId)
    )

    fun share(context: Context, payload: FynxSharePayload = defaultPayload()): Boolean {
        val listingId = payload.marketplaceListingId?.trim().orEmpty()
        if (listingId.isNotBlank()) {
            AlertDialog.Builder(context)
                .setTitle(payload.title)
                .setItems(arrayOf("Post to FYNX Home", "Share outside FYNX", "Copy link")) { _, which ->
                    when (which) {
                        0 -> postMarketplaceToHome(context, listingId)
                        1 -> shareExternally(context, payload)
                        2 -> copy(context, payload)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            return true
        }
        return shareExternally(context, payload)
    }

    private fun postMarketplaceToHome(context: Context, listingId: String) {
        Toast.makeText(context, "Posting product to FYNX Home…", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            FynxR6GIntegrationClient.shareListingToHome(context, listingId)
                .onSuccess { Toast.makeText(context, "Product posted to FYNX Home", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, it.message ?: "Could not post product to Home", Toast.LENGTH_LONG).show() }
        }
    }

    private fun shareExternally(context: Context, payload: FynxSharePayload): Boolean {
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
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(payload.title, payload.text))
        Toast.makeText(context, "FYNX invite copied", Toast.LENGTH_SHORT).show()
        return true
    }
}

fun shareFynx(context: Context) { FynxShareActions.share(context) }
fun copyFynxInvite(context: Context) { FynxShareActions.copy(context) }