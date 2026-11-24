package app.jrny.browser.tab

import android.annotation.SuppressLint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jrny.browser.TestPages.await
import app.jrny.browser.TestPages.onMain
import app.jrny.browser.TestPages.page
import app.jrny.browser.TestPages.runInPage
import app.jrny.browser.TestPages.titled
import app.jrny.browser.TestServer
import app.jrny.browser.engine.Engine
import org.mozilla.geckoview.GeckoSession
import app.jrny.browser.profile.ProfileId
import app.jrny.browser.profile.WebProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device or emulator. Uses `data:` pages, so it needs no network. */
@SuppressLint("RequiresFeature")
@RunWith(AndroidJUnit4::class)
class TabTest {

    private val tabs = mutableListOf<Tab>()

    @After
    fun destroyTabs() = onMain { tabs.forEach(Tab::destroy) }

    @Test
    fun aTabUsesItsProfilesWebData() {
        val tab = newTab("test-binding")
        assertEquals("jrny-profile-test-binding", onMain { tab.session.settings.contextId })
        assertFalse(onMain { tab.session.settings.usePrivateMode })
    }

    @Test
    fun profilesDontSeeEachOthersCookies() {
        TestServer(mapOf("/set" to "set-cookie:jrny_test=a", "/echo" to "echo-cookies")).use { server ->
            val a = newTab("test-iso-a")
            val b = newTab("test-iso-b")
            onMain { a.load(server.url("/set")) }
            awaitPage(a, server.url("/set"), "cookie set")
            onMain { a.load(server.url("/echo")) }
            awaitPage(a, server.url("/echo"), "cookies: jrny_test=a")
            onMain { b.load(server.url("/echo")) }
            await("B asked") { !b.isLoading && b.url == server.url("/echo") && b.title.startsWith("cookies:") }
            assertEquals("B sees A's cookie", "cookies:", onMain { b.title.trim() })
        }
    }

    @Test
    fun loadGoBackGoForwardAndReload() {
        val tab = newTab("test-nav")
        val one = titled("one")
        val two = titled("two")

        onMain { tab.load(one) }
        awaitPage(tab, one, "one navigate")
        assertFalse(onMain { tab.canGoBack })

        onMain { tab.load(two) }
        awaitPage(tab, two, "two navigate")
        assertTrue(onMain { tab.canGoBack })
        assertFalse(onMain { tab.canGoForward })

        onMain { tab.goBack() }
        awaitHistoryStep(tab, one, "one")
        assertTrue(onMain { tab.canGoForward })

        onMain { tab.goForward() }
        awaitHistoryStep(tab, two, "two")

        // The page itself reports a reload, so this fails if reload() does nothing.
        onMain { tab.reload() }
        awaitPage(tab, two, "two reload")
    }

    @Test
    fun theAddressIsSetAsSoonAsALoadStarts() {
        val tab = newTab("test-address")
        assertEquals(null, onMain { tab.url })
        val one = titled("one")
        assertEquals(one, onMain { tab.load(one); tab.url })
    }

    @Test
    fun linksToOtherSchemesDontLoad() {
        val tab = newTab("test-schemes")
        val start = page(
            "<title>start</title><script>" +
                "setTimeout(function(){location.href='mailto:someone@example.com'},0);" +
                "setTimeout(function(){location.href='intent://example.com#Intent;scheme=https;end'},100);" +
                "setTimeout(function(){document.title='done'},300)</script>",
        )
        onMain { tab.load(start) }
        awaitPage(tab, start, "done")
        Thread.sleep(500)
        // Had either link loaded, the Tab would show an error page at that address.
        assertEquals(start, onMain { tab.url })
        assertFalse(onMain { tab.crashed })
    }

