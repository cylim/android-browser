package app.jrny.browser.ui

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.TestPages.titled
import app.jrny.browser.library.Bookmark
import app.jrny.browser.library.Folder
import app.jrny.browser.library.PinnedItem
import app.jrny.browser.library.ProfilePages
import app.jrny.browser.library.Visit
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.tab.SavedTab
import app.jrny.browser.tab.Tab
import app.jrny.browser.tab.TabId
import app.jrny.browser.ui.theme.JrnyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Start Page in the place of a Tab without a web page, in the Bottom Bar and the Sidebar
 * layouts: what it shows for a Profile, for a Private Tab and before anything is pinned or
 * visited, and what choosing on it asks for. What it lists is StartPageTest's; what a Pinned Tab
 * chosen there does to the Tabs is PinnedRepositoryTest's.
 */
@RunWith(AndroidJUnit4::class)
class StartPageUiTest {

    @get:Rule
    val compose = createComposeRule()

    /** A Tab that has loaded nothing, so it shows the Start Page. */
    private lateinit var tab: Tab

    private val docs = Bookmark(1, "https://docs.example.com/", "Docs")
    private val mail = Bookmark(2, "https://mail.example.com/", "Mail")
    private val board = Bookmark(3, "https://board.example.com/", "Board", folderId = 5)
    private val work = Folder(5, "Work", bookmarks = listOf(board))

    private val empty = TestTabs.tab(1, selected = true)
    private val private = SavedTab(TabId(-1), TestProfiles.personal.id, url = null, title = "", scrollY = 0, selected = true, isPrivate = true)

    private var pinned by mutableStateOf<List<PinnedItem>>(emptyList())
    private var visits by mutableStateOf<List<Visit>>(emptyList())
    /** The Start Page's pages as the browser hands them over; by default the shown Profile's [pinned] and [visits]. */
    private var pages by mutableStateOf<(() -> ProfilePages?)>({ ProfilePages(TestProfiles.personal.id, pinned, visits) })
    private val openedHere = mutableListOf<Bookmark>()
    private val opened = mutableListOf<Bookmark>()

    @Before
    fun createTab() {
        compose.runOnUiThread {
            tab = Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-start-page-ui")))
        }
    }

    @After
    fun destroyTab() {
        if (::tab.isInitialized) compose.runOnUiThread { tab.destroy() }
    }

    private var windowSize by mutableStateOf(PHONE_PORTRAIT)
    private var shown by mutableStateOf(empty)

