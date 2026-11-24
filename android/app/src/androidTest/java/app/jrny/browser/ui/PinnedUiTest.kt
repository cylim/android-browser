package app.jrny.browser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.TestPages.titled
import app.jrny.browser.library.Bookmark
import app.jrny.browser.library.Folder
import app.jrny.browser.library.PinnedItem
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.settings.Settings
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
 * The Pinned section of the Tab list, in the Sidebar and the Tab Overview: a Profile's bookmarks
 * as Pinned Tabs, open or closed, and its Folders. What pinning does to Tabs and bookmarks is
 * PinnedRepositoryTest's and BrowserStateTest's.
 */
@RunWith(AndroidJUnit4::class)
class PinnedUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var tab: Tab

    private val docs = Bookmark(1, "https://docs.example.com/", "Docs")
    private val mail = Bookmark(2, "https://mail.example.com/", "Mail")
    private val board = Bookmark(3, "https://board.example.com/", "Board", folderId = 5)
    private val later = listOf(Bookmark(4, "https://a.example.org/", "Article", folderId = 6), Bookmark(7, "https://b.example.org/", "Essay", folderId = 6))
    private val work = Folder(5, "Work", bookmarks = listOf(board))
    private val reading = Folder(6, "Reading", collapsed = true, bookmarks = later)
    private var items by mutableStateOf<List<PinnedItem>>(listOf(PinnedItem.Page(docs), PinnedItem.Page(mail), PinnedItem.Group(work), PinnedItem.Group(reading)))

    /** Docs is open, and its Tab has moved on to another page of the site. Board is open on its own page. */
    private val docsTab = TestTabs.tab(10, "Docs: getting started", "https://docs.example.com/start", bookmarkId = 1)
    private val boardTab = TestTabs.tab(12, "Board", "https://board.example.com/", bookmarkId = 3)
    private val news = TestTabs.tab(11, "News of the day", "https://news.example.com/today", selected = true)
    private val empty = TestTabs.tab(13)

    private var compact by mutableStateOf(false)
    private val selected = mutableListOf<TabId>()
    private val closed = mutableListOf<TabId>()
    private val opened = mutableListOf<Bookmark>()
    private val pinnedTabs = mutableListOf<SavedTab>()
    private val unpinned = mutableListOf<Bookmark>()
    private val reset = mutableListOf<Bookmark>()
    private val renamed = mutableListOf<Pair<Bookmark, String>>()
    private val moved = mutableListOf<Pair<Bookmark, Folder?>>()
    private val addedFolders = mutableListOf<Pair<String, Bookmark?>>()
    private val renamedFolders = mutableListOf<Pair<Folder, String>>()
    private val collapsed = mutableListOf<Pair<Folder, Boolean>>()
    private val deletedFolders = mutableListOf<Folder>()
    private val reorderedTop = mutableListOf<List<PinnedItem>>()
    private val reorderedInFolder = mutableListOf<List<Bookmark>>()

    @Before
    fun createTab() {
        compose.runOnUiThread {
            tab = Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-pinned-ui")))
            // With a page, so the address field doesn't take focus and open the keyboard by itself.
            tab.load(titled("one"))
        }
        compose.waitUntil(15_000) { !tab.isLoading && tab.title == "one navigate" }
    }

    @After
    fun destroyTab() {
        if (::tab.isInitialized) compose.runOnUiThread { tab.destroy() }
    }

    private fun show(size: DpSize, all: List<SavedTab> = listOf(docsTab, news, boardTab, empty)) = compose.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
            JrnyTheme {
                BrowserWindow(
                    tab,
                    TestTabs.ui(
                        all,
                        selectedIds = selected,
                        closed = closed,
                        pins = PinsUi(
                            items = items,
                            open = { opened += it },
                            pin = { pinnedTabs += it },
                            unpin = { unpinned += it },
                            reset = { reset += it },
                            rename = { bookmark, name -> renamed += bookmark to name },
                            move = { bookmark, folder -> moved += bookmark to folder },
                            addFolder = { name, first -> addedFolders += name to first },
                            renameFolder = { folder, name -> renamedFolders += folder to name },
                            setCollapsed = { folder, hidden -> collapsed += folder to hidden },
                            deleteFolder = { deletedFolders += it },
                            reorder = { reorderedTop += it },
                            reorderIn = { reorderedInFolder += it },
                        ),
                    ),
                    TestProfiles.ui(),
                    settings = SettingsUi(Settings(compactTabs = compact)),
                )
            }
        }
    }

    /**
     * Confirms the name dialog and waits for its keyboard to go. A tablet-sized window forced
     * onto a phone's screen has no room for the list while the keyboard is up, and a slow device
     * takes a moment to put it away.
     *
     * The button is pressed through its click action, not a tap at its place: on a slow device
     * the keyboard comes up a second or more after the dialog, and the dialog slides up with it,
     * so a tap can land where the button was, outside the dialog, and dismiss it. These tests
     * check what confirming does, not where the button is.
     */
    private fun confirmName() {
        compose.onNodeWithTag("confirm-name").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        Espresso.closeSoftKeyboard()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("tab-row-11").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun pin(bookmark: Bookmark) = compose.onNodeWithTag("pinned-row-${bookmark.id}")
    private fun folder(folder: Folder) = compose.onNodeWithTag("folder-row-${folder.id}")
    private fun top(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top
    private fun state(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)
    private fun closeButtonOf(bookmark: Bookmark) =
        compose.onNode(hasTestTag("close-pinned") and hasAnyAncestor(hasTestTag("pinned-row-${bookmark.id}")), useUnmergedTree = true)

    @Test
    fun withNothingPinnedThereIsNoPinnedSection() {
        items = emptyList()
        show(TABLET_LANDSCAPE, all = listOf(news, empty))
        compose.onNodeWithText("Pinned").assertDoesNotExist()
        compose.onNodeWithTag("new-folder").assertDoesNotExist()
        compose.onNodeWithText("Tabs").assertExists()
        compose.onNodeWithTag("tab-row-11").assertIsSelected()
    }

    @Test
    fun pinnedTabsAreListedAboveTheOtherTabsOpenOrClosed() {
        show(TABLET_LANDSCAPE)
        // An open Pinned Tab can be closed. A closed one is only there to be opened.
        pin(docs).assert(state("Open")).assertTextContains("Docs")
        closeButtonOf(docs).assertExists()
        pin(mail).assert(state("Closed"))
        closeButtonOf(mail).assertDoesNotExist()

        // A bookmark's open Tab is listed in its row, not once more among the Tabs.
        compose.onNodeWithTag("tab-row-10").assertDoesNotExist()
        compose.onNodeWithTag("tab-row-12").assertDoesNotExist()
        compose.onNodeWithTag("tab-row-11").assertIsSelected()
        compose.onNodeWithTag("tab-row-13").assertExists()
        pin(docs).assertIsNotSelected()

        // An open Folder shows its bookmarks. A closed one says how many it holds.
        folder(work).assert(state("Showing its Tabs"))
        pin(board).assertExists()
        folder(reading).assert(state("Hiding its Tabs")).assertTextContains("2")
        pin(later[0]).assertDoesNotExist()

        val order = listOf(
            compose.onNodeWithText("Pinned").getUnclippedBoundsInRoot().top,
            top("pinned-row-1"), top("pinned-row-2"), top("folder-row-5"), top("pinned-row-3"), top("folder-row-6"),
            compose.onNodeWithText("Tabs").getUnclippedBoundsInRoot().top,
            top("tab-row-11"), top("tab-row-13"),
        )
        assertEquals(order.sorted(), order)
    }

    @Test
    fun aPinnedTabIsOpenedShownAndClosedFromItsRow() {
        show(TABLET_LANDSCAPE)
        pin(mail).performClick()
        assertEquals(listOf(mail), opened)

        pin(docs).performClick()
        assertEquals(listOf(TabId(10)), selected)
        assertEquals(listOf(mail), opened)

        closeButtonOf(docs).performClick()
        assertEquals(listOf(TabId(10)), closed)
        assertEquals(emptyList<Bookmark>(), unpinned)
    }

    @Test
    fun theSelectedPinnedTabIsMarked() {
        show(TABLET_LANDSCAPE, all = listOf(docsTab, news.copy(selected = false), boardTab.copy(selected = true)))
        pin(board).assertIsSelected()
        pin(docs).assertIsNotSelected()
        compose.onNodeWithTag("tab-row-11").assertIsNotSelected()
    }

    @Test
    fun aLongPressUnpinsRenamesAndMovesAPinnedTab() {
        show(TABLET_LANDSCAPE)
        pin(mail).performTouchInput { longClick() }
        // Its own page shows, or it is closed: there is no page to go back to.
        compose.onNodeWithTag("pinned-reset").assertDoesNotExist()
        compose.onNodeWithTag("pinned-unpin").performClick()
        assertEquals(listOf(mail), unpinned)

        pin(mail).performTouchInput { longClick() }
        compose.onNodeWithTag("pinned-rename").performClick()
        compose.onNodeWithText("Rename Pinned Tab").assertExists()
        compose.onNodeWithTag("name-field").assertTextContains("Mail")
        compose.onNodeWithTag("name-field").performTextReplacement("   ")
        compose.onNodeWithTag("confirm-name").assertIsNotEnabled()
        compose.onNodeWithTag("name-field").performTextReplacement(" Inbox ")
        compose.onNodeWithTag("confirm-name").assertIsEnabled()
        confirmName()
        compose.onNodeWithTag("name-dialog").assertDoesNotExist()
        assertEquals(listOf(mail to "Inbox"), renamed)

        // At the top level, it can go into either Folder, or a new one.
        pin(mail).performTouchInput { longClick() }
        compose.onNodeWithTag("pinned-move").performClick()
        compose.onNodeWithTag("move-to-top").assertDoesNotExist()
        compose.onNodeWithTag("move-to-folder-6").assertExists()
        compose.onNodeWithTag("move-to-folder-5").performClick()
        compose.onNodeWithTag("move-to-folder").assertDoesNotExist()
        assertEquals(listOf<Pair<Bookmark, Folder?>>(mail to work), moved)

        // In a Folder, it can go out of it, or into another one, but not into its own again.
        pin(board).performTouchInput { longClick() }
        compose.onNodeWithTag("pinned-move").performClick()
        compose.onNodeWithTag("move-to-folder-5").assertDoesNotExist()
        compose.onNodeWithTag("move-to-top").performClick()
        assertEquals(listOf<Pair<Bookmark, Folder?>>(mail to work, board to null), moved)

        // A new Folder takes the Pinned Tab along.
        pin(board).performTouchInput { longClick() }
        compose.onNodeWithTag("pinned-move").performClick()
        compose.onNodeWithTag("move-to-new-folder").performClick()
        compose.onNodeWithText("New Folder").assertExists()
        compose.onNodeWithTag("name-field").performTextReplacement("Trips")
        confirmName()
        assertEquals(listOf<Pair<String, Bookmark?>>("Trips" to board), addedFolders)
    }

    @Test
    fun aPinnedTabThatMovedOnOffersTheWayBackToItsPage() {
        show(TABLET_LANDSCAPE)
        pin(docs).performTouchInput { longClick() }
        compose.onNodeWithTag("pinned-reset").performClick()
        assertEquals(listOf(docs), reset)

        // Board still shows the page that was pinned.
        pin(board).performTouchInput { longClick() }
        compose.onNodeWithTag("pinned-reset").assertDoesNotExist()
    }

    @Test
    fun aFolderIsCollapsedRenamedDeletedAndAdded() {
        show(TABLET_LANDSCAPE)
        folder(work).performClick()
        folder(reading).performClick()
        assertEquals(listOf(work to true, reading to false), collapsed)

        folder(work).performTouchInput { longClick() }
        compose.onNodeWithTag("folder-rename").performClick()
        compose.onNodeWithText("Rename Folder").assertExists()
        compose.onNodeWithTag("name-field").assertTextContains("Work")
        compose.onNodeWithTag("name-field").performTextReplacement("Projects")
        confirmName()
        assertEquals(listOf(work to "Projects"), renamedFolders)

        folder(work).performTouchInput { longClick() }
        compose.onNodeWithTag("folder-delete").performClick()
        assertEquals(listOf(work), deletedFolders)

        compose.onNodeWithTag("new-folder").performClick()
        compose.onNodeWithTag("confirm-name").assertIsNotEnabled()
        compose.onNodeWithTag("name-field").performTextReplacement("Trips")
        confirmName()
        assertEquals(listOf<Pair<String, Bookmark?>>("Trips" to null), addedFolders)
    }

    @Test
    fun pinnedTabsAndFoldersMoveUpAndDownWithinTheirList() {
        // Board gets company in its Folder.
        val notes = Bookmark(9, "https://notes.example.com/", "Notes", folderId = 5)
        val both = work.copy(bookmarks = listOf(board, notes))
        items = listOf(PinnedItem.Page(docs), PinnedItem.Page(mail), PinnedItem.Group(both), PinnedItem.Group(reading))
        show(TABLET_LANDSCAPE)

        // The first can only go down, the last only up.
        pin(docs).performTouchInput { longClick() }
        compose.onNodeWithTag("pinned-up").assertDoesNotExist()
        compose.onNodeWithTag("pinned-down").performClick()
        assertEquals(listOf(listOf(items[1], items[0], items[2], items[3])), reorderedTop)

        folder(reading).performTouchInput { longClick() }
        compose.onNodeWithTag("folder-down").assertDoesNotExist()
        compose.onNodeWithTag("folder-up").performClick()
        assertEquals(listOf(items[0], items[1], items[3], items[2]), reorderedTop.last())

        // Inside a Folder, the order is the Folder's own.
        pin(notes).performTouchInput { longClick() }
        compose.onNodeWithTag("pinned-down").assertDoesNotExist()
        compose.onNodeWithTag("pinned-up").performClick()
        assertEquals(listOf(listOf(notes, board)), reorderedInFolder)
        assertEquals(2, reorderedTop.size)
    }

    /** Holds the row until it lifts, drags it [by] down (or up, when negative) in small steps, and lets go. */
    private fun SemanticsNodeInteraction.holdAndDrag(by: Dp) = performTouchInput {
        down(center)
        advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
        repeat(10) { moveBy(Offset(0f, by.toPx() / 10)) }
        up()
    }

    @Test
    fun aHeldPinnedTabIsDraggedToAnotherPlace() {
        show(TABLET_LANDSCAPE)
        // Once its middle is past the middle of the row below, the two change places.
        pin(docs).holdAndDrag(60.dp)
        assertEquals(listOf(listOf(items[1], items[0], items[2], items[3])), reorderedTop)
        // No menu: the hold was a drag.
        compose.onNodeWithTag("pinned-unpin").assertDoesNotExist()
        // The list shows the new order at once, before the saved one arrives.
        assertTrue(top("pinned-row-2") < top("pinned-row-1"))

        // Dragged, but not past its neighbor: nothing changes, and there is still no menu.
        pin(docs).holdAndDrag(36.dp)
        assertEquals(1, reorderedTop.size)
        compose.onNodeWithTag("pinned-unpin").assertDoesNotExist()
    }

    @Test
    fun aScreenReadersLongPressOpensTheMenuWithoutAFinger() {
        show(TABLET_LANDSCAPE)
        pin(mail).performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithTag("pinned-unpin").performClick()
        assertEquals(listOf(mail), unpinned)

        folder(work).performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithTag("folder-delete").performClick()
        assertEquals(listOf(work), deletedFolders)
    }

    @Test
    fun aPinnedTabDroppedOnAFolderGoesIntoIt() {
        show(TABLET_LANDSCAPE)
        // Mail sits right above the Work Folder: one row down puts it on the Folder's row.
        pin(mail).holdAndDrag(48.dp)
        assertEquals(listOf<Pair<Bookmark, Folder?>>(mail to work), moved)
        assertEquals(emptyList<List<PinnedItem>>(), reorderedTop)
    }

    @Test
    fun aPinnedTabDraggedOutOfItsFolderLeavesIt() {
        show(TABLET_LANDSCAPE)
        pin(board).holdAndDrag(70.dp)
        assertEquals(listOf<Pair<Bookmark, Folder?>>(board to null), moved)
        assertEquals(emptyList<List<Bookmark>>(), reorderedInFolder)
    }

    @Test
    fun rowsOfAFolderAreDraggedWithinItAndAFolderAmongTheTopLevel() {
        val notes = Bookmark(9, "https://notes.example.com/", "Notes", folderId = 5)
        val both = work.copy(bookmarks = listOf(board, notes))
        items = listOf(PinnedItem.Page(docs), PinnedItem.Page(mail), PinnedItem.Group(both), PinnedItem.Group(reading))
        show(TABLET_LANDSCAPE)

        pin(board).holdAndDrag(60.dp)
        assertEquals("moved instead: $moved", listOf(listOf(notes, board)), reorderedInFolder)
        assertEquals(emptyList<Pair<Bookmark, Folder?>>(), moved)

        // The closed Folder at the end goes up past the open one: past that Folder's own row,
        // which is above its two Pinned Tabs.
        folder(reading).holdAndDrag((-180).dp)
        assertEquals(listOf(PinnedItem.Page(docs), PinnedItem.Page(mail), PinnedItem.Group(reading)), reorderedTop.single().take(3))
    }

    @Test
    fun anOpenFolderWithNothingInItSaysHowToFillIt() {
        items = listOf(PinnedItem.Group(Folder(8, "Empty one")))
        show(TABLET_LANDSCAPE, all = listOf(news))
        compose.onNodeWithText("Nothing here yet.", substring = true).assertExists()
    }

    @Test
    fun aTabWithAWebPageIsPinnedFromItsRow() {
        show(TABLET_LANDSCAPE)
        compose.onNodeWithTag("tab-row-11").performTouchInput { longClick() }
        compose.onNodeWithTag("tab-pin").performClick()
        assertEquals(listOf(news), pinnedTabs)

        compose.onNodeWithTag("tab-row-11").performTouchInput { longClick() }
        compose.onNodeWithTag("tab-close").performClick()
        assertEquals(listOf(TabId(11)), closed)

        // A new Tab has no page to pin.
        compose.onNodeWithTag("tab-row-13").performTouchInput { longClick() }
        compose.onNodeWithTag("tab-pin").assertDoesNotExist()
    }

    @Test
    fun onAPhoneTheTabOverviewListsThePinnedTabsAndOpeningOneLeavesIt() {
        show(PHONE_PORTRAIT)
        compose.onNodeWithTag("tab-count").performClick()
        compose.onNodeWithTag("tab-overview").assertExists()
        compose.onNodeWithText("Pinned").assertExists()
        // The other Tabs get their label once there is a Pinned section above them.
        compose.onNodeWithText("Tabs").assertExists()
        assertTrue(top("pinned-row-2") < top("tab-row-11"))

        pin(mail).performClick()
        assertEquals(listOf(mail), opened)
        compose.onNodeWithTag("tab-overview").assertDoesNotExist()
    }

    @Test
    fun everyRowSharesTheListsLines() {
        show(TABLET_LANDSCAPE)
        // A tablet-sized window forced onto a phone's screen has pixels wider than 1dp.
        val pixel = 1.5f
        fun iconOf(row: String) =
            compose.onNode(hasTestTag("site-icon") and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val label = compose.onNodeWithText("Pinned").getUnclippedBoundsInRoot()
        val pinned = iconOf("pinned-row-1")
        val inFolder = iconOf("pinned-row-3")
        val ordinary = iconOf("tab-row-11")

        // Site marks start where the labels' text does, 16dp into the list. A Folder's are set in 16dp more.
        val list = compose.onNodeWithTag("tab-list").getUnclippedBoundsInRoot()
        assertEquals(16f, (pinned.left - list.left).value, pixel)
        assertEquals(pinned.left.value, label.left.value, pixel)
        assertEquals(pinned.left.value, ordinary.left.value, pixel)
        assertEquals(16f, (inFolder.left - pinned.left).value, pixel)
        assertEquals(20f, (pinned.right - pinned.left).value, pixel)

        // Close buttons, the new-Folder button and the new-Tab button share one column.
        val closePinned = closeButtonOf(docs).getUnclippedBoundsInRoot()
        val closeTab = compose.onNode(hasTestTag("close-tab") and hasAnyAncestor(hasTestTag("tab-row-11")), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val newFolder = compose.onNodeWithTag("new-folder").getUnclippedBoundsInRoot()
        val newTab = compose.onNodeWithTag("new-tab").getUnclippedBoundsInRoot()
        assertEquals(closeTab.right.value, closePinned.right.value, pixel)
        assertEquals(closeTab.right.value, newFolder.right.value, pixel)
        assertEquals(closeTab.right.value, newTab.right.value, pixel)

        // A Pinned Tab and a Folder take one line of text, a Tab two.
        fun height(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        assertEquals(48f, height("pinned-row-1"), pixel)
        assertEquals(48f, height("folder-row-5"), pixel)
        assertEquals(56f, height("tab-row-11"), pixel)
    }

    @Test
    fun withCompactRowsEveryRowTakesOneLine() {
        compact = true
        show(TABLET_LANDSCAPE)
        fun height(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        // A tablet-sized window forced onto a phone's screen has pixels wider than 1dp.
        val pixel = 1.5f
        assertEquals(40f, height("pinned-row-1"), pixel)
        assertEquals(40f, height("folder-row-5"), pixel)
        assertEquals(40f, height("tab-row-11"), pixel)
        // A Tab says its title, and its site only with roomy rows.
        compose.onNodeWithText("News of the day").assertExists()
        compose.onNodeWithText("news.example.com").assertDoesNotExist()
        // The close buttons are still there, and still in one column.
        val closePinned = closeButtonOf(docs).getUnclippedBoundsInRoot()
        val closeTab = compose.onNode(hasTestTag("close-tab") and hasAnyAncestor(hasTestTag("tab-row-11")), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(closeTab.right.value, closePinned.right.value, pixel)
        // Lower buttons, but their icons stay under the new-Tab button's: same center.
        val newTab = compose.onNodeWithTag("new-tab").getUnclippedBoundsInRoot()
        assertEquals((newTab.left + newTab.right).value / 2, (closeTab.left + closeTab.right).value / 2, pixel)
        closeButtonOf(docs).performClick()
        assertEquals(listOf(TabId(10)), closed)

        // Back to roomy rows, live.
        compact = false
        compose.waitForIdle()
        assertEquals(56f, height("tab-row-11"), pixel)
        compose.onNodeWithText("news.example.com").assertExists()
    }

    private companion object {
        val PHONE_PORTRAIT = DpSize(411.dp, 891.dp)
        val TABLET_LANDSCAPE = DpSize(1280.dp, 800.dp)
    }
}
