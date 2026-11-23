package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi")
class NoorAuthUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `unauthenticated startup renders mandatory Google Sign-In welcome screen with policies`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("privacy_policy_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("terms_of_use_button").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("privacy_policy_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Firebase Authentication", substring = true).assertIsDisplayed()
    }

    @Test
    fun `language switcher on auth screen toggles to English and opens terms dialog`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_language_toggle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertTextContains("Sign in with Google", substring = true)
        composeRule.onNodeWithTag("terms_of_use_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Sacred Text & Educational Scope", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Close").assertIsDisplayed()
    }
}
