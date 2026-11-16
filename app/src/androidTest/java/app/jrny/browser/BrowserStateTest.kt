package app.jrny.browser

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import app.jrny.browser.TestPages.await
import app.jrny.browser.TestPages.onMain
import app.jrny.browser.TestPages.titled
import app.jrny.browser.data.JrnyDatabase
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.tab.SavedTab
import app.jrny.browser.tab.Tab
import app.jrny.browser.tab.TabId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BrowserState with real WebViews and a throwaway database: at most 5 Tabs are live across all
 * Profiles, unloaded Tabs load again when shown, and Tabs come back after the app is killed.
 */
@SuppressLint("RequiresFeature")
@RunWith(AndroidJUnit4::class)
class BrowserStateTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database = JrnyDatabase.inMemory(context)
    private val repositories = Repositories(database)
    private val profiles = repositories.profiles
    private val tabs = repositories.tabs
    private val scopes = mutableListOf<CoroutineScope>()
    private val browsers = mutableListOf<BrowserState>()

    @Before
    fun firstProfile() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        runBlocking { profiles.ensureFirstProfile("Personal") }
    }

    @After
    fun close() {
        onMain { browsers.forEach(BrowserState::destroy) }
        // Wait for the state flows' queries to finish: closing the database under a running one
        // crashes the test process.
        runBlocking { scopes.forEach { it.coroutineContext.job.cancelAndJoin() } }
        database.close()
    }

    /** A BrowserState, as the activity makes one, on the shared database. */
    private fun newBrowser(): BrowserState {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scopes += scope
        return onMain { BrowserState(context, repositories, scope) }.also { browsers += it }
    }

    /** The Active Profile and its Tabs, once the database has given them. */
    private fun active(browser: BrowserState): ActiveProfileTabs {
        await("active Profile") { browser.active.value != null }
        return onMain { browser.active.value!! }
    }

    /** Waits for the Active Profile's selected Tab to be [id], then shows it, as BrowserScreen does. */
    private fun show(browser: BrowserState, id: TabId): Tab {
        await("tab $id selected") { browser.active.value?.selected?.id == id }
        return onMain {
            browser.tabFor(browser.active.value!!.selected).also { browser.shown(id) }
        }
    }

    private fun load(tab: Tab, url: String, title: String) {
        onMain { tab.load(url) }
        await("$title loaded") { !tab.isLoading && tab.title == title }
    }

    /** Waits until [id]'s saved address and title are [url] and [title]. */
    private fun awaitSaved(browser: BrowserState, id: TabId, url: String, title: String) {
        await("tab $id saved") {
            browser.active.value?.tabs?.any { it.id == id && it.url == url && it.title == title } == true
        }
    }

    @Test
    fun atMostFiveTabsAreLiveAndAnUnloadedTabLoadsAgain() {
        val browser = newBrowser()
        val personal = active(browser).profile.id
        val opened = (1..7).map { n ->
            val id = runBlocking { tabs.open(personal) }
            val tab = show(browser, id)
            load(tab, titled("page$n"), "page$n navigate")
            awaitSaved(browser, id, titled("page$n"), "page$n navigate")
            id
        }
        assertEquals(BrowserState.MAX_LIVE_TABS, onMain { browser.liveTabCount })
        assertFalse(onMain { browser.isLive(opened[0]) })
        assertFalse(onMain { browser.isLive(opened[1]) })
        (2..6).forEach { assertTrue(onMain { browser.isLive(opened[it]) }) }

        // Showing the first Tab again loads its saved address, and the least recently shown goes.
        runBlocking { tabs.select(opened[0]) }
        val reloaded = show(browser, opened[0])
        await("first page back") { !reloaded.isLoading && reloaded.url == titled("page1") && reloaded.title == "page1 navigate" }
        assertEquals(BrowserState.MAX_LIVE_TABS, onMain { browser.liveTabCount })
        assertFalse(onMain { browser.isLive(opened[2]) })
    }

    @Test
    fun theLimitCountsTabsInEveryProfile() {
        val browser = newBrowser()
        val personal = active(browser).profile.id
        val ids = mutableListOf<TabId>()
        repeat(3) {
            val id = runBlocking { tabs.open(personal) }
            show(browser, id)
            ids += id
        }
        val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
        repeat(3) {
            val id = runBlocking { tabs.open(work.id) }
            await("work active") { browser.active.value?.profile?.id == work.id }
            show(browser, id)
            ids += id
        }
        assertEquals(BrowserState.MAX_LIVE_TABS, onMain { browser.liveTabCount })
        assertFalse(onMain { browser.isLive(ids.first()) })
    }

    @Test
    fun tabsComeBackWithTheirAddressesAndSelectionAfterAKill() {
        val first = newBrowser()
        val personal = active(first).profile.id
        val a = runBlocking { tabs.open(personal) }
        load(show(first, a), titled("a"), "a navigate")
        val b = runBlocking { tabs.open(personal) }
        load(show(first, b), titled("b"), "b navigate")
        awaitSaved(first, b, titled("b"), "b navigate")
        runBlocking { tabs.select(a) }
        await("a selected") { first.active.value?.selected?.id == a }

        // A fresh BrowserState on the same database is what the app starts with after a kill.
        val second = newBrowser()
        await("tabs restored") { second.active.value?.tabs?.map(SavedTab::id)?.containsAll(listOf(a, b)) == true }
        assertEquals(a, active(second).selected.id)
        val restored = show(second, a)
        val firstSelected = active(first).selected
        assertNotSame(onMain { first.tabFor(firstSelected) }, restored)
        await("a reloaded") { !restored.isLoading && restored.url == titled("a") && restored.title == "a navigate" }
    }

    @Test
    fun aRestoredTabKeepsItsSavedTitleWhenThePageDoesNotLoad() {
        val browser = newBrowser()
        val personal = active(browser).profile.id
        // Nothing listens on this port, so the page fails to load and reports no title.
        val unreachable = "http://127.0.0.1:9/"
        val id = runBlocking { tabs.open(personal, unreachable) }
        runBlocking { tabs.savePage(id, unreachable, "Saved title") }
        // The Tab must be created from the row that already has the title.
        awaitSaved(browser, id, unreachable, "Saved title")

        // WebView reports the failure, then its error page's own title. Neither may replace the
        // saved title, on the Tab or in its saved row.
        val tab = show(browser, id)
        assertEquals("Saved title", onMain { tab.title })
        await("load failed") { !tab.isLoading }
        Thread.sleep(500)
        assertEquals("Saved title", onMain { tab.title })
        assertEquals("Saved title", runBlocking { tabs.tabs(personal).first() }.single { it.id == id }.title)
    }

    @Test
    fun anIncomingLinkOpensInTheActiveProfileAndOffersAMoveUntilAnotherTabShows() {
        val browser = newBrowser()
        val personal = active(browser).profile.id
        val before = runBlocking { tabs.open(personal) }
        show(browser, before)

        onMain { browser.openIncomingLink(titled("link")) }
        await("link opened") { browser.incomingLinkTab != null }
        val link = onMain { browser.incomingLinkTab!! }
        await("link selected") { browser.active.value?.selected?.id == link }
        assertEquals(personal, active(browser).profile.id)
        assertEquals(titled("link"), active(browser).selected.url)

        // The Tab shown before can still report itself shown after the link arrived, as it does
        // when the link starts the app. That must not end the offer.
        onMain { browser.shown(before) }
        assertEquals(link, onMain { browser.incomingLinkTab })
        show(browser, link)
        assertEquals(link, onMain { browser.incomingLinkTab })

        // Going on to another Tab does.
        runBlocking { tabs.select(before) }
        show(browser, before)
        assertEquals(null, onMain { browser.incomingLinkTab })
    }

    @Test
    fun aMoveTakesTheLivePagesAddressToTheOtherProfile() {
        val browser = newBrowser()
        val personal = active(browser).profile.id
        val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
        runBlocking { profiles.activate(personal) }
        await("personal active") { browser.active.value?.profile?.id == personal }
        val id = runBlocking { tabs.open(personal) }
        val tab = show(browser, id)
        load(tab, titled("moved"), "moved navigate")
        awaitSaved(browser, id, titled("moved"), "moved navigate")
        val saved = active(browser).selected

        onMain { browser.moveTab(saved, work) }

        await("work active") { browser.active.value?.profile?.id == work.id }
        val arrived = active(browser).selected
        assertEquals(titled("moved"), arrived.url)
        assertEquals("", arrived.title)
        val shown = show(browser, arrived.id)
        await("reloaded there") { !shown.isLoading && shown.title == "moved navigate" }
        assertNotSame(tab, shown)
        assertFalse(onMain { browser.isLive(id) })
        assertTrue(runBlocking { tabs.tabs(personal).first() }.none { it.id == id })
    }

    @Test
    fun visitsGoIntoTheHistoryOfTheirOwnProfileButRestoredPagesAndReloadsDoNot() {
        // /hop sends the page on from a script, before it has a title: a redirect's stop on the way.
        val pages = mapOf(
            "/a" to "<title>Page A</title>",
            "/b" to "<title>Page B</title>",
            "/hop" to "<script>location.replace('/b')</script>",
        )
        TestServer(pages).use { server ->
            val browser = newBrowser()
            val personal = active(browser).profile.id
            val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
            runBlocking { profiles.activate(personal) }
            await("personal active") { browser.active.value?.profile?.id == personal }

            val id = runBlocking { tabs.open(personal) }
            val tab = show(browser, id)
            load(tab, server.url("/a"), "Page A")
            load(tab, server.url("/hop"), "Page B")
            onMain { tab.reload() }
            await("reloaded") { !tab.isLoading }
            await("history has both, titled") { browser.visits.value.map { it.title } == listOf("Page B", "Page A") }
            assertEquals(listOf(server.url("/b"), server.url("/a")), onMain { browser.visits.value.map { it.url } })

            // A fresh BrowserState restores the Tab: the page loads again, and that is no new visit.
            val restarted = newBrowser()
            val restored = show(restarted, id)
            await("restored") { !restored.isLoading && restored.title == "Page B" }
            Thread.sleep(500)
            assertEquals(2, onMain { restarted.visits.value.size })

            // The other Profile has its own history: empty until the page is opened there.
            val saved = active(restarted).selected
            onMain { restarted.openInProfile(saved, work) }
            await("work active") { restarted.active.value?.profile?.id == work.id }
            await("work history") { restarted.visits.value.map { it.url } == listOf(server.url("/b")) }
            assertEquals(2, runBlocking { repositories.history.visits(personal).first() }.size)
        }
    }

    @Test
    fun aBookmarkIsToggledInTheActiveProfileOnly() {
        val browser = newBrowser()
        val personal = active(browser).profile
        onMain { browser.toggleBookmark(personal, "https://example.com/", "Example") }
        await("bookmarked") { browser.bookmarks.value.map { it.url } == listOf("https://example.com/") }

        val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
        await("work active") { browser.active.value?.profile?.id == work.id }
        await("work has none") { browser.bookmarks.value.isEmpty() }

        runBlocking { profiles.activate(personal.id) }
        await("personal's bookmark is back") { browser.bookmarks.value.size == 1 }
        onMain { browser.toggleBookmark(personal, "https://example.com/", "Example") }
        await("removed") { browser.bookmarks.value.isEmpty() }
    }

    @Test
    fun closingTheShownTabShowsAnotherAndFreesItsWebView() {
        val browser = newBrowser()
        val personal = active(browser).profile.id
        val keep = runBlocking { tabs.open(personal) }
        show(browser, keep)
        val close = runBlocking { tabs.open(personal) }
        show(browser, close)

        onMain { browser.closeTab(close) }
        await("closed") { browser.active.value?.tabs?.none { it.id == close } == true }
        assertEquals(keep, active(browser).selected.id)
        show(browser, keep)
        assertFalse(onMain { browser.isLive(close) })
    }
}
