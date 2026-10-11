package com.fynx.app.ui

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FynxMarketplaceCartPersistenceTest {
    private lateinit var context: Context

    @Before
    fun clearMarketplacePreferences() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("fynx_marketplace", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun savedCartRoundTripsItemsAndQuantitiesAndIsIsolatedByAccount() {
        val first = listing("listing-1", "NGN", 12500.0)
        val second = listing("listing-2", "NGN", 750.0)

        writeFynxMarketplaceCart(context, "@BuyerOne", listOf(first, second), mapOf(first.id to 2, second.id to 4))

        val restored = readFynxMarketplaceCart(context, "buyerone")
        assertEquals(listOf(first, second), restored.items)
        assertEquals(mapOf(first.id to 2, second.id to 4), restored.quantities)

        val otherAccount = readFynxMarketplaceCart(context, "buyer-two")
        assertTrue("A different account must not inherit the first account's cart", otherAccount.items.isEmpty())
        assertTrue(otherAccount.quantities.isEmpty())
    }

    @Test
    fun savedCartIsBoundedToTwentyDistinctListings() {
        val listings = (1..21).map { listing("listing-$it", "USD", it * 1.0) }
        val quantities = listings.associate { it.id to 1 }

        writeFynxMarketplaceCart(context, "buyer", listings, quantities)

        val restored = readFynxMarketplaceCart(context, "buyer")
        assertEquals(20, restored.items.size)
        assertEquals(20, restored.items.map { it.id }.distinct().size)
        assertEquals(20, restored.quantities.size)
        assertEquals((1..20).map { "listing-$it" }, restored.items.map { it.id })
    }

    private fun listing(id: String, currency: String, price: Double) =
        FynxRemoteSocialClient.MarketplaceListing(
            id = id,
            sellerId = "seller-id",
            sellerUsername = "seller",
            sellerDisplayName = "Seller",
            storeName = "Seller Store",
            title = "Test product $id",
            description = "Persistence test listing",
            price = price,
            currency = currency,
            category = "Electronics",
            condition = "New",
            quantity = 50,
            location = "Port Harcourt",
            deliveryAvailable = true,
            pickupAvailable = true,
            deliveryFee = 500.0,
            mediaIds = listOf("media-$id"),
            active = true
        )
}
