package com.fynx.app.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class FynxHomeProfilePhotoLimiterTest {
    @Test
    fun profileLookupsShareTheFourRequestConcurrencyBudget() = runBlocking {
        val active = AtomicInteger()
        val maximum = AtomicInteger()

        (0 until 16).map {
            async(Dispatchers.Default) {
                FynxHomeProfilePhotoLimiter.withPermit {
                    val current = active.incrementAndGet()
                    maximum.updateAndGet { previous -> maxOf(previous, current) }
                    try {
                        delay(20)
                    } finally {
                        active.decrementAndGet()
                    }
                }
            }
        }.awaitAll()

        assertTrue(maximum.get() <= FynxHomeProfilePhotoLimiter.MAX_CONCURRENT_REQUESTS)
        assertTrue(maximum.get() > 0)
    }
}
