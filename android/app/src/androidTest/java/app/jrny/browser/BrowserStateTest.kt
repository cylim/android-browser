package app.jrny.browser

import android.annotation.SuppressLint
import android.content.Context
import androidx.webkit.WebSettingsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import app.jrny.browser.TestPages.await
import app.jrny.browser.TestPages.onMain
import app.jrny.browser.TestPages.titled
import app.jrny.browser.data.JrnyDatabase
import app.jrny.browser.download.Download
import app.jrny.browser.download.DownloadRequest
import app.jrny.browser.download.DownloadStatus
import app.jrny.browser.profile.WebProfile
import app.jrny.browser.profile.ProfileColor
import app.jrny.browser.profile.ProfileIcon
import app.jrny.browser.rules.SitePattern
import app.jrny.browser.rules.SiteRule
import app.jrny.browser.library.PinnedItem
import app.jrny.browser.library.Visit
import app.jrny.browser.settings.SearchEngine
import app.jrny.browser.settings.SearchSuggestions
import app.jrny.browser.settings.SettingsRepository
import app.jrny.browser.settings.Suggestion
import app.jrny.browser.tab.PrivateTabs
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
    private val downloader = FakeDownloader()
    private var privateTabs = PrivateTabs()
    private val settingsFile = "test-settings-${System.nanoTime()}"
    private val settings = SettingsRepository(context.getSharedPreferences(settingsFile, Context.MODE_PRIVATE))

    /** Suggests "<query> one" and "<query> two", and records what it was asked. */
    private val asked = java.util.Collections.synchronizedList(mutableListOf<Pair<SearchEngine, String>>())
    private val suggestions = SearchSuggestions { engine, query ->
        asked += engine to query
        listOf("$query one", "$query two")
    }
    private val iconFolder = java.io.File(context.cacheDir, "test-site-icons-${System.nanoTime()}")
    private val siteIcons = app.jrny.browser.icons.SiteIconStore(iconFolder)
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
        context.deleteSharedPreferences(settingsFile)
        iconFolder.deleteRecursively()
    }

    /** A BrowserState, as the activity makes one, on the shared database. */
    private fun newBrowser(): BrowserState {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scopes += scope
        val services = BrowserServices(repositories, downloader, privateTabs, settings, suggestions, siteIcons)
        return onMain { BrowserState(context, services, scope) }.also { browsers += it }
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

    /** Opens a Private Tab in the Active Profile and returns it, shown. */
    private fun openPrivate(browser: BrowserState): Pair<TabId, Tab> {
        val profile = active(browser).profile
        onMain { browser.openPrivateTab(profile) }
        await("private tab selected") { browser.active.value?.selected?.isPrivate == true }
        val id = active(browser).selected.id
        return id to show(browser, id)
    }

    /** Loads [url] and waits for the page's title to satisfy [title]. */
    private fun loadUntil(tab: Tab, url: String, title: (String) -> Boolean) {
        onMain { tab.load(url) }
        await("loaded $url") { !tab.isLoading && tab.url == url && title(tab.title) }
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
    fun aSiteRuleSendsAnIncomingLinkToItsProfileWhichBecomesTheActiveProfile() {
        TestServer(mapOf("/work/board" to "<title>board</title>", "/news" to "<title>news</title>")).use { server ->
            val browser = newBrowser()
            val personal = active(browser).profile
            val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
            runBlocking { profiles.activate(personal.id) }
            await("personal active") { browser.active.value?.profile?.id == personal.id }
            val site = SitePattern.forSiteOf(server.url("/"))!!.text
            onMain { browser.saveSiteRule(null, SitePattern.parse("$site/work")!!, work) }
            await("rule saved") { browser.siteRules.value.size == 1 }
            // A Private Tab is what the person left the app from, in the Profile the rule names.
            onMain { browser.openPrivateTab(work) }

            // No rule covers this one: it opens in the Active Profile.
            onMain { browser.openIncomingLink(server.url("/news")) }
            await("news opened") { browser.active.value?.selected?.url == server.url("/news") }
            assertEquals(personal.id, active(browser).profile.id)
            assertEquals(null, (onMain { browser.linkOffer } as LinkOffer.Opened).rule)

            // The rule covers this one: it opens in Work, which becomes the Active Profile, in a
            // saved Tab and not the Private Tab.
            onMain { browser.openIncomingLink(server.url("/work/board?card=7")) }
            await("board opened in Work") {
                browser.active.value?.let { it.profile.id == work.id && it.selected.url == server.url("/work/board?card=7") } == true
            }
            assertFalse(active(browser).selected.isPrivate)
            val offer = onMain { browser.linkOffer } as LinkOffer.Opened
            assertEquals(active(browser).selected.id, offer.tab)
            assertEquals("$site/work", offer.rule?.pattern?.text)
            // The visit is in Work's history, and Personal has only its own link.
            await("visit recorded") { browser.visits.value.any { it.url == server.url("/work/board?card=7") } }
            assertEquals(listOf(server.url("/news")), runBlocking { repositories.history.visits(personal.id).first() }.map { it.url })
        }
    }

    @Test
    fun movingAnIncomingLinkOffersARuleForItsSiteAndTakingItSendsTheNextLinkThere() {
        TestServer(mapOf("/a" to "<title>a</title>", "/b" to "<title>b</title>")).use { server ->
            val browser = newBrowser()
            val personal = active(browser).profile
            val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
            runBlocking { profiles.activate(personal.id) }
            await("personal active") { browser.active.value?.profile?.id == personal.id }
            val site = SitePattern.forSiteOf(server.url("/"))!!

            onMain { browser.openIncomingLink(server.url("/a")) }
            await("link opened") { browser.incomingLinkTab != null }
            val link = onMain { browser.incomingLinkTab!! }
            show(browser, link)
            val moving1 = active(browser).selected
            onMain { browser.moveTab(moving1, work) }

            await("rule offered") { browser.linkOffer is LinkOffer.Always }
            val offer = onMain { browser.linkOffer } as LinkOffer.Always
            assertEquals(site to work.id, offer.pattern to offer.profile.id)
            await("moved Tab selected") { browser.active.value?.selected?.id == offer.tab }
            assertEquals(work.id, active(browser).profile.id)
            show(browser, offer.tab)
            // Nothing is saved before the person agrees.
            assertEquals(emptyList<SiteRule>(), onMain { browser.siteRules.value })

            onMain { browser.acceptRuleOffer() }
            await("rule saved") { browser.linkOffer is LinkOffer.RuleSaved }
            await("rule listed") { browser.siteRules.value.map { it.pattern to it.profileId } == listOf(site to work.id) }

            // From Personal, the next link to that site opens in Work, and moving isn't offered a rule again.
            runBlocking { profiles.activate(personal.id) }
            await("personal active") { browser.active.value?.profile?.id == personal.id }
            show(browser, active(browser).selected.id)
            assertEquals(null, onMain { browser.linkOffer })
            onMain { browser.openIncomingLink(server.url("/b")) }
            await("b opened in Work") { browser.active.value?.let { it.profile.id == work.id && it.selected.url == server.url("/b") } == true }

            // Moved to Personal by hand, against the rule: a rule for Personal is offered, and
            // taking it replaces the one for Work.
            show(browser, active(browser).selected.id)
            val moving2 = active(browser).selected
            onMain { browser.moveTab(moving2, personal) }
            await("rule for Personal offered") { (browser.linkOffer as? LinkOffer.Always)?.profile?.id == personal.id }
            onMain { browser.acceptRuleOffer() }
            await("rule replaced") { browser.siteRules.value.map { it.pattern to it.profileId } == listOf(site to personal.id) }
        }
    }

    @Test
    fun movingALinkThatANarrowRuleSentOffersToChangeThatRule() {
        TestServer(mapOf("/work/board" to "<title>board</title>")).use { server ->
            val browser = newBrowser()
            val personal = active(browser).profile
            val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
            runBlocking { profiles.activate(personal.id) }
            await("personal active") { browser.active.value?.profile?.id == personal.id }
            val site = SitePattern.forSiteOf(server.url("/"))!!
            val narrow = SitePattern.parse("${site.text}/work")!!
            onMain { browser.saveSiteRule(null, narrow, work) }
            await("rule saved") { browser.siteRules.value.size == 1 }

            onMain { browser.openIncomingLink(server.url("/work/board")) }
            await("opened in Work") { browser.active.value?.let { it.profile.id == work.id && it.selected.url == server.url("/work/board") } == true }
            show(browser, active(browser).selected.id)
            val moving = active(browser).selected
            onMain { browser.moveTab(moving, personal) }

            // A rule for the whole site would lose to the narrow one. The narrow one is offered.
            await("rule offered") { browser.linkOffer is LinkOffer.Always }
            val offer = onMain { browser.linkOffer } as LinkOffer.Always
            assertEquals(narrow to personal.id, offer.pattern to offer.profile.id)
            onMain { browser.acceptRuleOffer() }
            await("rule changed") { browser.siteRules.value.map { it.pattern to it.profileId } == listOf(narrow to personal.id) }

            // The same link again, from Work: it opens in Personal now.
            runBlocking { profiles.activate(work.id) }
            await("work active") { browser.active.value?.profile?.id == work.id }
            show(browser, active(browser).selected.id)
            onMain { browser.openIncomingLink(server.url("/work/board")) }
            await("opened in Personal") {
                browser.active.value?.let { it.profile.id == personal.id && it.selected.url == server.url("/work/board") } == true
            }
        }
    }

    @Test
    fun takingTheOfferChangesTheDecidingRuleAndKeepsItTheOlderOfTwoEquallyNarrowOnes() {
        TestServer(mapOf("/a" to "<title>a</title>")).use { server ->
            val browser = newBrowser()
            val personal = active(browser).profile
            val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
            runBlocking { profiles.activate(work.id) }
            await("work active") { browser.active.value?.profile?.id == work.id }
            // Two rules that cover the link and name as much of it: the older decides.
            val host = SitePattern.forSiteOf(server.url("/"))!!.text
            val older = SitePattern.parse(host.dropLast(1) + "*")!!
            val younger = SitePattern.parse("*" + host.drop(1))!!
            assertEquals(older.weight, younger.weight)
            onMain { browser.saveSiteRule(null, older, personal) }
            await("first rule saved") { browser.siteRules.value.size == 1 }
            onMain { browser.saveSiteRule(null, younger, personal) }
            await("second rule saved") { browser.siteRules.value.size == 2 }
            val deciding = onMain { browser.siteRules.value.first() }

            onMain { browser.openIncomingLink(server.url("/a")) }
            await("opened in Personal") { browser.active.value?.let { it.profile.id == personal.id && it.selected.url == server.url("/a") } == true }
            show(browser, active(browser).selected.id)
            val moving = active(browser).selected
            onMain { browser.moveTab(moving, work) }
            await("rule offered") { browser.linkOffer is LinkOffer.Always }
            assertEquals(deciding, (onMain { browser.linkOffer } as LinkOffer.Always).rule)
            onMain { browser.acceptRuleOffer() }

            // The same rule, with its place among the rules, now names Work.
            await("rule changed") {
                browser.siteRules.value.map { Triple(it.id, it.pattern, it.profileId) } ==
                    listOf(Triple(deciding.id, older, work.id), Triple(deciding.id + 1, younger, personal.id))
            }
            runBlocking { profiles.activate(personal.id) }
            await("personal active") { browser.active.value?.profile?.id == personal.id }
            show(browser, active(browser).selected.id)
            onMain { browser.openIncomingLink(server.url("/a")) }
            await("opened in Work") { browser.active.value?.let { it.profile.id == work.id && it.selected.url == server.url("/a") } == true }
        }
    }

    @Test
    fun aMoveFromTheMenuOrToTheRulesOwnProfileOffersNoRule() {
        TestServer(mapOf("/a" to "<title>a</title>")).use { server ->
            val browser = newBrowser()
            val personal = active(browser).profile
            val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
            runBlocking { profiles.activate(personal.id) }
            await("personal active") { browser.active.value?.profile?.id == personal.id }

            // An ordinary Tab, moved from the page menu.
            val id = runBlocking { tabs.open(personal.id, server.url("/a")) }
            show(browser, id)
            val moving3 = active(browser).selected
            onMain { browser.moveTab(moving3, work) }
            await("moved to Work") { browser.active.value?.profile?.id == work.id }
            Thread.sleep(300)
            assertEquals(null, onMain { browser.linkOffer })

            // An Incoming Link, moved to the Profile that a rule made meanwhile already names.
            runBlocking { profiles.activate(personal.id) }
            await("personal active") { browser.active.value?.profile?.id == personal.id }
            show(browser, active(browser).selected.id)
            onMain { browser.openIncomingLink(server.url("/a")) }
            await("link opened") { browser.incomingLinkTab != null }
            show(browser, onMain { browser.incomingLinkTab!! })
            onMain { browser.saveSiteRule(null, SitePattern.forSiteOf(server.url("/"))!!, work) }
            await("rule saved") { browser.siteRules.value.size == 1 }
            val moving4 = active(browser).selected
            onMain { browser.moveTab(moving4, work) }
            await("moved to Work") { browser.active.value?.profile?.id == work.id }
            Thread.sleep(300)
            assertEquals(null, onMain { browser.linkOffer })
        }
    }

    @Test
    fun aRuleForADeletedProfileIsGoneAndTheLinkOpensInTheActiveProfile() {
        TestServer(mapOf("/a" to "<title>a</title>")).use { server ->
            val browser = newBrowser()
            val personal = active(browser).profile
            val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
            onMain { browser.saveSiteRule(null, SitePattern.forSiteOf(server.url("/"))!!, work) }
            await("rule saved") { browser.siteRules.value.size == 1 }

            onMain { browser.delete(work) }
            await("personal active") { browser.active.value?.profile?.id == personal.id }
            await("rule gone") { browser.siteRules.value.isEmpty() }

            onMain { browser.openIncomingLink(server.url("/a")) }
            await("opened in Personal") { browser.active.value?.selected?.url == server.url("/a") }
            assertEquals(personal.id, active(browser).profile.id)
            assertEquals(null, (onMain { browser.linkOffer } as LinkOffer.Opened).rule)
        }
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
            "/untitled" to "<p>A page that never gives itself a title.</p>",
        )
        TestServer(pages).use { server ->
            val browser = newBrowser()
            val personal = active(browser).profile.id
            val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
            runBlocking { profiles.activate(personal) }
            await("personal active") { browser.active.value?.profile?.id == personal }

            val id = runBlocking { tabs.open(personal) }
            val tab = show(browser, id)
            // A page without a title is still a page the person saw: it stays when the next one loads.
            onMain { tab.load(server.url("/untitled")) }
            await("untitled visited") { browser.visits.value.any { it.url == server.url("/untitled") } }
            await("untitled loaded") { !tab.isLoading && tab.url == server.url("/untitled") }
            load(tab, server.url("/a"), "Page A")
            await("untitled stays") { browser.visits.value.map { it.url } == listOf(server.url("/a"), server.url("/untitled")) }
            onMain { browser.deleteVisit(browser.visits.value.last()) }
            await("removed for the checks below") { browser.visits.value.size == 1 }
            load(tab, server.url("/hop"), "Page B")
            onMain { tab.reload() }
            await("reloaded") { !tab.isLoading }
            try {
                await("history has both, titled") { browser.visits.value.map { it.title } == listOf("Page B", "Page A") }
            } catch (e: AssertionError) {
                throw AssertionError("history: " + onMain { browser.visits.value.map { it.url.substringAfterLast('/') + "=" + it.title } })
            }
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
        val shown = active(browser).selected
        onMain { browser.toggleBookmark(shown, "https://example.com/", "Example") }
        await("bookmarked") { browser.bookmarks.value.map { it.url } == listOf("https://example.com/") }
        // The Tab that showed the page is the bookmark's Pinned Tab now.
        await("pinned") { browser.active.value?.selected?.bookmarkId == browser.bookmarks.value.single().id }

        val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
        await("work active") { browser.active.value?.profile?.id == work.id }
        await("work has none") { browser.bookmarks.value.isEmpty() }

        runBlocking { profiles.activate(personal.id) }
        await("personal's bookmark is back") { browser.bookmarks.value.size == 1 }
        onMain { browser.toggleBookmark(shown, "https://example.com/", "Example") }
        await("removed") { browser.bookmarks.value.isEmpty() }
        // Its Tab stays open, as an ordinary Tab.
        await("unpinned") { browser.active.value?.tabs?.single { it.id == shown.id }?.isPinned == false }
    }

    @Test
    fun aPinnedTabIsClosedAndOpenedAgainWhileItsBookmarkStays() {
        TestServer(mapOf("/pin" to "<title>Pinned page</title>", "/other" to "<title>Other page</title>")).use { server ->
            val browser = newBrowser()
            val personal = active(browser).profile
            val id = runBlocking { tabs.open(personal.id) }
            val tab = show(browser, id)
            load(tab, server.url("/pin"), "Pinned page")

            // Pinned from the page the Tab shows now, which can be newer than its saved row.
            val unsaved = active(browser).tabs.single { it.id == id }.copy(url = null, title = "")
            onMain { browser.pinTab(unsaved) }
            await("pinned") { browser.bookmarks.value.map { it.url to it.title } == listOf(server.url("/pin") to "Pinned page") }
            val bookmark = onMain { browser.bookmarks.value.single() }
            await("the Tab is its Pinned Tab") { browser.active.value?.tabs?.single { it.id == id }?.bookmarkId == bookmark.id }
            await("listed under Pinned") { browser.pinned.value == listOf(PinnedItem.Page(bookmark)) }

            // Closing the Pinned Tab closes its Tab. The bookmark stays, closed.
            onMain { browser.closeTab(id) }
            await("closed") { browser.active.value?.tabs?.none { it.id == id } == true }
            assertEquals(listOf(bookmark), onMain { browser.bookmarks.value })
            assertTrue(active(browser).tabs.none { it.isPinned })

            // Opening it gives it a new Tab, which loads the bookmark's address.
            onMain { browser.openPinned(personal, bookmark) }
            await("opened again") { browser.active.value?.selected?.bookmarkId == bookmark.id }
            val reopened = active(browser).selected
            assertEquals(server.url("/pin"), reopened.url)
            val page = show(browser, reopened.id)
            await("loaded") { !page.isLoading && page.title == "Pinned page" }

            // Opening it again only shows that Tab.
            val other = runBlocking { tabs.open(personal.id) }
            show(browser, other)
            onMain { browser.openPinned(personal, bookmark) }
            await("shown again") { browser.active.value?.selected?.id == reopened.id }
            assertEquals(1, active(browser).tabs.count { it.bookmarkId == bookmark.id })

            // Unpinned, its Tab stays open as an ordinary Tab.
            onMain { browser.deleteBookmark(bookmark) }
            await("unpinned") { browser.bookmarks.value.isEmpty() && browser.active.value?.tabs?.none { it.isPinned } == true }
            assertTrue(active(browser).tabs.any { it.id == reopened.id })
        }
    }

    @Test
    fun aSitesIconIsKeptForItsProfileAndGoesWithTheHistoryUnlessItIsBookmarked() {
        // A page that names its own icon, a red square, so nothing is fetched from the network.
        val redSquare = "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAIAAACQkWg2AAAAF0lEQVR4nGP4z8BAEiJN9aiGUQ1DSgMAkPn/Afnh+ngAAAAASUVORK5CYII="
        val page = "<title>Icon page</title><link rel=\"icon\" type=\"image/png\" href=\"data:image/png;base64,$redSquare\"><p>hi</p>"
        TestServer(mapOf("/icon" to page)).use { server ->
            val browser = newBrowser()
            val personal = active(browser).profile
            val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
            runBlocking { profiles.activate(personal.id) }
            await("personal active") { browser.active.value?.profile?.id == personal.id }
            val id = runBlocking { tabs.open(personal.id) }
            val tab = show(browser, id)
            // WebView reports a page's icon some time after the page, and on a slow device not
            // on every load of a page nobody is looking at. Loading it again asks again.
            var kept = false
            repeat(4) {
                if (!kept) {
                    load(tab, server.url("/icon"), "Icon page")
                    kept = runCatching { await("icon kept", timeoutMs = 8_000) { browser.siteIcons.cached(personal.id, server.url("/")) != null } }.isSuccess
                }
            }
            assertTrue("the page's icon was never reported", kept)
            // Only in the Profile that showed the page.
            assertEquals(null, runBlocking { siteIcons.load(work.id, server.url("/")) })
            val before = onMain { browser.siteIcons.version.value }

            // Clearing the history takes the icon along, unless a bookmark or an open Tab still names the site.
            // The page the Tab shows now is what counts, even while its saved row still says another.
            runBlocking { tabs.savePage(id, "https://stale.example/", "Stale") }
            onMain { browser.clearHistory(personal) }
            await("history cleared") { browser.siteIcons.version.value > before }
            assertTrue(runBlocking { siteIcons.load(personal.id, server.url("/")) } != null)
            onMain { browser.closeTab(id) }
            await("tab closed") { browser.active.value?.tabs?.none { it.id == id } == true }
            val again = onMain { browser.siteIcons.version.value }
            onMain { browser.clearHistory(personal) }
            await("history cleared again") { browser.siteIcons.version.value > again }
            assertEquals(null, runBlocking { siteIcons.load(personal.id, server.url("/")) })
        }
    }

    @Test
    fun aPrivateTabIsNeverPinnedAndFoldersBelongToTheirProfile() {
        val browser = newBrowser()
        val personal = active(browser).profile
        onMain { browser.openPrivateTab(personal) }
        await("private tab shown") { browser.active.value?.selected?.isPrivate == true }
        val private = active(browser).selected.copy(url = "https://example.com/secret", title = "Secret")
        onMain { browser.pinTab(private) }
        // Bookmarking its page is the person's own choice, and leaves the Tab private.
        onMain { browser.toggleBookmark(private, "https://example.com/kept", "Kept") }
        await("bookmarked") { browser.bookmarks.value.map { it.url } == listOf("https://example.com/kept") }
        assertTrue(active(browser).tabs.none { it.isPinned })

        val kept = onMain { browser.bookmarks.value.single() }
        onMain { browser.addFolder(personal, "  Reading  ", first = kept) }
        await("in its Folder") { (browser.pinned.value.singleOrNull() as? PinnedItem.Group)?.folder?.bookmarks?.map { it.id } == listOf(kept.id) }
        assertEquals("Reading", (onMain { browser.pinned.value }.single() as PinnedItem.Group).folder.name)

        val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
        await("work active") { browser.active.value?.profile?.id == work.id }
        await("work has nothing pinned") { browser.pinned.value.isEmpty() }
    }

    @Test
    fun aDownloadUsesItsProfilesCookiesAndIsListedInThatProfileOnly() {
        val browser = newBrowser()
        val personal = active(browser).profile
        val file = DownloadRequest("https://example.com/files/report.pdf", "TestAgent", "report.pdf", "application/pdf", 2048)
        val cookie = "jrny_download=${System.nanoTime()}"
        val set = java.util.concurrent.CountDownLatch(1)
        onMain { WebProfile.of(personal.id).cookieManager.setCookie("https://example.com", "$cookie; Max-Age=600; Secure; Path=/") { set.countDown() } }
        assertTrue(set.await(5, java.util.concurrent.TimeUnit.SECONDS))

        onMain { browser.startDownload(personal, file) }
        await("listed") { browser.downloads.value.map { it.fileName } == listOf("report.pdf") }
        val started = downloader.started.single()
        assertEquals(file, started.request)
        assertTrue(started.cookies, started.cookies!!.contains(cookie))
        val listed = onMain { browser.downloads.value.single() }
        assertEquals(started.id, listed.downloadId)
        assertEquals(mapOf(listed.id to DownloadStatus.Running(null)), runBlocking { browser.downloadStatuses(listOf(listed)) })

        // Another Profile has its own list, and its downloads don't carry the first Profile's cookies.
        val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
        await("work active") { browser.active.value?.profile?.id == work.id }
        await("work has none") { browser.downloads.value.isEmpty() }
        onMain { browser.startDownload(work, file) }
        await("work listed") { browser.downloads.value.size == 1 }
        assertFalse(downloader.started[1].cookies.orEmpty().contains(cookie))

        // Removing a running download stops it. A file that isn't a web address is never started.
        val running = onMain { browser.downloads.value.single() }
        onMain { browser.removeDownload(running) }
        await("removed") { browser.downloads.value.isEmpty() }
        assertEquals(listOf(running.downloadId), downloader.cancelled.toList())
        onMain { browser.startDownload(work, file.copy(url = "blob:https://example.com/abc")) }
        Thread.sleep(300)
        assertEquals(2, downloader.started.size)
    }

    @Test
    fun aDownloadThatRedirectsToAnotherSiteDoesNotTakeTheFirstSitesCookiesAlong() {
        val browser = newBrowser()
        val personal = active(browser).profile
        val session = "jrny_session=${System.nanoTime()}"
        val set = java.util.concurrent.CountDownLatch(1)
        onMain { WebProfile.of(personal.id).cookieManager.setCookie("https://example.com", "$session; Max-Age=600; Secure; Path=/") { set.countDown() } }
        assertTrue(set.await(5, java.util.concurrent.TimeUnit.SECONDS))
        downloader.redirects["https://example.com/get/report"] = "https://files.example.net/report.pdf"

        onMain { browser.startDownload(personal, DownloadRequest("https://example.com/get/report", "TestAgent", "report.pdf", "application/pdf", -1)) }
        await("started") { downloader.started.size == 1 }

        // The download service starts at the other site, without example.com's session cookie.
        val started = downloader.started.single()
        assertEquals("https://files.example.net/report.pdf", started.request.url)
        assertFalse(started.cookies.orEmpty().contains(session))
        // The Profile's list still names the address the page linked.
        await("listed") { browser.downloads.value.size == 1 }
        assertEquals("https://example.com/get/report", onMain { browser.downloads.value.single().url })
    }

    @Test
    fun removingADownloadStopsItIfItIsStillRunningWhateverTheListLastShowed() {
        val browser = newBrowser()
        val personal = active(browser).profile
        onMain { browser.startDownload(personal, DownloadRequest("https://example.com/a.zip", "", "a.zip", "application/zip", -1)) }
        onMain { browser.startDownload(personal, DownloadRequest("https://example.com/b.zip", "", "b.zip", "application/zip", -1)) }
        await("listed") { browser.downloads.value.size == 2 }
        val (b, a) = onMain { browser.downloads.value }
        downloader.status[a.downloadId] = DownloadStatus.Done(10)

        onMain { browser.removeDownload(a) }
        onMain { browser.removeDownload(b) }
        await("removed") { browser.downloads.value.isEmpty() }
        // Only the one that was still running is cancelled. The finished file stays.
        assertEquals(listOf(b.downloadId), downloader.cancelled.toList())
    }

    @Test
    fun deletingAProfileEmptiesItsListOfDownloads() {
        val browser = newBrowser()
        val personal = active(browser).profile
        val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
        await("work active") { browser.active.value?.profile?.id == work.id }
        onMain { browser.startDownload(work, DownloadRequest("https://example.com/a.zip", "", "a.zip", "application/zip", -1)) }
        await("listed") { browser.downloads.value.size == 1 }

        onMain { browser.delete(work) }
        await("personal active") { browser.active.value?.profile?.id == personal.id }
        assertEquals(emptyList<Download>(), runBlocking { repositories.downloads.downloads(work.id).first() })
    }

    @Test
    fun aPrivateTabHasItsOwnCookiesWhichGoWhenTheLastOneCloses() {
        val cookie = "jrny_private=${System.nanoTime()}"
        TestServer(mapOf("/set" to "set-cookie:$cookie", "/echo" to "echo-cookies")).use { server ->
            val browser = newBrowser()
            val normal = active(browser).selected.id
            val normalTab = show(browser, normal)

            val (first, firstTab) = openPrivate(browser)
            loadUntil(firstTab, server.url("/set")) { it == "cookie set" }

            // A second Private Tab of the Profile shares the first one's store.
            val (second, secondTab) = openPrivate(browser)
            loadUntil(secondTab, server.url("/echo")) { it.startsWith("cookies:") }
            assertTrue(onMain { secondTab.title }, onMain { secondTab.title }.contains(cookie))

            // The Profile's own Tab doesn't see it.
            loadUntil(normalTab, server.url("/echo")) { it.startsWith("cookies:") }
            assertFalse(onMain { normalTab.title }, onMain { normalTab.title }.contains(cookie))

            // Once the last Private Tab has closed, a new one starts with nothing.
            onMain { browser.closeTab(first) }
            onMain { browser.closeTab(second) }
            await("back on the normal tab") { browser.active.value?.selected?.id == normal }
            show(browser, normal)
            val (_, freshTab) = openPrivate(browser)
            loadUntil(freshTab, server.url("/echo")) { it.startsWith("cookies:") }
            assertFalse(onMain { freshTab.title }, onMain { freshTab.title }.contains(cookie))
        }
    }

    @Test
    fun aPrivateTabIsListedAfterTheSavedTabsAndLeavesNoHistory() {
        TestServer(mapOf("/secret" to "<title>Secret</title>")).use { server ->
            val browser = newBrowser()
            val personal = active(browser).profile.id
            val normal = active(browser).selected.id
            show(browser, normal)

            val (private, tab) = openPrivate(browser)
            loadUntil(tab, server.url("/secret")) { it == "Secret" }
            await("kept in memory") { browser.active.value?.selected?.title == "Secret" }

            val listed = active(browser).tabs
            assertEquals(listOf(normal, private), listed.map { it.id })
            assertEquals(listOf(false, true), listed.map { it.isPrivate })
            assertEquals(listOf(false, true), listed.map { it.selected })
            // Nothing of it is in the database or in the history.
            assertEquals(listOf(normal), runBlocking { tabs.tabs(personal).first() }.map { it.id })
            Thread.sleep(300)
            assertEquals(emptyList<Visit>(), onMain { browser.visits.value })

            // Choosing the saved Tab shows it again, and the Private Tab stays open.
            val profile = active(browser).profile
            onMain { browser.selectTab(profile, normal) }
            await("normal selected") { browser.active.value?.selected?.id == normal }
            assertEquals(2, active(browser).tabs.size)
        }
    }

    @Test
    fun aPrivateTabChosenRightAfterASavedTabStaysChosen() {
        val browser = newBrowser()
        val profile = active(browser).profile
        val normal = active(browser).selected.id
        show(browser, normal)
        val (private, _) = openPrivate(browser)
        val other = runBlocking { tabs.open(profile.id) }
        await("other listed") { browser.active.value?.tabs?.any { it.id == other } == true }

        // The saved Tab's choice is still being written when the Private Tab is chosen.
        onMain {
            browser.selectTab(profile, normal)
            browser.selectTab(profile, private)
        }
        Thread.sleep(500)
        assertEquals(private, active(browser).selected.id)

        // A saved Tab chosen last wins as before.
        onMain { browser.selectTab(profile, normal) }
        await("saved tab shown") { browser.active.value?.selected?.id == normal }
    }

    @Test
    fun anIncomingLinkNeverOpensInAPrivateTab() {
        val browser = newBrowser()
        show(browser, active(browser).selected.id)
        val (private, _) = openPrivate(browser)

        onMain { browser.openIncomingLink(titled("link")) }
        await("link shown instead of the private tab") { browser.active.value?.selected?.url == titled("link") }
        val shown = active(browser).selected
        assertFalse(shown.isPrivate)
        assertTrue(active(browser).tabs.any { it.id == private })
    }

    @Test
    fun privateTabsOutliveTheActivityButNotTheProcess() {
        val first = newBrowser()
        show(first, active(first).selected.id)
        val (private, tab) = openPrivate(first)
        load(tab, titled("kept"), "kept navigate")
        await("kept in memory") { first.active.value?.selected?.title == "kept navigate" }

        // The activity is recreated: a new BrowserState, the same process.
        onMain { first.destroy() }
        val recreated = newBrowser()
        await("private tab back") { recreated.active.value?.selected?.id == private }
        val back = show(recreated, private)
        await("reloaded") { !back.isLoading && back.title == "kept navigate" }

        // Android killed the app: nothing in memory survives, and no Private Tab comes back.
        onMain { recreated.destroy() }
        privateTabs = PrivateTabs()
        val restarted = newBrowser()
        assertTrue(active(restarted).tabs.none { it.isPrivate })
    }

    @Test
    fun closingTheAppsWindowEndsEveryPrivateTabEvenIfTheProcessLivesOn() {
        val cookie = "jrny_closed=${System.nanoTime()}"
        TestServer(mapOf("/set" to "set-cookie:$cookie", "/echo" to "echo-cookies")).use { server ->
            val first = newBrowser()
            show(first, active(first).selected.id)
            val (_, tab) = openPrivate(first)
            loadUntil(tab, server.url("/set")) { it == "cookie set" }

            // What the activity does when it finishes: the same process and PrivateTabs go on.
            onMain {
                first.closeAllPrivateTabs()
                first.destroy()
            }
            assertEquals(0, onMain { first.liveTabCount })

            val reopened = newBrowser()
            assertTrue(active(reopened).tabs.none { it.isPrivate })
            show(reopened, active(reopened).selected.id)
            // A Private Tab opened now starts with nothing of the one before.
            val (_, fresh) = openPrivate(reopened)
            loadUntil(fresh, server.url("/echo")) { it.startsWith("cookies:") }
            assertFalse(onMain { fresh.title }, onMain { fresh.title }.contains(cookie))
        }
    }

    @Test
    fun aPrivateTabStaysPrivate() {
        val browser = newBrowser()
        val personal = active(browser).profile
        val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
        runBlocking { profiles.activate(personal.id) }
        await("personal active") { browser.active.value?.profile?.id == personal.id }
        show(browser, active(browser).selected.id)
        val (_, tab) = openPrivate(browser)
        load(tab, titled("private"), "private navigate")
        await("kept in memory") { browser.active.value?.selected?.url == titled("private") }
        val private = active(browser).selected

        // It can't be moved to, or opened in, another Profile as a saved Tab.
        onMain { browser.moveTab(private, work) }
        onMain { browser.openInProfile(private, work) }
        Thread.sleep(400)
        assertEquals(personal.id, active(browser).profile.id)
        assertTrue(runBlocking { tabs.tabs(work.id).first() }.none { it.url == titled("private") })

        // Its download uses its own cookies and isn't put on the Profile's list.
        onMain { browser.startPrivateDownload(private, DownloadRequest("https://example.com/file.zip", "", "file.zip", "application/zip", -1)) }
        await("download started") { downloader.started.size == 1 }
        Thread.sleep(300)
        assertEquals(emptyList<Download>(), onMain { browser.downloads.value })

        // Deleting its Profile closes it.
        runBlocking { profiles.activate(work.id) }
        await("work active") { browser.active.value?.profile?.id == work.id }
        onMain { browser.delete(personal) }
        await("personal gone") { browser.profiles.value.none { it.id == personal.id } }
        await("private tabs closed") { privateTabs.current(personal.id) == null }
    }

    @Test
    fun suggestionsArePagesOfTheProfileThenSearchesFromTheChosenEngine() {
        val browser = newBrowser()
        val personal = active(browser).profile
        runBlocking {
            repositories.bookmarks.add(personal.id, "https://kotlinlang.org/", "Kotlin")
            repositories.history.record(personal.id, "https://example.com/kotlin-news")
            repositories.history.record(personal.id, "https://example.com/other")
        }
        await("loaded") { browser.bookmarks.value.size == 1 && browser.visits.value.size == 2 }
        onMain { browser.updateSettings { it.copy(searchEngine = SearchEngine.DuckDuckGo) } }
        val tab = active(browser).selected

        val offered = runBlocking { browser.suggest(tab, " kotlin ") }
        assertEquals(
            listOf(
                Suggestion("Kotlin", "https://kotlinlang.org/", Suggestion.Kind.Bookmark),
                Suggestion("https://example.com/kotlin-news", "https://example.com/kotlin-news", Suggestion.Kind.History),
                Suggestion("kotlin one", null, Suggestion.Kind.Search),
                Suggestion("kotlin two", null, Suggestion.Kind.Search),
            ),
            offered,
        )
        assertEquals(listOf(SearchEngine.DuckDuckGo to "kotlin"), asked.toList())
    }

    @Test
    fun whatIsTypedGoesToTheSearchEngineOnlyWhenItMay() {
        val browser = newBrowser()
        show(browser, active(browser).selected.id)
        val tab = active(browser).selected

        // An address, or text shaped like credentials, is never sent.
        assertEquals(emptyList<Suggestion>(), runBlocking { browser.suggest(tab, "https://bank.example/account?token=abc") })
        assertEquals(emptyList<Suggestion>(), runBlocking { browser.suggest(tab, "user:secret@example.com") })
        // Nor is a password typed before its "@", at a pause in typing.
        assertEquals(emptyList<Suggestion>(), runBlocking { browser.suggest(tab, "admin:hunter2") })
        assertEquals(emptyList<Suggestion>(), runBlocking { browser.suggest(tab, "   ") })

        // Nothing is sent from a Private Tab.
        openPrivate(browser)
        val private = active(browser).selected
        assertEquals(emptyList<Suggestion>(), runBlocking { browser.suggest(private, "private search") })

        // Nothing is sent with the setting off.
        onMain { browser.updateSettings { it.copy(suggestions = false) } }
        assertEquals(emptyList<Suggestion>(), runBlocking { browser.suggest(tab, "weather") })
        assertEquals(emptyList<Pair<SearchEngine, String>>(), asked.toList())

        onMain { browser.updateSettings { it.copy(suggestions = true) } }
        assertEquals(2, runBlocking { browser.suggest(tab, "weather") }.size)
        assertEquals(listOf(SearchEngine.Google to "weather"), asked.toList())
    }

    @Test
    fun safeBrowsingFollowsTheSettingAlsoForPagesAlreadyOpen() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE))
        val browser = newBrowser()
        val tab = show(browser, active(browser).selected.id)
        fun enabled(of: Tab) = onMain { WebSettingsCompat.getSafeBrowsingEnabled(of.webView.settings) }
        assertTrue(enabled(tab))

        onMain { browser.updateSettings { it.copy(safeBrowsing = false) } }
        await("off for the open page") { !WebSettingsCompat.getSafeBrowsingEnabled(tab.webView.settings) }

        // A Tab opened now starts with it off, and turning it on reaches both.
        val (_, private) = openPrivate(browser)
        assertFalse(enabled(private))
        onMain { browser.updateSettings { it.copy(safeBrowsing = true) } }
        await("on again") { WebSettingsCompat.getSafeBrowsingEnabled(tab.webView.settings) && WebSettingsCompat.getSafeBrowsingEnabled(private.webView.settings) }
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

    /** A page on 127.0.0.1, a secure origin, that asks for the camera and puts the answer in its title. */
    private val askForCamera =
        "<title>asking</title><script>navigator.mediaDevices.getUserMedia({video:true})" +
            ".then(function(s){s.getTracks().forEach(function(t){t.stop()});document.title='granted'}," +
            "function(e){document.title='denied'})</script>"

    @Test
    fun aSiteTheProfileBlockedIsRefusedWithoutAskingAndAnAllowedOneSkipsTheQuestion() {
        // Android's own prompt is a system dialog a test can't answer, so the app already has the camera.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.CAMERA)
        TestServer(mapOf("/cam" to askForCamera), loopback = true).use { server ->
            val url = server.url("/cam")
            val origin = app.jrny.browser.site.SiteOrigin.of(url)!!
            val browser = newBrowser()
            val profile = active(browser).profile
            val tab = show(browser, active(browser).selected.id)

            runBlocking { repositories.siteSettings.set(profile.id, origin, app.jrny.browser.site.SiteSetting.Camera, false) }
            load(tab, url, "denied")
            assertEquals(null, onMain { tab.permissionAsk })

            // Allowed: the window only has Android's permission left to check.
            runBlocking { repositories.siteSettings.set(profile.id, origin, app.jrny.browser.site.SiteSetting.Camera, true) }
            onMain { tab.load(url) }
            await("decided ask") { tab.permissionAsk?.decided == true }
            onMain { tab.permissionAsk!!.grant() }
            await("granted") { tab.title == "granted" }
        }
    }

    @Test
    fun aTabThatIsntOnScreenCantAsk() {
        TestServer(mapOf("/cam" to askForCamera), loopback = true).use { server ->
            val browser = newBrowser()
            val first = active(browser).selected
            val hidden = show(browser, first.id)
            // Another Tab goes on screen; the first stays live but hidden.
            val profile = active(browser).profile
            onMain { browser.openTab(profile) }
            await("second tab") { browser.active.value?.selected?.id != first.id }
            show(browser, active(browser).selected.id)

            load(hidden, server.url("/cam"), "denied")
            assertEquals(null, onMain { hidden.permissionAsk })
        }
    }


    @Test
    fun aLinkForAnotherAppThatNobodyTappedWaitsForOpen() {
        val browser = newBrowser()
        val tab = show(browser, active(browser).selected.id)
        val start = TestPages.page("<title>start</title><script>setTimeout(function(){location.href='tel:+15550100'},0)</script>")
        onMain { tab.load(start) }
        await("handoff offered") { tab.handoff != null }
        assertEquals("tel", onMain { tab.handoff!!.scheme })
        // The page stays where it was.
        assertEquals(start, onMain { tab.url })
    }

    @Test
    fun anIntentLinkForAWebPageLoadsInTheTab() {
        TestServer(mapOf("/target" to "<title>target</title>")).use { server ->
            val browser = newBrowser()
            val tab = show(browser, active(browser).selected.id)
            val target = server.url("/target")
            val hostAndPath = target.removePrefix("http://")
            val start = TestPages.page("<title>start</title><script>setTimeout(function(){location.href='intent://$hostAndPath#Intent;scheme=http;end'},0)</script>")
            onMain { tab.load(start) }
            await("target loaded") { tab.title == "target" }
            assertEquals(null, onMain { tab.handoff })
        }
    }

    @Test
    fun openInNewTabKeepsTheShownTabShown() {
        val browser = newBrowser()
        val first = active(browser).selected
        show(browser, first.id)
        onMain { browser.openInNewTab(first, "https://example.com/next") }
        await("opened behind") { browser.active.value?.tabs?.any { it.url == "https://example.com/next" } == true }
        assertEquals(first.id, active(browser).selected.id)
        assertEquals("https://example.com/next", onMain { browser.openedBehind?.url })
    }

    @Test
    fun aDataFileIsSavedByOkapiAndListedInItsProfile() {
        val browser = newBrowser()
        val shown = active(browser).selected
        val tab = show(browser, shown.id)
        val request = DownloadRequest("data:text/csv;base64,YSxiCjEsMgo=", "", "table.csv", "text/csv", -1)
        onMain { browser.savePageFile(shown, tab, request) }
        await("saved") { browser.pageFileSaved == ("table.csv" to true) }
        assertEquals("a,b\n1,2\n", String(downloader.saved.values.single().second))
        await("listed") { browser.downloads.value.size == 1 }
        val listed = onMain { browser.downloads.value.single() }
        assertEquals("table.csv", listed.fileName)
        assertTrue(listed.fileUri != null)
    }

    @Test
    fun aBlobFileIsReadOutOfThePageOnlyOnceThePersonAgrees() {
        // The page makes a file, offers it, and drops its own address for it a moment later, as
        // file-saving scripts do.
        val maker = "<title>maker</title><a id=a download='notes.txt'>save</a><script>" +
            "var u=URL.createObjectURL(new Blob(['hello from the page'],{type:'text/plain'}));" +
            "var a=document.getElementById('a');a.href=u;a.click();setTimeout(function(){URL.revokeObjectURL(u)},1000)</script>"
        TestServer(mapOf("/maker" to maker)).use { server ->
            val browser = newBrowser()
            val shown = active(browser).selected
            val tab = show(browser, shown.id)
            onMain { tab.load(server.url("/maker")) }
            await("offered") { tab.download != null }
            // The link names the file.
            await("named") { tab.download?.fileName == "notes.txt" }
            val request = onMain { tab.download!! }
            assertTrue(request.fromPage)
            // Long after the page dropped its address, the person agrees.
            Thread.sleep(2000)
            assertTrue(downloader.saved.isEmpty())
            onMain {
                browser.savePageFile(shown, tab, request)
                tab.dismissDownload(kept = true)
            }
            await("saved") { browser.pageFileSaved != null }
            assertEquals(true, onMain { browser.pageFileSaved?.second })
            assertEquals("hello from the page", String(downloader.saved.values.single().second))
        }
    }

    @Test
    fun aClosedTabCanBeOpenedAgainInItsPlace() {
        TestServer(mapOf("/first" to "<title>first</title>", "/second" to "<title>second</title>")).use { server ->
            val browser = newBrowser()
            val profile = active(browser).profile
            val first = active(browser).selected
            load(show(browser, first.id), server.url("/first"), "first")
            onMain { browser.openTab(profile) }
            await("second tab") { browser.active.value?.tabs?.size == 2 }
            val second = active(browser).selected
            load(show(browser, second.id), server.url("/second"), "second")
            awaitSaved(browser, first.id, server.url("/first"), "first")

            onMain { browser.closeTab(first.id) }
            await("undo offered") { browser.justClosed?.tabId == first.id }
            await("listed") { browser.recentlyClosed.value?.second.orEmpty().size == 1 }
            onMain { browser.undoClose() }
            await("back in its place") { browser.active.value?.tabs?.firstOrNull()?.id == first.id }
            assertEquals(first.id, active(browser).selected.id)
            assertEquals(server.url("/first"), active(browser).selected.url)
            await("off the list") { browser.recentlyClosed.value?.second.orEmpty().isEmpty() }
        }
    }

    @Test
    fun aClosedTabReopenedFromTheListCantBeReopenedAgainWithUndo() {
        TestServer(mapOf("/page" to "<title>page</title>")).use { server ->
            val browser = newBrowser()
            val profile = active(browser).profile
            val first = active(browser).selected
            load(show(browser, first.id), server.url("/page"), "page")
            onMain { browser.openTab(profile) }
            await("second tab") { browser.active.value?.tabs?.size == 2 }
            show(browser, active(browser).selected.id)
            awaitSaved(browser, first.id, server.url("/page"), "page")

            onMain { browser.closeTab(first.id) }
            await("listed") { browser.recentlyClosed.value?.second.orEmpty().size == 1 }
            val closed = onMain { browser.recentlyClosed.value!!.second.single() }
            onMain { browser.reopenClosed(closed) }
            await("reopened") { browser.active.value?.tabs?.count { it.url == server.url("/page") } == 1 }
            await("no undo left") { browser.justClosed == null }
            onMain { browser.undoClose() }
            Thread.sleep(500)
            assertEquals(1, onMain { browser.active.value!!.tabs.count { it.url == server.url("/page") } })
        }
    }

    @Test
    fun aPrivateTabOrAnEmptyTabIsNeverRemembered() {
        val browser = newBrowser()
        val profile = active(browser).profile
        val empty = active(browser).selected
        onMain { browser.openTab(profile) }
        await("second tab") { browser.active.value?.tabs?.size == 2 }
        onMain { browser.closeTab(empty.id) }
        val (privateId, privateTab) = openPrivate(browser)
        load(privateTab, titled("secret"), "secret navigate")
        onMain { browser.closeTab(privateId) }
        Thread.sleep(500)
        assertEquals(null, onMain { browser.justClosed })
        assertTrue(onMain { browser.recentlyClosed.value?.second.orEmpty().isEmpty() })
    }

    @Test
    fun aShortcutOpensInItsOwnProfileAndAnUnknownOneOpensNothing() {
        val browser = newBrowser()
        val personal = active(browser).profile
        val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
        val shortcut = runBlocking { repositories.shortcuts.add(work.id, "https://example.com/work", "Work page") }!!
        // Personal is the Active Profile; the shortcut still opens in Work.
        assertEquals(personal.id, active(browser).profile.id)
        assertTrue(runBlocking { browser.openShortcut(shortcut.id) })
        await("work active") { browser.active.value?.profile?.id == work.id }
        await("page open") { browser.active.value?.selected?.url == "https://example.com/work" }
        assertFalse(runBlocking { browser.openShortcut("okapi-not-a-shortcut") })
    }

    @Test
    fun clearingCookiesSignsTheProfileOutAndLeavesOthersAlone() {
        val browser = newBrowser()
        val personal = active(browser).profile
        val work = runBlocking { profiles.create("Work", ProfileColor.Teal, ProfileIcon.Build) }
        val site = "https://example.com"
        val set = java.util.concurrent.CountDownLatch(2)
        onMain {
            WebProfile.of(personal.id).cookieManager.setCookie(site, "a=1; Max-Age=600; Secure") { set.countDown() }
            WebProfile.of(work.id).cookieManager.setCookie(site, "b=2; Max-Age=600; Secure") { set.countDown() }
        }
        assertTrue(set.await(5, java.util.concurrent.TimeUnit.SECONDS))
        onMain { browser.clearBrowsingData(personal, setOf(BrowsingData.Cookies, BrowsingData.Cache)) }
        await("personal signed out") { WebProfile.of(personal.id).cookieManager.getCookie(site).isNullOrEmpty() }
        assertEquals("b=2", onMain { WebProfile.of(work.id).cookieManager.getCookie(site) })
    }

}