    /**
     * Shows the window at [size] with [selected] as the shown Tab. An empty Tab puts the keyboard
     * up for an address, which a tablet-sized window forced onto a phone's screen has no room
     * for, so it is put away.
     */
    private fun show(size: DpSize, selected: SavedTab = empty) {
        windowSize = size
        shown = selected
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(windowSize)) {
                JrnyTheme {
                    BrowserWindow(
                        tab,
                        TestTabs.ui(
                            all = listOf(shown),
                            selected = shown,
                            pins = PinsUi(items = pinned, open = { opened += it }, openHere = { openedHere += it }),
                        ),
                        TestProfiles.ui(),
                        library = LibraryUi(visits = visits),
                        startPagePages = pages(),
                    )
                }
            }
        }
        putKeyboardAway()
    }

    private fun putKeyboardAway() {
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
    }

    private fun visit(id: Long, url: String, title: String) = Visit(id, url, title, visitedAt = 1_000 - id)

    private fun contains(text: String) = hasText(text, substring = true)

    @Test
    fun anEmptyTabShowsTheStartPageWithItsProfileAndAnEmptyAddressField() {
        show(PHONE_PORTRAIT)
        listOf(PHONE_PORTRAIT, TABLET_LANDSCAPE).forEach { size ->
            windowSize = size
            putKeyboardAway()
            compose.onNodeWithTag("start-page").assertIsDisplayed()
            compose.onNodeWithTag("start-page-profile").assert(contains("Personal"))
            compose.onNodeWithTag("address").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        }
    }

    @Test
    fun withNothingPinnedAndNoHistoryItSaysHowToStart() {
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("start-page-first-run").assertIsDisplayed()
        compose.onNodeWithTag("start-page-first-run").assert(contains("Pin Tab"))
        compose.onNodeWithTag("start-page-pinned").assertDoesNotExist()
        compose.onNodeWithTag("start-page-recent").assertDoesNotExist()
    }

    @Test
    fun pinnedTabsShowAsTilesWithEachFolderUnderItsName() {
        pinned = listOf(PinnedItem.Page(docs), PinnedItem.Page(mail), PinnedItem.Group(work))
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("start-page-first-run").assertDoesNotExist()
        compose.onNodeWithTag("start-tile-1").assert(contains("Docs"))
        compose.onNodeWithTag("start-tile-2").assert(contains("Mail"))
        compose.onNodeWithTag("start-page-folder-5").assert(contains("Work"))
        // The Folder's name sits above its tiles.
        val folderLabel = compose.onNodeWithTag("start-page-folder-5").getUnclippedBoundsInRoot()
        assertTrue(compose.onNodeWithTag("start-tile-3").getUnclippedBoundsInRoot().top >= folderLabel.bottom)
        // Each tile has its site's mark: the letter, until the Profile keeps the site's icon.
        val tile = SemanticsMatcher("a tile") { it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith("start-tile-") == true }
        assertEquals(3, compose.onAllNodes(hasTestTag("site-icon") and hasAnyAncestor(tile), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun choosingATileOpensThePinnedTabFromThisTab() {
        pinned = listOf(PinnedItem.Page(docs), PinnedItem.Group(work))
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("start-tile-3").performScrollTo().performClick()
        assertEquals(listOf(board), openedHere)
        // Not as a new Tab, which would leave this empty one behind.
        assertEquals(emptyList<Bookmark>(), opened)
    }

    @Test
    fun recentListsEachPageOnceAndChoosingOneLoadsItHere() {
        val page = titled("recent")
        visits = listOf(visit(1, page, "Recent page"), visit(2, "https://news.example.com/", "News"), visit(3, page, "Recent page"))
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("start-page-recent").assertIsDisplayed()
        compose.onNodeWithTag("start-recent-1").assertExists()
        compose.onNodeWithTag("start-recent-2").assertExists()
        compose.onNodeWithTag("start-recent-3").assertDoesNotExist()

        compose.onNodeWithTag("start-recent-1").performScrollTo().performClick()
        compose.waitUntil(15_000) { tab.url == page }
        compose.onNodeWithTag("start-page").assertDoesNotExist()
        compose.onNodeWithTag("page").assertExists()
    }

    @Test
    fun showAllOpensTheHistory() {
        visits = listOf(visit(1, "https://news.example.com/", "News"))
        show(PHONE_PORTRAIT)
        listOf(PHONE_PORTRAIT, TABLET_LANDSCAPE).forEach { size ->
            windowSize = size
            putKeyboardAway()
            compose.onNodeWithTag("start-page-show-history").performScrollTo().performClick()
            compose.onNodeWithTag("panel-History").assertIsDisplayed()
            compose.onNodeWithTag("close-panel").performClick()
            compose.onNodeWithTag("start-page").assertIsDisplayed()
        }
    }

    @Test
    fun aPrivateTabSaysWhatItKeepsAndShowsNoneOfTheProfilesPages() {
        pinned = listOf(PinnedItem.Page(docs))
        visits = listOf(visit(1, "https://news.example.com/", "News"))
        show(PHONE_PORTRAIT, selected = private)
        compose.onNodeWithTag("start-page-private").assertIsDisplayed()
        compose.onNodeWithTag("start-page-private").assert(contains("history"))
        compose.onNodeWithTag("start-page-lock", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("start-page-pinned").assertDoesNotExist()
        compose.onNodeWithTag("start-page-recent").assertDoesNotExist()
        compose.onNodeWithText("News").assertDoesNotExist()
    }

    @Test
    fun inTheSidebarLayoutTheStartPageTakesThePagesPlaceBesideTheSidebar() {
        pinned = listOf(PinnedItem.Page(docs))
        show(TABLET_LANDSCAPE)
        val sidebar = compose.onNodeWithTag("sidebar").getUnclippedBoundsInRoot()
        val start = compose.onNodeWithTag("start-page").getUnclippedBoundsInRoot()
        assertTrue("the Start Page starts at ${start.left}, the Sidebar ends at ${sidebar.right}", start.left >= sidebar.right)
        compose.onNodeWithTag("start-tile-1").assertIsDisplayed()
    }

    @Test
    fun anotherProfilesPagesAreNotShownAndNothingFlashesWhileTheyLoad() {
        pinned = listOf(PinnedItem.Page(docs))
        visits = listOf(visit(1, "https://news.example.com/", "News"))
        // Still the Work Profile's, a moment after switching to Personal.
        pages = { ProfilePages(TestProfiles.work.id, pinned, visits) }
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("start-page-profile").assert(contains("Personal"))
        compose.onNodeWithTag("start-page-pinned").assertDoesNotExist()
        compose.onNodeWithTag("start-page-recent").assertDoesNotExist()
        compose.onNodeWithTag("start-page-first-run").assertDoesNotExist()

        // Not loaded yet: no first-run note either.
        pages = { null }
        compose.waitForIdle()
        compose.onNodeWithTag("start-page-first-run").assertDoesNotExist()

        pages = { ProfilePages(TestProfiles.personal.id, pinned, visits) }
        compose.onNodeWithTag("start-tile-1").assertExists()
        compose.onNodeWithTag("start-recent-1").assertExists()
    }

    @Test
    fun thePageUnderTheStartPageIsHiddenFromAScreenReader() {
        show(PHONE_PORTRAIT)
        compose.runOnIdle { assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, SessionViews.viewOf(tab.session)!!.importantForAccessibility) }
        compose.runOnUiThread { tab.load(titled("one")) }
        compose.waitUntil(15_000) { tab.url == titled("one") }
        compose.onNodeWithTag("start-page").assertDoesNotExist()
        compose.runOnIdle { assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, SessionViews.viewOf(tab.session)!!.importantForAccessibility) }
    }

    @Test
    fun aTabWithAPageShowsThePageNotTheStartPage() {
        compose.runOnUiThread { tab.load(titled("one")) }
        compose.waitUntil(15_000) { !tab.isLoading && tab.title == "one navigate" }
        show(PHONE_PORTRAIT, selected = TestTabs.one)
        compose.onNodeWithTag("start-page").assertDoesNotExist()
        compose.onNodeWithTag("page").assertExists()
    }

    private companion object {
        val PHONE_PORTRAIT = DpSize(411.dp, 891.dp)
        val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)
    }
}
