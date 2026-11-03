package com.fynx.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FynxMarketplaceFeedCacheTest {
    private lateinit var base: Context

    @Before fun clearCache() {
        base = InstrumentationRegistry.getInstrumentation().targetContext
        base.getSharedPreferences("fynx_feed_cache", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun accountContext(username: String): Context = object : ContextWrapper(base) {
        private val authPrefs: SharedPreferences =
            base.getSharedPreferences("marketplace_cache_test_auth_$username", Context.MODE_PRIVATE).apply {
                edit().putString("username", username).commit()
            }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            if (name == "fynx_auth") authPrefs else super.getSharedPreferences(name, mode)
    }

    @Test fun cachedFirstPageRoundTripsAndIsScopedToSearchAndCategory() {
        val context = accountContext("buyer-one")
        val listing = listing("listing-1")
        FynxMarketplaceFeedCache.write(context, "", "All", "", listOf(listing), true)
        val restored = FynxMarketplaceFeedCache.read(context, "", "All", "")
        assertNotNull(restored)
        assertEquals(listOf(listing), restored!!.listings)
        assertTrue(restored.hasMore)
        assertNull(FynxMarketplaceFeedCache.read(context, "phone", "All", ""))
        assertNull(FynxMarketplaceFeedCache.read(context, "", "Electronics", ""))
    }

    @Test fun cachedProductsCannotLeakAcrossAccounts() {
        val first = accountContext("buyer-one")
        val second = accountContext("buyer-two")
        FynxMarketplaceFeedCache.write(first, "", "All", "", listOf(listing("private-scope")), false)
        assertNotNull(FynxMarketplaceFeedCache.read(first, "", "All", ""))
        assertNull(FynxMarketplaceFeedCache.read(second, "", "All", ""))
    }

    @Test fun inactiveListingsAreNotRestoredFromTheStaleFeed() {
        val context = accountContext("buyer-one")
        FynxMarketplaceFeedCache.write(
            context, "", "All", "", listOf(listing("inactive-listing").copy(active = false)), false
        )
        assertNull(FynxMarketplaceFeedCache.read(context, "", "All", ""))
    }

    @Test fun cachedListingsWithNoAvailableStockAreNotRestored() {
        val context = accountContext("buyer-one")
        FynxMarketplaceFeedCache.write(
            context, "", "All", "", listOf(listing("out-of-stock").copy(quantity = 0)), false
        )
        assertNull(FynxMarketplaceFeedCache.read(context, "", "All", ""))
    }

    @Test fun promotionResponseCacheIsAlsoAccountScoped() {
        val first = accountContext("buyer-one")
        val second = accountContext("buyer-two")
        FynxMarketplaceFeedCache.writeResponse(first, "seasonal_promotions", """{"promotions":[]}""")
        assertEquals("""{"promotions":[]}""", FynxMarketplaceFeedCache.readResponse(first, "seasonal_promotions", 60_000L))
        assertNull(FynxMarketplaceFeedCache.readResponse(second, "seasonal_promotions", 60_000L))
    }

    private fun listing(id: String) = FynxRemoteSocialClient.MarketplaceListing(
        id=id, sellerId="12", sellerUsername="seller", sellerDisplayName="Seller",
        storeName="Seller Store", title="Test product", description="Cached Marketplace product",
        price=12500.0, currency="NGN", category="Electronics", condition="NEW",
        quantity=4, location="Abuja", deliveryAvailable=true, pickupAvailable=true,
        deliveryFee=500.0, mediaIds=listOf("media-1"), active=true, mediaTypes=listOf("image")
    )
}
