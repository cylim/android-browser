package app.jrny.browser.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.data.JrnyDatabase
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.ProfileRepository
import app.jrny.browser.tab.SavedTab
import app.jrny.browser.tab.TabRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A Profile's bookmarks as Pinned Tabs, against a throwaway database: their order, their Folders,
 * and the Tab that shows each (SPEC, "Pinned Tabs").
 */
@RunWith(AndroidJUnit4::class)
class PinnedRepositoryTest {

    private val database = JrnyDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    private val profiles = ProfileRepository(database.profiles())
    private val tabs = TabRepository(database.tabs())
    private var clock = 1_000L
    private val bookmarks = BookmarkRepository(database.bookmarks()) { clock++ }
    private val personal = ProfileId("1")

    @Before
    fun firstProfile() = runBlocking { profiles.ensureFirstProfile("Personal") }

    @After
    fun close() = database.close()

    private suspend fun marked(profile: ProfileId = personal) = bookmarks.bookmarks(profile).first()
    private suspend fun bookmark(url: String) = marked().single { it.url == url }
    private suspend fun open(profile: ProfileId = personal): List<SavedTab> = tabs.tabs(profile).first()

    /** The top level as text: a bookmark by its title, a Folder as "name[titles]". */
    private suspend fun shape(profile: ProfileId = personal): List<String> = bookmarks.pinned(profile).first().map {
        when (it) {
            is PinnedItem.Page -> it.bookmark.title
            is PinnedItem.Group -> it.folder.name + it.folder.bookmarks.map(Bookmark::title)
        }
    }

    @Test
    fun bookmarksAndFoldersKeepTheOrderTheyWereAddedIn() = runBlocking {
        bookmarks.add(personal, "https://a.example/", "A")
        val folder = bookmarks.addFolder(personal, "Reading")!!
        bookmarks.add(personal, "https://b.example/", "B")
        assertEquals(listOf("A", "Reading[]", "B"), shape())

        // Moved into the Folder, a bookmark goes to its end, and out of it to the end of the top level.
        bookmarks.move(bookmark("https://b.example/"), Folder(folder, "Reading"))
        bookmarks.move(bookmark("https://a.example/"), Folder(folder, "Reading"))
        assertEquals(listOf("Reading[B, A]"), shape())
        bookmarks.move(bookmark("https://b.example/"), null)
        assertEquals(listOf("Reading[A]", "B"), shape())
        assertEquals(folder, bookmark("https://a.example/").folderId)
    }

    @Test
    fun theTopLevelAndAFolderCanBePutInANewOrder() = runBlocking {
        bookmarks.add(personal, "https://a.example/", "A")
        bookmarks.add(personal, "https://b.example/", "B")
        val folder = bookmarks.addFolder(personal, "Reading")!!
        bookmarks.add(personal, "https://c.example/", "C")
        bookmarks.add(personal, "https://d.example/", "D")
        bookmarks.move(bookmark("https://c.example/"), Folder(folder, "Reading"))
        bookmarks.move(bookmark("https://d.example/"), Folder(folder, "Reading"))
        assertEquals(listOf("A", "B", "Reading[C, D]"), shape())

        bookmarks.reorder(personal, bookmarks.pinned(personal).first().reversed())
        assertEquals(listOf("Reading[C, D]", "B", "A"), shape())
        bookmarks.reorderIn(personal, listOf(bookmark("https://d.example/"), bookmark("https://c.example/")))
        assertEquals(listOf("Reading[D, C]", "B", "A"), shape())

        // What is added next still goes to the end.
        bookmarks.add(personal, "https://e.example/", "E")
        assertEquals(listOf("Reading[D, C]", "B", "A", "E"), shape())
    }

    @Test
    fun aFolderIsRenamedCollapsedAndDeletedWhileItsBookmarksStay() = runBlocking {
        val id = bookmarks.addFolder(personal, "  Reading  ")!!
        bookmarks.add(personal, "https://a.example/", "A")
        bookmarks.move(bookmark("https://a.example/"), Folder(id, "Reading"))
        fun folder() = runBlocking { (bookmarks.pinned(personal).first().single() as PinnedItem.Group).folder }
        assertEquals("Reading", folder().name)
        assertFalse(folder().collapsed)

        bookmarks.renameFolder(folder(), "Later")
        bookmarks.renameFolder(folder(), "   ")
        bookmarks.setCollapsed(folder(), true)
        assertEquals("Later" to true, folder().name to folder().collapsed)
        assertNull(bookmarks.addFolder(personal, ""))
        assertNull(bookmarks.addFolder(personal, "x".repeat(FolderName.MAX_LENGTH + 1)))

        bookmarks.deleteFolder(folder())
        assertEquals(listOf("A"), shape())
        assertNull(bookmark("https://a.example/").folderId)
    }

    @Test
    fun aBookmarkGetsAnotherNameButNeverABlankOne() = runBlocking {
        bookmarks.add(personal, "https://a.example/", "A")
        bookmarks.rename(bookmark("https://a.example/"), "  Alpha ")
        bookmarks.rename(bookmark("https://a.example/"), "  ")
        assertEquals("Alpha", bookmark("https://a.example/").title)
    }

