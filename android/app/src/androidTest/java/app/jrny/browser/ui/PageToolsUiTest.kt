package app.jrny.browser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.TestServer
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.settings.Settings
import app.jrny.browser.tab.Tab
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Find in page, the Desktop site switch, and the page settings (SPEC, "Pages"). */
@RunWith(AndroidJUnit4::class)
class PageToolsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var tab: Tab
    private var server: TestServer? = null
    private val desktopChoices = mutableListOf<Boolean>()
    private var settings by mutableStateOf(Settings())

    @Before
    fun createTab() {
        compose.runOnUiThread { tab = Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-page-tools"))) }
        val started = TestServer(mapOf("/words" to "<title>words</title><p>okapi one</p><p>okapi two</p>"))
        server = started
        compose.runOnUiThread { tab.load(started.url("/words")) }
        compose.waitUntil(15_000) { !tab.isLoading && tab.title == "words" }
    }

    @After
    fun destroy() {
        if (::tab.isInitialized) compose.runOnUiThread { tab.destroy() }
        server?.close()
    }

    private fun show(size: DpSize) = compose.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
            JrnyTheme {
                BrowserWindow(
                    tab,
                    TestTabs.ui(),
                    TestProfiles.ui(),
                    settings = SettingsUi(settings, update = { settings = it(settings) }),
                    links = LinksUi(setDesktopSite = { desktopChoices += it }),
                )
            }
        }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun openFind() {
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-find").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("find-bar").assertExists()
    }

    @Test
    fun onAPhoneFindTakesTheAddressBarsPlaceAndCountsMatches() {
        show(PHONE_PORTRAIT)
        openFind()
        compose.onNodeWithTag("address").assertDoesNotExist()
        compose.onNodeWithTag("find-field").performTextInput("okapi")
        compose.waitUntil(10_000) { tab.find?.total == 2 }
        compose.onNodeWithText("1 of 2").assertExists()
        compose.onNodeWithTag("find-next").performClick()
        compose.waitUntil(10_000) { tab.find?.current == 2 }

        // Closing brings the address bar back.
        compose.onNodeWithTag("find-close").performClick()
        compose.onNodeWithTag("find-bar").assertDoesNotExist()
        compose.onNodeWithTag("address").assertExists()
    }

    @Test
    fun onATabletFindRunsAcrossTheTopOfThePageAndBackClosesIt() {
        show(TABLET_LANDSCAPE)
        openFind()
        // The Sidebar keeps its address field.
        compose.onNodeWithTag("address").assertExists()
        // The first Back would put the keyboard away; the next one closes Find in page.
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        compose.waitUntil(5_000) { !exists("find-bar") }
        assertEquals(null, tab.find)
    }

    @Test
    fun theDesktopSiteSwitchAsksForTheOtherVersion() {
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-desktop-site").performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf(true), desktopChoices)
    }

    @Test
    fun thePageSettingsChangeTextSizeDarkPagesAndPullToRefresh() {
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-settings").performScrollTo().performClick()
        compose.onNodeWithTag("setting-text-size").performScrollTo().assertExists()
        compose.onNodeWithText("100%").assertExists()
        compose.onNodeWithTag("setting-darken-pages").performScrollTo().performClick()
        compose.onNodeWithTag("setting-pull-to-refresh").performScrollTo().performClick()
        assertEquals(true, settings.darkenPages)
        assertEquals(false, settings.pullToRefresh)
    }

    private companion object {
        val PHONE_PORTRAIT = DpSize(411.dp, 891.dp)
        val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)
    }
}
