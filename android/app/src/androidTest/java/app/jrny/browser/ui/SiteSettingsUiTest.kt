package app.jrny.browser.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.jrny.browser.site.SiteOrigin
import app.jrny.browser.site.SiteSetting
import app.jrny.browser.site.SiteSettingsEntry
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings → Site settings: the Active Profile's sites, and changing or resetting what they may use. */
@RunWith(AndroidJUnit4::class)
class SiteSettingsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val meet = SiteOrigin.of("https://meet.example.com")!!
    private val changes = mutableListOf<Triple<SiteOrigin, SiteSetting, Boolean?>>()
    private var resetAll = 0

    private fun show(sites: List<SiteSettingsEntry>) = compose.setContent {
        JrnyTheme {
            SiteSettingsPanel(
                SiteSettingsUi(
                    sites = sites,
                    set = { origin, setting, enabled -> changes += Triple(origin, setting, enabled) },
                    reset = { origin, setting -> changes += Triple(origin, setting, null) },
                    resetAll = { resetAll++ },
                ),
                profileName = "Work",
                onBack = {},
                onClose = {},
            )
        }
    }

    @Test
    fun withNoSitesItSaysWhatWillShowHere() {
        show(emptyList())
        compose.onNodeWithTag("site-settings-empty").assertExists()
        compose.onNodeWithTag("reset-all-sites").assertDoesNotExist()
    }

    @Test
    fun aSiteShowsWhatItMayUseAndAChoiceChangesIt() {
        show(listOf(SiteSettingsEntry(meet, mapOf(SiteSetting.Camera to true, SiteSetting.Location to false))))
        compose.onNodeWithText("meet.example.com").assertExists()
        compose.onNodeWithTag("site-setting-meet.example.com-camera").assertExists()
        compose.onNodeWithText("Allowed").assertExists()
        compose.onNodeWithText("Blocked").assertExists()
        compose.onNodeWithTag("site-setting-meet.example.com-microphone").assertDoesNotExist()

        compose.onNodeWithTag("site-setting-meet.example.com-camera").performClick()
        compose.onNodeWithTag("site-setting-block").performClick()
        compose.onNodeWithTag("site-setting-meet.example.com-location").performClick()
        compose.onNodeWithTag("site-setting-reset").performClick()
        assertEquals(listOf(Triple(meet, SiteSetting.Camera, false), Triple(meet, SiteSetting.Location, null)), changes)
    }

    @Test
    fun resetAllAsksFirst() {
        show(listOf(SiteSettingsEntry(meet, mapOf(SiteSetting.Camera to true))))
        compose.onNodeWithTag("reset-all-sites").performClick()
        compose.onNodeWithText("Reset every site in Work?").assertExists()
        assertEquals(0, resetAll)
        compose.onNodeWithTag("confirm-reset-all-sites").performClick()
        assertEquals(1, resetAll)
    }
}