    @Test
    fun pinningATabBookmarksItsPageAndMakesItThatBookmarksOnlyPinnedTab() = runBlocking {
        val first = tabs.open(personal, "https://a.example/")
        assertTrue(bookmarks.pin(first, "https://a.example/", "A"))
        val pin = bookmark("https://a.example/")
        assertEquals(pin.id, open().single { it.id == first }.bookmarkId)
        assertTrue(open().single { it.id == first }.isPinned)

        // Another Tab on the same page takes the pin over: the first is an ordinary Tab again.
        val second = tabs.open(personal, "https://a.example/")
        assertTrue(bookmarks.pin(second, "https://a.example/", "A again"))
        assertEquals(listOf(pin), marked())
        assertEquals(mapOf(first to null, second to pin.id), open().associate { it.id to it.bookmarkId })

        // Pinned to another page, the Tab leaves the first bookmark closed.
        assertTrue(bookmarks.pin(second, "https://b.example/", "B"))
        assertEquals(bookmark("https://b.example/").id, open().single { it.id == second }.bookmarkId)
        assertEquals(1, open().count { it.isPinned })

        assertFalse(bookmarks.pin(second, "data:text/html,<p>no</p>", "Not a web page"))
        tabs.close(first)
        assertFalse(bookmarks.pin(first, "https://c.example/", "Gone"))
        assertEquals(listOf("A", "B"), shape())
    }

    @Test
    fun aClosedPinnedTabOpensInANewTabAndAnOpenOneIsOnlyShown() = runBlocking {
        val other = tabs.open(personal, "https://other.example/")
        bookmarks.add(personal, "https://a.example/", "A")
        val pin = bookmark("https://a.example/")

        val opened = tabs.openPinned(pin.id)!!
        val tab = open().single { it.id == opened }
        assertEquals(Triple("https://a.example/", "A", pin.id), Triple(tab.url, tab.title, tab.bookmarkId))
        assertTrue(tab.selected)

        tabs.select(other)
        assertEquals(opened, tabs.openPinned(pin.id))
        assertEquals(2, open().size)
        assertTrue(open().single { it.id == opened }.selected)

        // Closing its Tab keeps the bookmark, and it opens again later.
        tabs.close(opened)
        assertEquals(listOf(pin), marked())
        assertTrue(tabs.openPinned(pin.id) != opened)

        bookmarks.delete(pin)
        assertNull(tabs.openPinned(pin.id))
        assertTrue(open().none { it.isPinned })
    }

    @Test
    fun removingABookmarkLeavesItsTabOpenAsAnOrdinaryTab() = runBlocking {
        val tab = tabs.open(personal, "https://a.example/")
        bookmarks.pin(tab, "https://a.example/", "A")
        assertFalse(bookmarks.toggle(personal, "https://a.example/", "A"))
        assertEquals(listOf<Long?>(null), open().map { it.bookmarkId })

        bookmarks.pin(tab, "https://a.example/", "A")
        bookmarks.remove(personal, "https://a.example/")
        assertEquals(listOf<Long?>(null), open().map { it.bookmarkId })
        assertEquals(emptyList<Bookmark>(), marked())
    }

    @Test
    fun aToggleBookmarksAndPinsInOneStepAndTheNextToggleUndoesBoth() = runBlocking {
        val tab = tabs.open(personal, "https://a.example/")
        assertTrue(bookmarks.toggle(personal, "https://a.example/", "A", pinTab = tab))
        assertEquals(bookmark("https://a.example/").id, open().single().bookmarkId)

        // The second toggle removes the bookmark and leaves nothing pinned.
        assertFalse(bookmarks.toggle(personal, "https://a.example/", "A", pinTab = tab))
        assertEquals(emptyList<Bookmark>(), marked())
        assertEquals(listOf<Long?>(null), open().map { it.bookmarkId })

        // A Tab of another Profile is never taken.
        val work = profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build).id
        assertTrue(bookmarks.toggle(work, "https://w.example/", "W", pinTab = tab))
        assertEquals(listOf<Long?>(null), open().map { it.bookmarkId })
        assertEquals(1, marked(work).size)
    }

    @Test
    fun foldersAndPinsBelongToTheirProfileAndGoWithIt() = runBlocking {
        val work = profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build).id
        val reading = bookmarks.addFolder(personal, "Reading")!!
        bookmarks.add(work, "https://w.example/", "W")
        val atWork = marked(work).single()

        // A bookmark can't be moved into another Profile's Folder.
        bookmarks.move(atWork, Folder(reading, "Reading"))
        assertEquals(listOf("W"), shape(work))
        assertEquals(listOf("Reading[]"), shape())
        // Nor put in order from another Profile.
        bookmarks.reorderIn(personal, listOf(atWork))
        assertEquals(atWork, marked(work).single())

        bookmarks.addFolder(work, "Projects")
        assertTrue(profiles.delete(work))
        assertEquals(emptyList<String>(), shape(work))
        assertNull(bookmarks.addFolder(work, "Late"))
        assertEquals(listOf("Reading[]"), shape())
    }
}
