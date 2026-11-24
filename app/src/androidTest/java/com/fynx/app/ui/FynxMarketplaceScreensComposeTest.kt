package com.fynx.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Screen-level instrumentation for the real production Marketplace, Seller Center,
 * Local Business Directory and Wallet composables. Every injected record is test-only;
 * production clients are bypassed only when an explicit fixture parameter is supplied.
 */
class FynxMarketplaceScreensComposeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val marketplaceListings =
        FynxMarketplaceIsolatedTestFixtures.listings

    private val sellerListings = marketplaceListings.map { item ->
        FynxMarketplaceClient.Listing(
            id = item.id,
            sellerUsername = item.sellerUsername,
            sellerDisplayName = item.sellerDisplayName,
            storeName = item.storeName,
            title = item.title,
            description = item.description,
            price = item.price,
            currency = item.currency,
            category = item.category,
            condition = item.condition,
            quantity = item.quantity,
            location = item.location,
            deliveryAvailable = item.deliveryAvailable,
            pickupAvailable = item.pickupAvailable,
            deliveryFee = item.deliveryFee,
            mediaIds = emptyList(),
            active = item.active,
            originalPrice = item.originalPrice,
            productAverageRating = item.productAverageRating,
            productReviewCount = item.productReviewCount,
            priceDrop = item.priceDrop
        )
    }

    @Test
    fun actualMarketplaceScreenRendersFixturesAndKeepsNavigationActionsConnected() {
        var shopOpened = 0
        var walletOpened = 0
        var businessesOpened = 0

        compose.setContent {
            MaterialTheme {
                FynxMarketplacePanel(
                    currentUsername = FynxMarketplaceIsolatedTestFixtures.buyer.username,
                    onOpenMyShop = { shopOpened++ },
                    onOpenWallet = { walletOpened++ },
                    onOpenBusinesses = { businessesOpened++ },
                    testOnlyListings = marketplaceListings
                )
            }
        }

        compose.onNodeWithContentDescription("Search products, sellers or categories").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithText("FYNX TEST DATA — isolated instrumentation only; no live listings or account data are changed.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        // This status notice is rendered above the scrollable feed, not inside its LazyColumn.
        // Assert that it exists; scrolling is reserved for actual feed content below.
        assertTrue(
            "Marketplace test-data status notice should exist",
            compose.onAllNodesWithText("FYNX TEST DATA — isolated instrumentation only; no live listings or account data are changed.")
                .fetchSemanticsNodes().isNotEmpty()
        )
        saveScreenshot("marketplace-screen.png")

        compose.onNodeWithText("My Shop").performClick()
        compose.runOnIdle { assertEquals(1, shopOpened) }

        compose.onNodeWithText("Wallet").performClick()
        compose.runOnIdle { assertEquals(1, walletOpened) }

        compose.onNodeWithText("Local businesses").performClick()
        compose.runOnIdle { assertEquals(1, businessesOpened) }
    }

    @Test
    fun actualMarketplaceProductDetailsUseOnlyFixtureReviewsAndKeepActionsLocal() {
        val fixtureReviews = FynxMarketplaceIsolatedTestFixtures.sampleReviews.map { review ->
            FynxRemoteSocialClient.MarketplaceProductReview(
                id = review.id,
                rating = review.rating,
                comment = review.comment,
                createdAt = null,
                reviewerUsername = "fynx_test_buyer",
                reviewerDisplayName = review.authorLabel
            )
        }
        compose.setContent {
            MaterialTheme {
                FynxMarketplacePanel(
                    currentUsername = FynxMarketplaceIsolatedTestFixtures.buyer.username,
                    testOnlyListings = marketplaceListings,
                    testOnlyReviews = fixtureReviews
                )
            }
        }

        compose.onNodeWithText("Recommended for you").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Shop this product: ${marketplaceListings.first().title}")
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText("Product details").assertIsDisplayed()
        compose.onNodeWithText("Product reviews").performScrollTo().assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithText("Sample review content — not a customer review.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("FYNX TEST BUYER A").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sample review content — not a customer review.").performScrollTo().assertIsDisplayed()
        saveScreenshot("marketplace-product-details-test-reviews.png")

        compose.onNodeWithText("Save product").performScrollTo().performClick()
        compose.onNodeWithText("Remove from Saved Products").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Buy now").performScrollTo().performClick()
        compose.onNodeWithText("Checkout").assertIsDisplayed()
        compose.onNodeWithText("Review exact total").performClick()
        compose.onNodeWithText("FYNX fee: NGN 2,875.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Delivery: NGN 0.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Total: NGN 60,375.00").performScrollTo().assertIsDisplayed()
        saveScreenshot("marketplace-checkout-pickup-quote.png")
        compose.onNodeWithText("Confirm test preview").performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithText("FYNX TEST MODE — quote reviewed; no order or payment was created.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("FYNX TEST MODE — quote reviewed; no order or payment was created.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun actualMarketplaceCheckoutDeliveryPreviewRequiresAddressAndCalculatesDeliveryFee() {
        compose.setContent {
            MaterialTheme {
                FynxMarketplacePanel(
                    currentUsername = FynxMarketplaceIsolatedTestFixtures.buyer.username,
                    // Keep the quote test deterministic: listing 1 has both fulfillment
                    // methods and a fixed NGN 1,500 test delivery fee.
                    testOnlyListings = listOf(marketplaceListings.first())
                )
            }
        }
        compose.onNodeWithText("Recommended for you").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Shop this product: ${marketplaceListings.first().title}")
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText("Buy now").performScrollTo().performClick()
        compose.onNodeWithText("Delivery").performClick()
        compose.onNodeWithText("Full name").performTextInput("FYNX Test Buyer")
        compose.onNodeWithText("Phone").performTextInput("08000000000")
        compose.onNodeWithText("Delivery address").performTextInput("FYNX Test Delivery Address")
        compose.onNodeWithText("City").performTextInput("Port Harcourt")
        compose.onNodeWithText("State").performTextInput("Rivers")
        compose.onNodeWithText("Review exact total").performClick()
        compose.onNodeWithText("Delivery: NGN 1,500.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("FYNX fee: NGN 2,875.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Total: NGN 61,875.00").performScrollTo().assertIsDisplayed()
        saveScreenshot("marketplace-checkout-delivery-quote.png")
        compose.onNodeWithText("Confirm test preview").performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithText("FYNX TEST MODE — quote reviewed; no order or payment was created.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("FYNX TEST MODE — quote reviewed; no order or payment was created.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun actualSellerCenterScreenRendersIsolatedInventoryAndSectionNavigation() {
        compose.setContent {
            MaterialTheme {
                FynxMarketplaceSellerCenterPanel(
                    testOnlyListings = sellerListings,
                    testOnlyShopName = FynxMarketplaceIsolatedTestFixtures.shop.name,
                    testOnlyShopDescription = FynxMarketplaceIsolatedTestFixtures.shop.description,
                    testOnlyOrders = listOf(
                        SellerOrderRow(
                            id = "FYNX-TEST-DELIVERY-001",
                            buyerName = "FYNX Test Buyer",
                            title = "FYNX Test Delivery Order",
                            total = 25_000.0,
                            currency = "NGN",
                            status = "PAID",
                            fulfillment = "DELIVERY",
                            fulfillmentStatus = "PREPARING",
                            address = null,
                            tracking = "FYNX-TEST-TRACK-001",
                            driverId = null,
                            driverType = "FYNX_ACCOUNT",
                            localDriverName = null,
                            localDriverPhone = null,
                            pickupPoint = null,
                            pickupInstructions = null,
                            vehicleType = null,
                            vehicleColor = null,
                            vehiclePlate = null,
                            vehicleNote = null,
                            driverApprovalActive = false,
                            driverAcceptedAt = null,
                            driverHandoverAt = null,
                            driverDeliveredAt = null
                        ),
                        SellerOrderRow(
                            id = "FYNX-TEST-PICKUP-001",
                            buyerName = "FYNX Test Buyer",
                            title = "FYNX Test Pickup Order",
                            total = 12_500.0,
                            currency = "NGN",
                            status = "PAID",
                            fulfillment = "PICKUP",
                            fulfillmentStatus = "READY_FOR_PICKUP",
                            address = null,
                            tracking = null,
                            driverId = null,
                            driverType = "FYNX_ACCOUNT",
                            localDriverName = null,
                            localDriverPhone = null,
                            pickupPoint = "FYNX Test Market Pickup Desk",
                            pickupInstructions = "Ask for the isolated test order only.",
                            vehicleType = null,
                            vehicleColor = null,
                            vehiclePlate = null,
                            vehicleNote = null,
                            driverApprovalActive = false,
                            driverAcceptedAt = null,
                            driverHandoverAt = null,
                            driverDeliveredAt = null
                        )
                    )
                )
            }
        }

        compose.onNodeWithText("My Shop").assertIsDisplayed()
        compose.onNodeWithText(FynxMarketplaceIsolatedTestFixtures.shop.name).assertIsDisplayed()
        compose.onNodeWithText("Your shop at a glance").assertIsDisplayed()
        saveScreenshot("my-shop-overview-screen.png")

        compose.onNodeWithTag("fynx-seller-center-section-listings").performClick()
        compose.onNodeWithText("Your listings").assertIsDisplayed()
        compose.onNodeWithText("FYNX Test Wireless Headphones").assertIsDisplayed()
        compose.onNodeWithTag("fynx-seller-center-scroll-container")
            .performScrollToNode(hasText("FYNX Test Android Smartphone"))
        compose.onNodeWithText("FYNX Test Android Smartphone").assertIsDisplayed()
        saveScreenshot("my-shop-listings-screen.png")

        // The previous product scroll moved the outer LazyColumn past its header,
        // which disposes the nested section LazyRow. Bring that real header back first.
        compose.onNodeWithTag("fynx-seller-center-scroll-container").performScrollToIndex(1)
        compose.onNodeWithTag("fynx-seller-center-section-tabs").performScrollToNode(hasText("Orders"))
        compose.onNodeWithText("Orders").performClick()
        compose.onNodeWithText("Review buyer orders, fulfillment progress, protected payment status, receipts and available next actions.").assertIsDisplayed()
        compose.onNodeWithText("Seller orders").performClick()
        compose.onNodeWithText("FYNX Test Delivery Order").assertIsDisplayed()
        compose.onNodeWithText("FYNX Test Pickup Order").assertIsDisplayed()
        compose.onNodeWithText("Pickup").assertIsDisplayed()
    }

    @Test
    fun actualLocalBusinessDirectoryRendersSearchableTestBusinessesAndDetails() {
        val businesses = (1..32).map { index ->
            FynxBusinessDirectoryItem(
                id = "fynx-test-business-%03d".format(index),
                businessName = if (index == 1) FynxMarketplaceIsolatedTestFixtures.localBusiness.name
                    else "FYNX Test Local Store %02d".format(index),
                username = "fynx_test_business_%02d".format(index),
                category = if (index % 2 == 0) "Retail" else "Services",
                description = "FYNX TEST DATA — sample business directory record $index.",
                location = "Port Harcourt, Rivers State, Nigeria",
                phone = "",
                website = "",
                verified = false
            )
        }

        compose.setContent {
            MaterialTheme {
                FynxLocalBusinessesPanel(testOnlyBusinesses = businesses)
            }
        }

        compose.onNodeWithText("Search businesses, services or names").assertIsDisplayed()
        compose.onNodeWithText("Area, city or neighbourhood").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithText(FynxMarketplaceIsolatedTestFixtures.localBusiness.name)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("fynx-local-business-list")
            .performScrollToNode(hasText(FynxMarketplaceIsolatedTestFixtures.localBusiness.name))
        compose.onNodeWithText(FynxMarketplaceIsolatedTestFixtures.localBusiness.name).assertIsDisplayed()
        saveScreenshot("local-store-directory-screen.png")

        compose.onNodeWithText(FynxMarketplaceIsolatedTestFixtures.localBusiness.name).performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithText("FYNX TEST DATA — sample business directory record 1.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(FynxMarketplaceIsolatedTestFixtures.localBusiness.name).assertIsDisplayed()
        compose.onNodeWithText("FYNX TEST DATA — sample business directory record 1.").assertIsDisplayed()
    }

    @Test
    fun actualWalletScreenRendersOnlySimulatedLedgerAndNeverEnablesMoneyMovement() {
        val summary = FynxBuyerWalletSummary(
            walletStatus = "ACTIVE",
            currency = "NGN",
            availableBalance = FynxMarketplaceIsolatedTestFixtures.wallet.available,
            reservedBalance = FynxMarketplaceIsolatedTestFixtures.wallet.reserved,
            withdrawalPendingBalance = FynxMarketplaceIsolatedTestFixtures.wallet.withdrawalPending,
            canFund = false,
            canSpend = false,
            canWithdraw = false,
            notice = "FYNX TEST DATA — simulated balances only. No provider, bank or payment request is made.",
            testMode = true
        )
        val activity = FynxMarketplaceIsolatedTestFixtures.wallet.transactions.map { transaction ->
            FynxBuyerWalletActivity(
                id = transaction.reference,
                type = transaction.type,
                status = transaction.status,
                amount = transaction.amount,
                fee = 0.0,
                walletDelta = transaction.availableBalanceDelta,
                currency = "NGN",
                reference = transaction.reference,
                description = transaction.status,
                createdAt = "2026-11-15T00:00:00Z"
            )
        }

        compose.setContent {
            MaterialTheme {
                FynxBuyerWalletPanel(
                    testOnlySummary = summary,
                    testOnlyActivity = activity,
                    testOnlySellerSummary = FynxWalletSellerSummary(
                        totals = listOf(
                            FynxWalletSellerTotal(
                                currency = "NGN",
                                protectedFunds = 20_000.0,
                                availableForPayout = 0.0,
                                payoutPending = 0.0,
                                paidOut = 0.0,
                                grossSales = 25_000.0,
                                marketplaceFees = 1_250.0,
                                netEarnings = 23_750.0
                            )
                        ),
                        settlements = listOf(
                            FynxWalletSellerSettlement(
                                orderId = "FYNX-TEST-ORDER-001",
                                title = "FYNX Test Wireless Headphones",
                                currency = "NGN",
                                sellerNetAmount = 23_750.0,
                                marketplaceFeeSeller = 1_250.0,
                                funds = "PROTECTED",
                                payoutStatus = "SIMULATED",
                                updatedAt = "2026-11-15T00:00:00Z"
                            )
                        )
                    )
                )
            }
        }

        compose.onNodeWithText("FYNX Wallet").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithText("TEST MODE — simulated balance; cannot spend or withdraw")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("TEST MODE — simulated balance; cannot spend or withdraw").assertIsDisplayed()
        compose.onNodeWithTag("fynx-wallet-scroll-container")
            .performScrollToNode(hasText("FYNX-TEST-FUND-001", substring = true))
        compose.onNodeWithText("FYNX-TEST-FUND-001", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("fynx-wallet-scroll-container")
            .performScrollToNode(hasText("Wallet security & PIN"))
        compose.onNodeWithText("Wallet security & PIN").assertIsDisplayed()
        assertTrue(FynxMarketplaceIsolatedTestFixtures.wallet.isReconciled())
        saveScreenshot("wallet-simulated-test-ledger-screen.png")
    }

    @Test
    fun actualWalletScreenUsesUsdForUsdBalancesAndActivity() {
        val summary = FynxBuyerWalletSummary(
            walletStatus = "ACTIVE",
            currency = "USD",
            availableBalance = 1_234.5,
            reservedBalance = 250.0,
            withdrawalPendingBalance = 100.0,
            canFund = false,
            canSpend = false,
            canWithdraw = false,
            notice = "TEST MODE — simulated USD balance only.",
            testMode = true
        )
        val transaction = FynxBuyerWalletActivity(
            id = "FYNX-TEST-USD-001",
            type = "FUNDING",
            status = "SIMULATED — TEST ONLY",
            amount = 1_234.5,
            fee = 0.0,
            walletDelta = 1_234.5,
            currency = "USD",
            reference = "FYNX-TEST-USD-001",
            description = "USD test transaction only.",
            createdAt = "2026-11-15T00:00:00Z"
        )

        compose.setContent {
            MaterialTheme {
                FynxBuyerWalletPanel(
                    testOnlySummary = summary,
                    testOnlyActivity = listOf(transaction)
                )
            }
        }

        compose.onNodeWithText("US Dollar").assertIsDisplayed()
        compose.onAllNodesWithText("\$1,234.50")[0].assertIsDisplayed()
        compose.onNodeWithTag("fynx-wallet-scroll-container").performScrollToNode(hasText("\$250.00"))
        compose.onNodeWithText("\$250.00").assertIsDisplayed()
        compose.onNodeWithTag("fynx-wallet-scroll-container")
            .performScrollToNode(hasText("FYNX-TEST-USD-001", substring = true))
        compose.onNodeWithText("FYNX-TEST-USD-001", substring = true).assertIsDisplayed()
        saveScreenshot("wallet-usd-simulated-test-screen.png")
    }

    private fun saveScreenshot(name: String) {
        // Visual evidence is optional: screenshot capture must never fail a functional test.
        runCatching {
            val externalFiles = requireNotNull(compose.activity.getExternalFilesDir(null)) {
                "External app files directory is unavailable for instrumentation screenshots."
            }
            val directory = File(externalFiles, "marketplace-test-fixtures")
            android.util.Log.i("FYNX_MARKETPLACE_TEST_SCREENSHOT", "directory=${directory.absolutePath}")
            check(directory.mkdirs() || directory.isDirectory)
            val file = File(directory, name)
            compose.waitForIdle()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            check(device.takeScreenshot(file)) { "Android could not capture the full device display." }
            check(file.isFile && file.length() > 0L)
        }.onFailure { error ->
            android.util.Log.w("FYNXMarketplaceTest", "Optional screenshot '$name' was not captured", error)
        }
    }
}
