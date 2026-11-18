package app.jrny.browser.ui

import androidx.compose.ui.unit.dp
import app.jrny.browser.library.Bookmark
import app.jrny.browser.library.Folder
import app.jrny.browser.library.PinnedItem
import app.jrny.browser.library.ProfilePages
import app.jrny.browser.library.Visit
import app.jrny.browser.profile.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Start Page shows, and for which Tabs (SPEC, "Start Page"). */
class StartPageTest {

    private val docs = Bookmark(1, "https://docs.example.com/", "Docs")
    private val mail = Bookmark(2, "https://mail.example.com/", "Mail")
    private val board = Bookmark(3, "https://board.example.com/", "Board", folderId = 5)
    private val news = Bookmark(4, "https://news.example.com/", "News")
    private val work = Folder(5, "Work", bookmarks = listOf(board))

    private fun visit(id: Long, url: String, title: String = "", at: Long = 1_000 - id) = Visit(id, url, title, visitedAt = at)

    @Test
    fun aTabWithoutAWebPageShowsTheStartPage() {
        assertTrue(StartPage.shows(null))
        assertTrue(StartPage.shows(""))
        assertTrue(StartPage.shows("about:blank"))
        assertFalse(StartPage.shows("https://example.com/"))
        assertFalse(StartPage.shows("data:text/html,<p>hi</p>"))
    }

    @Test
    fun aPrivateTabShowsNoneOfTheProfilesPages() {
        val content = StartPage.content(private = true, pinned = listOf(PinnedItem.Page(docs)), visits = listOf(visit(1, "https://a.example/")))
        assertEquals(StartPageContent.Private, content)
    }

    @Test
    fun withNothingPinnedAndNoHistoryItShowsTheFirstRunNote() {
        assertEquals(StartPageContent.FirstRun, StartPage.content(private = false, pinned = emptyList(), visits = emptyList()))
        // An empty Folder pins nothing.
        assertEquals(StartPageContent.FirstRun, StartPage.content(private = false, pinned = listOf(PinnedItem.Group(Folder(9, "Empty"))), visits = emptyList()))
    }

    @Test
    fun onlyTheProfilesOwnPagesShowAndNothingBeforeTheyLoad() {
        val personal = ProfileId("personal")
        val work = ProfileId("work")
        val workPages = ProfilePages(work, listOf(PinnedItem.Page(docs)), listOf(visit(1, "https://a.example/", "A")))
        // Not yet loaded: no first-run note in the meantime.
        assertNull(StartPage.content(personal, private = false, pages = null))
        // Still the Profile shown before a switch.
        assertNull(StartPage.content(personal, private = false, pages = workPages))
        assertEquals(StartPageContent.FirstRun, StartPage.content(personal, private = false, pages = ProfilePages(personal, emptyList(), emptyList())))
        assertEquals(
            StartPageContent.Sections(listOf(PinnedGroup(null, listOf(docs))), listOf(visit(1, "https://a.example/", "A"))),
            StartPage.content(work, private = false, pages = workPages),
        )
        // A Private Tab shows none of them, so it needn't wait.
        assertEquals(StartPageContent.Private, StartPage.content(personal, private = true, pages = null))
    }

    @Test
    fun eitherSectionShowsOnItsOwn() {
        val pinnedOnly = StartPage.content(private = false, pinned = listOf(PinnedItem.Page(docs)), visits = emptyList())
        assertEquals(StartPageContent.Sections(listOf(PinnedGroup(null, listOf(docs))), emptyList()), pinnedOnly)

        val recentOnly = StartPage.content(private = false, pinned = emptyList(), visits = listOf(visit(1, "https://a.example/", "A")))
        assertEquals(StartPageContent.Sections(emptyList(), listOf(visit(1, "https://a.example/", "A"))), recentOnly)
    }

    @Test
    fun pinnedKeepsTheTabListsOrderWithEachFolderUnderItsName() {
        val groups = StartPage.pinnedGroups(
            listOf(PinnedItem.Page(docs), PinnedItem.Page(mail), PinnedItem.Group(work), PinnedItem.Group(Folder(6, "Empty")), PinnedItem.Page(news)),
        )
        assertEquals(
            listOf(PinnedGroup(null, listOf(docs, mail)), PinnedGroup(work, listOf(board)), PinnedGroup(null, listOf(news))),
            groups,
        )
    }

    @Test
    fun aCollapsedFolderStillShowsItsBookmarks() {
        val hidden = work.copy(collapsed = true)
        assertEquals(listOf(PinnedGroup(hidden, listOf(board))), StartPage.pinnedGroups(listOf(PinnedItem.Group(hidden))))
    }

    @Test
    fun recentListsEachPageOnceAtItsNewestVisit() {
        val visits = listOf(
            visit(1, "https://a.example/", "A again"),
            visit(2, "https://b.example/", "B"),
            visit(3, "https://a.example/", "A"),
            visit(4, "https://b.example/#comments", "B"),
            visit(5, "https://c.example/", "C"),
        )
        assertEquals(listOf(1L, 2L, 5L), StartPage.recent(visits).map { it.id })
        assertEquals("A again", StartPage.recent(visits).first().title)
    }

    @Test
    fun aVisitWithoutATitleTakesTheNewestTitleOfItsPage() {
        val visits = listOf(visit(1, "https://a.example/"), visit(2, "https://a.example/", "A"), visit(3, "https://a.example/", "Old A"))
        assertEquals(listOf(visit(1, "https://a.example/", "A")), StartPage.recent(visits))
    }

    @Test
    fun recentStopsAtSixPages() {
        val visits = (1L..10L).map { visit(it, "https://site$it.example/") }
        assertEquals((1L..6L).toList(), StartPage.recent(visits).map { it.id })
        assertEquals(6, StartPage.MAX_RECENT)
    }

    @Test
    fun tilesFillTheWidthWithAtLeastTwoAndAtMostEightToARow() {
        assertEquals(2, StartPage.columns(100.dp))
        // A phone in portrait: 360dp less the 8dp gutters.
        assertEquals(3, StartPage.columns(344.dp))
        assertEquals(4, StartPage.columns(384.dp))
        // The widest column, 720dp less its gutters.
        assertEquals(7, StartPage.columns(704.dp))
        assertEquals(8, StartPage.columns(2_000.dp))
    }
}
