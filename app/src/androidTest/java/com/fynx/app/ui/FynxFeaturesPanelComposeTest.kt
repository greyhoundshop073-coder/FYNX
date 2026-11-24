package com.fynx.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FynxFeaturesPanelComposeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ordinaryAccountSeesEveryDistinctFeatureButNoOwnerOrDuplicateShortcuts() {
        compose.setContent {
            MaterialTheme {
                FynxFeaturesPanel(adminRole = null, onSelect = {})
            }
        }

        listOf(
            "Wallet", "Calls", "Gifts", "Share", "To-Do", "Calendar",
            "Extra Tools", "Saved Posts", "Money Tools", "Business Account",
            "Advertising", "Advertising Dashboard", "AI", "Announcements"
        ).forEach { route ->
            compose.onNodeWithTag("fynx-feature-card-$route").performScrollTo().assertIsDisplayed()
        }

        listOf("Admin", "Marketplace", "Notifications", "Privacy", "Saved Products", "Seller Center")
            .forEach { route ->
                compose.onNodeWithTag("fynx-feature-card-$route").assertDoesNotExist()
            }
    }

    @Test
    fun ownerGetsOwnerControlCenterCardAndOwnerLabel() {
        compose.setContent {
            MaterialTheme {
                FynxFeaturesPanel(adminRole = "OWNER", onSelect = {})
            }
        }

        compose.onNodeWithTag("fynx-feature-card-Admin").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("fynx-feature-role-owner", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun adminGetsAdminControlCenterCardButNotOwnerLabel() {
        compose.setContent {
            MaterialTheme {
                FynxFeaturesPanel(adminRole = "ADMIN", onSelect = {})
            }
        }

        compose.onNodeWithTag("fynx-feature-card-Admin").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("fynx-feature-role-admin", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("fynx-feature-role-owner", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun unknownRoleCannotSeeOwnerOrAdminCard() {
        compose.setContent {
            MaterialTheme {
                FynxFeaturesPanel(adminRole = "SUPERADMIN", onSelect = {})
            }
        }

        compose.onNodeWithTag("fynx-feature-card-Admin").assertDoesNotExist()
    }

    @Test
    fun firstFeatureRowUsesTwoColumnsAndCardsNavigateToTheirExistingRoutes() {
        var selectedRoute: String? = null
        compose.setContent {
            MaterialTheme {
                FynxFeaturesPanel(adminRole = null, onSelect = { selectedRoute = it })
            }
        }

        val calls = compose.onNodeWithTag("fynx-feature-card-Calls").fetchSemanticsNode().boundsInRoot
        val gifts = compose.onNodeWithTag("fynx-feature-card-Gifts").fetchSemanticsNode().boundsInRoot
        assertEquals(calls.top, gifts.top, 1.5f)
        assertTrue("First feature card should occupy the left grid column", calls.center.x < gifts.center.x)

        compose.onNodeWithTag("fynx-feature-card-Wallet").performScrollTo().performClick()
        assertEquals("Wallet", selectedRoute)
    }

    @Test
    fun searchFiltersLocallyToMatchingFeatures() {
        compose.setContent {
            MaterialTheme {
                FynxFeaturesPanel(adminRole = null, onSelect = {})
            }
        }

        compose.onNodeWithContentDescription("Search FYNX tools").performTextInput("Savings")
        compose.onNodeWithTag("fynx-feature-card-Money Tools").assertIsDisplayed()
        compose.onNodeWithTag("fynx-feature-card-Calls").assertDoesNotExist()
        compose.onNodeWithTag("fynx-features-grid").assertIsDisplayed()
    }
}
