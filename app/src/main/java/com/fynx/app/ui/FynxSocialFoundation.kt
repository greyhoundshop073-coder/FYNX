package com.fynx.app.ui

/**
 * Shared friend relationship status used by the server-backed friend flows.
 * The old social user/story foundation models were removed because they had no
 * production callers.
 */
enum class FynxFriendStatus {
    NONE,
    REQUESTED,
    PENDING,
    OUTGOING_PENDING,
    INCOMING_PENDING,
    FRIENDS,
    DECLINED,
    BLOCKED
}
