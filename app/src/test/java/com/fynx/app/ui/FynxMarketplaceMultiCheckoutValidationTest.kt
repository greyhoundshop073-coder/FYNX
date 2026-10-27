package com.fynx.app.ui

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FynxMarketplaceMultiCheckoutValidationTest {
    private val now = Instant.parse("2026-10-27T07:00:00Z")
    private val expected = listOf(
        FynxMarketplaceExpectedCheckoutLine("11", 2, "NGN", "PICKUP"),
        FynxMarketplaceExpectedCheckoutLine("22", 1, "NGN", "DELIVERY")
    )

    private fun validQuote(edit: (JSONObject, JSONArray) -> Unit = { _, _ -> }): String {
        val first = JSONObject()
            .put("listingId", "11").put("sellerUsername", "seller-a").put("productTitle", "Phone")
            .put("quantity", 2).put("unitPrice", 10.0).put("currency", "NGN")
            .put("fulfillmentMethod", "PICKUP").put("subtotal", 20.0).put("deliveryFee", 0.0)
            .put("marketplaceFee", 1.0).put("marketplaceFeeBuyer", 0.5).put("marketplaceFeeSeller", 0.5)
            .put("total", 20.5)
        val second = JSONObject()
            .put("listingId", "22").put("sellerUsername", "seller-b").put("productTitle", "Bag")
            .put("quantity", 1).put("unitPrice", 30.0).put("currency", "NGN")
            .put("fulfillmentMethod", "DELIVERY").put("subtotal", 30.0).put("deliveryFee", 5.0)
            .put("marketplaceFee", 1.5).put("marketplaceFeeBuyer", 0.75).put("marketplaceFeeSeller", 0.75)
            .put("total", 35.75)
        val items = JSONArray().put(first).put(second)
        val quote = JSONObject()
            .put("id", "550e8400-e29b-41d4-a716-446655440001")
            .put("expiresAt", "2026-10-27T07:05:00Z")
            .put("currency", "NGN").put("items", items)
            .put("subtotal", 50.0).put("deliveryFee", 5.0)
            .put("marketplaceFee", 2.5).put("marketplaceFeeBuyer", 1.25).put("marketplaceFeeSeller", 1.25)
            .put("total", 56.25)
        edit(quote, items)
        return JSONObject().put("quote", quote).toString()
    }

    private fun parseQuote(raw: String = validQuote()) =
        parseMarketplaceMultiCheckoutQuoteResponse(raw, expected, now)

    private fun assertInvalidQuote(raw: String) {
        assertTrue("Expected invalid cart quote to be rejected.", runCatching { parseQuote(raw) }.isFailure)
    }

    @Test
    fun validMultiQuoteMapsEveryProductAndAggregateTotal() {
        val quote = parseQuote()
        assertEquals(2, quote.items.size)
        assertEquals(50.0, quote.subtotal, 0.001)
        assertEquals(2.5, quote.marketplaceFeeTotal, 0.001)
        assertEquals(1.25, quote.fee, 0.001)
        assertEquals(56.25, quote.total, 0.001)
    }

    @Test
    fun multiQuoteRejectsExpiredInvalidAndMismatchedProductDetails() {
        assertInvalidQuote(validQuote { q, _ -> q.put("expiresAt", "2026-10-27T06:59:00Z") })
        assertInvalidQuote(validQuote { q, _ -> q.put("id", "not-a-uuid") })
        assertInvalidQuote(validQuote { _, items -> items.getJSONObject(0).put("listingId", "999") })
        assertInvalidQuote(validQuote { _, items -> items.getJSONObject(0).put("quantity", 5) })
        assertInvalidQuote(validQuote { _, items -> items.getJSONObject(0).put("fulfillmentMethod", "DELIVERY") })
        assertInvalidQuote(validQuote { _, items -> items.getJSONObject(0).put("currency", "USD") })
    }

    @Test
    fun multiQuoteRejectsMalformedMoneyAndAggregateArithmetic() {
        assertInvalidQuote(validQuote { _, items -> items.getJSONObject(0).remove("marketplaceFeeSeller") })
        assertInvalidQuote(validQuote { _, items -> items.getJSONObject(1).put("total", 90.0) })
        assertInvalidQuote(validQuote { q, _ -> q.put("total", 57.0) })
        assertInvalidQuote(validQuote { _, items -> items.getJSONObject(0).put("marketplaceFee", "not-a-number") })
        assertInvalidQuote(validQuote { _, items -> items.getJSONObject(0).put("subtotal", -1.0) })
    }

    @Test
    fun multiQuoteRejectsMissingDuplicateAndMixedCurrencyProducts() {
        assertInvalidQuote(validQuote { _, items -> items.getJSONObject(1).put("listingId", "11") })
        assertInvalidQuote(JSONObject().put("unexpected", true).toString())
        assertTrue(runCatching {
            parseMarketplaceMultiCheckoutQuoteResponse(
                validQuote(),
                listOf(expected[0], expected[1].copy(currency = "USD")),
                now
            )
        }.isFailure)
    }

    private fun orderFor(
        listingId: String,
        orderId: String,
        quantity: Int,
        unitPrice: Double,
        subtotal: Double,
        deliveryFee: Double,
        marketplaceFee: Double,
        buyerFee: Double,
        sellerFee: Double,
        total: Double,
        method: String
    ) = JSONObject()
        .put("id", orderId).put("buyerId", "buyer-1").put("sellerId", "seller-1")
        .put("listingId", listingId).put("quantity", quantity)
        .put("unitPrice", unitPrice).put("productSubtotal", subtotal).put("deliveryFee", deliveryFee)
        .put("marketplaceFee", marketplaceFee).put("marketplaceFeeBuyer", buyerFee)
        .put("marketplaceFeeSeller", sellerFee).put("discountAmount", 0.0)
        .put("buyerTotal", total).put("totalAmount", total).put("currency", "NGN")
        .put("product", JSONObject().put("title", "Item $listingId").put("sellerUsername", "seller"))
        .put("status", "PAYMENT_PENDING").put("fulfillmentMethod", method)

    private fun validOrderResponse(): String {
        val checkout = JSONObject()
            .put("id", "550e8400-e29b-41d4-a716-446655440001")
            .put("buyer_id", "buyer-1").put("currency", "NGN").put("status", "PAYMENT_PENDING")
            .put("subtotal", 50.0).put("delivery_fee", 5.0).put("marketplace_fee", 2.5).put("buyer_total", 56.25)
        val orders = JSONArray()
            .put(orderFor("11", "550e8400-e29b-41d4-a716-446655440011", 2, 10.0, 20.0, 0.0, 1.0, 0.5, 0.5, 20.5, "PICKUP"))
            .put(orderFor("22", "550e8400-e29b-41d4-a716-446655440022", 1, 30.0, 30.0, 5.0, 1.5, 0.75, 0.75, 35.75, "DELIVERY"))
        return JSONObject().put("checkout", checkout).put("orders", orders).toString()
    }

    private fun validQuoteForOrders() = parseQuote()

    private fun assertInvalidOrder(raw: String) {
        assertTrue("Expected invalid protected cart response to be rejected.", runCatching {
            parseMarketplaceMultiCheckoutOrderResponse(raw, validQuoteForOrders())
        }.isFailure)
    }

    @Test
    fun validMultiOrderResponseMustMatchReviewedQuoteBeforePayment() {
        val result = parseMarketplaceMultiCheckoutOrderResponse(validOrderResponse(), validQuoteForOrders())
        assertEquals("550e8400-e29b-41d4-a716-446655440001", result.checkoutId)
        assertEquals("NGN", result.currency)
        assertEquals(56.25, result.total, 0.001)
        assertEquals(2, result.orderIds.size)
    }

    @Test
    fun multiOrderRejectsChangedAggregateTotalOrIncompleteOrders() {
        val badTotal = JSONObject(validOrderResponse()).apply { getJSONObject("checkout").put("buyer_total", 60.0) }.toString()
        val missingOrder = JSONObject(validOrderResponse()).apply {
            put("orders", JSONArray().put(getJSONArray("orders").getJSONObject(0)))
        }.toString()
        val changedStatus = JSONObject(validOrderResponse()).apply { getJSONObject("checkout").put("status", "PAID") }.toString()
        assertInvalidOrder(badTotal)
        assertInvalidOrder(missingOrder)
        assertInvalidOrder(changedStatus)
    }

    @Test
    fun multiOrderRejectsWrongIdentityDuplicateOrdersAndPriceDrift() {
        val wrongProduct = JSONObject(validOrderResponse()).apply {
            getJSONArray("orders").getJSONObject(0).put("listingId", "999")
        }.toString()
        val duplicateOrder = JSONObject(validOrderResponse()).apply {
            getJSONArray("orders").getJSONObject(1).put("id", "550e8400-e29b-41d4-a716-446655440011")
        }.toString()
        val changedPrice = JSONObject(validOrderResponse()).apply {
            getJSONArray("orders").getJSONObject(0).put("buyerTotal", 22.0).put("totalAmount", 22.0)
                .put("marketplaceFeeBuyer", 2.0)
        }.toString()
        assertInvalidOrder(wrongProduct)
        assertInvalidOrder(duplicateOrder)
        assertInvalidOrder(changedPrice)
    }
}
