package com.example

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.i18n.AppLanguage
import com.example.ui.theme.NoorTheme
import com.example.viewmodel.NoorViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi")
class NoorUiJourneyTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun launchAuthenticatedShell(): NoorViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = NoorViewModel(app)
        composeRule.setContent {
            NoorTheme(darkTheme = true, language = AppLanguage.AR) {
                NoorAuthenticatedShell(vm = vm)
            }
        }
        return vm
    }

    @Test
    fun `khatmah portion completion stays inside worship section and displays inline confirmation`() {
        launchAuthenticatedShell()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_profile").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("worship_tab_khatmah").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("create_khatmah_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("khatmah_dialog_save_btn").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("khatmah_complete_day_1").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("khatmah_fasting_list").assertIsDisplayed()
        composeRule.onNodeWithTag("completion_confirmation_banner").assertIsDisplayed()
    }

    @Test
    fun `mushaf screen displays page 1 index and single choose reciter control`() {
        launchAuthenticatedShell()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_quran").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("mushaf_page_header").assertIsDisplayed()
        composeRule.onNodeWithTag("mushaf_page_header").assertTextContains("1 / 604", substring = true)
        composeRule.onNodeWithTag("choose_reciter_button").assertIsDisplayed()
        composeRule.onNodeWithTag("mushaf_index_button").assertIsDisplayed()
    }

    @Test
    fun `memorization screen displays 6-mode interactive tests panel inside Plans and Test tab`() {
        launchAuthenticatedShell()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_memorize").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("mem_subtab_1").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("test_mode_missing_words").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("test_mode_next_ayah").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("test_mode_identify_surah").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("test_mode_recite_blind").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `profile hub omits admin dashboard tab for non-admin account and supports adhkar counter`() {
        launchAuthenticatedShell()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_profile").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("profile_tab_4").assertDoesNotExist()
        composeRule.onNodeWithTag("worship_tab_adhkar").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dhikr_count_m_1").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dhikr_count_m_1").assertTextContains("1 / 1", substring = true)
    }
}
