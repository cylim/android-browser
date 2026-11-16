package app.jrny.browser.tab

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.data.JrnyDatabase
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.ProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Tab rules (SPEC, "Tabs") against a throwaway database. */
@RunWith(AndroidJUnit4::class)
class TabRepositoryTest {

    private val database = JrnyDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    private val profiles = ProfileRepository(database.profiles())
    private val tabs = TabRepository(database.tabs())
    private val personal = ProfileId("1")

    @Before
    fun firstProfile() = runBlocking { profiles.ensureFirstProfile("Personal") }

    @After
    fun close() = database.close()

    private suspend fun tabsOf(profile: ProfileId = personal) = tabs.tabs(profile).first()
    private suspend fun selected(profile: ProfileId = personal) = tabsOf(profile).single { it.selected }.id

    @Test
    fun aProfileAlwaysHasOneTab() = runBlocking {
        tabs.ensureOne(personal)
        tabs.ensureOne(personal)
        val only = tabsOf().single()
        assertNull(only.url)
        assertEquals(only.id, selected())
    }

    @Test
    fun aNewTabGoesAtTheEndAndIsSelected() = runBlocking {
        val first = tabs.open(personal, "https://example.com/")
        val second = tabs.open(personal, "https://example.org/")
        assertEquals(listOf(first, second), tabsOf().map { it.id })
        assertEquals(second, selected())
        tabs.select(first)
        assertEquals(first, selected())
    }

    @Test
    fun closingTheSelectedTabSelectsTheMostRecentlyUsedOne() = runBlocking {
        val a = tabs.open(personal)
        val b = tabs.open(personal)
        val c = tabs.open(personal)
        tabs.select(a)
        tabs.select(c)
        tabs.close(c)
        assertEquals(listOf(a, b), tabsOf().map { it.id })
        assertEquals(a, selected())
    }

    @Test
    fun closingTheLastTabLeavesAnEmptyOne() = runBlocking {
        val only = tabs.open(personal, "https://example.com/")
        tabs.close(only)
        val replacement = tabsOf().single()
        assertNull(replacement.url)
        assertEquals(true, replacement.id != only)
    }

    @Test
    fun theAddressTitleAndScrollPositionAreKept() = runBlocking {
        val id = tabs.open(personal)
        tabs.savePage(id, "https://example.com/page", "A page")
        tabs.saveScroll(id, 1234)
        val saved = tabsOf().single()
        assertEquals("https://example.com/page", saved.url)
        assertEquals("A page", saved.title)
        assertEquals(1234, saved.scrollY)
    }

    @Test
    fun eachProfileHasItsOwnTabsAndSelection() = runBlocking {
        val work = profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build).id
        val mine = tabs.open(personal, "https://example.com/")
        val theirs = tabs.open(work, "https://example.org/")
        tabs.open(work)
        tabs.select(theirs)
        assertEquals(listOf(mine), tabsOf(personal).map { it.id })
        assertEquals(mine, selected(personal))
        assertEquals(theirs, selected(work))
    }

    @Test
    fun deletingAProfileClosesItsTabs() = runBlocking {
        val work = profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build).id
        tabs.open(work, "https://example.org/secret")
        profiles.delete(work)
        assertEquals(emptyList<SavedTab>(), tabsOf(work))
    }
}