    @Test
    fun httpPagesLoad() {
        // A browser must load the http:// pages people ask for (network_security_config.xml).
        TestServer(mapOf("/" to "<title>http ok</title>")).use { server ->
            val tab = newTab("test-http")
            val address = server.url("/")
            onMain { tab.load(address) }
            try {
                await("http page loaded") { !tab.isLoading && tab.url == address && tab.title == "http ok" }
            } catch (e: AssertionError) {
                val state = onMain { "url=${tab.url} title=${tab.title} loading=${tab.isLoading}" }
                throw AssertionError("${e.message}: $state")
            }
        }
    }

    @Test
    fun pageConsoleOutputStaysOutOfTheSystemLog() {
        val tab = newTab("test-console")
        val nonce = System.nanoTime().toString()
        // The page puts the nonce together itself, so it is only in what it logs, not in its address.
        val start = page("<script>console.log('jrny-console-'+'${nonce.reversed()}'.split('').reverse().join(''));document.title='logged'</script>")
        onMain { tab.load(start) }
        awaitPage(tab, start, "logged")
        // A line the test writes itself proves this process can read its own log.
        Log.i("JrnyTabTest", "jrny-control-$nonce")
        Thread.sleep(500)

        val log = readOwnLog()
        assertTrue("the test can read its own log", log.contains("jrny-control-$nonce"))
        assertFalse("page console output was logged", log.contains("jrny-console-$nonce"))
    }

    @Test
    fun aDeadContentProcessGivesTheTabANewSession() {
        val visits = java.util.Collections.synchronizedList(mutableListOf<String>())
        val tab = onMain {
            Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-crash")), onVisited = { visits += it })
                .also { tabs += it }
        }
        val one = titled("one")
        onMain { tab.load(one) }
        awaitPage(tab, one, "one navigate")
        val first = onMain { tab.session }

        // Gecko's own address for crashing the page's content process.
        onMain { first.load(GeckoSession.Loader().uri("about:crashcontent").flags(GeckoSession.LOAD_FLAGS_BYPASS_LOAD_URI_DELEGATE)) }
        await("content process gone") { tab.crashed }
        assertTrue(onMain { tab.session } !== first)

        // The reload brings the same page back in the new session, with its title kept meanwhile.
        onMain { tab.reload() }
        assertEquals("one navigate", onMain { tab.title })
        awaitPage(tab, one, "one navigate")
        assertFalse(onMain { tab.crashed })
        // It is the same visit as before, not a new one.
        assertEquals(listOf(one), visits.toList())
    }

    @Test
    fun aPageThatFailsToLoadKeepsItsAddressAndTakesNoTitleFromTheErrorPage() {
        val tab = newTab("test-load-failure")
        val one = titled("one")
        onMain { tab.load(one) }
        awaitPage(tab, one, "one navigate")

        // Nothing listens on this port. Okapi's error page shows, and its title isn't the page's.
        val unreachable = "http://127.0.0.1:9/"
        onMain { tab.load(unreachable) }
        await("load failed") { !tab.isLoading && tab.url == unreachable }
        Thread.sleep(300)
        assertEquals("", onMain { tab.title })
        assertEquals(unreachable, onMain { tab.url })

        // The next page that loads has its title again.
        val two = titled("two")
        onMain { tab.load(two) }
        awaitPage(tab, two, "two navigate")
    }

    @Test
    fun onlyPagesThePersonGoesToCountAsVisits() {
        val visits = java.util.Collections.synchronizedList(mutableListOf<String>())
        val tab = onMain {
            Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-visits")), onVisited = { visits += it })
                .also { tabs += it }
        }
        val one = titled("one")
        val two = titled("two")

        // A saved page coming back is not a visit, and neither is reloading it. The saved title
        // differs from the page's, so the wait below ends only once the page has really loaded.
        onMain { tab.restore(one, "saved", 0) }
        awaitPage(tab, one, "one navigate")
        onMain { tab.reload() }
        awaitPage(tab, one, "one reload")
        assertEquals(emptyList<String>(), visits.toList())

        // Going to a page is a visit. Going back to the one before, and forward again, is not.
        onMain { tab.load(two) }
        awaitPage(tab, two, "two navigate")
        onMain { tab.goBack() }
        awaitHistoryStep(tab, one, "one")
        onMain { tab.goForward() }
        awaitHistoryStep(tab, two, "two")
        assertEquals(listOf(two), visits.toList())
    }

