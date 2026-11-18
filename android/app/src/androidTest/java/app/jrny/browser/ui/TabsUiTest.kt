package app.jrny.browser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import app.jrny.browser.TestPages.titled
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.SavedTab
import app.jrny.browser.tab.Tab
import app.jrny.browser.tab.TabId
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Sidebar's Tab list and the Bottom Bar's Tab count, swipe and Tab Overview. */
@RunWith(AndroidJUnit4::class)
class TabsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var tab: Tab
    private val selected = mutableListOf<TabId>()
    private val closed = mutableListOf<TabId>()
    private val opened = mutableListOf<Unit>()
    private var incomingLink by mutableStateOf(false)

    private val news = TestTabs.tab(1, "News of the day", "https://news.example.com/today")
    private val mail = TestTabs.tab(2, "Inbox", "https://mail.example.com/", selected = true)
    private val empty = TestTabs.tab(3)

    @Before
    fun createTab() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        compose.runOnUiThread {
            tab = Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-tabs-ui")))
            tab.load(titled("one"))
        }
        compose.waitUntil(15_000) { !tab.isLoading && tab.title == "one navigate" }
    }

    @After
    fun destroyTab() {
        if (::tab.isInitialized) compose.runOnUiThread { tab.destroy() }
    }

    private fun show(size: DpSize, all: List<SavedTab> = listOf(news, mail, empty)) = compose.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
            JrnyTheme {
                BrowserWindow(
                    tab,
                    TestTabs.ui(all, selectedIds = selected, closed = closed, opened = opened, incomingLink = incomingLink),
                    TestProfiles.ui(),
                )
            }
        }
    }

    private fun row(tab: SavedTab) = compose.onNodeWithTag("tab-row-${tab.id.value}")

    @Test
    fun theSidebarListsTheTabsWithTheSelectedOneMarked() {
        show(TABLET_LANDSCAPE)
        compose.onNodeWithText("News of the day").assertExists()
        compose.onNodeWithText("news.example.com").assertExists()
        compose.onNodeWithText("New Tab").assertExists()
        row(mail).assertIsSelected()
        row(news).assertIsNotSelected()
    }

    @Test
    fun theSidebarSelectsClosesAndOpensTabs() {
        show(TABLET_LANDSCAPE)
        row(news).performClick()
        assertEquals(listOf(news.id), selected)

        compose.onNodeWithContentDescription("Close Inbox").performClick()
        assertEquals(listOf(mail.id), closed)

        compose.onNodeWithTag("new-tab").performClick()
        assertEquals(1, opened.size)
    }

    @Test
    fun theBottomBarShowsTheTabCountAndOpensTheOverview() {
        show(PHONE_PORTRAIT)
        compose.onNodeWithContentDescription("Show all 3 Tabs").assertExists()
        compose.onNodeWithTag("tab-overview").assertDoesNotExist()

        compose.onNodeWithTag("tab-count").performClick()
        compose.onNodeWithTag("tab-overview").assertExists()
        compose.onNodeWithText("3 Tabs").assertExists()
        row(mail).assertIsSelected()
        // Nothing of the page or the bar is left underneath to focus or to read out.
        compose.onNodeWithTag("address").assertDoesNotExist()
        compose.onNodeWithTag("page").assertDoesNotExist()
        compose.onNodeWithTag("tab-count").assertDoesNotExist()

        // Choosing a Tab selects it and goes back to the page.
        row(news).performClick()
        assertEquals(listOf(news.id), selected)
        compose.onNodeWithTag("tab-overview").assertDoesNotExist()
    }

    @Test
    fun theOverviewClosesTabsOpensOneAndGoesBack() {
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("tab-count").performClick()
        row(news).onChildren().filterToOne(hasTestTag("close-tab")).performClick()
        assertEquals(listOf(news.id), closed)
        compose.onNodeWithTag("tab-overview").assertExists()

        // Back leaves the overview.
        Espresso.pressBack()
        compose.onNodeWithTag("tab-overview").assertDoesNotExist()

        compose.onNodeWithTag("tab-count").performClick()
        compose.onNodeWithTag("close-overview").performClick()
        compose.onNodeWithTag("tab-overview").assertDoesNotExist()

        compose.onNodeWithTag("tab-count").performClick()
        compose.onNodeWithTag("new-tab").performClick()
        assertEquals(1, opened.size)
        compose.onNodeWithTag("tab-overview").assertDoesNotExist()
    }

    @Test
    fun aLinkFromAnotherAppClosesTheOverviewSoItsPageShows() {
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("tab-count").performClick()
        compose.onNodeWithTag("tab-overview").assertExists()

        incomingLink = true
        compose.onNodeWithTag("tab-overview").assertDoesNotExist()
        compose.onNodeWithTag("page").assertExists()
    }

    @Test
    fun swipingAlongTheBottomBarSwitchesTabs() {
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("bottom-bar").performTouchInput { swipeLeft() }
        assertEquals(listOf(empty.id), selected)
        compose.onNodeWithTag("bottom-bar").performTouchInput { swipeRight() }
        assertEquals(listOf(empty.id, news.id), selected)
    }

    @Test
    fun aSwipeThatStartsOnTheAddressFieldSwitchesTabs() {
        // Long enough that the field's text could scroll, which must not eat the swipe.
        compose.runOnUiThread { tab.load(titled("a page with a long address ".repeat(4))) }
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("address").performTouchInput { swipeLeft() }
        assertEquals(listOf(empty.id), selected)
        compose.onNodeWithTag("address").assertIsNotFocused()
    }

    @Test
    fun swipingPastTheLastTabDoesNothing() {
        show(PHONE_PORTRAIT, all = listOf(news, mail))
        compose.onNodeWithTag("bottom-bar").performTouchInput { swipeLeft() }
        assertEquals(emptyList<TabId>(), selected)
    }

    @Test
    fun oneTabIsCountedInTheSingular() {
        show(PHONE_PORTRAIT, all = listOf(mail))
        compose.onNodeWithContentDescription("Show 1 Tab").assertExists()
    }

    private companion object {
        val PHONE_PORTRAIT = DpSize(411.dp, 891.dp)
        val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)
    }
}
