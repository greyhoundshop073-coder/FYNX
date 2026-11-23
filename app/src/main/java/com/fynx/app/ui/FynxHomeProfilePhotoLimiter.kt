package com.fynx.app.ui

import kotlinx.coroutines.sync.Semaphore

/**
 * Shares a small request budget between Home post authors and comment authors.
 * This prevents a long feed/page of replies from starting an unbounded burst of
 * profile lookups while keeping each screen's in-flight identity deduplication.
 */
internal object FynxHomeProfilePhotoLimiter {
    const val MAX_CONCURRENT_REQUESTS = 4

    private val semaphore = Semaphore(permits = MAX_CONCURRENT_REQUESTS)

    suspend fun <T> withPermit(action: suspend () -> T): T {
        semaphore.acquire()
        try {
            return action()
        } finally {
            semaphore.release()
        }
    }
}