    @Test
    fun aFileIsOfferedAndThePageBeforeItStays() {
        val pages = mapOf("/page" to "<title>page</title><a id=f href=/file>file</a>", "/file" to "download:../notes 2026.txt:hello")
        TestServer(pages).use { server ->
            val tab = newTab("test-download")
            onMain { tab.load(server.url("/page")) }
            awaitPage(tab, server.url("/page"), "page")

            runInPage(tab, "document.getElementById('f').click()")
            await("file offered") { tab.download != null }
            val offered = onMain { tab.download!! }
            assertEquals(server.url("/file"), offered.url)
            // The site's name for the file, without the folders it tried to add.
            assertTrue(offered.fileName, offered.fileName.endsWith("notes 2026.txt") && "/" !in offered.fileName)
            assertEquals(5L, offered.size)
            assertEquals(server.url("/page"), offered.page)
            assertTrue(offered.supported)

            // The Tab still shows the page, not a load that never ends.
            await("page stays") { !tab.isLoading && tab.url == server.url("/page") && tab.title == "page" }
            onMain { tab.dismissDownload() }
            assertEquals(null, onMain { tab.download })

            // Typing the file's address offers it too. The Tab goes back to the page it shows, and
            // no page is named as the one that linked the file.
            onMain { tab.load(server.url("/file")) }
            await("file offered again") { tab.download != null }
            assertEquals(null, onMain { tab.download!!.page })
            await("page still stays") { !tab.isLoading && tab.url == server.url("/page") && tab.title == "page" }
        }
    }

    @Test
    fun aFileInANewTabLeavesTheTabEmpty() {
        TestServer(mapOf("/file" to "download:notes.txt:hello")).use { server ->
            val tab = newTab("test-download-new-tab")
            onMain { tab.load(server.url("/file")) }
            await("file offered") { tab.download != null }
            await("no page") { !tab.isLoading && tab.url == null }
        }
    }

    @Test
    fun aNewAddressWithinTheSamePageIsAVisitWithThatPagesTitle() {
        // As a site does when it swaps in its preferred address, or an app page moves on.
        val pages = mapOf("/app" to "<title>App</title><button id=go onclick=\"history.pushState({}, '', '/app/next')\">go</button>")
        TestServer(pages).use { server ->
            val visits = java.util.Collections.synchronizedList(mutableListOf<String>())
            val seen = java.util.Collections.synchronizedList(mutableListOf<Pair<String, String>>())
            val tab = onMain {
                Tab(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    WebProfile.of(ProfileId("test-same-page")),
                    onVisited = { visits += it },
                    onPageSeen = { url, title -> seen += url to title },
                ).also { tabs += it }
            }
            onMain { tab.load(server.url("/app")) }
            awaitPage(tab, server.url("/app"), "App")

            runInPage(tab, "document.getElementById('go').click()")
            await("moved on") { tab.url == server.url("/app/next") }
            assertEquals("App", onMain { tab.title })
            assertEquals(listOf(server.url("/app"), server.url("/app/next")), visits.toList())
            assertTrue(seen.toList().toString(), seen.contains(server.url("/app/next") to "App"))
        }
    }

    @Test
    fun aDesktopSiteGetsTheDesktopAgentAndAMobileOneTheTabsOwn() {
        TestServer(mapOf("/agent" to "echo-agent")).use { server ->
            val url = server.url("/agent")
            val site = app.jrny.browser.site.SiteOrigin.of(url)!!
            val tab = onMain {
                Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId("test-desktop")), desktopFor = { it == site })
                    .also { tabs += it }
            }
            onMain { tab.load(url) }
            await("desktop agent") { !tab.isLoading && tab.title.startsWith("agent: ") }
            val desktop = onMain { tab.title }
            assertTrue(desktop, "X11; Linux x86_64" in desktop && "Android" !in desktop)
            assertTrue(onMain { tab.desktopSite })

