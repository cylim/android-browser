package app.jrny.browser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Typography
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertHeightIsEqualTo
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.TestPages.page
import app.jrny.browser.TestPages.titled
import app.jrny.browser.TestServer
import app.jrny.browser.address.WebAddress
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.Tab
import app.jrny.browser.TestPages.runInPage
import app.jrny.browser.TestPages.runInPopup
import org.mozilla.geckoview.GeckoSession
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
@RunWith(AndroidJUnit4::class)
class BrowserLayoutUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var tab: Tab
    private val profiles = TestProfiles.ui()
    private var windowSize by mutableStateOf(TABLET_LANDSCAPE)

    @Before
    fun createTab() {
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
                JrnyTheme { BrowserWindow(tab, TestTabs.ui(), profiles) }
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
        val session = tab.session
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
            // The same session moved into the new layout, with the page still loaded.
            assertSame(session, tab.session)
            compose.waitUntil(5_000) { SessionViews.viewOf(session)?.isAttachedToWindow == true }
            assertEquals(one, tab.url)
            assertEquals("one navigate", tab.title)
        }
    }

    @Test
    fun aDeadContentProcessShowsAMessageAndReloadBringsThePageBack() {
        val one = titled("one")
        loadAndWait(one, "one navigate")
        show(TABLET_LANDSCAPE)
        val first = tab.session

        compose.runOnUiThread {
            first.load(GeckoSession.Loader().uri("about:crashcontent").flags(GeckoSession.LOAD_FLAGS_BYPASS_LOAD_URI_DELEGATE))
        }
        compose.waitUntil(15_000) { tab.crashed }
        compose.onNodeWithText("This page stopped working.").assertIsDisplayed()
        // The new session is already in the window, behind the message.
        compose.waitUntil(5_000) { tab.session !== first && SessionViews.viewOf(tab.session)?.isAttachedToWindow == true }

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
            if (fitsTheScreen(size)) compose.waitUntil(5_000) { SessionViews.viewOf(tab.session)?.hasFocus() == true }
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

        // A real tap: without one, the page may not open a window.
        compose.onNodeWithTag("page").performTouchInput { click(center) }
        compose.waitUntil(10_000) { tab.popup != null }
        compose.onNodeWithTag("popup").assertExists()
        assertEquals(tab.profile.name, tab.popup!!.settings.contextId)

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
            // An IP address has no registrable domain, so the whole host is the site.
            compose.onNodeWithTag("popup-site", useUnmergedTree = true).assertTextEquals(WebAddress.hostOf(popupAddress))
            // The popup's page really loaded, rather than an error page at that address.
            compose.waitUntil(10_000) { runInPopup(tab, "document.title") == "\"sign in\"" }
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
    fun aLongPopupHostShowsItsSiteWholeAndATapShowsTheFullAddress() {
        val host = "accounts.google.com." + "x".repeat(60) + ".attacker.com"
        val address = "https://$host/signin?continue=" + "y".repeat(80)
        compose.setContent {
            JrnyTheme { Box(Modifier.width(320.dp)) { PopupBar(address, onClose = {}) } }
        }
        // The site, attacker.com, shows whole.
        compose.onNodeWithTag("popup-site", useUnmergedTree = true).assertTextEquals("attacker.com")
        assertShownWhole(textLayout("popup-site"), "attacker.com")
        // Only the part before it is cut, from the start, so the part next to the site stays.
        val before = textLayout("popup-host-prefix")
        assertTrue("the part before the site is cut", before.isLineEllipsized(0))
        assertEquals(host.length - "attacker.com".length, before.getLineEnd(0, visibleEnd = true))
        assertEquals(1, textLayout("popup-address").lineCount)

        compose.onNodeWithTag("popup-site", useUnmergedTree = true).performClick()
        assertTrue("the full address wraps", textLayout("popup-address").lineCount > 1)
    }

    @Test
    fun aLongRegisteredNameShowsWholeEvenAtTwiceTheFontSize() {
        // 60 characters: DNS labels are at most 63.
        val site = "a".repeat(55) + "-bank.com"
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                JrnyTheme { Box(Modifier.width(320.dp)) { PopupBar("https://login.$site/", onClose = {}) } }
            }
        }
        compose.onNodeWithTag("popup-site", useUnmergedTree = true).assertTextEquals(site)
        val layout = textLayout("popup-site")
        assertTrue("a site this long wraps", layout.lineCount > 1)
        assertShownWhole(layout, site)
    }

    @Test
    fun aVeryLongExpandedAddressStaysWithinFiveLinesAndScrollsToItsEnd() {
        val address = "https://login.example.com/" + "segment/".repeat(180)
        compose.setContent {
            JrnyTheme { Box(Modifier.width(320.dp)) { PopupBar(address, onClose = {}) } }
        }
        compose.onNodeWithTag("popup-site", useUnmergedTree = true).performClick()

        val node = compose.onNodeWithTag("popup-address", useUnmergedTree = true)
        val lineHeight = with(compose.density) { Typography().bodySmall.lineHeight.toPx() }
        assertTrue("the address wraps past five lines", textLayout("popup-address").lineCount > 5)
        assertTrue("the box is at most five lines tall", node.fetchSemanticsNode().size.height <= lineHeight * 5 + 1)

        // The end of the address can be reached by scrolling inside the box.
        val range = node.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("the box scrolls", range.maxValue() > 0f)
        node.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        compose.waitForIdle()
        assertEquals(range.maxValue(), range.value(), 0.5f)
    }

    /** Every character of [text] is laid out and visible: no ellipsis, nothing cut off. */
    private fun assertShownWhole(layout: TextLayoutResult, text: String) {
        assertEquals(text, layout.layoutInput.text.text)
        for (line in 0 until layout.lineCount) assertTrue("line $line is cut", !layout.isLineEllipsized(line))
        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        // No wrapped line is cut off at the bottom. (Width isn't checked: a Text sized to its content
        // reports didOverflowWidth while its paragraph was laid out at the full available width.)
        assertTrue("lines are cut off at the bottom", !layout.didOverflowHeight)
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
        // As a sign-in popup does once it has signed the person in.
        TestServer(mapOf("/done" to "<title>done</title><script>setTimeout(function(){window.close()},500)</script>")).use { server ->
            val opener = page(
                "<title>opener</title><button style='position:fixed;left:0;top:0;width:100%;height:100%' " +
                    "onclick=\"window.open('${server.url("/done")}','signin')\">Sign in</button>",
            )
            loadAndWait(opener, "opener")
            show(PHONE_PORTRAIT)
            compose.onNodeWithTag("page").performTouchInput { click(center) }
            compose.waitUntil(10_000) { tab.popup != null }
            compose.waitUntil(10_000) { tab.popup == null }
            assertTrue(SessionViews.viewOf(tab.session)?.isAttachedToWindow == true)
        }
    }

    @Test
    fun theSidebarsListEndsAboveTheKeyboardAndTheAddressFieldKeepsItsPlace() {
        // Only where a tablet-sized window is the real size, and so is the keyboard's place in it.
        assumeTrue(fitsTheScreen(TABLET_LANDSCAPE))
        loadAndWait(titled("one"), "one navigate")
        show(TABLET_LANDSCAPE)
        compose.onNodeWithTag("address").performClick()
        compose.waitForIdle()
        val window = (compose.onNodeWithTag("sidebar").fetchSemanticsNode().root as ViewRootForTest).view.rootView
        fun keyboard() = ViewCompat.getRootWindowInsets(window)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        // Not every test device shows a keyboard. Where one shows, give it time to come up.
        runCatching { compose.waitUntil(3_000) { keyboard() > 0 } }
        assumeTrue("no on-screen keyboard on this device", keyboard() > 0)

        val fieldTop = compose.onNodeWithTag("address").fetchSemanticsNode().boundsInWindow.top
        fun listEndsAt() = compose.onNodeWithTag("tab-list").fetchSemanticsNode().boundsInWindow.bottom
        runCatching { compose.waitUntil(5_000) { listEndsAt() <= window.height - keyboard() } }
        assertTrue("the list ends at ${listEndsAt()}, the keyboard starts at ${window.height - keyboard()}", listEndsAt() <= window.height - keyboard())
        // It ends at the keyboard, not somewhere above it: it gave up no more room than it had to.
        val slack = 4 * window.resources.displayMetrics.density
        assertTrue(
            "the list ends at ${listEndsAt()}, well above the keyboard at ${window.height - keyboard()}",
            listEndsAt() >= window.height - keyboard() - slack,
        )
        // Only the list gave way. The field is where it was.
        assertEquals(fieldTop, compose.onNodeWithTag("address").fetchSemanticsNode().boundsInWindow.top)
    }

    @Test
    fun theHintStaysOnOneLineInAPhonesNarrowField() {
        // An empty Tab shows the hint. A hint that wraps makes the field, and the bar, taller.
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("address").assertHeightIsEqualTo(56.dp)
        windowSize = PHONE_SMALL
        compose.onNodeWithTag("address").assertHeightIsEqualTo(56.dp)
    }

    @Test
    fun aRestoredTabScrollsBackToWhereItWas() {
        TestServer(mapOf("/tall" to "<title>tall</title><div style='height:20000px'>top</div>")).use { server ->
            show(PHONE_PORTRAIT)
            val tall = server.url("/tall")
            compose.runOnUiThread {
                tab.restore(tall, "", 1500)
                // Saved while it's still loading, the Tab keeps the position it's going back to.
                assertEquals(1500, tab.scrollY)
            }
            compose.waitUntil(15_000) { !tab.isLoading && tab.title == "tall" }
            // The page itself, not the position the Tab is still waiting to apply.
            compose.waitUntil(15_000) { runInPage(tab, "window.scrollY") == "1500" }
            compose.waitUntil(5_000) { tab.scrollY == 1500 }
        }
    }

    private companion object {
        val PHONE_PORTRAIT = DpSize(411.dp, 891.dp)
        val PHONE_SMALL = DpSize(360.dp, 740.dp)
        val PHONE_LANDSCAPE = DpSize(891.dp, 411.dp)
        val TABLET_PORTRAIT = DpSize(800.dp, 1280.dp)
        val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)
    }
}
