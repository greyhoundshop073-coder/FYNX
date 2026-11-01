package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxBuyerWalletClientTest {
    @Test
    fun notSetupWalletDoesNotInventAnAvailableBalance() {
        val summary = FynxBuyerWalletClient.parseSummary(
            """{"walletStatus":"NOT_SETUP","currency":"NGN","availableBalance":null,"reservedBalance":null,"withdrawalPendingBalance":null,"canFund":false,"canSpend":false,"canWithdraw":false}"""
        )
        assertEquals("NOT_SETUP", summary.walletStatus)
        assertNull(summary.availableBalance)
        assertFalse(summary.canFund)
        assertFalse(summary.canSpend)
        assertFalse(summary.canWithdraw)
    }

    @Test
    fun activeWalletSummaryKeepsBalanceBucketsSeparate() {
        val summary = FynxBuyerWalletClient.parseSummary(
            """{"walletStatus":"ACTIVE","currency":"NGN","availableBalance":12500.0,"reservedBalance":2000.0,"withdrawalPendingBalance":500.0,"canFund":false,"canSpend":false,"canWithdraw":false}"""
        )
        assertEquals(12500.0, summary.availableBalance!!, 0.0)
        assertEquals(2000.0, summary.reservedBalance!!, 0.0)
        assertEquals(500.0, summary.withdrawalPendingBalance!!, 0.0)
    }

    @Test
    fun walletSummaryRejectsUnsupportedCurrency() {
        assertThrows(IllegalArgumentException::class.java) {
            FynxBuyerWalletClient.parseSummary("""{"walletStatus":"ACTIVE","currency":"USD"}""")
        }
    }

    @Test
    fun activityReadsDirectionFromLedgerDelta() {
        val rows = FynxBuyerWalletClient.parseTransactions(
            """{"transactions":[{"id":"txn-1","type":"FUNDING","status":"SUCCEEDED","amount":10000.0,"fee":0.0,"walletDelta":10000.0,"currency":"NGN","reference":"FYNX-TEST-1","description":"Test funding","createdAt":"2026-10-31T10:00:00Z"},{"id":"txn-2","type":"MARKETPLACE_PAYMENT","status":"SUCCEEDED","amount":2500.0,"fee":0.0,"walletDelta":-2500.0,"currency":"NGN","reference":"FYNX-TEST-2","description":"Test order","createdAt":"2026-10-31T10:01:00Z"}]}"""
        )
        assertEquals(2, rows.transactions.size)
        assertTrue(rows.transactions[0].walletDelta > 0)
        assertTrue(rows.transactions[1].walletDelta < 0)
        assertFalse(rows.hasMore)
        assertNull(rows.nextCursor)
    }

    @Test
    fun sellerSummarySeparatesGrossSalesFeesAndNetEarnings() {
        val summary = FynxBuyerWalletClient.parseSellerSummary(
            """{"totals":[{"currency":"NGN","protectedFunds":1000.0,"availableForPayout":500.0,"payoutPending":100.0,"paidOut":2500.0,"grossSales":4500.0,"marketplaceFees":225.0,"netEarnings":4275.0}],"settlements":[{"orderId":"order-1","title":"Leather bag","currency":"NGN","sellerNetAmount":950.0,"marketplaceFeeSeller":50.0,"funds":"HELD","payoutStatus":"HELD","updatedAt":"2026-10-31T10:00:00Z"}]}"""
        )
        assertEquals(1, summary.totals.size)
        assertEquals(4500.0, summary.totals[0].grossSales, 0.0)
        assertEquals(225.0, summary.totals[0].marketplaceFees, 0.0)
        assertEquals(4275.0, summary.totals[0].netEarnings, 0.0)
        assertEquals(50.0, summary.settlements[0].marketplaceFeeSeller, 0.0)
        assertEquals("HELD", summary.settlements[0].funds)
    }

    @Test
    fun sellerSummaryAcceptsUsdAndEmptySellerHistory() {
        val summary = FynxBuyerWalletClient.parseSellerSummary(
            """{"totals":[{"currency":"USD","protectedFunds":0,"availableForPayout":0,"payoutPending":0,"paidOut":0,"grossSales":0,"marketplaceFees":0,"netEarnings":0}],"settlements":[]}"""
        )
        assertEquals("USD", summary.totals.single().currency)
        assertTrue(summary.settlements.isEmpty())
    }

    @Test
    fun sellerSummaryRejectsNegativeOrUnsupportedAmounts() {
        assertThrows(IllegalArgumentException::class.java) {
            FynxBuyerWalletClient.parseSellerSummary(
                """{"totals":[{"currency":"NGN","protectedFunds":-1,"availableForPayout":0,"payoutPending":0,"paidOut":0,"grossSales":0,"marketplaceFees":0,"netEarnings":0}]}"""
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FynxBuyerWalletClient.parseSellerSummary(
                """{"totals":[{"currency":"EUR","protectedFunds":0,"availableForPayout":0,"payoutPending":0,"paidOut":0,"grossSales":0,"marketplaceFees":0,"netEarnings":0}]}"""
            )
        }
    }

    @Test
    fun activityDirectionIsNormalizedAndAllowlisted() {
        assertEquals("ALL", FynxBuyerWalletClient.normalizeActivityDirection(" all "))
        assertEquals("IN", FynxBuyerWalletClient.normalizeActivityDirection("in"))
        assertEquals("OUT", FynxBuyerWalletClient.normalizeActivityDirection("Out"))
        assertThrows(IllegalArgumentException::class.java) {
            FynxBuyerWalletClient.normalizeActivityDirection("RECENT")
        }
    }

    @Test
    fun activityPageRequiresCursorWhenMoreRowsExist() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            FynxBuyerWalletClient.parseTransactions(
                """{"transactions":[],"hasMore":true,"nextCursor":null}"""
            )
        }
        assertTrue(error.message.orEmpty().contains("did not return a cursor"))
    }

    @Test
    fun activityPageParsesNextCursor() {
        val page = FynxBuyerWalletClient.parseTransactions(
            """{"transactions":[],"hasMore":true,"nextCursor":{"createdAt":"2026-10-31T10:00:00Z","id":"txn-cursor"}}"""
        )
        assertTrue(page.hasMore)
        assertEquals("txn-cursor", page.nextCursor?.id)
        assertEquals("2026-10-31T10:00:00Z", page.nextCursor?.createdAt)
    }

    @Test
    fun activityRejectsInvalidCurrencyAndNegativeAmount() {
        assertThrows(IllegalStateException::class.java) {
            FynxBuyerWalletClient.parseTransactions(
                """{"transactions":[{"id":"txn-1","type":"FUNDING","status":"SUCCEEDED","amount":20.0,"fee":0.0,"walletDelta":20.0,"currency":"USD","reference":"FYNX-1"}]}"""
            )
        }
        assertThrows(IllegalStateException::class.java) {
            FynxBuyerWalletClient.parseTransactions(
                """{"transactions":[{"id":"txn-2","type":"FUNDING","status":"SUCCEEDED","amount":-20.0,"fee":0.0,"walletDelta":20.0,"currency":"NGN","reference":"FYNX-2"}]}"""
            )
        }
    }
}
