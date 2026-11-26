package app.jrny.browser.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.TestServer
import app.jrny.browser.engine.ContentBlocker
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.Tab
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** uBlock Origin's popup from the page menu, and its dashboard from Settings (SPEC, "Content blocking"). */
@RunWith(AndroidJUnit4::class)
class ContentBlockerUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var tab: Tab
    private val server = TestServer(mapOf("/" to "<title>plain</title>"))

    @Before
    fun createTab() {
        compose.runOnUiThread {
            tab = Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-blocker-ui")))
        }
        compose.setContent { JrnyTheme { BrowserWindow(tab, TestTabs.ui(), TestProfiles.ui()) } }
        compose.runOnUiThread { tab.load(server.url("/")) }
        compose.waitUntil(15_000) { !tab.isLoading && tab.title == "plain" }
        compose.waitUntil(30_000) { ContentBlocker.ready }
    }

    @After
    fun closeAll() {
        compose.runOnUiThread {
            ContentBlocker.closePopup()
            ContentBlocker.closePages()
            tab.destroy()
        }
        server.close()
    }

    @Test
    fun thePageMenuOpensThePopupAndBackClosesIt() {
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-content-blocker").performClick()
        compose.waitUntil(15_000) { ContentBlocker.popup != null }
        compose.onNodeWithTag("content-blocker-popup").assertIsDisplayed()

        Espresso.pressBack()
        compose.waitUntil(5_000) { ContentBlocker.popup == null }
    }

    @Test
    fun settingsOpensTheDashboardAndBackLeadsToSettingsAgain() {
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-settings").performScrollTo().performClick()
        compose.onNodeWithTag("setting-content-blocking").performScrollTo().performClick()
        compose.onNodeWithTag("panel-ContentBlocker").assertIsDisplayed()
        assertTrue(ContentBlocker.pages.size == 1)

        Espresso.pressBack()
        compose.onNodeWithTag("panel-Settings").assertIsDisplayed()
        assertNull(ContentBlocker.pages.lastOrNull())
    }
}
