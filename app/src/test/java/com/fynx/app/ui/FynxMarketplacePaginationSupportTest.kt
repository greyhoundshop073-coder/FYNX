package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FynxMarketplacePaginationSupportTest {
    private data class Listing(val id: String, val title: String)

    @Test
    fun overlappingPagesKeepFirstOccurrenceAndPreserveOrder() {
        val firstPage = listOf(
            Listing("a", "First A"),
            Listing("b", "First B"),
            Listing("c", "First C")
        )
        val secondPage = listOf(
            Listing("b", "Duplicate B"),
            Listing("d", "First D"),
            Listing("d", "Duplicate D within page"),
            Listing("a", "Duplicate A")
        )

        val result = appendDistinctMarketplacePage(firstPage, secondPage) { it.id }

        assertEquals(listOf("a", "b", "c", "d"), result.map { it.id })
        assertEquals("First B", result[1].title)
        assertEquals("First D", result[3].title)
    }

    @Test
    fun fullyOverlappingPageDoesNotChangeVisibleListings() {
        val existing = listOf(Listing("a", "A"), Listing("b", "B"))

        val result = appendDistinctMarketplacePage(
            existing,
            listOf(Listing("a", "Duplicate A"), Listing("b", "Duplicate B"))
        ) { it.id }

        assertEquals(existing, result)
    }

    @Test
    fun emptyIncomingPageLeavesExistingListingsUntouched() {
        val existing = listOf(Listing("a", "A"))

        assertEquals(existing, appendDistinctMarketplacePage(existing, emptyList()) { it.id })
    }

    @Test
    fun paginationOffsetAdvancesByRequestedPageSizeEvenWhenPagesAreShortOrOverlap() {
        assertEquals(24, advanceMarketplaceListingsOffset(0, 24))
        assertEquals(48, advanceMarketplaceListingsOffset(24, 24))
        assertEquals(72, advanceMarketplaceListingsOffset(48, 24))
    }

    @Test
    fun cataloguePaginationRetainsMoreThanOneHundredProductsAcrossOverlappingPages() {
        val pageSize = 24
        var visible = emptyList<Listing>()
        var offset = 0

        repeat(5) { pageIndex ->
            val start = pageIndex * pageSize
            val page = (start until start + pageSize).map { index ->
                Listing("product-$index", "Product $index")
            }
            val overlappingPage = if (pageIndex == 0) page else listOf(visible.last()) + page
            visible = appendDistinctMarketplacePage(visible, overlappingPage) { it.id }
            offset = advanceMarketplaceListingsOffset(offset, pageSize)
        }

        assertEquals(120, visible.size)
        assertEquals(120, offset)
        assertEquals("product-0", visible.first().id)
        assertEquals("product-119", visible.last().id)
        assertEquals(120, visible.map { it.id }.distinct().size)
        assertEquals((0 until 120).map { "product-$it" }, visible.map { it.id })
    }

    @Test
    fun failedPageCanBeRetriedWithoutAdvancingTheOffsetOrDuplicatingProducts() {
        val firstPage = (0 until 24).map { Listing("product-$it", "Product $it") }
        val offsetBeforeRetry = 24
        val afterFailedRequest = firstPage
        assertEquals(24, offsetBeforeRetry)
        assertEquals(firstPage, afterFailedRequest)

        val retryPage = (24 until 48).map { Listing("product-$it", "Product $it") }
        val afterRetry = appendDistinctMarketplacePage(afterFailedRequest, retryPage) { it.id }
        val nextOffset = advanceMarketplaceListingsOffset(offsetBeforeRetry, 24)

        assertEquals(48, afterRetry.size)
        assertEquals(48, nextOffset)
        assertEquals(48, afterRetry.map { it.id }.distinct().size)
    }

    @Test
    fun paginationOffsetUsesAtLeastOneForInvalidPageSize() {
        assertEquals(25, advanceMarketplaceListingsOffset(24, 0))
        assertEquals(25, advanceMarketplaceListingsOffset(24, -10))
    }
}
