package app.jrny.browser.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.TestServer
import app.jrny.browser.engine.ExtensionAsk
import app.jrny.browser.engine.Extensions
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.Tab
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Extensions (SPEC, "Extensions"): uBlock Origin, added by default, in the page menu with its
 * popup; the list and an extension's own panel; turning one off; and adding one, which asks first.
 */
@RunWith(AndroidJUnit4::class)
class ExtensionsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var tab: Tab
    private val server = TestServer(mapOf("/" to "<title>plain</title>"))
    private val ublock = Extensions.DEFAULTS.first()

    @Before
    fun showAPage() {
        compose.runOnUiThread {
            tab = Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-extensions-ui")))
        }
        compose.setContent { JrnyTheme { BrowserWindow(tab, TestTabs.ui(), TestProfiles.ui()) } }
        compose.runOnUiThread { tab.load(server.url("/")) }
        compose.waitUntil(15_000) { !tab.isLoading && tab.title == "plain" }
        // Added on the app's first start, and on with its button once it has started.
        compose.waitUntil(60_000) { Extensions.actions.containsKey(ublock.id) }
    }

    /** Leaves uBlock Origin installed and on, as the app's first start does, for the tests after this one. */
    @After
    fun closeAll() {
        try {
            compose.runOnUiThread {
                Extensions.closePopup()
                Extensions.closePages()
                Extensions.dismissNotice()
                (Extensions.ask as? ExtensionAsk.Add)?.answer?.invoke(false, false)
                val present = Extensions.installed.firstOrNull { it.id == ublock.id }
                if (present == null) Extensions.install(ublock.location) else if (!present.metaData.enabled) Extensions.setEnabled(present, true)
                tab.destroy()
            }
            // A removed one comes back through the question any install asks.
            compose.waitUntil(30_000) {
                (Extensions.ask as? ExtensionAsk.Add)?.takeIf { it.extension.id == ublock.id }?.answer?.invoke(true, true)
                Extensions.actions.containsKey(ublock.id)
            }
            compose.runOnUiThread { Extensions.dismissNotice() }
        } finally {
            server.close()
        }
    }

    @Test
    fun thePageMenuOpensAnExtensionsPopupAndBackClosesIt() {
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-extension-${ublock.id}").performScrollTo().performClick()
        compose.waitUntil(15_000) { Extensions.popup != null }
        compose.onNodeWithTag("extension-popup").assertIsDisplayed()

        Espresso.pressBack()
        compose.waitUntil(5_000) { Extensions.popup == null }
    }

    @Test
    fun settingsLeadToTheListAndAnExtensionsPanelAndBackLeadsBack() {
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-settings").performScrollTo().performClick()
        compose.onNodeWithTag("setting-extensions").performScrollTo().performClick()
        compose.onNodeWithTag("panel-Extensions").assertIsDisplayed()
        compose.onNodeWithTag("extension-${ublock.id}").performScrollTo().performClick()
        compose.onNodeWithTag("panel-Extension").assertIsDisplayed()
        compose.onNodeWithTag("extension-permissions").performScrollTo().assertIsDisplayed()

        Espresso.pressBack()
        compose.onNodeWithTag("panel-Extensions").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithTag("panel-Settings").assertIsDisplayed()
    }

    @Test
    fun anExtensionTurnedOffLeavesThePageMenu() {
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-extensions").performScrollTo().performClick()
        compose.onNodeWithTag("extension-switch-${ublock.id}").performScrollTo().performClick()
        compose.waitUntil(15_000) { Extensions.installed.any { it.id == ublock.id && !it.metaData.enabled } }
        Espresso.pressBack()

        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-extensions").performScrollTo()
        assertTrue("its popup is still offered", compose.onAllNodesWithTag("menu-extension-${ublock.id}").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun addingAnExtensionAsksFirstAndListsWhatItCanDo() {
        val installed = Extensions.installed.first { it.id == ublock.id }
        compose.runOnUiThread { Extensions.remove(installed) }
        compose.waitUntil(15_000) { Extensions.installed.none { it.id == ublock.id } }

        compose.runOnUiThread { Extensions.install(ublock.location) }
        compose.waitUntil(30_000) { Extensions.ask != null }
        compose.onNodeWithTag("extension-add-dialog").assertIsDisplayed()
        compose.onNodeWithTag("extension-permissions").assertIsDisplayed()
        compose.onNodeWithTag("extension-add-private").performClick()
        compose.onNodeWithTag("extension-add-confirm").performClick()

        compose.waitUntil(30_000) { Extensions.installed.any { it.id == ublock.id && it.metaData.allowedInPrivateBrowsing } }
        compose.onNodeWithTag("extension-notice").assertIsDisplayed()
        compose.runOnUiThread { Extensions.dismissNotice() }
    }
}
