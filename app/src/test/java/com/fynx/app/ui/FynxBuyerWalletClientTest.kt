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
    fun pendingWalletBalanceCaptionDoesNotPromiseSpendableFunds() {
        val pending = FynxBuyerWalletClient.parseSummary(
            """{"walletStatus":"PENDING_PROVIDER","currency":"NGN","availableBalance":0.0,"reservedBalance":0.0,"withdrawalPendingBalance":0.0,"canFund":false,"canSpend":false,"canWithdraw":false,"testMode":false}"""
        )
        assertEquals(
            "Wallet setup pending — this balance cannot be spent or withdrawn yet",
            buyerWalletBalanceCaption(pending, summaryLoading = false, summaryError = null)
        )
    }

    @Test
    fun activeWalletBalanceCaptionDescribesClearedFunds() {
        val active = FynxBuyerWalletClient.parseSummary(
            """{"walletStatus":"ACTIVE","currency":"NGN","availableBalance":125.0,"canSpend":false,"canWithdraw":false}"""
        )
        assertEquals(
            "Cleared funds available to use",
            buyerWalletBalanceCaption(active, summaryLoading = false, summaryError = null)
        )
    }

    @Test
    fun firstVisitPendingWalletShowsOnlyItsLedgerDerivedZeroBalance() {
        val summary = FynxBuyerWalletClient.parseSummary(
            """{"walletStatus":"PENDING_PROVIDER","currency":"NGN","availableBalance":0.0,"reservedBalance":0.0,"withdrawalPendingBalance":0.0,"canFund":false,"canSpend":false,"canWithdraw":false,"testMode":false}"""
        )
        assertEquals(0.0, summary.availableBalance!!, 0.0)
        assertTrue(shouldShowBuyerWalletBalance(summary))
        assertFalse(summary.canFund)
        assertFalse(summary.canSpend)
        assertFalse(summary.canWithdraw)
    }

    @Test
    fun restrictedAndClosedWalletBalancesRemainHidden() {
        val restricted = FynxBuyerWalletClient.parseSummary(
            """{"walletStatus":"RESTRICTED","currency":"NGN","availableBalance":350.0,"canFund":false,"canSpend":false,"canWithdraw":false}"""
        )
        val closed = FynxBuyerWalletClient.parseSummary(
            """{"walletStatus":"CLOSED","currency":"NGN","availableBalance":0.0,"canFund":false,"canSpend":false,"canWithdraw":false}"""
        )
        assertFalse(shouldShowBuyerWalletBalance(restricted))
        assertFalse(shouldShowBuyerWalletBalance(closed))
        assertFalse(shouldShowBuyerWalletBalance(null))
    }

    @Test
    fun testModeWalletShowsSimulatedBalanceWithoutEnablingSpendingOrWithdrawals() {
        val summary = FynxBuyerWalletClient.parseSummary(
            """{"walletStatus":"PENDING_PROVIDER","currency":"NGN","availableBalance":1500.0,"reservedBalance":0,"withdrawalPendingBalance":0,"canFund":true,"canSpend":true,"canWithdraw":true,"canFundByBankTransfer":true,"canFundByCard":true,"canFundByUssd":true,"testMode":true,"notice":"TEST MODE ONLY"}"""
        )
        assertTrue(summary.testMode)
        assertEquals(1500.0, summary.availableBalance!!, 0.0)
        assertTrue(summary.canFund)
        assertTrue(summary.canFundByCard)
        assertFalse(summary.canFundByBankTransfer)
        assertFalse(summary.canFundByUssd)
        assertFalse(summary.canSpend)
        assertFalse(summary.canWithdraw)
    }

    @Test
    fun testModeDoesNotAdvertiseFundingWhenCardCheckoutIsUnavailable() {
        val summary = FynxBuyerWalletClient.parseSummary(
            """{"walletStatus":"PENDING_PROVIDER","currency":"NGN","availableBalance":0,"canFund":true,"canFundByBankTransfer":true,"canFundByCard":false,"canFundByUssd":true,"canSpend":true,"canWithdraw":true,"testMode":true}"""
        )
        assertFalse(summary.canFund)
        assertFalse(summary.canFundByBankTransfer)
        assertFalse(summary.canFundByCard)
        assertFalse(summary.canFundByUssd)
        assertFalse(summary.canSpend)
        assertFalse(summary.canWithdraw)
    }

    @Test
    fun ordinaryWalletSummaryDefaultsToNonTestMode() {
        val summary = FynxBuyerWalletClient.parseSummary(
            """{"walletStatus":"ACTIVE","currency":"NGN","availableBalance":50.0,"canSpend":false,"canWithdraw":false}"""
        )
        assertFalse(summary.testMode)
    }

    @Test
    fun hostedWalletFundingSessionCarriesTestModeNotice() {
        val session = FynxBuyerWalletClient.parseWalletFundingSession(
            """{"status":"SUCCEEDED","reference":"fwd_abcdef0123456789abcdef0123456789","amount":100.0,"paymentMethod":"CARD","testMode":true,"message":"TEST MODE simulated payment"}"""
        )
        assertTrue(session.testMode)
        assertTrue(session.message.startsWith("TEST MODE"))
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
    fun hostedWalletFundingSessionParsesPendingCardCheckout() {
        val session = FynxBuyerWalletClient.parseWalletFundingSession(
            """{"status":"PENDING","reference":"fwd_0123456789abcdef0123456789abcdef","amount":5000.0,"paymentMethod":"CARD","authorizationUrl":"https://checkout.paystack.com/session","message":"pending"}"""
        )
        assertEquals("PENDING", session.status)
        assertEquals("CARD", session.paymentMethod)
        assertEquals(5000.0, session.amount, 0.0)
        assertEquals("https://checkout.paystack.com/session", session.authorizationUrl)
    }

    @Test
    fun hostedWalletFundingSessionParsesRecoverableProcessingAndTerminalStates() {
        val processing = FynxBuyerWalletClient.parseWalletFundingSession(
            """{"status":"PROCESSING","reference":"fwd_0123456789abcdef0123456789abcdef","amount":1000.0,"paymentMethod":"CARD","message":"Check payment status"}"""
        )
        assertEquals("PROCESSING", processing.status)
        assertNull(processing.authorizationUrl)

        val failed = FynxBuyerWalletClient.parseWalletFundingSession(
            """{"status":"FAILED","reference":"fwd_abcdef0123456789abcdef0123456789","amount":1000.0,"paymentMethod":"CARD","message":"No credit"}"""
        )
        assertEquals("FAILED", failed.status)
        assertNull(failed.authorizationUrl)

        val blocked = FynxBuyerWalletClient.parseWalletFundingSession(
            """{"status":"BLOCKED","reference":"fwd_0123456789abcdef0123456789abcdef","amount":1000.0,"paymentMethod":"USSD","message":"Checkout blocked"}"""
        )
        assertEquals("BLOCKED", blocked.status)
        assertNull(blocked.authorizationUrl)
    }

    @Test
    fun hostedWalletFundingSessionRejectsMissingPaymentMethod() {
        assertThrows(IllegalArgumentException::class.java) {
            FynxBuyerWalletClient.parseWalletFundingSession(
                """{"status":"PENDING","reference":"fwd_0123456789abcdef0123456789abcdef","amount":1000.0}"""
            )
        }
    }

    @Test
    fun hostedWalletFundingSessionAcceptsVerifiedSuccessWithoutCheckoutUrl() {
        val session = FynxBuyerWalletClient.parseWalletFundingSession(
            """{"status":"SUCCEEDED","reference":"fwd_abcdef0123456789abcdef0123456789","amount":2000.0,"paymentMethod":"USSD"}"""
        )
        assertEquals("SUCCEEDED", session.status)
        assertNull(session.authorizationUrl)
    }

    @Test
    fun hostedWalletFundingSessionRejectsUntrustedCheckoutUrls() {
        val unsafeUrls = listOf(
            "http://checkout.paystack.com/session",
            "https://checkout.paystack.com@attacker.example/session",
            "https://checkout.paystack.com.attacker.example/session",
            "https://attacker.example/session",
            "https://checkout.paystack.com:8443/session"
        )
        unsafeUrls.forEach { url ->
            assertThrows(IllegalArgumentException::class.java) {
                FynxBuyerWalletClient.parseWalletFundingSession(
                    """{"status":"PENDING","reference":"fwd_0123456789abcdef0123456789abcdef","amount":100.0,"paymentMethod":"CARD","authorizationUrl":"$url"}"""
                )
            }
        }
    }

    @Test
    fun hostedWalletFundingSessionRejectsInvalidReferenceAndPaymentMethod() {
        assertThrows(IllegalArgumentException::class.java) {
            FynxBuyerWalletClient.parseWalletFundingSession(
                """{"status":"PENDING","reference":"bad-ref","amount":100.0,"paymentMethod":"CARD"}"""
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FynxBuyerWalletClient.parseWalletFundingSession(
                """{"status":"PENDING","reference":"fwd_0123456789abcdef0123456789abcdef","amount":100.0,"paymentMethod":"WIRE"}"""
            )
        }
    }

    @Test
    fun fundingAccountParsesOnlyAssignedProviderAccount() {
        val account = FynxBuyerWalletClient.parseFundingAccount(
            """{"status":"ACTIVE","currency":"NGN","canRequest":false,"account":{"bankName":"Wema Bank","accountName":"FYNX / TEST USER","accountNumber":"0123456789","currency":"NGN"}}"""
        )
        assertEquals("ACTIVE", account.status)
        assertEquals("Wema Bank", account.bankName)
        assertEquals("0123456789", account.accountNumber)
        assertFalse(account.canRequest)
    }

    @Test
    fun pendingFundingAccountDoesNotInventBankDetails() {
        val account = FynxBuyerWalletClient.parseFundingAccount(
            """{"status":"ASSIGNING","currency":"NGN","canRequest":false,"account":null}"""
        )
        assertEquals("ASSIGNING", account.status)
        assertNull(account.bankName)
        assertNull(account.accountName)
        assertNull(account.accountNumber)
    }

    @Test
    fun activeFundingAccountRejectsMissingOrInvalidAccountNumber() {
        assertThrows(IllegalArgumentException::class.java) {
            FynxBuyerWalletClient.parseFundingAccount(
                """{"status":"ACTIVE","currency":"NGN","account":{"bankName":"Wema","accountName":"FYNX","accountNumber":"123"}}"""
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FynxBuyerWalletClient.parseFundingAccount(
                """{"status":"ACTIVE","currency":"USD","account":{"bankName":"Bank","accountName":"FYNX","accountNumber":"0123456789"}}"""
            )
        }
    }

    @Test
    fun walletSecurityParsesServerEnforcedDailyWithdrawalLimits() {
        val security = FynxBuyerWalletClient.parseWalletSecurity(
            """{"hasWithdrawalPin":true,"walletActive":true,"isLocked":false,"limits":{"minimumPerTransfer":50,"maximumPerTransfer":50000,"dailyAmountLimit":150000,"dailyCountLimit":3}}"""
        )
        assertTrue(security.walletActive)
        assertEquals(50.0, security.minimumPerTransfer!!, 0.0)
        assertEquals(50000.0, security.maximumPerTransfer!!, 0.0)
        assertEquals(150000.0, security.dailyAmountLimit!!, 0.0)
        assertEquals(3, security.dailyCountLimit)
    }

    @Test
    fun walletSecurityDoesNotOfferPinSetupBeforeWalletActivation() {
        val security = FynxBuyerWalletClient.parseWalletSecurity(
            """{"hasWithdrawalPin":false,"walletActive":false,"isLocked":false,"lockedUntil":null}"""
        )
        assertFalse(security.walletActive)
        assertFalse(security.hasWithdrawalPin)
    }

    @Test
    fun walletWithdrawalRejectsAmountsBelowNairaMinimumBeforeRequestingProvider() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            FynxBuyerWalletClient.validateWithdrawalAmount(49.99)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            FynxBuyerWalletClient.validateWithdrawalAmount(Double.NaN)
        }
        FynxBuyerWalletClient.validateWithdrawalAmount(50.0)
    }

    @Test
    fun walletSecurityReportsWhenWithdrawalPinIsNotConfigured() {
        val security = FynxBuyerWalletClient.parseWalletSecurity(
            """{"hasWithdrawalPin":false,"isLocked":false,"lockedUntil":null}"""
        )
        assertFalse(security.hasWithdrawalPin)
        assertFalse(security.isLocked)
        assertNull(security.lockedUntil)
    }

    @Test
    fun walletSecurityReportsTemporaryPinLock() {
        val security = FynxBuyerWalletClient.parseWalletSecurity(
            """{"hasWithdrawalPin":true,"isLocked":true,"lockedUntil":"2026-11-01T10:15:00Z"}"""
        )
        assertTrue(security.hasWithdrawalPin)
        assertTrue(security.isLocked)
        assertEquals("2026-11-01T10:15:00Z", security.lockedUntil)
    }

    @Test
    fun payoutBankListKeepsOnlyValidProviderBankRecords() {
        val banks = FynxBuyerWalletClient.parsePayoutBanks(
            """{"banks":[{"name":"Access Bank","code":"044","slug":"access-bank"},{"name":"Invalid","code":"x"}]}"""
        )
        assertEquals(1, banks.size)
        assertEquals("Access Bank", banks.single().name)
        assertEquals("044", banks.single().code)
    }

    @Test
    fun withdrawalResultParsesPendingReferenceAndValidatesAmounts() {
        val result = FynxBuyerWalletClient.parseWithdrawalResult(
            """{"status":"PENDING","reference":"fw_1234567890","amount":2500.0,"bankName":"Access Bank","accountLast4":"6789","message":"processing"}"""
        )
        assertEquals("PENDING", result.status)
        assertEquals("fw_1234567890", result.reference)
        assertEquals(2500.0, result.amount, 0.0)
        assertEquals("6789", result.accountLast4)
        assertThrows(IllegalArgumentException::class.java) {
            FynxBuyerWalletClient.parseWithdrawalResult(
                """{"status":"PENDING","reference":"fw_bad","amount":-1}"""
            )
        }
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
