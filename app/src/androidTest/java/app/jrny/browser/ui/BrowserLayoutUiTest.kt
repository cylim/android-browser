package app.jrny.browser.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
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
import androidx.compose.ui.text.TextLayoutResult
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
import app.jrny.browser.TestServer
import app.jrny.browser.address.WebAddress
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
 *
 * Tests that touch the page load it before showing the window. An empty Tab focuses the address
 * field, and on a phone the on-screen keyboard would then squeeze a forced tablet-sized window's
 * page down to nothing.
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

    private fun fitsTheScreen(size: DpSize): Boolean {
        val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        return size.width.value <= metrics.widthPixels / metrics.density &&
            size.height.value <= metrics.heightPixels / metrics.density
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
        loadAndWait(one, "one navigate")
        show(TABLET_LANDSCAPE)
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
        // Start with a page, so the empty-Tab focus (and keyboard) doesn't get in the way.
        loadAndWait(titled("one"), "one navigate")
        for (size in listOf(PHONE_PORTRAIT, TABLET_LANDSCAPE)) {
            if (size == PHONE_PORTRAIT) show(size) else windowSize = size
            compose.waitForIdle()
            compose.onNodeWithTag("address").performClick()
            compose.onNodeWithTag("address").performTextReplacement(address)
            compose.onNodeWithTag("address").performImeAction()

            compose.waitUntil(5_000) { tab.url == address }
            // The page takes focus once the on-screen keyboard has gone. Checked only where the
            // forced window fits the real screen: squeezing a 1280dp window onto a phone breaks
            // focus for embedded Android views (it works on a real tablet).
            if (fitsTheScreen(size)) compose.waitUntil(5_000) { tab.webView.hasFocus() }
            compose.onNodeWithTag("address").assertIsNotFocused()
            compose.onNodeWithTag("address").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(address)),
            )
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
        loadAndWait(opener, "opener")
        show(TABLET_LANDSCAPE)

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
    fun aPopupShowsItsOwnAddressAndANewAddressReplacesIt() {
        val pages = mapOf("/go" to "redirect:/signin", "/signin" to "<title>sign in</title><p>Sign in")
        TestServer(pages).use { server ->
            // The popup opens /go, which redirects: the bar must follow to /signin.
            val popupAddress = server.url("/signin")
            val opener = page(
                "<title>opener</title><button style='position:fixed;left:0;top:0;width:100%;height:100%' " +
                    "onclick=\"window.open('${server.url("/go")}','signin')\">Sign in</button>",
            )
            loadAndWait(opener, "opener")
            show(PHONE_PORTRAIT)
            compose.onNodeWithTag("page").performTouchInput { click(center) }
            // The bar above the popup names the popup's own address, not the page's.
            compose.waitUntil(10_000) { tab.popupUrl == popupAddress }
            compose.onNodeWithTag("popup-address", useUnmergedTree = true).assertTextEquals(popupAddress)
            compose.onNodeWithTag("popup-host", useUnmergedTree = true).assertTextEquals(WebAddress.hostOf(popupAddress))
            // The popup's page really loaded, rather than an error page at that address.
            var popupTitle = ""
            compose.waitUntil(10_000) {
                compose.runOnUiThread { tab.popup?.evaluateJavascript("document.title") { popupTitle = it } }
                popupTitle == "\"sign in\""
            }
            assertEquals(opener, tab.url)

            val address = "https://jrny-test.invalid/next"
            compose.onNodeWithTag("address").performClick()
            compose.onNodeWithTag("address").performTextReplacement(address)
            compose.onNodeWithTag("address").performImeAction()
            compose.waitUntil(5_000) { tab.popup == null }
            compose.onNodeWithTag("popup").assertDoesNotExist()
            assertEquals(address, tab.url)
        }
    }

    @Test
    fun aLongPopupHostKeepsItsDomainVisibleAndATapShowsTheFullAddress() {
        val host = "accounts.google.com." + "x".repeat(60) + ".attacker.example"
        val address = "https://$host/signin?continue=" + "y".repeat(80)
        compose.setContent {
            JrnyTheme { Box(Modifier.width(320.dp)) { PopupBar(address, onClose = {}) } }
        }
        val hostLayout = textLayout("popup-host")
        assertTrue("the host is cut", hostLayout.isLineEllipsized(0))
        // Cut from the start: the visible text runs to the host's end, so "attacker.example" shows.
        assertEquals(host.length, hostLayout.getLineEnd(0, visibleEnd = true))
        assertEquals(1, textLayout("popup-address").lineCount)

        compose.onNodeWithTag("popup-host", useUnmergedTree = true).performClick()
        assertTrue("the full address wraps", textLayout("popup-address").lineCount > 1)
    }

    private fun textLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.single()
    }

    @Test
    fun aHalfTypedAddressSurvivesTheCollapsedRail() {
        loadAndWait(titled("one"), "one navigate")
        show(TABLET_LANDSCAPE)
        compose.onNodeWithContentDescription("Hide sidebar").performClick()
        compose.onNodeWithTag("sidebar-rail").assertExists()

        windowSize = PHONE_PORTRAIT
        compose.waitForIdle()
        compose.onNodeWithTag("address").performClick()
        compose.onNodeWithTag("address").performTextReplacement("half typed")

        // Back to the wide window: the Sidebar opens instead of the Rail, with the draft in its field.
        windowSize = TABLET_LANDSCAPE
        compose.waitForIdle()
        compose.onNodeWithTag("sidebar").assertExists()
        compose.onNodeWithTag("address").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("half typed")),
        )
        compose.onNodeWithTag("address").assertIsFocused()
    }

    @Test
    fun aPopupClosesWithItsCloseButton() {
        val opener = page(
            "<title>opener</title><button style='position:fixed;left:0;top:0;width:100%;height:100%' " +
                "onclick=\"window.open('about:blank','signin')\">Sign in</button>",
        )
        loadAndWait(opener, "opener")
        show(TABLET_LANDSCAPE)
        compose.onNodeWithTag("page").performTouchInput { click(center) }
        compose.waitUntil(10_000) { tab.popup != null }
        compose.onNodeWithContentDescription("Close popup").performClick()
        compose.waitUntil(5_000) { tab.popup == null }
        assertEquals(opener, tab.url)
    }

    @Test
    fun aHalfTypedAddressSurvivesALayoutSwitch() {
        loadAndWait(titled("one"), "one navigate")
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("address").performClick()
        compose.onNodeWithTag("address").performTextReplacement("half typed")

        for (size in listOf(TABLET_LANDSCAPE, PHONE_PORTRAIT)) {
            windowSize = size
            compose.waitForIdle()
            compose.onNodeWithTag("address").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("half typed")),
            )
            compose.onNodeWithTag("address").assertIsFocused()
        }
    }

    @Test
    fun aPopupClosesItselfWithWindowClose() {
        val opener = page(
            "<title>opener</title><button style='position:fixed;left:0;top:0;width:100%;height:100%' " +
                "onclick=\"window.open('about:blank','signin')\">Sign in</button>",
        )
        loadAndWait(opener, "opener")
        show(PHONE_PORTRAIT)
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
