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
import app.jrny.browser.profile.ProfileRepository
import app.jrny.browser.tab.SavedTab
import app.jrny.browser.tab.Tab
import app.jrny.browser.tab.TabId
import app.jrny.browser.tab.TabRepository
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
    private val profiles = ProfileRepository(database.profiles())
    private val tabs = TabRepository(database.tabs())
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
        return onMain { BrowserState(context, profiles, tabs, scope) }.also { browsers += it }
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
