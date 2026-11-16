package app.jrny.browser.tab

import app.jrny.browser.profile.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateTabsTest {

    private val tabs = PrivateTabs()
    private val personal = ProfileId("1")
    private val work = ProfileId("2")

    private fun open(profile: ProfileId) = tabs.current(profile)?.tabs.orEmpty()

    @Test
    fun aNewPrivateTabIsSelectedAndHasANegativeId() {
        val first = tabs.open(personal)
        val second = tabs.open(personal, "https://example.com/")
        assertTrue(first.value < 0 && second.value < 0)
        assertNotEquals(first, second)
        assertEquals(listOf(first, second), open(personal).map { it.id })
        assertEquals(listOf(false, true), open(personal).map { it.selected })
        assertTrue(open(personal).all { it.isPrivate })
        assertEquals("https://example.com/", open(personal).last().url)
    }

    @Test
    fun selectingNoneLeavesTheProfilesNormalTabToShow() {
        val a = tabs.open(personal)
        val b = tabs.open(personal)
        tabs.select(personal, a)
        assertEquals(listOf(true, false), open(personal).map { it.selected })
        tabs.select(personal, null)
        assertEquals(listOf(false, false), open(personal).map { it.selected })
        assertEquals(listOf(a, b), open(personal).map { it.id })
    }

    @Test
    fun thePageAndScrollPositionAreKeptInMemory() {
        val id = tabs.open(personal)
        tabs.savePage(personal, id, "https://example.com/page", "A page")
        tabs.saveScroll(personal, id, 800)
        val saved = open(personal).single()
        assertEquals("https://example.com/page", saved.url)
        assertEquals("A page", saved.title)
        assertEquals(800, saved.scrollY)
    }

    @Test
    fun theSessionEndsWithItsLastTabAndTheNextOneIsNew() {
        val a = tabs.open(personal)
        val b = tabs.open(personal)
        val first = tabs.current(personal)!!.session
        assertNull("one Private Tab is still open", tabs.close(personal, a))
        assertEquals(first, tabs.close(personal, b)!!.session)
        assertNull(tabs.current(personal))
        assertNull(tabs.close(personal, b))

        tabs.open(personal)
        assertNotEquals(first, tabs.current(personal)!!.session)
    }

    @Test
    fun eachProfileHasItsOwnSession() {
        tabs.open(personal)
        tabs.open(work)
        assertNotEquals(tabs.current(personal)!!.session, tabs.current(work)!!.session)
        assertEquals(tabs.current(work)!!.session, tabs.closeAll(work)!!.session)
        assertNull(tabs.current(work))
        assertNull(tabs.closeAll(work))
        assertEquals(1, open(personal).size)
    }

    @Test
    fun closingEverythingEndsEverySession() {
        tabs.open(personal)
        tabs.open(personal)
        tabs.open(work)
        val ended = tabs.closeAll()
        assertEquals(setOf(personal, work), ended.map { it.profile }.toSet())
        assertNull(tabs.current(personal))
        assertNull(tabs.current(work))
        assertEquals(emptyList<PrivateSession>(), tabs.closeAll())
    }
}
