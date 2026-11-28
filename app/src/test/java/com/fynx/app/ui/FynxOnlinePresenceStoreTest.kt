package com.fynx.app.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FynxOnlinePresenceStoreTest {
    @Before fun clearPresenceBeforeEachTest() { FynxOnlinePresenceStore.clear() }
    @After fun clearPresenceAfterEachTest() { FynxOnlinePresenceStore.clear() }

    @Test
    fun realtimeOfflineEventNeverInventsAnActivityTimestamp() {
        FynxOnlinePresenceStore.setOnline("seller-101", true)
        assertTrue(FynxOnlinePresenceStore.isOnline("seller-101"))
        FynxOnlinePresenceStore.setOnline("seller-101", false)
        assertFalse(FynxOnlinePresenceStore.isOnline("seller-101"))
        assertNull(FynxOnlinePresenceStore.lastActiveAt("seller-101"))
    }

    @Test
    fun authoritativeServerSnapshotReplacesOnlyCurrentlyVisibleActivity() {
        val visibleTimestamp = 1_800_000_000_000L
        val hiddenTimestamp = 1_800_000_100_000L
        FynxOnlinePresenceStore.replaceLastActiveSnapshot(
            mapOf("seller-visible" to visibleTimestamp, "seller-hidden" to hiddenTimestamp)
        )
        assertEquals(visibleTimestamp, FynxOnlinePresenceStore.lastActiveAt("seller-visible"))
        assertEquals(hiddenTimestamp, FynxOnlinePresenceStore.lastActiveAt("seller-hidden"))

        val refreshedTimestamp = visibleTimestamp - 60_000L
        FynxOnlinePresenceStore.replaceLastActiveSnapshot(mapOf("seller-visible" to refreshedTimestamp))
        assertEquals(refreshedTimestamp, FynxOnlinePresenceStore.lastActiveAt("seller-visible"))
        assertNull(FynxOnlinePresenceStore.lastActiveAt("seller-hidden"))
    }

    @Test
    fun snapshotRemovingAnOnlineAccountDoesNotAssignLocalLastActiveTime() {
        FynxOnlinePresenceStore.setOnline("seller-202", true)
        FynxOnlinePresenceStore.replaceSnapshot(emptySet())
        assertFalse(FynxOnlinePresenceStore.isOnline("seller-202"))
        assertNull(FynxOnlinePresenceStore.lastActiveAt("seller-202"))
    }

    @Test
    fun serverActivitySnapshotPopulatesRealTimestampAfterSellerTransitionsOffline() {
        val serverTimestamp = 1_800_000_000_000L
        FynxOnlinePresenceStore.setOnline("seller-204", true)

        FynxOnlinePresenceStore.replaceSnapshot(emptySet())
        FynxOnlinePresenceStore.replaceLastActiveSnapshot(mapOf("seller-204" to serverTimestamp))

        assertFalse(FynxOnlinePresenceStore.isOnline("seller-204"))
        assertEquals(serverTimestamp, FynxOnlinePresenceStore.lastActiveAt("seller-204"))
    }

    @Test
    fun onlineSnapshotClearsAnyPreviouslyStoredOfflineTimestamp() {
        FynxOnlinePresenceStore.replaceLastActiveSnapshot(mapOf("seller-303" to 1_800_000_000_000L))
        FynxOnlinePresenceStore.replaceSnapshot(setOf("seller-303"))
        assertTrue(FynxOnlinePresenceStore.isOnline("seller-303"))
        assertNull(FynxOnlinePresenceStore.lastActiveAt("seller-303"))
    }
}
