package app.jrny.browser.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.data.HistoryEntity
import app.jrny.browser.data.JrnyDatabase
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.ProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** History and bookmarks, each per Profile, against a throwaway database. */
@RunWith(AndroidJUnit4::class)
class LibraryRepositoryTest {

    private val database = JrnyDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    private val profiles = ProfileRepository(database.profiles())
    private var clock = 1_000L
    private val history = HistoryRepository(database.history()) { clock++ }
    private val bookmarks = BookmarkRepository(database.bookmarks()) { clock++ }
    private val personal = ProfileId("1")

    @Before
    fun firstProfile() = runBlocking { profiles.ensureFirstProfile("Personal") }

    @After
    fun close() = database.close()

    private suspend fun visited(profile: ProfileId = personal) = history.visits(profile).first()
    private suspend fun marked(profile: ProfileId = personal) = bookmarks.bookmarks(profile).first()

    @Test
    fun visitsAreListedNewestFirstAndOnlyWebPagesAreKept() = runBlocking {
        history.record(personal, "https://example.com/one")
        history.record(personal, "about:blank")
        history.record(personal, "data:text/html,<p>hi</p>")
        history.record(personal, "javascript:alert(1)")
        history.record(personal, "http://example.org/two")
        assertEquals(listOf("http://example.org/two", "https://example.com/one"), visited().map { it.url })
    }

    @Test
    fun aTitleGoesToTheLatestVisitOfThatAddress() = runBlocking {
        history.record(personal, "https://example.com/")
        history.record(personal, "https://example.org/")
        history.record(personal, "https://example.com/")
        history.setTitle(personal, "https://example.com/", "Example")
        history.setTitle(personal, "https://example.org/", "   ")
        assertEquals(listOf("Example", "", ""), visited().map { it.title })
    }

    @Test
    fun visitingTheLatestAddressAgainKeepsOneVisitWithTheNewTime() = runBlocking {
        val first = history.record(personal, "https://example.com/")!!
        history.setTitle(personal, "https://example.com/", "Example")
        val again = history.record(personal, "https://example.com/")!!
        val only = visited().single()
        assertEquals(first.id, only.id)
        assertEquals(first.id, again.id)
        assertEquals("Example", only.title)
        assertEquals(1_001L, only.visitedAt)

        // Not the latest any more: a later return to it is a visit of its own.
        history.record(personal, "https://example.org/")
        history.record(personal, "https://example.com/")
        assertEquals(3, visited().size)
    }

    @Test
    fun aVisitThatWasOnlyARedirectsStopIsTakenBack() = runBlocking {
        // A new row goes.
        history.record(personal, "https://example.com/")
        val hop = history.record(personal, "https://example.com/hop")!!
        history.takeBack(hop)
        assertEquals(listOf("https://example.com/"), visited().map { it.url })

        // A repeat of the latest visit keeps its row and gets its earlier time back.
        val earlier = visited().single().visitedAt
        val repeat = history.record(personal, "https://example.com/")!!
        assertEquals(earlier, repeat.previousVisitedAt)
        history.takeBack(repeat)
        assertEquals(earlier, visited().single().visitedAt)
    }

    @Test
    fun theOldestVisitsGoOnceThereAreTooMany() = runBlocking {
        val dao = database.history()
        (1..5).forEach { n -> dao.record(HistoryEntity(profileId = 1, url = "https://example.com/$n", visitedAt = n.toLong()), keep = 3) }
        assertEquals(listOf("https://example.com/5", "https://example.com/4", "https://example.com/3"), visited().map { it.url })
    }

    @Test
    fun eachProfileHasItsOwnHistoryAndClearingOneLeavesTheOther() = runBlocking {
        val work = profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build).id
        history.record(personal, "https://example.com/personal")
        history.record(work, "https://example.com/work")
        assertEquals(listOf("https://example.com/personal"), visited(personal).map { it.url })

        history.clear(personal)
        assertEquals(emptyList<Visit>(), visited(personal))
        assertEquals(listOf("https://example.com/work"), visited(work).map { it.url })

        history.delete(visited(work).single())
        assertEquals(emptyList<Visit>(), visited(work))
    }

    @Test
    fun anAddressIsBookmarkedOnceInAProfileAndSeparatelyInAnother() = runBlocking {
        val work = profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build).id
        bookmarks.add(personal, "https://example.com/", "Example")
        bookmarks.add(personal, "https://example.com/", "Example again")
        bookmarks.add(personal, "data:text/html,<p>no</p>", "Not a web page")
        bookmarks.add(work, "https://example.com/", "Example at work")
        assertEquals(listOf("Example"), marked(personal).map { it.title })
        assertEquals(listOf("Example at work"), marked(work).map { it.title })

        bookmarks.remove(personal, "https://example.com/")
        assertEquals(emptyList<Bookmark>(), marked(personal))
        assertEquals(1, marked(work).size)
        bookmarks.delete(marked(work).single())
        assertEquals(emptyList<Bookmark>(), marked(work))
    }

    @Test
    fun deletingAProfileRemovesItsHistoryAndBookmarksAtOnceAndNothingIsAddedAfter() = runBlocking {
        val work = profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build).id
        history.record(work, "https://example.com/private-to-work")
        bookmarks.add(work, "https://example.com/private-to-work", "Work")
        history.record(personal, "https://example.com/personal")

        profiles.delete(work)

        assertEquals(emptyList<Visit>(), visited(work))
        assertEquals(emptyList<Bookmark>(), marked(work))
        // A page of the deleted Profile that is still closing must not write after the delete.
        history.record(work, "https://example.com/late")
        bookmarks.add(work, "https://example.com/late", "Late")
        assertEquals(emptyList<Visit>(), visited(work))
        assertEquals(emptyList<Bookmark>(), marked(work))
        assertEquals(1, visited(personal).size)
    }
}
