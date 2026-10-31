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
        assertEquals(2, rows.size)
        assertTrue(rows[0].walletDelta > 0)
        assertTrue(rows[1].walletDelta < 0)
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
