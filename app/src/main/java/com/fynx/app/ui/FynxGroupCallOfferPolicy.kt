package com.fynx.app.ui

/**
 * Elects exactly one SDP offerer for every peer-to-peer group-call edge.
 *
 * User IDs are opaque strings from the authoritative server roster. A stable
 * lexical ordering is sufficient: both ends independently make the same choice.
 */
internal object FynxGroupCallOfferPolicy {
    fun shouldCreateOffer(localUserId: String?, remoteUserId: String): Boolean {
        val local = localUserId?.trim().orEmpty()
        val remote = remoteUserId.trim()
        return local.isNotEmpty() && remote.isNotEmpty() && local != remote && local < remote
    }
}