            // Turned off for the site: the page loads again with the Tab's own agent.
            onMain { tab.setDesktopSite(site, false) }
            await("mobile agent") { !tab.isLoading && "Android" in tab.title }
            assertFalse(onMain { tab.desktopSite })
        }
    }

    @Test
    fun textSizeReachesEveryPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            onMain { Engine.apply(context, textZoom = 150, safeBrowsing = true, pagesDark = false) }
            assertEquals(1.5f, onMain { Engine.runtime(context).settings.fontSizeFactor })
        } finally {
            onMain { Engine.apply(context, textZoom = 100, safeBrowsing = true, pagesDark = false) }
        }
    }

    @Test
    fun darkPagesDarkenOnlyAPageWithoutADarkTheme() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pages = mapOf(
            "/light" to "<title>light</title><body style='background:#fff'><p>text</p></body>",
            "/themed" to "<title>themed</title><style>body{background:#fff}@media (prefers-color-scheme: dark){body{background:#111}}</style><p>text</p>",
        )
        val darkened = "getComputedStyle(document.documentElement).filter.indexOf('invert')>=0"
        TestServer(pages).use { server ->
            try {
                onMain { Engine.apply(context, textZoom = 100, safeBrowsing = true, pagesDark = true) }
                val tab = newTab("test-dark")
                onMain { tab.darkPages = true }
                onMain { tab.load(server.url("/light")) }
                awaitPage(tab, server.url("/light"), "light")
                awaitInPage("light page darkened", tab, darkened, "true")

                // A page with a dark theme of its own shows that, untouched.
                onMain { tab.load(server.url("/themed")) }
                awaitPage(tab, server.url("/themed"), "themed")
                Thread.sleep(800)
                assertEquals("false", runInPage(tab, darkened))

                // Turned off, the light page is light again.
                onMain { tab.load(server.url("/light")) }
                awaitPage(tab, server.url("/light"), "light")
                awaitInPage("darkened again", tab, darkened, "true")
                onMain { tab.darkPages = false }
                awaitInPage("light again", tab, darkened, "false")
            } finally {
                onMain { Engine.apply(context, textZoom = 100, safeBrowsing = true, pagesDark = false) }
            }
        }
    }

    /** Waits until [script], run in [tab]'s page, gives [expected]. */
    private fun awaitInPage(what: String, tab: Tab, script: String, expected: String, timeoutMs: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (runInPage(tab, script) != expected) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for: $what")
            Thread.sleep(100)
        }
    }

    private fun newTab(profileId: String): Tab = onMain {
        Tab(InstrumentationRegistry.getInstrumentation().targetContext, WebProfile.of(ProfileId(profileId)))
            .also { tabs += it }
    }

    /**
     * Waits for Back or Forward to show [url] again, the page named [name]: as it was, from the
     * back-forward cache (with the title it had), or loaded again ("back_forward").
     */
    private fun awaitHistoryStep(tab: Tab, url: String, name: String) = try {
        await("$name again at $url") { !tab.isLoading && tab.url == url && tab.title.startsWith("$name ") }
    } catch (e: AssertionError) {
        throw AssertionError("${e.message}, but the Tab has " + onMain { "title=${tab.title} loading=${tab.isLoading} url=${tab.url}" })
    }

    private fun awaitPage(tab: Tab, url: String, title: String) = try {
        await("$title at $url") { !tab.isLoading && tab.url == url && tab.title == title }
    } catch (e: AssertionError) {
        throw AssertionError("${e.message}, but the Tab has " + onMain { "title=${tab.title} loading=${tab.isLoading} url=${tab.url}" })
    }

    private fun readOwnLog(): String =
        Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "raw")).inputStream.bufferedReader().readText()
}
