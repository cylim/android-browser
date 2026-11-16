package app.jrny.browser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import app.jrny.browser.TestPages.titled
import app.jrny.browser.TestServer
import app.jrny.browser.library.Bookmark
import app.jrny.browser.library.Visit
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.Tab
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The history and bookmarks panels, and bookmarking from the page menu. */
@RunWith(AndroidJUnit4::class)
class LibraryUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var tab: Tab
    private val server = TestServer(mapOf("/news" to "<title>News</title>", "/mail" to "<title>Mail</title>"))
    private var visits by mutableStateOf(emptyList<Visit>())
    private var bookmarks by mutableStateOf(emptyList<Bookmark>())
    private val toggled = mutableListOf<Pair<String, String>>()
    private val deletedVisits = mutableListOf<Visit>()
    private val deletedBookmarks = mutableListOf<Bookmark>()
    private var cleared = 0

    @Before
    fun createTab() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        compose.runOnUiThread {
            tab = Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-library")))
        }
        visits = listOf(
            Visit(2, server.url("/mail"), "Mail", visitedAt = 1_700_000_100_000),
            Visit(1, server.url("/news"), "", visitedAt = 1_700_000_000_000),
        )
        bookmarks = listOf(Bookmark(5, server.url("/news"), "News"))
    }

    @After
    fun destroyTab() {
        if (::tab.isInitialized) compose.runOnUiThread { tab.destroy() }
        server.close()
    }

    private fun load(path: String, title: String) {
        compose.runOnUiThread { tab.load(server.url(path)) }
        compose.waitUntil(15_000) { !tab.isLoading && tab.title == title }
    }

    private fun show(size: DpSize) = compose.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
            JrnyTheme {
                BrowserWindow(
                    tab,
                    TestTabs.ui(),
                    TestProfiles.ui(),
                    LibraryUi(
                        visits = visits,
                        bookmarks = bookmarks,
                        toggleBookmark = { url, title -> toggled += url to title },
                        deleteBookmark = { deletedBookmarks += it },
                        deleteVisit = { deletedVisits += it },
                        clearHistory = { cleared++ },
                    ),
                )
            }
        }
    }

    private fun openFromMenu(item: String) {
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag(item).performClick()
    }

    @Test
    fun thePageMenuBookmarksThePageAndSaysWhenItAlreadyIs() {
        load("/mail", "Mail")
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithText("Bookmark this page").assertExists()
        compose.onNodeWithTag("menu-bookmark").assertIsEnabled().performClick()
        assertEquals(listOf(server.url("/mail") to "Mail"), toggled)

        bookmarks = bookmarks + Bookmark(6, server.url("/mail"), "Mail")
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithText("Remove bookmark").assertExists()
    }

    @Test
    fun aPageThatIsNotAWebPageCantBeBookmarked() {
        compose.runOnUiThread { tab.load(titled("local")) }
        compose.waitUntil(15_000) { !tab.isLoading && tab.title == "local navigate" }
        show(TABLET_LANDSCAPE)
        compose.onNodeWithTag("page-menu").performClick()
        compose.onNodeWithTag("menu-bookmark").assertIsNotEnabled()
    }

    @Test
    fun onATabletTheHistoryTakesThePagesPlaceBesideTheSidebar() {
        show(TABLET_LANDSCAPE)
        openFromMenu("menu-history")
        compose.onNodeWithTag("panel-History").assertExists()
        compose.onNodeWithTag("sidebar").assertExists()
        compose.onNodeWithTag("page").assertDoesNotExist()
        // A visit without a title shows its site; one with a title shows both.
        compose.onNodeWithText("Mail").assertExists()
        val site = server.url("/news").removePrefix("http://").substringBefore(':')
        assertEquals(2, compose.onAllNodesWithText(site, substring = true).fetchSemanticsNodes().size)

        compose.onNodeWithTag("close-panel").performClick()
        compose.onNodeWithTag("panel-History").assertDoesNotExist()
        compose.onNodeWithTag("page").assertExists()
    }

    @Test
    fun onAPhoneTheHistoryFillsTheScreenAndBackClosesIt() {
        show(PHONE_PORTRAIT)
        openFromMenu("menu-history")
        compose.onNodeWithTag("panel-History").assertExists()
        compose.onNodeWithTag("bottom-bar").assertDoesNotExist()
        compose.onNodeWithTag("address").assertDoesNotExist()

        Espresso.pressBack()
        compose.onNodeWithTag("panel-History").assertDoesNotExist()
        compose.onNodeWithTag("bottom-bar").assertExists()
    }

    @Test
    fun choosingAVisitOpensItInTheTabAndClosesThePanel() {
        show(TABLET_LANDSCAPE)
        openFromMenu("menu-history")
        compose.onNodeWithText("Mail").performClick()
        compose.onNodeWithTag("panel-History").assertDoesNotExist()
        compose.waitUntil(15_000) { !tab.isLoading && tab.url == server.url("/mail") && tab.title == "Mail" }
    }

    @Test
    fun aVisitCanBeRemovedAndClearingAsksFirst() {
        show(PHONE_PORTRAIT)
        openFromMenu("menu-history")
        compose.onNodeWithContentDescription("Remove Mail from history").performClick()
        assertEquals(listOf(visits[0]), deletedVisits)

        compose.onNodeWithTag("clear-history").performClick()
        compose.onNodeWithText("Clear the history of Personal?").assertExists()
        assertEquals(0, cleared)
        compose.onNodeWithTag("confirm-clear-history").performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun theBookmarksPanelOpensAndRemovesBookmarks() {
        show(TABLET_LANDSCAPE)
        openFromMenu("menu-bookmarks")
        compose.onNodeWithTag("panel-Bookmarks").assertExists()
        compose.onNodeWithContentDescription("Remove the bookmark News").performClick()
        assertEquals(listOf(bookmarks[0]), deletedBookmarks)

        compose.onNodeWithText("News").performClick()
        compose.onNodeWithTag("panel-Bookmarks").assertDoesNotExist()
        compose.waitUntil(15_000) { !tab.isLoading && tab.url == server.url("/news") && tab.title == "News" }
    }

    @Test
    fun emptyListsSayWhatWillShowThere() {
        visits = emptyList()
        bookmarks = emptyList()
        show(PHONE_PORTRAIT)
        openFromMenu("menu-history")
        compose.onNodeWithText("Pages you visit in this Profile show here.").assertExists()
        compose.onNodeWithTag("clear-history").assertDoesNotExist()
        compose.onNodeWithTag("close-panel").performClick()
        openFromMenu("menu-bookmarks")
        compose.onNodeWithText("Pages you bookmark in this Profile show here.").assertExists()
    }

    private companion object {
        val PHONE_PORTRAIT = DpSize(411.dp, 891.dp)
        val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)
    }
}
