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
}
