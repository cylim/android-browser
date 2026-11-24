package app.jrny.browser.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.TestPages.page
import app.jrny.browser.TestServer
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.SavedTab
import app.jrny.browser.tab.Tab
import app.jrny.browser.tab.TabId
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The page menu's sections, the menu for a link the person holds (SPEC, "Link and image menu"),
 * and the bar that offers a link for another app (SPEC, "Links to other apps").
 */
@RunWith(AndroidJUnit4::class)
class LinksUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var tab: Tab
    private var server: TestServer? = null
    private val openedInNewTab = mutableListOf<String>()
    private val openedPrivate = mutableListOf<String>()

    @Before
    fun createTab() {
        compose.runOnUiThread { tab = Tab(instrumentation.targetContext, WebProfile.of(ProfileId("test-links"))) }
    }

    @After
    fun destroy() {
        if (::tab.isInitialized) compose.runOnUiThread { tab.destroy() }
        server?.close()
    }

    private fun show(private: Boolean = false) = compose.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(PHONE_PORTRAIT)) {
            JrnyTheme {
                val shown = SavedTab(TabId(if (private) -1 else 1), TestProfiles.personal.id, tab.url, "", 0, selected = true, isPrivate = private)
                BrowserWindow(
                    tab,
                    TestTabs.ui(all = listOf(shown)),
                    TestProfiles.ui(),
                    links = LinksUi(openInNewTab = { openedInNewTab += it }, openInPrivateTab = { openedPrivate += it }),
                )
            }
        }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun loadServed(html: String) {
        val started = TestServer(mapOf("/page" to html))
        server = started
        compose.runOnUiThread { tab.load(started.url("/page")) }
        compose.waitUntil(15_000) { !tab.isLoading && tab.title.isNotEmpty() }
    }

    /**
     * Holds the middle of the page, as a person would: a touch goes down and stays, and GeckoView
     * sees a long press. The touch goes to the page's view itself, so it lands whatever the test
     * window's size.
     */
    private fun holdPage() {
        compose.waitForIdle()
        var x = 0f
        var y = 0f
        val down = SystemClock.uptimeMillis()
        compose.runOnUiThread {
            val view = SessionViews.viewOf(tab.session)!!
            x = view.width / 2f
            y = view.height / 2f
            val event = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            view.dispatchTouchEvent(event)
            event.recycle()
        }
        // Held for longer than a long press takes.
        Thread.sleep(1_200)
        compose.runOnUiThread {
            val view = SessionViews.viewOf(tab.session)!!
            val now = SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(down, now, MotionEvent.ACTION_UP, x, y, 0)
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            view.dispatchTouchEvent(up)
            up.recycle()
        }
    }

    private val bigLink = "<title>links</title><a id=l href='https://example.com/next' style='display:block;position:fixed;inset:0'>next</a>"

    @Test
    fun thePageMenuHasAnIconRowAndSectionsAndOnlyAWebPageCanBeSharedOrCopied() {
        compose.runOnUiThread { tab.load(page("<title>local</title>")) }
        compose.waitUntil(15_000) { tab.title == "local" }
        show()
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithText("Page").assertExists()
        compose.onNodeWithText("Profile").assertExists()
        compose.onNodeWithText("Browser").assertExists()
        compose.onNodeWithTag("menu-reload").assertIsEnabled()
        compose.onNodeWithTag("menu-share").assertIsNotEnabled()
        compose.onNodeWithTag("menu-copy-link").assertIsNotEnabled()
        compose.onNodeWithTag("menu-print").assertIsNotEnabled()
    }

    @Test
    fun copyLinkPutsThePagesAddressOnTheClipboard() {
        loadServed("<title>copy me</title>")
        show()
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-copy-link").assertIsEnabled().performClick()
        val clip = compose.runOnUiThread {
            instrumentation.targetContext.getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()
        }
        assertEquals(tab.url, clip)
    }

    @Test
    fun holdingALinkOffersItsMenuAndOpenInNewTab() {
        loadServed(bigLink)
        show()
        holdPage()
        compose.waitUntil(10_000) { exists("link-menu") }
        compose.onNodeWithText("https://example.com/next").assertExists()
        compose.onNodeWithTag("link-private-tab").assertExists()
        compose.onNodeWithTag("link-copy").assertExists()
        compose.onNodeWithTag("link-new-tab").performClick()
        assertEquals(listOf("https://example.com/next"), openedInNewTab)
        compose.onNodeWithTag("link-menu").assertDoesNotExist()
    }

    @Test
    fun inAPrivateTabALinkOpensOnlyInPrivateTabs() {
        loadServed(bigLink)
        show(private = true)
        holdPage()
        compose.waitUntil(10_000) { exists("link-menu") }
        compose.onNodeWithTag("link-private-tab").assertDoesNotExist()
        compose.onNodeWithTag("link-open-in").assertDoesNotExist()
        compose.onNodeWithTag("link-new-tab").assertExists()
    }

    @Test
    fun holdingTextKeepsTheEnginesOwnHandling() {
        loadServed("<title>text</title><p style='font-size:40px'>Just some words to select on this page</p>")
        show()
        holdPage()
        Thread.sleep(500)
        compose.onNodeWithTag("link-menu").assertDoesNotExist()
    }

    @Test
    fun aLinkForAnotherAppIsOfferedUnderThePage() {
        show()
        compose.runOnUiThread { tab.load(page("<title>start</title><script>setTimeout(function(){location.href='zoomus://join?confno=1'},0)</script>")) }
        compose.waitUntil(15_000) { exists("handoff-prompt") }
        compose.onNodeWithText("This page wants to open another app.").assertExists()
        compose.onNodeWithTag("handoff-action").assertExists()
    }

    private companion object {
        val PHONE_PORTRAIT = DpSize(411.dp, 891.dp)
    }
}
