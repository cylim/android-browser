package app.jrny.browser.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.jrny.browser.TestPages.page
import app.jrny.browser.TestPages.titled
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.Tab
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The browser window at forced window sizes, so every layout is checked on any device. The same
 * tests also run on a phone and a tablet (see CONTRIBUTING.md).
 */
@SuppressLint("RequiresFeature")
@RunWith(AndroidJUnit4::class)
class BrowserLayoutUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var tab: Tab
    private var windowSize by mutableStateOf(TABLET_LANDSCAPE)

    @Before
    fun createTab() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        compose.runOnUiThread {
            tab = Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-layout")))
        }
    }

    @After
    fun destroyTab() {
        if (::tab.isInitialized) compose.runOnUiThread { tab.destroy() }
    }

    private fun show(size: DpSize) {
        windowSize = size
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(windowSize)) {
                JrnyTheme { BrowserScreen(tab) }
            }
        }
    }

    private fun loadAndWait(url: String, title: String) {
        compose.runOnUiThread { tab.load(url) }
        compose.waitUntil(15_000) { !tab.isLoading && tab.url == url && tab.title == title }
    }

    @Test
    fun landscapeTabletGetsTheSidebarWithTabsAndProfile() {
        show(TABLET_LANDSCAPE)
        compose.onNodeWithTag("sidebar").assertExists()
        compose.onNodeWithTag("bottom-bar-layout").assertDoesNotExist()
        compose.onNodeWithText("Tabs").assertExists()
        compose.onNodeWithTag("profile-switcher").assertExists()
    }

    @Test
    fun theSidebarCollapsesToTheRailAndExpandsAgain() {
        show(TABLET_LANDSCAPE)
        compose.onNodeWithContentDescription("Hide sidebar").performClick()
        compose.onNodeWithTag("sidebar-rail").assertExists()
        compose.onNodeWithTag("sidebar").assertDoesNotExist()

        compose.onNodeWithContentDescription("Show sidebar").performClick()
        compose.onNodeWithTag("sidebar").assertExists()
    }

    @Test
    fun phonesAndPortraitTabletsGetTheBottomBarWithTheProfileAvatar() {
        listOf(PHONE_PORTRAIT, TABLET_PORTRAIT).forEach { size ->
            windowSize = size
            if (size == PHONE_PORTRAIT) show(size)
            compose.waitForIdle()
            compose.onNodeWithTag("bottom-bar-layout").assertExists()
            compose.onNodeWithTag("sidebar-layout").assertDoesNotExist()
            compose.onNodeWithTag("profile-avatar").assertExists()
        }
    }

    @Test
    fun resizingSwitchesLayoutsLiveAndKeepsTheTabsPage() {
        val one = titled("one")
        loadAndWait(one, "one navigate")
        val webView = tab.webView
        show(TABLET_LANDSCAPE)

        for (size in listOf(PHONE_PORTRAIT, TABLET_LANDSCAPE, TABLET_PORTRAIT, PHONE_LANDSCAPE)) {
            windowSize = size
            compose.waitForIdle()
            val expected = if (BrowserLayout.forWindowWidth(size.width.value) == BrowserLayout.Sidebar) {
                "sidebar-layout"
            } else {
                "bottom-bar-layout"
            }
            compose.onNodeWithTag(expected).assertExists()
            // The same WebView moved into the new layout, with the page still loaded.
            assertSame(webView, tab.webView)
            compose.waitUntil(5_000) { webView.isAttachedToWindow }
            assertEquals(one, tab.url)
            assertEquals("one navigate", tab.title)
        }
    }

    @Test
    fun aDeadRendererShowsAMessageAndReloadBringsThePageBack() {
        val one = titled("one")
        show(TABLET_LANDSCAPE)
        loadAndWait(one, "one navigate")
        val first: WebView = tab.webView

        compose.runOnUiThread { first.loadUrl("chrome://crash") }
        compose.waitUntil(15_000) { tab.crashed }
        compose.onNodeWithText("This page stopped working.").assertIsDisplayed()
        // The new WebView is already in the window, behind the message.
        compose.waitUntil(5_000) { tab.webView !== first && tab.webView.isAttachedToWindow }

        compose.onNodeWithText("Reload").performClick()
        compose.waitUntil(15_000) { !tab.crashed && !tab.isLoading && tab.title == "one navigate" }
        compose.onNodeWithText("This page stopped working.").assertDoesNotExist()
    }

    @Test
    fun goLoadsTheAddressShowsItAndHandsFocusToThePage() {
        // A .invalid host never resolves, so this needs no network: the address is what matters.
        val address = "https://jrny-test.invalid/"
        for (size in listOf(PHONE_PORTRAIT, TABLET_LANDSCAPE)) {
            if (size == PHONE_PORTRAIT) show(size) else windowSize = size
            compose.waitForIdle()
            compose.onNodeWithTag("address").performClick()
            compose.onNodeWithTag("address").performTextReplacement(address)
            compose.onNodeWithTag("address").performImeAction()

            compose.waitUntil(5_000) { tab.url == address }
            compose.onNodeWithTag("address").assertIsNotFocused()
            compose.onNodeWithTag("address").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(address)),
            )
            assertTrue("the page has focus", tab.webView.hasFocus())
        }
    }

    @Test
    fun tappingTheAddressSelectsAllOfItSoTypingReplacesIt() {
        val one = titled("one")
        loadAndWait(one, "one navigate")
        show(PHONE_PORTRAIT)

        compose.onNodeWithTag("address").performClick()
        compose.onNodeWithTag("address").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, one.length)),
        )
        compose.onNodeWithTag("address").performTextInput("x")
        compose.onNodeWithTag("address").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("x")),
        )
    }

    @Test
    fun aPopupOpensOverThePageInTheSameProfileAndBackClosesIt() {
        val opener = page(
            "<title>opener</title><button style='position:fixed;left:0;top:0;width:100%;height:100%' " +
                "onclick=\"window.open('about:blank','signin')\">Sign in</button>",
        )
        show(TABLET_LANDSCAPE)
        loadAndWait(opener, "opener")

        // A real tap: without one, WebView blocks window.open.
        compose.onNodeWithTag("page").performTouchInput { click(center) }
        compose.waitUntil(10_000) { tab.popup != null }
        compose.onNodeWithTag("popup").assertExists()
        assertEquals(tab.profile.name, WebViewCompat.getProfile(tab.popup!!).name)

        Espresso.pressBack()
        compose.waitUntil(5_000) { tab.popup == null }
        compose.onNodeWithTag("popup").assertDoesNotExist()
        assertEquals(opener, tab.url)
    }

    @Test
    fun aPopupClosesItselfWithWindowClose() {
        val opener = page(
            "<title>opener</title><button style='position:fixed;left:0;top:0;width:100%;height:100%' " +
                "onclick=\"window.open('about:blank','signin')\">Sign in</button>",
        )
        show(PHONE_PORTRAIT)
        loadAndWait(opener, "opener")
        compose.onNodeWithTag("page").performTouchInput { click(center) }
        compose.waitUntil(10_000) { tab.popup != null }

        compose.runOnUiThread { tab.popup!!.evaluateJavascript("window.close()", null) }
        compose.waitUntil(5_000) { tab.popup == null }
        assertTrue(tab.webView.isAttachedToWindow)
    }

    private companion object {
        val PHONE_PORTRAIT = DpSize(411.dp, 891.dp)
        val PHONE_LANDSCAPE = DpSize(891.dp, 411.dp)
        val TABLET_PORTRAIT = DpSize(800.dp, 1280.dp)
        val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)
    }
}
