package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxMarketplaceCarouselSupportTest {
    private fun product(
        id: String,
        seller: String = "seller-$id",
        title: String = "Product $id",
        price: Double = 100.0,
        currency: String = "NGN",
        quantity: Int = 4,
        active: Boolean = true
    ) = MarketplaceAdvertisingCandidate(
        id = id,
        sellerUsername = seller,
        title = title,
        price = price,
        currency = currency,
        quantity = quantity,
        active = active
    )

    @Test
    fun emptyAndSingleProductSetsDoNotScheduleRotation() {
        assertTrue(selectMarketplaceAdvertisingCandidates(emptyList()).isEmpty())
        assertEquals(null, nextMarketplaceAdvertisingDisplayIndex(0, 0))
        assertEquals(null, nextMarketplaceAdvertisingDisplayIndex(0, 1))
        assertEquals(listOf("one"), selectMarketplaceAdvertisingCandidates(listOf(product("one"))).map { it.id })
    }

    @Test
    fun twoThreeAndManyProductsHavePredictableForwardAndWrapTargets() {
        assertEquals(1, nextMarketplaceAdvertisingDisplayIndex(0, 2))
        assertEquals(2, nextMarketplaceAdvertisingDisplayIndex(1, 2))
        assertEquals(0, nextMarketplaceAdvertisingDisplayIndex(2, 2))

        assertEquals(1, nextMarketplaceAdvertisingDisplayIndex(0, 3))
        assertEquals(2, nextMarketplaceAdvertisingDisplayIndex(1, 3))
        assertEquals(3, nextMarketplaceAdvertisingDisplayIndex(2, 3))
        assertEquals(0, nextMarketplaceAdvertisingDisplayIndex(3, 3))

        assertEquals(1, nextMarketplaceAdvertisingDisplayIndex(0, 12))
        assertEquals(12, nextMarketplaceAdvertisingDisplayIndex(11, 12))
        assertEquals(0, nextMarketplaceAdvertisingDisplayIndex(12, 12))
    }

    @Test
    fun selectionFiltersInactiveUnavailableMalformedAndDuplicateListings() {
        val selected = selectMarketplaceAdvertisingCandidates(
            listOf(
                product("duplicate", title = "First copy"),
                product("duplicate", title = "Duplicate copy", price = 1.0),
                product("inactive", active = false),
                product("empty-stock", quantity = 0),
                product("negative-price", price = -1.0),
                product("infinite-price", price = Double.POSITIVE_INFINITY),
                product(id = " "),
                product("blank-title", title = " "),
                product("blank-currency", currency = " "),
                product("blank-seller", seller = " ")
            )
        )
        assertEquals(listOf("duplicate"), selected.map { it.id })
        assertEquals("First copy", selected.single().title)
    }

    @Test
    fun selectionUsesOneDominantCurrencyWithStableTieBreaking() {
        val selected = selectMarketplaceAdvertisingCandidates(
            listOf(
                product("usd-a", currency = "USD"),
                product("ngn-a", currency = "NGN"),
                product("ngn-b", currency = "ngn"),
                product("usd-b", currency = "usd")
            )
        )
        assertTrue(selected.isNotEmpty())
        assertTrue(selected.all { it.currency.trim().uppercase() == "NGN" })

        val majority = selectMarketplaceAdvertisingCandidates(
            listOf(
                product("usd-a", currency = "USD"),
                product("ngn-a", currency = "NGN"),
                product("ngn-b", currency = "NGN")
            )
        )
        assertTrue(majority.all { it.currency.trim().uppercase() == "NGN" })
    }

    @Test
    fun verifiedSuccessfulSalesRankFirstAndMissingSalesFallBackToPrice() {
        val products = listOf(
            product("cheap", seller = " @new-shop ", price = 10.0),
            product("top-seller", seller = "@high-sales", price = 500.0),
            product("middle", seller = "middle-shop", price = 50.0)
        )
        val ranked = selectMarketplaceAdvertisingCandidates(
            products,
            successfulSalesBySeller = mapOf("high-sales" to 25, "middle-shop" to 5)
        )
        assertEquals(listOf("top-seller", "middle", "cheap"), ranked.map { it.id })

        val fallback = selectMarketplaceAdvertisingCandidates(products)
        assertEquals(listOf("cheap", "middle", "top-seller"), fallback.map { it.id })
    }

    @Test
    fun stableSortDoesNotDependOnInputOrderWhenSalesAndPricesTie() {
        val first = product("z", title = "Same", price = 100.0)
        val second = product("a", title = "Same", price = 100.0)
        assertEquals(listOf("a", "z"), selectMarketplaceAdvertisingCandidates(listOf(first, second)).map { it.id })
        assertEquals(listOf("a", "z"), selectMarketplaceAdvertisingCandidates(listOf(second, first)).map { it.id })
    }
}
